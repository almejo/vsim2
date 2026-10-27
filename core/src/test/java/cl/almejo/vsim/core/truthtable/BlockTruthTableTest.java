package cl.almejo.vsim.core.truthtable;

import cl.almejo.vsim.core.component.standard.MemoryTypes;
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

    private static TruthTable tableOf(TruthTableCircuits circuits) {
        Preparation preparation = TruthTableScope.prepare(circuits.document, Set.of(), circuits.registry);
        assertThat(preparation).isInstanceOf(Preparation.Ready.class);
        return TruthTableEvaluator.evaluate((Preparation.Ready) preparation, percent -> { }, () -> false)
                .orElseThrow();
    }

    /** The bits of column {@code name} in {@code values} (the inputs or the outputs of a row). */
    private static long bitsOf(List<Column> columns, List<Value> values, String name) {
        for (int index = 0; index < columns.size(); index++) {
            if (columns.get(index).name().equals(name)) {
                Value value = values.get(index);
                assertThat(value.isFullyDefined()).as(name).isTrue();
                return value.bits();
            }
        }
        throw new AssertionError("no column " + name);
    }

    /** M3 (M7 fix round): a 2-bit adder with Cin; its carry out is a column of its own. */
    @Test
    void aTwoBitAdderGivesTheSumAndTheCarryOut() {
        TruthTableCircuits circuits = new TruthTableCircuits();
        Map<String, Object> twoBits = Map.of(StandardComponentTypes.WIDTH, 2);
        Component adder = circuits.place(StandardComponentTypes.ADDER, 20, 0, "", twoBits);
        feedFromTheLeft(circuits, circuits.pin(adder, 0), 2, "A");
        feedFromTheLeft(circuits, circuits.pin(adder, 1), 2, "B");
        GridPoint carryInPin = circuits.pin(adder, 2);
        Component carryIn = circuits.place(InterfacePins.INPUT_PIN, carryInPin.x() - 6, carryInPin.y() - 5, "Cin",
                Map.of(InterfacePins.WIDTH, 1));
        GridPoint carryInCorner = new GridPoint(carryInPin.x(), circuits.pin(carryIn, 0).y());
        circuits.wire(circuits.pin(carryIn, 0), carryInCorner, carryInPin);
        GridPoint sumPin = circuits.pin(adder, 3);
        Component sum = circuits.place(InterfacePins.OUTPUT_PIN, sumPin.x() + 4, sumPin.y() - 1, "S", twoBits);
        circuits.wire(sumPin, circuits.pin(sum, 0));
        GridPoint carryOutPin = circuits.pin(adder, 4);
        Component carryOut = circuits.place(InterfacePins.OUTPUT_PIN, carryOutPin.x() + 4, carryOutPin.y() + 3,
                "Cout", Map.of(InterfacePins.WIDTH, 1));
        GridPoint carryOutCorner = new GridPoint(carryOutPin.x(), circuits.pin(carryOut, 0).y());
        circuits.wire(carryOutPin, carryOutCorner, circuits.pin(carryOut, 0));

        TruthTable table = tableOf(circuits);

        assertThat(table.inputs()).extracting(Column::name).containsExactlyInAnyOrder("A", "B", "Cin");
        assertThat(table.outputs()).extracting(Column::name).containsExactlyInAnyOrder("S", "Cout");
        assertThat(table.rows()).hasSize(32);
        for (Row row : table.rows()) {
            long total = bitsOf(table.inputs(), row.inputs(), "A") + bitsOf(table.inputs(), row.inputs(), "B")
                    + bitsOf(table.inputs(), row.inputs(), "Cin");
            assertThat(bitsOf(table.outputs(), row.outputs(), "S")).as("%s", row).isEqualTo(total & 0b11);
            assertThat(bitsOf(table.outputs(), row.outputs(), "Cout")).as("%s", row).isEqualTo(total >> 2);
            assertThat(row.settled()).isTrue();
        }
    }

    /** M3 (M7 fix round): a 4-input mux of 1-bit data; its select is a 2-bit column. */
    @Test
    void aFourInputMuxPicksTheInputItsTwoBitSelectNames() {
        TruthTableCircuits circuits = new TruthTableCircuits();
        Component mux = circuits.place(StandardComponentTypes.MUX, 20, 0, "",
                Map.of(StandardComponentTypes.WIDTH, 1, "inputs", "4"));
        for (int index = 0; index < 4; index++) {
            feedFromTheLeft(circuits, circuits.pin(mux, index), 1, "D" + index);
        }
        GridPoint selectPin = circuits.pin(mux, 4);
        Component select = circuits.place(InterfacePins.INPUT_PIN, selectPin.x() - 2, selectPin.y() + 3, "S",
                Map.of(InterfacePins.WIDTH, 2));
        circuits.wire(circuits.pin(select, 0), selectPin);
        GridPoint resultPin = circuits.pin(mux, 5);
        Component result = circuits.place(InterfacePins.OUTPUT_PIN, resultPin.x() + 4, resultPin.y() - 1, "Y",
                Map.of(InterfacePins.WIDTH, 1));
        circuits.wire(resultPin, circuits.pin(result, 0));

        TruthTable table = tableOf(circuits);

        assertThat(table.inputs()).extracting(Column::name).containsExactlyInAnyOrder("D0", "D1", "D2", "D3", "S");
        assertThat(table.inputs()).filteredOn(column -> column.name().equals("S")).extracting(Column::width)
                .containsExactly(2);
        assertThat(table.rows()).hasSize(64);
        for (Row row : table.rows()) {
            long chosen = bitsOf(table.inputs(), row.inputs(), "S");
            assertThat(bitsOf(table.outputs(), row.outputs(), "Y")).as("%s", row)
                    .isEqualTo(bitsOf(table.inputs(), row.inputs(), "D" + chosen));
        }
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

    /** A ROM is a lookup table: its truth table is its contents. */
    @Test
    void aRomBetweenInputsAndOutputsGivesItsContents() {
        TruthTableCircuits circuits = new TruthTableCircuits();
        StringBuilder contents = new StringBuilder("@0");
        for (int address = 0; address < 16; address++) {
            contents.append(' ').append(Integer.toHexString(romWord(address)));
        }
        Component rom = circuits.place(StandardComponentTypes.ROM, 20, 0, "", Map.of(MemoryTypes.WORDS, "16",
                MemoryTypes.WIDTH, 4, MemoryTypes.CONTENTS, contents.toString()));
        feedFromTheLeft(circuits, circuits.pin(rom, 0), 4, "A");
        GridPoint dataPin = circuits.pin(rom, 2);
        Component data = circuits.place(InterfacePins.OUTPUT_PIN, dataPin.x() + 4, dataPin.y() - 1, "Q",
                Map.of(InterfacePins.WIDTH, 4));
        circuits.wire(dataPin, circuits.pin(data, 0));

        TruthTable table = tableOf(circuits);

        assertThat(table.rows()).hasSize(16);
        for (Row row : table.rows()) {
            long address = bitsOf(table.inputs(), row.inputs(), "A");
            assertThat(bitsOf(table.outputs(), row.outputs(), "Q")).as("%s", row).isEqualTo(romWord((int) address));
        }
    }

    private static int romWord(int address) {
        return (address * 7 + 3) & 0xF;
    }
}
