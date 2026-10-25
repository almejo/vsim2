package cl.almejo.vsim.automation.edit;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.edit.EditOperation.CreateComponent;
import cl.almejo.vsim.automation.edit.EditOperation.PlacePart;
import cl.almejo.vsim.automation.edit.EditOperation.SetComponentShape;
import cl.almejo.vsim.core.component.appearance.SymbolTemplates;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The AI's «Cambiar forma»: {@code SetComponentShape} on one instance or on a definition, all or nothing. */
class SetComponentShapeTest {

    private static final String DOCUMENT = "circuit-1";

    private final FakeCircuitHost host = new FakeCircuitHost();
    private EditService service;
    private FakeCircuitHost.Circuit circuit;
    private int nextOperationId = 1;
    private String instanceId;
    private String definitionId;

    @BeforeEach
    void anInverterComponentWithOneInstance() {
        circuit = host.open(DOCUMENT);
        service = new EditService(host, new CopyOnWriteArrayList<>(), Duration.ofSeconds(5), Runnable::run);
        EditReceipt parts = service.edit(request(
                new PlacePart(Optional.of("a"), "input-pin", 0, 1, Rotation.R0, Map.of(), "A"),
                new PlacePart(Optional.of("not"), "not", 8, 0, Rotation.R0, Map.of(), ""),
                new PlacePart(Optional.of("y"), "output-pin", 18, 1, Rotation.R0, Map.of(), "Y"),
                new EditOperation.Connect(PinTarget.named("new:a", "pin"), PinTarget.named("new:not", "in"), List.of()),
                new EditOperation.Connect(PinTarget.named("new:not", "out"), PinTarget.named("new:y", "pin"),
                        List.of())));
        EditReceipt created = service.edit(request(new CreateComponent(Optional.empty(), "Inversor",
                List.copyOf(parts.aliases().values()), Map.of(), true)));
        instanceId = created.components().getFirst().instanceId().orElseThrow();
        definitionId = created.components().getFirst().definitionId();
    }

    @Test
    void aTemplateOnOneInstanceGivesItAnOwnShapeAndLeavesTheDefinition() {
        ComponentDefinition before = definition();

        service.edit(request(shape(Optional.of(instanceId), Optional.empty(),
                Optional.of(SymbolTemplates.Template.RECTANGLE), List.of(), false)));

        assertThat(instance().appearance()).isPresent();
        assertThat(instance().appearance().get().drawing()).isPresent();
        assertThat(definition()).isEqualTo(before);
    }

    @Test
    void aTemplateOnTheDefinitionChangesItAndIsOneUndoStep() {
        ComponentDefinition before = definition();

        service.edit(request(shape(Optional.empty(), Optional.of("Inversor"),
                Optional.of(SymbolTemplates.Template.ALU), List.of(), false)));

        assertThat(definition().appearance()).isPresent();
        assertThat(instance().appearance()).isEmpty();
        circuit.history().undo();
        assertThat(definition()).isEqualTo(before);
        assertThat(circuit.history().canUndo()).isTrue();
    }

    @Test
    void clearGivesTheInstanceBackTheShapeOfItsDefinition() {
        service.edit(request(shape(Optional.of(instanceId), Optional.empty(),
                Optional.of(SymbolTemplates.Template.TRIANGLE), List.of(), false)));

        service.edit(request(shape(Optional.of(instanceId), Optional.empty(), Optional.empty(), List.of(), true)));

        assertThat(instance().appearance()).isEmpty();
    }

    @Test
    void aMovedPinIsPlacedWhereTheCallSaysAndItsWireFollows() {
        ComponentDefinition inverter = definition();
        int height = inverter.size().height();
        EditReceipt lamp = service.edit(request(
                new PlacePart(Optional.of("lamp"), "led", 40, -2, Rotation.R0, Map.of(), ""),
                new EditOperation.Connect(PinTarget.named(instanceId, "Y"), PinTarget.named("new:lamp", "in"),
                        List.of())));
        String wire = lamp.connections().getFirst().wireIds().getFirst();

        service.edit(request(shape(Optional.of(instanceId), Optional.empty(), Optional.empty(),
                List.of(new SetComponentShape.PinPosition("Y", inverter.size().width(), height)), false)));

        assertThat(instance().appearance().orElseThrow().pins()).anySatisfy(pin ->
                assertThat(pin.offset()).isEqualTo(new GridPoint(inverter.size().width(), height)));
        assertThat(circuit.document().element(new ElementId(wire))).isPresent();
    }

    @Test
    void aPinThatWouldLandOnAnotherNetIsRefusedWholeBecauseTheAiNeverLoosensAPin() {
        ComponentDefinition inverter = definition();
        GridPoint origin = instance().position();
        service.edit(request(
                new PlacePart(Optional.of("wired"), "led", 40, -2, Rotation.R0, Map.of(), ""),
                new EditOperation.Connect(PinTarget.named(instanceId, "Y"), PinTarget.named("new:wired", "in"),
                        List.of())));
        // Every orthogonal arrival at the destination would pass another pin: the wire cannot follow.
        GridPoint destination = new GridPoint(origin.x() + inverter.size().width(),
                origin.y() + inverter.size().height());
        for (GridPoint around : List.of(destination.translate(-1, 0), destination.translate(1, 0),
                destination.translate(0, -1), destination.translate(0, 1))) {
            service.edit(request(placedWithItsPinAt(around)));
        }
        long version = circuit.version().value();

        AutomationException refusal = refused(shape(Optional.of(instanceId), Optional.empty(), Optional.empty(),
                List.of(new SetComponentShape.PinPosition("Y", inverter.size().width(), inverter.size().height())),
                false));

        assertThat(refusal.error()).isEqualTo(AutomationError.INVALID_PARAMETER);
        assertThat(refusal.getMessage()).contains("another net");
        assertThat(circuit.version().value()).isEqualTo(version);
        assertThat(instance().appearance()).isEmpty();
    }

