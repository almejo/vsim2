package cl.almejo.vsim.core.truthtable;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Truth tables over «Bloques» blocks: bus pins become multi-bit columns. */
class BlockTruthTableTest {

    /** An input pin whose pin lands on {@code target} coming from the left (its pin is at (2, 1) of its box). */
    private static void feedFromTheLeft(TruthTableCircuits circuits, GridPoint target, int width, String label) {
        Component input = circuits.place(InterfacePins.INPUT_PIN, target.x() - 6, target.y() - 1, label,
                Map.of(InterfacePins.WIDTH, width));
        circuits.wire(circuits.pin(input, 0), target);
    }

    @Test
    void aTwoBitAluGivesMultiBitColumnsForEveryOperation() {
        TruthTableCircuits circuits = new TruthTableCircuits();
        Map<String, Object> twoBits = Map.of(StandardComponentTypes.WIDTH, 2);
        Component alu = circuits.place(StandardComponentTypes.ALU, 20, 0, "", twoBits);
        feedFromTheLeft(circuits, circuits.pin(alu, 0), 2, "A");
        feedFromTheLeft(circuits, circuits.pin(alu, 1), 2, "B");
        GridPoint operationPin = circuits.pin(alu, 2);
        Component operation = circuits.place(InterfacePins.INPUT_PIN, operationPin.x() - 2, operationPin.y() + 3,
                "OP", Map.of(InterfacePins.WIDTH, 3));
        circuits.wire(circuits.pin(operation, 0), operationPin);
        GridPoint resultPin = circuits.pin(alu, 3);
        Component result = circuits.place(InterfacePins.OUTPUT_PIN, resultPin.x() + 4, resultPin.y() - 1, "Y",
                twoBits);
        circuits.wire(resultPin, circuits.pin(result, 0));
        GridPoint zeroPin = circuits.pin(alu, 4);
        Component zero = circuits.place(InterfacePins.OUTPUT_PIN, zeroPin.x() + 10, zeroPin.y() - 1, "Z",
                Map.of(InterfacePins.WIDTH, 1));
        circuits.wire(zeroPin, circuits.pin(zero, 0));

        Preparation preparation = TruthTableScope.prepare(circuits.document, Set.of(), circuits.registry);
        assertThat(preparation).isInstanceOf(Preparation.Ready.class);
        TruthTable table = TruthTableEvaluator.evaluate((Preparation.Ready) preparation, percent -> { }, () -> false)
                .orElseThrow();

        assertThat(table.inputs()).extracting(Column::name).containsExactly("A", "B", "OP");
        assertThat(table.inputs()).extracting(Column::width).containsExactly(2, 2, 3);
        assertThat(table.outputs()).extracting(Column::name).containsExactly("Y", "Z");
        assertThat(table.outputs()).extracting(Column::width).containsExactly(2, 1);
        assertThat(table.rows()).hasSize(128);
        for (int index = 0; index < 128; index++) {
            int first = index >> 5 & 0b11;
            int second = index >> 3 & 0b11;
            int code = index & 0b111;
            int expected = switch (code) {
                case 0 -> first + second;
                case 1 -> first - second;
                case 2 -> first & second;
                case 3 -> first | second;
                case 4 -> first ^ second;
                case 5 -> ~first;
                case 6 -> first << 1;
                default -> first >>> 1;
            } & 0b11;
            assertThat(table.rows().get(index)).as("row %d", index).isEqualTo(new Row(
                    List.of(Value.of(2, first), Value.of(2, second), Value.of(3, code)),
                    List.of(Value.of(2, expected), Value.of(expected == 0)), true));
        }
    }
}
