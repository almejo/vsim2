package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.core.DocumentFixtures.gate;
import static cl.almejo.vsim.core.DocumentFixtures.wire;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A change of a bus part's groups or «Ancho» never joins one of its pins to a wire or pin of the same width it was not
 * joined to: the signal would go to the wrong reader with no warning. A touch with a different width is allowed, the
 * netlist shows it as a width mismatch.
 */
class PinJoinCheckTest {

    private static final int SPLITTER_X = 20;
    private static final int SPLITTER_Y = 10;

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitDocument document = new CircuitDocument();

    private Component splitter(int width, String groups) {
        ComponentType type = registry.require(StandardComponentTypes.BUS_SPLITTER);
        Component component = type.newComponent(document.newComponentId(), new GridPoint(SPLITTER_X, SPLITTER_Y),
                Rotation.R0, Map.of(StandardComponentTypes.WIDTH, width, StandardComponentTypes.GROUPS, groups), "");
        document.add(component);
        return component;
    }

    /** A straight wire from each one-bit pin of an 8-bit splitter to a NOT gate of its own (the review's layout). */
    private void readerOnEveryBit() {
        for (int bit = 0; bit < 8; bit++) {
            int x = 40 + 8 * bit;
            gate(registry, document, x, SPLITTER_Y - 1 + bit);
            wire(document, SPLITTER_X + 2, SPLITTER_Y + 1 + bit, x, SPLITTER_Y + 1 + bit);
        }
    }

    private Optional<PinJoinCheck.PinJoin> change(Component component, int width, String groups) {
        Map<String, Object> values = new HashMap<>(component.parameters());
        values.put(StandardComponentTypes.WIDTH, width);
        values.put(StandardComponentTypes.GROUPS, groups);
        return PinJoinCheck.find(document, registry, component.id(), values);
    }

    @Test
    void groupsThatPutAOneBitPinOnAnotherBitsWireAreRefusedNamingThePin() {
        Component component = splitter(8, "");
        readerOnEveryBit();

        Optional<PinJoinCheck.PinJoin> join = change(component, 8, "0-3|4|5|6|7");

        assertThat(join).map(PinJoinCheck.PinJoin::pinName).contains("b4");
        assertThat(join).map(PinJoinCheck.PinJoin::kind).contains(PinJoinCheck.Kind.JOINS_ANOTHER_NET);
    }

    @Test
    void checkingLeavesTheDocumentAsItWas() {
        Component component = splitter(8, "");
        readerOnEveryBit();
        List<Wire> wires = List.copyOf(document.wires());

        change(component, 8, "0-3|4|5|6|7");

        assertThat(document.wires()).containsExactlyElementsOf(wires);
        assertThat(document.component(component.id())).contains(component);
    }

    @Test
    void groupsWhosePinsTouchOnlyWiresOfAnotherWidthAreAllowed() {
        Component component = splitter(8, "");
        readerOnEveryBit();

        assertThat(change(component, 8, "0-3|4-7")).isEmpty();
    }

    @Test
    void aNarrowerOrWiderBusWhosePinsLandOnTheirGroupsOldWiresIsAllowed() {
        Component component = splitter(8, "0-3|4-7");
        wire(document, 23, 11, 30, 11);
        wire(document, 23, 13, 30, 13);

        assertThat(change(component, 6, "0-3|4-5")).isEmpty();
        assertThat(change(component, 16, "0-3|4-7|8-11|12-15")).isEmpty();
    }

    @Test
    void aWiderBusWhoseNewOneBitPinLandsOnAOneBitReadersWireIsRefused() {
        Component component = splitter(4, "");
        gate(registry, document, 40, SPLITTER_Y + 3);
        wire(document, SPLITTER_X + 2, SPLITTER_Y + 5, 40, SPLITTER_Y + 5);

        assertThat(change(component, 8, "")).map(PinJoinCheck.PinJoin::pinName).contains("b4");
    }

    @Test
    void theSameGroupsJoinNothing() {
        Component component = splitter(8, "");
        readerOnEveryBit();

        assertThat(change(component, 8, "")).isEmpty();
    }

    @Test
    void anotherPartKeepsTheNewPinRuleOfQBar() {
        ComponentType flipFlopType = registry.require(StandardComponentTypes.FLIP_FLOP_DATA);
        Component flipFlop = flipFlopType.newComponent(document.newComponentId(), new GridPoint(10, 10), Rotation.R0,
                Map.of(StandardComponentTypes.SHOW_INVERTED, false), "");
        document.add(flipFlop);
        Map<String, Object> shown = Map.of(StandardComponentTypes.SHOW_INVERTED, true);
        GridPoint future = flipFlopType.pinLocation(flipFlop.withParameters(flipFlopType.resolveParameters(shown)), 3);
        wire(document, future.x(), future.y(), future.x() + 4, future.y());

        Optional<PinJoinCheck.PinJoin> join = PinJoinCheck.find(document, registry, flipFlop.id(), shown);

        assertThat(join).map(PinJoinCheck.PinJoin::pinName).contains("nQ");
        assertThat(join).map(PinJoinCheck.PinJoin::kind).contains(PinJoinCheck.Kind.NEW_PIN_TOUCHES);
    }
}