    @Test
    void twoPinsOnTheSamePointAreRefusedAndNothingChanges() {
        ComponentDefinition inverter = definition();
        GridPoint other = inverter.pinout().getFirst().spec().position();
        long version = circuit.version().value();

        AutomationException refusal = refused(shape(Optional.of(instanceId), Optional.empty(), Optional.empty(),
                List.of(new SetComponentShape.PinPosition(inverter.pinout().get(1).spec().name(), other.x(),
                        other.y())), false));

        assertThat(refusal.error()).isEqualTo(AutomationError.INVALID_PARAMETER);
        assertThat(circuit.version().value()).isEqualTo(version);
        assertThat(instance().appearance()).isEmpty();
    }

    @Test
    void anUnknownPinNameNamesThePinsTheComponentHas() {
        AutomationException refusal = refused(shape(Optional.of(instanceId), Optional.empty(), Optional.empty(),
                List.of(new SetComponentShape.PinPosition("nowhere", 0, 0)), false));

        assertThat(refusal.error()).isEqualTo(AutomationError.INVALID_PIN);
        assertThat(refusal.getMessage()).contains("A").contains("Y");
    }

    @Test
    void theTargetIsEitherAnInstanceOrADefinitionNeverBothNorNeither() {
        assertThat(refused(shape(Optional.of(instanceId), Optional.of("Inversor"),
                Optional.of(SymbolTemplates.Template.AND), List.of(), false)).error())
                .isEqualTo(AutomationError.INVALID_PARAMETER);
        assertThat(refused(shape(Optional.empty(), Optional.empty(), Optional.of(SymbolTemplates.Template.AND),
                List.of(), false)).error()).isEqualTo(AutomationError.INVALID_PARAMETER);
        assertThat(refused(shape(Optional.of(instanceId), Optional.empty(), Optional.empty(), List.of(), false))
                .error()).isEqualTo(AutomationError.INVALID_PARAMETER);
    }

    @Test
    void movingAPinOfADefinitionUsedInsideAnotherNamesTheContainingComponent() {
        ComponentDefinition inner = definition();
        circuit.document().putDefinition(new ComponentDefinition("outer", "Externo", "", new GridSize(6, 4), List.of(),
                new CircuitFragment(List.of(new Component(new ElementId("inside"),
                        SubcircuitTypes.typeIdOf(definitionId), GridPoint.ORIGIN, Rotation.R0, Map.of(), "")),
                        List.of(), List.of())));
        circuit.document().add(new Component(new ElementId("placedOuter"), SubcircuitTypes.typeIdOf("outer"),
                new GridPoint(60, 60), Rotation.R0, Map.of(), ""));
        ComponentDefinition before = definition();

        AutomationException refusal = refused(shape(Optional.empty(), Optional.of(inner.name()), Optional.empty(),
                List.of(new SetComponentShape.PinPosition("Y", inner.size().width(), 1)), false));

        assertThat(refusal.error()).isEqualTo(AutomationError.INVALID_PARAMETER);
        assertThat(refusal.getMessage()).contains("Externo").contains(inner.name());
        assertThat(definition()).isEqualTo(before);
    }

    @Test
    void aPartThatIsNotAUserComponentIsRefused() {
        EditReceipt gate = service.edit(request(new PlacePart(Optional.of("gate"), "nand2", 30, 30, Rotation.R0,
                Map.of(), "")));

        assertThat(refused(shape(Optional.of(gate.aliases().get("gate")), Optional.empty(),
                Optional.of(SymbolTemplates.Template.AND), List.of(), false)).error())
                .isEqualTo(AutomationError.INVALID_REFERENCE);
    }

    // --- helpers ---

    /** An input pin whose connection point is at {@code point}. */
    private PlacePart placedWithItsPinAt(GridPoint point) {
        Component probe = circuit.registry().require("input-pin").newComponent(new ElementId("probe"),
                GridPoint.ORIGIN, Rotation.R0, Map.of(), "");
        GridPoint offset = circuit.registry().typeOf(probe).pinLocation(probe, 0);
        return new PlacePart(Optional.empty(), "input-pin", point.x() - offset.x(), point.y() - offset.y(),
                Rotation.R0, Map.of(), "");
    }

    private ComponentDefinition definition() {
        return circuit.document().definition(definitionId).orElseThrow();
    }

    private Component instance() {
        return circuit.document().component(new ElementId(instanceId)).orElseThrow();
    }

    private static SetComponentShape shape(Optional<String> instance, Optional<String> definition,
                                           Optional<SymbolTemplates.Template> template,
                                           List<SetComponentShape.PinPosition> pins, boolean clear) {
        return new SetComponentShape(instance, definition, template, pins, clear);
    }

    private AutomationException refused(EditOperation operation) {
        List<AutomationException> refusals = new ArrayList<>();
        assertThatThrownBy(() -> service.edit(request(operation)))
                .isInstanceOfSatisfying(AutomationException.class, refusals::add);
        return refusals.getFirst();
    }

    private EditRequest request(EditOperation... operations) {
        return new EditRequest(new DocumentId(DOCUMENT), circuit.version(), "op-" + nextOperationId++,
                Optional.empty(), List.of(operations), false);
    }
}
