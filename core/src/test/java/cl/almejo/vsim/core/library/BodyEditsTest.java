package cl.almejo.vsim.core.library;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinDirection;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.standard.MemoryTypes;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.StickyColor;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.core.document.StickyText;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static cl.almejo.vsim.core.DocumentFixtures.frame;
import static cl.almejo.vsim.core.DocumentFixtures.place;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A definition rebuilt from its edited working copy (spec §6.1, §10.4): the body keeps ids and coordinates, the pins
 * follow their interface-pin parts by source (name from the label, width from the part, position kept) and a new
 * part takes a free edge spot.
 */
class BodyEditsTest {

    private static final ElementId INPUT_PART = new ElementId("c1");
    private static final ElementId OUTPUT_PART = new ElementId("c3");

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final ComponentDefinition previous = inverterWithAWire();
    private final CircuitDocument workingCopy = previous.bodyDocument();

    @Test
    void rebuiltKeepsCoordinatesAndIds() {
        ComponentDefinition rebuilt = BodyEdits.rebuilt(previous, workingCopy);

        assertThat(rebuilt.body().components()).containsExactlyElementsOf(previous.body().components());
        assertThat(rebuilt.body().wires()).containsExactlyElementsOf(previous.body().wires());
        assertThat(rebuilt.sameContentAs(previous)).isTrue();
        assertThat(rebuilt.revision()).isEqualTo(previous.revision());
    }

    @Test
    void rebuiltKeepsAPinWhosePartMovedInside() {
        Component input = workingCopy.component(INPUT_PART).orElseThrow();
        workingCopy.replace(input.withPosition(input.position().translate(0, 10)));

        ComponentDefinition rebuilt = BodyEdits.rebuilt(previous, workingCopy);

        assertThat(pinNamed(rebuilt, "a").spec().position()).isEqualTo(new GridPoint(0, 2));
        assertThat(rebuilt.revision()).isEqualTo(previous.revision() + 1);
        assertThat(BodyEdits.interfaceChanged(previous, rebuilt)).isFalse();
    }

    @Test
    void rebuiltRenamesAPinFromItsLabel() {
        workingCopy.replace(workingCopy.component(INPUT_PART).orElseThrow().withLabel("CLK"));

        ComponentDefinition rebuilt = BodyEdits.rebuilt(previous, workingCopy);

        assertThat(rebuilt.pinout()).extracting(pin -> pin.spec().name()).containsExactly("CLK", "y");
        assertThat(pinNamed(rebuilt, "CLK").spec().position()).isEqualTo(new GridPoint(0, 2));
        assertThat(pinNamed(rebuilt, "CLK").source()).isEqualTo(INPUT_PART);
    }

    @Test
    void aBlankLabelKeepsThePreviousName() {
        workingCopy.replace(workingCopy.component(INPUT_PART).orElseThrow().withLabel(" "));

        ComponentDefinition rebuilt = BodyEdits.rebuilt(previous, workingCopy);

        assertThat(rebuilt.pinout()).extracting(pin -> pin.spec().name()).containsExactly("a", "y");
    }

    @Test
    void aLabelThatRepeatsAnotherPinsNameIsNumberedAndTheOtherPinKeepsItsName() {
        workingCopy.replace(workingCopy.component(INPUT_PART).orElseThrow().withLabel("Y"));

        ComponentDefinition rebuilt = BodyEdits.rebuilt(previous, workingCopy);

        assertThat(rebuilt.pinout()).extracting(pin -> pin.spec().name()).containsExactly("Y2", "y");
    }

    @Test
    void rebuiltWidensAPinFromItsPart() {
        Component input = workingCopy.component(INPUT_PART).orElseThrow();
        workingCopy.replace(input.withParameters(Map.of(InterfacePins.WIDTH, 8)));

        ComponentDefinition rebuilt = BodyEdits.rebuilt(previous, workingCopy);

        assertThat(pinNamed(rebuilt, "a").spec().width()).isEqualTo(8);
        assertThat(BodyEdits.interfaceChanged(previous, rebuilt)).isTrue();
    }

    @Test
    void rebuiltDropsAPinWhosePartWasRemoved() {
        workingCopy.remove(OUTPUT_PART);

        ComponentDefinition rebuilt = BodyEdits.rebuilt(previous, workingCopy);

        assertThat(rebuilt.pinout()).extracting(pin -> pin.spec().name()).containsExactly("a");
        assertThat(rebuilt.size()).isEqualTo(previous.size());
        assertThat(BodyEdits.interfaceChanged(previous, rebuilt)).isTrue();
    }

