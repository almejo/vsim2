package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.KeyboardTypes;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.component.standard.TerminalTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.wiring.Connectivity;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static cl.almejo.vsim.core.DocumentFixtures.gate;
import static cl.almejo.vsim.core.DocumentFixtures.place;
import static cl.almejo.vsim.core.DocumentFixtures.wire;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A parameter edit that moves pins (the Teclado's «Ancho» typed in the inspector, a Terminal's rows, a gate's inputs)
 * makes the wires on those pins follow them, as one undo step, the way dragging an edge does.
 */
class ChangeParametersWithWiresCommandTest {

    private static final String CONFIGURE = "command.configure";
    private static final int KEYBOARD_AVAILABLE = 3;
    private static final int KEYBOARD_DATA = 4;
    private static final int TERMINAL_CLOCK = 1;

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitDocument document = new CircuitDocument();

    private Component current(Component component) {
        return document.component(component.id()).orElseThrow();
    }

    private Map<DocumentElement, Long> snapshot() {
        Map<DocumentElement, Long> snapshot = new LinkedHashMap<>();
        document.elements().forEach(element -> snapshot.put(element, document.orderOf(element.id())));
        return snapshot;
    }

    private boolean connected(PinRef first, PinRef second) {
        return Connectivity.of(document.wires(), document, registry).pinNets().stream()
                .anyMatch(net -> net.contains(first) && net.contains(second));
    }

    private List<GridPoint> wireEnds() {
        return document.wires().stream().flatMap(wire -> Stream.of(wire.start(), wire.end())).toList();
    }

    private Map<String, Object> with(Component component, String parameter, Object value) {
        Map<String, Object> parameters = new HashMap<>(component.parameters());
        parameters.put(parameter, value);
        return parameters;
    }

    private ChangeParametersWithWiresCommand edit(Component component, String parameter, Object value) {
        return ChangeParametersWithWiresCommand.of(document, registry, component.id(),
                with(current(component), parameter, value), CONFIGURE);
    }

    /** An AND at (20, 10): in0 (20, 10) and in1 (20, 14) driven by NOT gates, out (24, 12) read by a NOT gate. */
    private Component wiredAndGate() {
        Component and = place(registry, document, StandardComponentTypes.AND, 20, 10, "");
        gate(registry, document, 4, 8);
        gate(registry, document, 4, 12);
        gate(registry, document, 40, 10);
        wire(document, 8, 10, 20, 10);
        wire(document, 8, 14, 20, 14);
        wire(document, 24, 12, 40, 12);
        return and;
    }

    private Component driverAt(int x, int y) {
        return document.components().stream()
                .filter(component -> component.position().equals(new GridPoint(x, y)))
                .findFirst().orElseThrow();
    }

    @Test
    void typingAWiderTecladoCarriesTheWiresOfAvAndD() {
        Component keyboard = place(registry, document, StandardComponentTypes.KEYBOARD, 10, 10, "");
        Component availableReader = gate(registry, document, 40, 10);
        Component dataReader = gate(registry, document, 44, 12);
        wire(document, 26, 12, 40, 12);
        wire(document, 26, 14, 44, 14);

        new History(document).execute(edit(keyboard, KeyboardTypes.PART_WIDTH, 24));

        ComponentType type = registry.typeOf(current(keyboard));
        assertThat(type.pinLocation(current(keyboard), KEYBOARD_AVAILABLE)).isEqualTo(new GridPoint(34, 12));
        assertThat(connected(new PinRef(keyboard.id(), KEYBOARD_AVAILABLE), new PinRef(availableReader.id(), 0)))
                .isTrue();
        assertThat(connected(new PinRef(keyboard.id(), KEYBOARD_DATA), new PinRef(dataReader.id(), 0))).isTrue();
        assertThat(wireEnds()).contains(new GridPoint(34, 12), new GridPoint(34, 14))
                .doesNotContain(new GridPoint(26, 12), new GridPoint(26, 14));
    }

    @Test
    void typingFewerTerminalRowsCarriesTheWiresOfTheBottomPins() {
        Component terminal = place(registry, document, StandardComponentTypes.TERMINAL, 10, 10, "");
        Component clockReader = gate(registry, document, 12, 68);
        wire(document, 12, 62, 12, 70);

        new History(document).execute(edit(terminal, TerminalTypes.ROWS, 12));

        assertThat(registry.typeOf(current(terminal)).pinLocation(current(terminal), TERMINAL_CLOCK))
                .isEqualTo(new GridPoint(12, 38));
        assertThat(connected(new PinRef(terminal.id(), TERMINAL_CLOCK), new PinRef(clockReader.id(), 0))).isTrue();
    }

    @Test
    void typingFewerTerminalColumnsKeepsTheWiresOnThePins() {
        Component terminal = place(registry, document, StandardComponentTypes.TERMINAL, 10, 10, "");
        Component clockReader = gate(registry, document, 12, 68);
        wire(document, 12, 62, 12, 70);

        new History(document).execute(edit(terminal, TerminalTypes.COLUMNS, 40));

        assertThat(connected(new PinRef(terminal.id(), TERMINAL_CLOCK), new PinRef(clockReader.id(), 0))).isTrue();
    }

    @Test
    void moreGateInputsCarryTheOutputWireAndEachInputKeepsItsSignal() {
        Component and = wiredAndGate();
        Component reader = driverAt(40, 10);

        new History(document).execute(edit(and, StandardComponentTypes.GATE_INPUTS, 6));

        int output = 6;
        assertThat(registry.typeOf(current(and)).pinLocation(current(and), output)).isEqualTo(new GridPoint(24, 13));
        assertThat(connected(new PinRef(and.id(), output), new PinRef(reader.id(), 0))).isTrue();
        assertThat(connected(new PinRef(and.id(), 0), new PinRef(driverAt(4, 8).id(), 1))).isTrue();
        assertThat(connected(new PinRef(and.id(), 1), new PinRef(driverAt(4, 12).id(), 1))).isTrue();
        for (int newInput = 2; newInput < output; newInput++) {
            assertThat(connected(new PinRef(and.id(), newInput), new PinRef(driverAt(4, 12).id(), 1)))
                    .as("in%d", newInput).isFalse();
        }
    }

    /**
     * A three-input AND at (20, 10) going down to two inputs: in1 moves from (20, 12) to (20, 14), where the removed in2
     * was wired. in2's wire stays where it was (now on in1, as before wires followed) and in1's wire stays too: following
     * it there would join the drivers of in1 and in2.
     */
    @Test
    void fewerGateInputsLeaveTheWireOfARemovedInputAndNeverJoinTwoDrivers() {
        Component and = registry.require(StandardComponentTypes.AND).newComponent(document.newComponentId(),
                new GridPoint(20, 10), Rotation.R0, Map.of(StandardComponentTypes.GATE_INPUTS, 3), "");
        document.add(and);
        Component secondDriver = gate(registry, document, 4, 10);
        Component thirdDriver = gate(registry, document, 4, 12);
        Wire secondInputWire = wire(document, 8, 12, 20, 12);
        Wire thirdInputWire = wire(document, 8, 14, 20, 14);

        new History(document).execute(edit(and, StandardComponentTypes.GATE_INPUTS, 2));

        assertThat(document.wire(thirdInputWire.id())).contains(thirdInputWire);
        assertThat(document.wire(secondInputWire.id())).contains(secondInputWire);
        assertThat(connected(new PinRef(secondDriver.id(), 1), new PinRef(thirdDriver.id(), 1))).isFalse();
    }

    @Test
    void oneUndoRestoresTheParameterAndTheWiresAndRedoRepeatsBoth() {
        Component and = wiredAndGate();
        Map<DocumentElement, Long> before = snapshot();
        History history = new History(document);

        history.execute(edit(and, StandardComponentTypes.GATE_INPUTS, 6));
        List<DocumentElement> after = document.elements();
        history.undo();

        assertThat(snapshot()).containsExactlyEntriesOf(before);
        assertThat(history.canUndo()).isFalse();
        history.redo();
        assertThat(document.elements()).containsExactlyInAnyOrderElementsOf(after);
        assertThat(history.undoDescription()).contains(CONFIGURE);
    }

    @Test
    void anEditThatMovesNoPinChangesNoWire() {
        Component and = wiredAndGate();
        List<Wire> wiresBefore = document.wires();

        ChangeParametersWithWiresCommand command = edit(and, "delay", 5);
        new History(document).execute(command);

        assertThat(command.plan().isEmpty()).isTrue();
        assertThat(document.wires()).containsExactlyElementsOf(wiresBefore);
        assertThat(current(and).parameters()).containsEntry("delay", 5);
    }
}
