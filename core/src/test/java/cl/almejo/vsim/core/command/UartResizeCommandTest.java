package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.component.standard.TerminalTypes;
import cl.almejo.vsim.core.component.standard.UartTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.wiring.Connectivity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static cl.almejo.vsim.core.DocumentFixtures.gate;
import static cl.almejo.vsim.core.DocumentFixtures.wire;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Changing the columns and rows of a UART 16550's integrated screen by its corner, as a Terminal's: one undo step,
 * the bottom pins follow the bottom edge with their wires. A Normal 80 × 24 UART at (10, 10) is 101 × 52 units: CLK at
 * (12, 62) wired to a gate's input at (12, 70). With TX and RX pins it has no screen to resize.
 */
class UartResizeCommandTest {

    private static final int CLOCK = 5;

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitDocument document = new CircuitDocument();

    private Component place(String screen) {
        ComponentType type = registry.require(StandardComponentTypes.UART_16550);
        Component uart = type.newComponent(document.newComponentId(), new GridPoint(10, 10), Rotation.R0,
                Map.of(UartTypes.SCREEN, screen), "");
        new AddElementsCommand(List.of(uart)).apply(document);
        return uart;
    }

    private static Map<String, Integer> size(int columns, int rows) {
        return Map.of(TerminalTypes.COLUMNS, columns, TerminalTypes.ROWS, rows);
    }

    @Test
    void theIntegratedScreensColumnsAndRowsChangeAndTheBottomPinsCarryTheirWires() {
        Component uart = place(UartTypes.SCREEN_INTEGRATED);
        Component clockReader = gate(registry, document, 12, 68);
        wire(document, 12, 62, 12, 70);
        History history = new History(document);

        history.execute(ResizeComponentCommand.of(document, registry, uart.id(), size(40, 12)));

        Component resized = document.component(uart.id()).orElseThrow();
        ComponentType type = registry.typeOf(resized);
        assertThat(type.size()).isEqualTo(new GridSize(61, 28));
        assertThat(type.pinLocation(resized, CLOCK)).isEqualTo(new GridPoint(12, 38));
        assertThat(Connectivity.of(document.wires(), document, registry).pinNets().stream()
                .anyMatch(net -> net.contains(new PinRef(uart.id(), CLOCK))
                        && net.contains(new PinRef(clockReader.id(), 0)))).isTrue();

        history.undo();
        assertThat(registry.typeOf(document.component(uart.id()).orElseThrow()).size())
                .isEqualTo(new GridSize(101, 52));
    }

    @Test
    void aUartWithPinsCannotBeResized() {
        Component uart = place(UartTypes.SCREEN_PINS);

        assertThatThrownBy(() -> ResizeComponentCommand.of(document, registry, uart.id(), size(40, 12)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