    @Test
    void rebuiltAddsANewPinOnAFreeEdgeSpot() {
        place(registry, workingCopy, InterfacePins.OUTPUT_PIN, 20, 1, "z");

        ComponentDefinition rebuilt = BodyEdits.rebuilt(previous, workingCopy);

        // the 4 x 4 box has no free right-edge row two units from y at (4,2): it grows by one pin spacing
        DefinitionPin added = pinNamed(rebuilt, "z");
        assertThat(added.spec().direction()).isEqualTo(PinDirection.OUTPUT);
        assertThat(added.spec().position()).isEqualTo(new GridPoint(4, 4));
        assertThat(rebuilt.size()).isEqualTo(new GridSize(4, 4 + DefinitionBuilder.PIN_SPACING));
        assertThat(rebuilt.pinout()).extracting(pin -> pin.spec().name()).containsExactly("a", "y", "z");
    }

    @Test
    void aNewPinTakesTheFirstFreeRowWhenThereIsOne() {
        ComponentDefinition tall = new ComponentDefinition(previous.id(), previous.name(), "", new GridSize(4, 8),
                previous.pinout(), previous.body());
        CircuitDocument copy = tall.bodyDocument();
        place(registry, copy, InterfacePins.INPUT_PIN, 0, 20, "");

        ComponentDefinition rebuilt = BodyEdits.rebuilt(tall, copy);

        DefinitionPin added = rebuilt.pinout().getLast();
        assertThat(added.spec().position()).isEqualTo(new GridPoint(0, 4));
        assertThat(added.spec().name()).isEqualTo("in1");
        assertThat(rebuilt.size()).isEqualTo(tall.size());
    }

    @Test
    void rebuiltKeepsFramesAndNotes() {
        Frame frame = frame(workingCopy, 0, 0, 10, 8);
        StickyNote note = new StickyNote(workingCopy.newStickyNoteId(), new GridPoint(12, 0), 8, 6, StickyColor.BLUE,
                new StickyText("recordar"), false);
        workingCopy.add(note);

        ComponentDefinition rebuilt = BodyEdits.rebuilt(previous, workingCopy);

        assertThat(rebuilt.body().frames()).containsExactly(frame);
        assertThat(rebuilt.body().stickyNotes()).containsExactly(note);
    }

    @Test
    void rebuiltKeepsMemoryLinks() {
        // the user's decision (2026-11-06): a link made inside a component is the component's, in every piece
        Component ram = registry.require(StandardComponentTypes.RAM).newComponent(workingCopy.newComponentId(),
                new GridPoint(10, 10), Rotation.R0, Map.of(MemoryTypes.CONTENTS, "@0 3A", MemoryTypes.LINK,
                        "/home/student/programa.hex"), "");
        workingCopy.add(ram);

        ComponentDefinition rebuilt = BodyEdits.rebuilt(previous, workingCopy);

        Component inner = rebuilt.body().components().stream()
                .filter(component -> component.id().equals(ram.id())).findFirst().orElseThrow();
        assertThat(inner.parameters().get(MemoryTypes.LINK)).isEqualTo("/home/student/programa.hex");
        assertThat(inner.parameters().get(MemoryTypes.CONTENTS)).isEqualTo("@0 3A");
    }

    private static DefinitionPin pinNamed(ComponentDefinition definition, String name) {
        return definition.pinout().stream().filter(pin -> pin.spec().name().equals(name)).findFirst().orElseThrow();
    }

    /** {@link ExampleDefinitions#inverter()} with a wire w9 hanging from its output, at their usual coordinates. */
    private static ComponentDefinition inverterWithAWire() {
        ComponentDefinition inverter = ExampleDefinitions.inverter();
        List<Wire> wires = new ArrayList<>(inverter.body().wires());
        wires.add(new Wire(new ElementId("w9"), new GridPoint(6, 2), new GridPoint(6, 6)));
        CircuitFragment body = new CircuitFragment(inverter.body().components(), wires, List.of());
        return new ComponentDefinition(inverter.id(), inverter.name(), "", inverter.size(),
                List.of(new DefinitionPin(PinSpec.input("a", 0, 2), INPUT_PART),
                        new DefinitionPin(PinSpec.output("y", 4, 2), OUTPUT_PART)), body);
    }
}
