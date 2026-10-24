package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.AddWireCommand;
import cl.almejo.vsim.core.command.ChangeAppearanceCommand;
import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.appearance.ComponentAppearance;
import cl.almejo.vsim.core.component.appearance.PinPlacement;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.netlist.PinRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The session marks of spec §6.4: the pins «Aplicar y conectar a mano» left unconnected stay marked until they are
 * on a net with another pin again or the command is undone.
 */
class OpenDocumentSessionMarksTest {
    private static final GridPoint LOOSENED_PIN = new GridPoint(16, 12);

    private final ComponentDefinition inverter = ExampleDefinitions.inverter();
    private OpenDocument openDocument;
    private Component instance;

    @BeforeEach
    void create() {
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        openDocument.execute(new PutDefinitionsCommand(List.of(inverter)));
        instance = new Component(new ElementId("c20"), SubcircuitTypes.typeIdOf(inverter.id()), new GridPoint(10, 10),
                Rotation.R0, Map.of(), "U5");
        openDocument.execute(new AddElementsCommand(List.of(instance)));
        pinAt(new GridPoint(14, 30));
        openDocument.execute(new AddWireCommand(new Wire(openDocument.document().newWireId(),
                new GridPoint(14, 12), new GridPoint(14, 30))));
        // Every orthogonal arrival at (16,12) would pass another pin, so the output's wire cannot follow it there.
        pinAt(new GridPoint(15, 12));
        pinAt(new GridPoint(17, 12));
        pinAt(new GridPoint(16, 11));
        pinAt(new GridPoint(16, 13));
    }

    @AfterEach
    void close() {
        openDocument.close();
    }

    @Test
    void aPinLeftUnconnectedByAnAcceptedShapeIsMarkedAndUndoClearsTheMark() {
        assertThat(openDocument.loosenedPins()).isEmpty();

        openDocument.execute(widerShape());

        assertThat(openDocument.loosenedPins()).containsExactly(new PinRef(instance.id(), 1));
        openDocument.history().undo();
        assertThat(openDocument.loosenedPins()).isEmpty();
    }

    @Test
    void theMarkGoesAwayWhenThePinIsOnANetWithAnotherPinAgain() {
        openDocument.execute(widerShape());

        openDocument.execute(new AddWireCommand(new Wire(openDocument.document().newWireId(), new GridPoint(40, 40),
                new GridPoint(44, 40))));
        assertThat(openDocument.loosenedPins()).containsExactly(new PinRef(instance.id(), 1));
        openDocument.execute(new AddWireCommand(new Wire(openDocument.document().newWireId(), LOOSENED_PIN,
                new GridPoint(16, 13))));

        assertThat(openDocument.loosenedPins()).isEmpty();
    }

    @Test
    void aDocumentListenerSeesTheMarksOfTheChangeItIsToldAbout() {
        PinRef loosened = new PinRef(instance.id(), 1);
        List<Set<PinRef>> seen = new ArrayList<>();
        openDocument.document().addListener((document, change) -> seen.add(openDocument.loosenedPins()));

        openDocument.execute(widerShape());
        assertThat(seen).last().isEqualTo(Set.of(loosened));
        openDocument.history().undo();
        assertThat(seen).last().isEqualTo(Set.of());
        openDocument.history().redo();
        assertThat(seen).last().isEqualTo(Set.of(loosened));
    }

    @Test
    void theMarksAreReadOnly() {
        openDocument.execute(widerShape());

        assertThat(openDocument.loosenedPins()).isUnmodifiable();
    }

    private ChangeAppearanceCommand widerShape() {
        ComponentAppearance wider = new ComponentAppearance(new GridSize(6, 4), Optional.empty(), List.of(
                new PinPlacement(inverter.pinout().get(0).source(), new GridPoint(0, 2)),
                new PinPlacement(inverter.pinout().get(1).source(), new GridPoint(6, 2))));
        return ChangeAppearanceCommand.forInstance(openDocument.document(), openDocument.registry(), instance.id(),
                Optional.of(wider));
    }

    private void pinAt(GridPoint target) {
        ComponentTypeRegistry registry = openDocument.registry();
        Component pin = registry.require(InterfacePins.INPUT_PIN).newComponent(
                openDocument.document().newComponentId(), GridPoint.ORIGIN, Rotation.R0, Map.of(), "");
        GridPoint offset = registry.typeOf(pin).pinLocation(pin, 0);
        openDocument.execute(new AddElementsCommand(List.of(pin.withPosition(
                target.translate(-offset.x(), -offset.y())))));
    }
}
