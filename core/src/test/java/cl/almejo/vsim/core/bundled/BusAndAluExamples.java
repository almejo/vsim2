package cl.almejo.vsim.core.bundled;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.examples.CircuitBuilder;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.DefinitionBuilder;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.PinOrder;
import cl.almejo.vsim.core.library.SubcircuitTypes;

import java.util.List;
import java.util.Map;

import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.AND2;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.AND3;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.BUS_JOINER;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.BUS_SPLITTER;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.INPUT_PIN;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.LED;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.NOR4;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.NOT;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.OR2;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.OR4;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.OUTPUT_PIN;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY_DOUBLE;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.SWITCH;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.WIDTH;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.XOR2;

/**
 * The bus and the ALU of Archivo ▸ Ejemplos (spec M6.1 §3.1). «Bus de 8 bits»: eight switches joined into one 8-bit
 * wire and split again, once for eight LEDs and once for a double display. «ALU de 4 bits»: four «Bit de ALU» user
 * components (each a «Sumador completo» plus and2, or2, xor2 and a 4:1 multiplexer choosing by S1 S0: 00 suma,
 * 01 AND, 10 OR, 11 XOR) with the carry rippling down, a result display and the flags Cero, Negativo and Acarreo.
 */
public final class BusAndAluExamples {

    /** The id of the user component «Bit de ALU» embedded in {@code alu-4-bits}. */
    public static final String ALU_BIT_ID = "bit-de-alu";

    private static final Map<String, Object> OFF = Map.of("value", "0");
    private static final Map<String, Object> NO_PARAMETERS = Map.of();
    private static final Map<String, Object> EIGHT_BITS = Map.of(WIDTH, 8);

    private BusAndAluExamples() {
    }

    /**
     * Bus de 8 bits. The switches (b0 on top, 5 rows apart) converge into the joiner's inputs (1 row apart): the upper
     * four bend down and the lower four up, the outer ones at the columns nearest the joiner, so no two wires cross.
     * The bus goes to two splitters: one feeds the LEDs standing in a diagonal on straight lines, the other drops
     * straight into the double display (its pin k under bit k).
     */
    static CircuitBuilder bus() {
        CircuitBuilder builder = new CircuitBuilder("Bus de 8 bits");
        Component joiner = builder.placeByBox(BUS_JOINER, 20, 19, Rotation.R0, EIGHT_BITS, "");
        int[] columns = {16, 14, 12, 10, 10, 12, 14, 16};
        Component[] switches = new Component[8];
        for (int bit = 0; bit < 8; bit++) {
            int row = 6 + 5 * bit;
            switches[bit] = builder.placeByPin(SWITCH, new GridPoint(6, row), 0, Rotation.R0, OFF, "b" + bit);
            builder.route(new GridPoint(6, row), new GridPoint(columns[bit], row),
                    new GridPoint(columns[bit], 20 + bit), new GridPoint(20, 20 + bit));
            builder.link(switches[bit], 0, joiner, 1 + bit);
        }
        Component ledSplitter = builder.placeByBox(BUS_SPLITTER, 30, 30, Rotation.R0, EIGHT_BITS, "");
        Component displaySplitter = builder.placeByPin(BUS_SPLITTER, new GridPoint(74, 26), 0, Rotation.R90,
                EIGHT_BITS, "");
        Component display = builder.placeByBox(SEVEN_SEGMENTS_DISPLAY_DOUBLE, 65, 31, Rotation.R0, NO_PARAMETERS, "");
        builder.route(new GridPoint(22, 19), new GridPoint(74, 19), new GridPoint(74, 26));
        builder.route(new GridPoint(26, 19), new GridPoint(26, 30), new GridPoint(30, 30));
        builder.link(joiner, 0, ledSplitter, 0);
        builder.link(joiner, 0, displaySplitter, 0);
        for (int bit = 0; bit < 8; bit++) {
            int row = 31 + bit;
            int ledX = 36 + 3 * bit;
            builder.route(new GridPoint(32, row), new GridPoint(ledX, row), new GridPoint(ledX, row - 2));
            Component led = builder.placeByPin(LED, new GridPoint(ledX, row - 2), 0, Rotation.R0,
                    Map.of("color", "red"), "b" + bit);
            builder.link(ledSplitter, 1 + bit, led, 0);
            builder.route(new GridPoint(73 - bit, 28), new GridPoint(73 - bit, 31));
            builder.link(displaySplitter, 1 + bit, display, bit);
        }
        builder.text(2, 0, "Bus de 8 bits");
        builder.text(52, 21, "bus: 8 bits en un solo cable");
        builder.text(65, 38, "el byte en hexadecimal");
        builder.text(2, 45, "Prueba: los 8 interruptores viajan juntos por el bus y se separan otra vez:");
        builder.text(2, 47, "los LED b0..b7 y el display muestran el mismo byte");
        return builder;
    }

