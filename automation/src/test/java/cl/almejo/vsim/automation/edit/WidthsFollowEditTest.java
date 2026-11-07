package cl.almejo.vsim.automation.edit;

import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.edit.EditOperation.Connect;
import cl.almejo.vsim.automation.edit.EditOperation.PlacePart;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * «Anchos que siguen lo conectado» for the AI: its Connect, AddWire, PlacePart and Move follow the same rule as the
 * editor, in the edit's one undo step, and the receipt says which part changed width.
 */
class WidthsFollowEditTest {

    private static final String DOCUMENT = "circuit-1";
    private static final String WIDTH = StandardComponentTypes.WIDTH;

    private final FakeCircuitHost host = new FakeCircuitHost();
    private EditService service;
    private FakeCircuitHost.Circuit circuit;
    private int nextOperationId = 1;

    @BeforeEach
    void openACircuit() {
        circuit = host.open(DOCUMENT);
        service = new EditService(host, List.of(), Duration.ofSeconds(5), Runnable::run);
    }

    @Test
    void connectingAOneBitConstantToAnEightBitAdderMakesTheConstantEightBitsAndTheReceiptSaysSo() {
        EditReceipt receipt = service.edit(request(
                part("value", StandardComponentTypes.CONSTANT, 0, 2, Map.of(WIDTH, 1)),
                part("sum", StandardComponentTypes.ADDER, 20, 0, Map.of(WIDTH, 8)),
                new Connect(PinTarget.named("new:value", "out"), PinTarget.named("new:sum", "A"), List.of())));

        String constant = receipt.aliases().get("value");
        assertThat(widthOf(constant)).isEqualTo(8);
        assertThat(receipt.warnings()).contains("operation 2: " + constant
                + " (constant) took the width of what it was connected to: 1 -> 8 bits");

        circuit.history().undo();

        assertThat(circuit.document().isEmpty()).isTrue();
    }

    @Test
    void anOutputPinSetDownOnTheEndOfABusTakesItsWidth() {
        EditReceipt receipt = service.edit(request(
                part("bus", StandardComponentTypes.INPUT_PIN, 0, 0, Map.of(WIDTH, 8)),
                part("probe", StandardComponentTypes.OUTPUT_PIN, 10, 0, Map.of()),
                new Connect(PinTarget.named("new:bus", "pin"), PinTarget.named("new:probe", "pin"), List.of())));

        assertThat(widthOf(receipt.aliases().get("probe"))).isEqualTo(8);
    }

    private int widthOf(String id) {
        Component component = circuit.document().component(new ElementId(id)).orElseThrow();
        return (Integer) component.parameters().get(WIDTH);
    }

    private static PlacePart part(String alias, String typeId, int x, int y, Map<String, Object> parameters) {
        return new PlacePart(Optional.of(alias), typeId, x, y, Rotation.R0, parameters, "");
    }

    private EditRequest request(EditOperation... operations) {
        return new EditRequest(new DocumentId(DOCUMENT), circuit.version(), "op-" + nextOperationId++,
                Optional.empty(), List.of(operations), false);
    }
}