    /**
     * The body of «Bit de ALU»: a «Sumador completo» and and2, or2, xor2 on A and B, then a 4:1 multiplexer (two NOT
     * and four and3 into an or4) that picks R by S1 S0: 00 the sum, 01 A·B, 10 A+B, 11 A xor B. The carry goes up and
     * over to the Cout pin.
     */
    static void aluBitBody(CircuitBuilder builder) {
        builder.define(CombinationalExamples.fullAdderDefinition().root());
        Component carryIn = builder.placeByPin(INPUT_PIN, new GridPoint(4, 8), 0, Rotation.R0, NO_PARAMETERS, "Cin");
        Component inputA = builder.placeByPin(INPUT_PIN, new GridPoint(4, 14), 0, Rotation.R0, NO_PARAMETERS, "A");
        Component inputB = builder.placeByPin(INPUT_PIN, new GridPoint(4, 20), 0, Rotation.R0, NO_PARAMETERS, "B");
        Component select1 = builder.placeByPin(INPUT_PIN, new GridPoint(4, 44), 0, Rotation.R0, NO_PARAMETERS, "S1");
        Component select0 = builder.placeByPin(INPUT_PIN, new GridPoint(4, 52), 0, Rotation.R0, NO_PARAMETERS, "S0");
        Component adder = builder.placeByBox(SubcircuitTypes.typeIdOf(CombinationalExamples.FULL_ADDER_ID), 16, 6,
                Rotation.R0, NO_PARAMETERS, "");
        Component andGate = builder.placeByBox(AND2, 16, 18, Rotation.R0, NO_PARAMETERS, "");
        Component orGate = builder.placeByBox(OR2, 16, 26, Rotation.R0, NO_PARAMETERS, "");
        Component xorGate = builder.placeByBox(XOR2, 16, 34, Rotation.R0, NO_PARAMETERS, "");

        builder.route(new GridPoint(4, 8), new GridPoint(16, 8));
        builder.link(carryIn, 0, adder, 0);
        // A rail at x = 10, B rail at x = 12
        builder.route(new GridPoint(4, 14), new GridPoint(10, 14));
        builder.route(new GridPoint(10, 10), new GridPoint(10, 34));
        builder.route(new GridPoint(4, 20), new GridPoint(12, 20));
        builder.route(new GridPoint(12, 12), new GridPoint(12, 38));
        Component[] users = {adder, andGate, orGate, xorGate};
        int[] rowsA = {10, 18, 26, 34};
        int[] pinsA = {1, 0, 0, 0};
        int[] rowsB = {12, 22, 30, 38};
        int[] pinsB = {2, 1, 1, 1};
        for (int index = 0; index < 4; index++) {
            builder.route(new GridPoint(10, rowsA[index]), new GridPoint(16, rowsA[index]));
            builder.link(inputA, 0, users[index], pinsA[index]);
            builder.route(new GridPoint(12, rowsB[index]), new GridPoint(16, rowsB[index]));
            builder.link(inputB, 0, users[index], pinsB[index]);
        }

        // selector: S1 and S0 come in at the bottom; NOT gates make ¬S1 and ¬S0; four rails go up
        Component notSelect1 = builder.placeByBox(NOT, 10, 45, Rotation.R0, NO_PARAMETERS, "");
        Component notSelect0 = builder.placeByBox(NOT, 10, 53, Rotation.R0, NO_PARAMETERS, "");
        builder.route(new GridPoint(4, 44), new GridPoint(26, 44), new GridPoint(26, 30));
        builder.route(new GridPoint(8, 44), new GridPoint(8, 47), new GridPoint(10, 47));
        builder.link(select1, 0, notSelect1, 0);
        builder.route(new GridPoint(14, 47), new GridPoint(30, 47), new GridPoint(30, 10));
        builder.route(new GridPoint(4, 52), new GridPoint(32, 52), new GridPoint(32, 24));
        builder.route(new GridPoint(8, 52), new GridPoint(8, 55), new GridPoint(10, 55));
        builder.link(select0, 0, notSelect0, 0);
        builder.route(new GridPoint(14, 55), new GridPoint(36, 55), new GridPoint(36, 12));

        // the multiplexer: and3 i gets data i on in0 (straight from its source) and the selector terms on in1, in2
        Component[] gates = new Component[4];
        Component[] dataSources = {adder, andGate, orGate, xorGate};
        int[] dataPins = {3, 2, 2, 2};
        int[] dataRows = {8, 20, 28, 36};
        int[] dataStartX = {22, 20, 20, 20};
        int[] railX = {26, 30, 32, 36};
        Component[] railSources = {select1, notSelect1, select0, notSelect0};
        int[] railPins = {0, 1, 0, 1};
        for (int index = 0; index < 4; index++) {
            int top = dataRows[index];
            gates[index] = builder.placeByBox(AND3, 40, top, Rotation.R0, NO_PARAMETERS, "");
            builder.route(new GridPoint(dataStartX[index], top), new GridPoint(40, top));
            builder.link(dataSources[index], dataPins[index], gates[index], 0);
            int highRail = (index & 2) != 0 ? 0 : 1;
            int lowRail = (index & 1) != 0 ? 2 : 3;
            builder.route(new GridPoint(railX[highRail], top + 2), new GridPoint(40, top + 2));
            builder.link(railSources[highRail], railPins[highRail], gates[index], 1);
            builder.route(new GridPoint(railX[lowRail], top + 4), new GridPoint(40, top + 4));
            builder.link(railSources[lowRail], railPins[lowRail], gates[index], 2);
        }
        Component orAll = builder.placeByBox(OR4, 50, 21, Rotation.R0, NO_PARAMETERS, "");
        builder.route(new GridPoint(44, 10), new GridPoint(46, 10), new GridPoint(46, 21), new GridPoint(50, 21));
        builder.link(gates[0], 3, orAll, 0);
        builder.route(new GridPoint(44, 22), new GridPoint(50, 22));
        builder.link(gates[1], 3, orAll, 1);
        builder.route(new GridPoint(44, 30), new GridPoint(46, 30), new GridPoint(46, 23), new GridPoint(50, 23));
        builder.link(gates[2], 3, orAll, 2);
        builder.route(new GridPoint(44, 38), new GridPoint(48, 38), new GridPoint(48, 24), new GridPoint(50, 24));
        builder.link(gates[3], 3, orAll, 3);
        builder.route(new GridPoint(54, 23), new GridPoint(58, 23));
        Component result = builder.placeByPin(OUTPUT_PIN, new GridPoint(58, 23), 0, Rotation.R0, NO_PARAMETERS, "R");
        builder.link(orAll, 4, result, 0);
        builder.route(new GridPoint(22, 10), new GridPoint(24, 10), new GridPoint(24, 2), new GridPoint(58, 2));
        Component carryOut = builder.placeByPin(OUTPUT_PIN, new GridPoint(58, 2), 0, Rotation.R0, NO_PARAMETERS,
                "Cout");
        builder.link(adder, 4, carryOut, 0);
    }

    /** The body of «Bit de ALU» alone (its checks). */
    static CircuitBuilder aluBitBodyBuilder() {
        CircuitBuilder body = new CircuitBuilder("Bit de ALU (interior)");
        aluBitBody(body);
        return body;
    }

    /**
     * «Bit de ALU» made with {@link DefinitionBuilder} from its body, with «Sumador completo» as a dependency. Pins:
     * Cin, A, B, S1, S0 on the left (y = 2 … 10) and R, Cout on the right (y = 2, 4): box 6 × 12.
     */
    public static DefinitionBundle aluBitDefinition() {
        CircuitBuilder body = aluBitBodyBuilder();
        CircuitDocument document = body.document();
        List<ElementId> everything = document.elements().stream().map(DocumentElement::id).toList();
        PinOrder found = DefinitionBuilder.defaultPinOrder(document, everything);
        PinOrder resultFirst = new PinOrder(found.inputs(), List.of(found.outputs().get(1), found.outputs().get(0)));
        return DefinitionBuilder.fromSelection(document, everything, ALU_BIT_ID, "Bit de ALU", resultFirst,
                body.registry());
    }

    /**
     * ALU de 4 bits: four «Bit de ALU» stacked (bit 0 on top), the carry going down like in «Sumador de 4 bits»;
     * S1 and S0 at the bottom with two rails up to every bit; R to a display; flags Cero = nor4(R), Negativo = R3,
     * Acarreo = Cout3 · ¬S1 · ¬S0 (only a sum has a carry).
     */
    static CircuitBuilder alu() {
        CircuitBuilder builder = new CircuitBuilder("ALU de 4 bits");
        aluBitDefinition().all().forEach(builder::define);
        String bitType = SubcircuitTypes.typeIdOf(ALU_BIT_ID);
        // Instance pins: Cin (0,2), A (0,4), B (0,6), S1 (0,8), S0 (0,10); R (6,2), Cout (6,4); box 6 x 12.
        Component select1 = builder.placeByPin(SWITCH, new GridPoint(6, 86), 0, Rotation.R0, OFF, "S1");
        Component select0 = builder.placeByPin(SWITCH, new GridPoint(6, 92), 0, Rotation.R0, OFF, "S0");
        Component carryIn = builder.placeByPin(SWITCH, new GridPoint(6, 8), 0, Rotation.R0, OFF, "Cin");
        Component display = builder.placeByBox(SEVEN_SEGMENTS_DISPLAY, 54, 72, Rotation.R0, NO_PARAMETERS, "");
        Component zero = builder.placeByBox(NOR4, 68, 38, Rotation.R0, NO_PARAMETERS, "");
        builder.route(new GridPoint(6, 86), new GridPoint(30, 86), new GridPoint(30, 18));
        builder.route(new GridPoint(6, 92), new GridPoint(34, 92), new GridPoint(34, 20));
        Component[] bits = new Component[4];
        int[] turnColumns = {64, 62, 62, 64};
        for (int bit = 0; bit < 4; bit++) {
            int top = 10 + 18 * bit;
            bits[bit] = builder.placeByBox(bitType, 40, top, Rotation.R0, NO_PARAMETERS, "");
            Component switchA = builder.placeByPin(SWITCH, new GridPoint(6, top + 3), 0, Rotation.R0, OFF, "A" + bit);
            Component switchB = builder.placeByPin(SWITCH, new GridPoint(6, top + 9), 0, Rotation.R0, OFF, "B" + bit);
            builder.route(new GridPoint(6, top + 3), new GridPoint(26, top + 3), new GridPoint(26, top + 4),
                    new GridPoint(40, top + 4));
            builder.link(switchA, 0, bits[bit], 1);
            builder.route(new GridPoint(6, top + 9), new GridPoint(28, top + 9), new GridPoint(28, top + 6),
                    new GridPoint(40, top + 6));
            builder.link(switchB, 0, bits[bit], 2);
            builder.route(new GridPoint(30, top + 8), new GridPoint(40, top + 8));
            builder.link(select1, 0, bits[bit], 3);
            builder.route(new GridPoint(34, top + 10), new GridPoint(40, top + 10));
            builder.link(select0, 0, bits[bit], 4);
            if (bit == 0) {
                builder.route(new GridPoint(6, 8), new GridPoint(38, 8), new GridPoint(38, top + 2),
                        new GridPoint(40, top + 2));
                builder.link(carryIn, 0, bits[bit], 0);
            } else {
                builder.route(new GridPoint(46, top - 14), new GridPoint(48, top - 14), new GridPoint(48, top - 4),
                        new GridPoint(38, top - 4), new GridPoint(38, top + 2), new GridPoint(40, top + 2));
                builder.link(bits[bit - 1], 6, bits[bit], 0);
            }
            // R bit k: right past its display column (58 - k, going down to pin k) to the nor4 input k
            int row = top + 2;
            int zeroRow = 38 + bit;
            builder.route(new GridPoint(46, row), new GridPoint(turnColumns[bit], row),
                    new GridPoint(turnColumns[bit], zeroRow), new GridPoint(68, zeroRow));
            builder.link(bits[bit], 5, zero, bit);
            builder.route(new GridPoint(58 - bit, row), new GridPoint(58 - bit, 72));
            builder.link(bits[bit], 5, display, bit);
        }
        builder.route(new GridPoint(72, 40), new GridPoint(76, 40), new GridPoint(76, 38));
        Component zeroLed = builder.placeByPin(LED, new GridPoint(76, 38), 0, Rotation.R0, Map.of("color", "green"),
                "Cero");
        builder.link(zero, 4, zeroLed, 0);
        builder.route(new GridPoint(64, 50), new GridPoint(80, 50), new GridPoint(80, 48));
        Component negative = builder.placeByPin(LED, new GridPoint(80, 48), 0, Rotation.R0,
                Map.of("color", "yellow"), "Negativo");
        builder.link(bits[3], 5, negative, 0);
        // Acarreo = Cout3 · ¬S1 · ¬S0
        Component notSelect1 = builder.placeByBox(NOT, 36, 84, Rotation.R0, NO_PARAMETERS, "");
        Component notSelect0 = builder.placeByBox(NOT, 36, 90, Rotation.R0, NO_PARAMETERS, "");
        Component carryGate = builder.placeByBox(AND3, 52, 88, Rotation.R0, NO_PARAMETERS, "");
        builder.route(new GridPoint(30, 86), new GridPoint(36, 86));
        builder.link(select1, 0, notSelect1, 0);
        builder.route(new GridPoint(34, 92), new GridPoint(36, 92));
        builder.link(select0, 0, notSelect0, 0);
        builder.route(new GridPoint(46, 68), new GridPoint(48, 68), new GridPoint(48, 88), new GridPoint(52, 88));
        builder.link(bits[3], 6, carryGate, 0);
        builder.route(new GridPoint(40, 86), new GridPoint(44, 86), new GridPoint(44, 90), new GridPoint(52, 90));
        builder.link(notSelect1, 1, carryGate, 1);
        builder.route(new GridPoint(40, 92), new GridPoint(52, 92));
        builder.link(notSelect0, 1, carryGate, 2);
        builder.route(new GridPoint(56, 90), new GridPoint(60, 90), new GridPoint(60, 88));
        Component carryLed = builder.placeByPin(LED, new GridPoint(60, 88), 0, Rotation.R0, Map.of("color", "red"),
                "Acarreo");
        builder.link(carryGate, 3, carryLed, 0);

        // Locked frames (they never touch the simulation). Each encloses whole parts with a margin and keeps its two
        // title rows free; the bit frame ends at 78 and the selector frame starts at 80 so they do not overlap.
        frame(builder, 28, 6, 24, 72, "Sumador y lógica", FrameTint.BLUE);
        frame(builder, 0, 80, 44, 16, "Selector de operación", FrameTint.YELLOW);
        frame(builder, 66, 29, 24, 23, "Banderas", FrameTint.PURPLE);

        builder.text(2, 0, "ALU de 4 bits");
        builder.text(60, 74, "resultado");
        builder.text(2, 97, "selector (S1 S0): 00 suma, 01 AND, 10 OR, 11 XOR");
        builder.text(2, 100, "Prueba: elige A, B y la operación con S1 S0: el display muestra el resultado y las banderas");
        builder.text(2, 102, "dicen si es cero, si su bit 3 está en 1 (negativo) y si la suma se pasó de F (acarreo)");
        return builder;
    }

    private static void frame(CircuitBuilder builder, int left, int top, int width, int height, String title,
                              FrameTint tint) {
        builder.document().add(new Frame(builder.document().newFrameId(), new GridPoint(left, top), width, height,
                title, tint, true));
    }
}
