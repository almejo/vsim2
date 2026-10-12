package cl.almejo.vsim.core.bundled;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
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
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.INPUT_PIN;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.LED;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.NOT;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.OR2;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.OR4;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.OUTPUT_PIN;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.SWITCH;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.XOR2;

/**
 * The combinational examples of Archivo ▸ Ejemplos (spec M6.1 §3.1): sumador completo, sumador de 4 bits,
 * multiplexor 4:1, decodificador 2 a 4, paridad de 3 bits and multiplicador 2×2. Inputs on the left, outputs on the
 * right; texts Spanish only (spec §8.3). Every wire ends on a pin, a corner or a T; stacked gates are at least 6 rows
 * apart; an LED is fed from below at its pin; a display from above at its pins.
 */
public final class CombinationalExamples {

    /** The id of the user component «Sumador completo» (in «Sumador de 4 bits» and inside the ALU's slice). */
    public static final String FULL_ADDER_ID = "sumador-completo";

    private static final Map<String, Object> OFF = Map.of("value", "0");
    private static final Map<String, Object> NO_PARAMETERS = Map.of();

    private CombinationalExamples() {
    }

    // Sumador completo: S = A xor B xor Cin, Cout = A·B + Cin·(A xor B).
    static CircuitBuilder fullAdder() {
        CircuitBuilder builder = new CircuitBuilder("Sumador completo");
        fullAdderBody(builder, false);
        builder.text(2, 0, "Sumador completo");
        builder.text(51, 7, "suma");
        builder.text(53, 17, "acarreo");
        builder.text(2, 33, "Prueba: cambia A, B y Cin con un clic: S y Cout dan la suma en binario (Cout vale 2)");
        return builder;
    }

    /**
     * The gates of a full adder. With {@code asComponent} the inputs are input pins and the outputs output pins (the
     * body of «Sumador completo»); otherwise switches and LEDs standing on their wires.
     */
    static void fullAdderBody(CircuitBuilder builder, boolean asComponent) {
        Component inputA = input(builder, asComponent, new GridPoint(6, 7), "A");
        Component inputB = input(builder, asComponent, new GridPoint(6, 13), "B");
        Component inputCarry = input(builder, asComponent, new GridPoint(6, 21), "Cin");
        Component firstXor = builder.placeByBox(XOR2, 16, 6, Rotation.R0, NO_PARAMETERS, "");
        Component firstAnd = builder.placeByBox(AND2, 16, 26, Rotation.R0, NO_PARAMETERS, "");
        Component secondXor = builder.placeByBox(XOR2, 28, 8, Rotation.R0, NO_PARAMETERS, "");
        Component secondAnd = builder.placeByBox(AND2, 28, 16, Rotation.R0, NO_PARAMETERS, "");
        Component orGate = builder.placeByBox(OR2, 40, 18, Rotation.R0, NO_PARAMETERS, "");

        builder.route(new GridPoint(6, 7), new GridPoint(10, 7));
        builder.route(new GridPoint(10, 6), new GridPoint(10, 26));
        builder.route(new GridPoint(10, 6), new GridPoint(16, 6));
        builder.route(new GridPoint(10, 26), new GridPoint(16, 26));
        builder.link(inputA, 0, firstXor, 0);
        builder.link(inputA, 0, firstAnd, 0);

        builder.route(new GridPoint(6, 13), new GridPoint(12, 13));
        builder.route(new GridPoint(12, 10), new GridPoint(12, 30));
        builder.route(new GridPoint(12, 10), new GridPoint(16, 10));
        builder.route(new GridPoint(12, 30), new GridPoint(16, 30));
        builder.link(inputB, 0, firstXor, 1);
        builder.link(inputB, 0, firstAnd, 1);

        builder.route(new GridPoint(6, 21), new GridPoint(24, 21), new GridPoint(24, 12), new GridPoint(28, 12));
        builder.route(new GridPoint(24, 20), new GridPoint(28, 20));
        builder.link(inputCarry, 0, secondXor, 1);
        builder.link(inputCarry, 0, secondAnd, 1);

        builder.route(new GridPoint(20, 8), new GridPoint(28, 8));
        builder.route(new GridPoint(22, 8), new GridPoint(22, 16), new GridPoint(28, 16));
        builder.link(firstXor, 2, secondXor, 0);
        builder.link(firstXor, 2, secondAnd, 0);

        builder.route(new GridPoint(32, 18), new GridPoint(40, 18));
        builder.link(secondAnd, 2, orGate, 0);
        builder.route(new GridPoint(20, 28), new GridPoint(36, 28), new GridPoint(36, 22), new GridPoint(40, 22));
        builder.link(firstAnd, 2, orGate, 1);

        output(builder, asComponent, secondXor, new GridPoint(48, 10), "S", "green");
        output(builder, asComponent, orGate, new GridPoint(50, 20), "Cout", "yellow");
    }

    private static Component input(CircuitBuilder builder, boolean asComponent, GridPoint pinPoint, String label) {
        return asComponent
                ? builder.placeByPin(INPUT_PIN, pinPoint, 0, Rotation.R0, NO_PARAMETERS, label)
                : builder.placeByPin(SWITCH, pinPoint, 0, Rotation.R0, OFF, label);
    }

    /** From the gate's output right to {@code end}: an output pin there, or an LED standing two rows above it. */
    private static void output(CircuitBuilder builder, boolean asComponent, Component gate, GridPoint end,
                               String label, String color) {
        GridPoint start = builder.pin(gate, 2);
        Component load;
        if (asComponent) {
            builder.route(start, end);
            load = builder.placeByPin(OUTPUT_PIN, end, 0, Rotation.R0, NO_PARAMETERS, label);
        } else {
            GridPoint ledPin = end.translate(0, -2);
            builder.route(start, end, ledPin);
            load = builder.placeByPin(LED, ledPin, 0, Rotation.R0, Map.of("color", color), label);
        }
        builder.link(gate, 2, load, 0);
    }

    /**
     * «Sumador completo» as the user would make it: the full adder with input and output pins, selected whole and
     * turned into a component with {@link DefinitionBuilder}. Its pins are ordered Cin, A, B on the left and S, Cout
     * on the right, so the carry of a stack of them enters at the top and leaves below the sum.
     */
    public static DefinitionBundle fullAdderDefinition() {
        CircuitBuilder body = new CircuitBuilder("Sumador completo");
        fullAdderBody(body, true);
        CircuitDocument document = body.document();
        List<ElementId> everything = document.elements().stream().map(DocumentElement::id).toList();
        PinOrder found = DefinitionBuilder.defaultPinOrder(document, everything);
        PinOrder carryFirst = new PinOrder(List.of(found.inputs().get(2), found.inputs().get(0),
                found.inputs().get(1)), found.outputs());
        return DefinitionBuilder.fromSelection(document, everything, FULL_ADDER_ID, "Sumador completo", carryFirst,
                body.registry());
    }

    /** The body of «Sumador completo» alone (its checks). */
    static CircuitBuilder fullAdderBodyBuilder() {
        CircuitBuilder body = new CircuitBuilder("Sumador completo (interior)");
        fullAdderBody(body, true);
        return body;
    }

    /**
     * Sumador de 4 bits: four «Sumador completo» stacked, bit 0 on top, the carry going down from each Cout to the
     * next Cin. Displays A and B (below the switches, fed by vertical taps that pass under the other rows) and the
     * sum (fed by a staircase without crossings); Cout on an LED.
     */
    static CircuitBuilder adderFourBits() {
        CircuitBuilder builder = new CircuitBuilder("Sumador de 4 bits");
        fullAdderDefinition().all().forEach(builder::define);
        String adderType = SubcircuitTypes.typeIdOf(FULL_ADDER_ID);
        // Instance pins: Cin (0,2), A (0,4), B (0,6) on the left; S (6,2), Cout (6,4) on the right; box 6 x 8.
        Component displayA = builder.placeByBox(SEVEN_SEGMENTS_DISPLAY, 10, 62, Rotation.R0, NO_PARAMETERS, "");
        Component displayB = builder.placeByBox(SEVEN_SEGMENTS_DISPLAY, 18, 62, Rotation.R0, NO_PARAMETERS, "");
        Component displaySum = builder.placeByBox(SEVEN_SEGMENTS_DISPLAY, 50, 62, Rotation.R0, NO_PARAMETERS, "");
        Component carryIn = builder.placeByPin(SWITCH, new GridPoint(6, 7), 0, Rotation.R0, OFF, "Cin");
        Component previous = null;
        for (int bit = 0; bit < 4; bit++) {
            int top = 10 + 12 * bit;
            Component adder = builder.placeByBox(adderType, 36, top, Rotation.R0, NO_PARAMETERS, "");
            Component switchA = builder.placeByPin(SWITCH, new GridPoint(6, top + 3), 0, Rotation.R0, OFF, "A" + bit);
            Component switchB = builder.placeByPin(SWITCH, new GridPoint(6, top + 8), 0, Rotation.R0, OFF, "B" + bit);
            builder.route(new GridPoint(6, top + 3), new GridPoint(30, top + 3), new GridPoint(30, top + 4),
                    new GridPoint(36, top + 4));
            builder.link(switchA, 0, adder, 1);
            builder.route(new GridPoint(6, top + 8), new GridPoint(32, top + 8), new GridPoint(32, top + 6),
                    new GridPoint(36, top + 6));
            builder.link(switchB, 0, adder, 2);
            // the displays of A and B read the switches through taps going down to their pins (bit k: pin in k)
            builder.route(new GridPoint(14 - bit, top + 3), new GridPoint(14 - bit, 62));
            builder.link(switchA, 0, displayA, bit);
            builder.route(new GridPoint(22 - bit, top + 8), new GridPoint(22 - bit, 62));
            builder.link(switchB, 0, displayB, bit);
            if (previous == null) {
                builder.route(new GridPoint(6, 7), new GridPoint(34, 7), new GridPoint(34, top + 2),
                        new GridPoint(36, top + 2));
                builder.link(carryIn, 0, adder, 0);
            } else {
                builder.route(new GridPoint(42, top - 8), new GridPoint(44, top - 8), new GridPoint(44, top - 2),
                        new GridPoint(34, top - 2), new GridPoint(34, top + 2), new GridPoint(36, top + 2));
                builder.link(previous, 4, adder, 0);
            }
            // the sum bit k goes right and down to pin k of the sum display (bit 0 to the rightmost pin)
            builder.route(new GridPoint(42, top + 2), new GridPoint(54 - bit, top + 2), new GridPoint(54 - bit, 62));
            builder.link(adder, 3, displaySum, bit);
            previous = adder;
        }
        builder.route(new GridPoint(42, 50), new GridPoint(44, 50), new GridPoint(44, 60), new GridPoint(47, 60),
                new GridPoint(47, 58));
        Component carryOut = builder.placeByPin(LED, new GridPoint(47, 58), 0, Rotation.R0,
                Map.of("color", "yellow"), "Cout");
        builder.link(previous, 4, carryOut, 0);

        builder.text(2, 0, "Sumador de 4 bits");
        builder.text(45, 14, "acarreo");
        builder.text(11, 69, "A");
        builder.text(19, 69, "B");
        builder.text(50, 69, "A + B");
        builder.text(2, 73, "Prueba: elige A y B con los interruptores: los displays muestran A, B y A + B en hexadecimal;");
        builder.text(2, 75, "Cout se enciende cuando la suma pasa de F.");
        return builder;
    }

    /**
     * Multiplexor 4:1: four and3 (Di, S1 or ¬S1, S0 or ¬S0) into an or4. The selector rails (S1, ¬S1, S0, ¬S0) go up
     * from the switches at the bottom; the data rows pass over them.
     */
    static CircuitBuilder multiplexer() {
        CircuitBuilder builder = new CircuitBuilder("Multiplexor 4:1");
        Component[] gates = new Component[4];
        Component[] data = new Component[4];
        for (int index = 0; index < 4; index++) {
            int top = 6 + 8 * index;
            gates[index] = builder.placeByBox(AND3, 36, top, Rotation.R0, NO_PARAMETERS, "");
            data[index] = builder.placeByPin(SWITCH, new GridPoint(6, top), 0, Rotation.R0, OFF, "D" + index);
            builder.route(new GridPoint(6, top), new GridPoint(36, top));
            builder.link(data[index], 0, gates[index], 0);
        }
        Component select1 = builder.placeByPin(SWITCH, new GridPoint(6, 42), 0, Rotation.R0, OFF, "S1");
        Component select0 = builder.placeByPin(SWITCH, new GridPoint(6, 52), 0, Rotation.R0, OFF, "S0");
        Component notSelect1 = builder.placeByBox(NOT, 14, 44, Rotation.R0, NO_PARAMETERS, "");
        Component notSelect0 = builder.placeByBox(NOT, 26, 54, Rotation.R0, NO_PARAMETERS, "");
        // rails: S1 at x = 12, ¬S1 at 20, S0 at 24, ¬S0 at 32, each up to its last tap
        builder.route(new GridPoint(6, 42), new GridPoint(12, 42), new GridPoint(12, 24));
        builder.route(new GridPoint(12, 42), new GridPoint(12, 46), new GridPoint(14, 46));
        builder.link(select1, 0, notSelect1, 0);
        builder.route(new GridPoint(18, 46), new GridPoint(20, 46), new GridPoint(20, 8));
        builder.route(new GridPoint(6, 52), new GridPoint(24, 52), new GridPoint(24, 18));
        builder.route(new GridPoint(24, 52), new GridPoint(24, 56), new GridPoint(26, 56));
        builder.link(select0, 0, notSelect0, 0);
        builder.route(new GridPoint(30, 56), new GridPoint(32, 56), new GridPoint(32, 10));
        int[] railX = {12, 20, 24, 32};
        Component[] railSource = {select1, notSelect1, select0, notSelect0};
        int[] railPin = {0, 1, 0, 1};
        for (int index = 0; index < 4; index++) {
            int top = 6 + 8 * index;
            int highRail = (index & 2) != 0 ? 0 : 1;
            int lowRail = (index & 1) != 0 ? 2 : 3;
            builder.route(new GridPoint(railX[highRail], top + 2), new GridPoint(36, top + 2));
            builder.link(railSource[highRail], railPin[highRail], gates[index], 1);
            builder.route(new GridPoint(railX[lowRail], top + 4), new GridPoint(36, top + 4));
            builder.link(railSource[lowRail], railPin[lowRail], gates[index], 2);
        }
        Component orGate = builder.placeByBox(OR4, 48, 15, Rotation.R0, NO_PARAMETERS, "");
        builder.route(new GridPoint(40, 8), new GridPoint(44, 8), new GridPoint(44, 15), new GridPoint(48, 15));
        builder.link(gates[0], 3, orGate, 0);
        builder.route(new GridPoint(40, 16), new GridPoint(48, 16));
        builder.link(gates[1], 3, orGate, 1);
        builder.route(new GridPoint(40, 24), new GridPoint(44, 24), new GridPoint(44, 17), new GridPoint(48, 17));
        builder.link(gates[2], 3, orGate, 2);
        builder.route(new GridPoint(40, 32), new GridPoint(46, 32), new GridPoint(46, 18), new GridPoint(48, 18));
        builder.link(gates[3], 3, orGate, 3);
        builder.route(new GridPoint(52, 17), new GridPoint(56, 17), new GridPoint(56, 15));
        Component led = builder.placeByPin(LED, new GridPoint(56, 15), 0, Rotation.R0, Map.of("color", "green"), "Y");
        builder.link(orGate, 4, led, 0);

        builder.text(2, 0, "Multiplexor 4:1");
        builder.text(2, 57, "selector");
        builder.text(58, 14, "salida");
        builder.text(2, 60, "Prueba: S1 S0 eligen qué dato llega a Y: 00 → D0, 01 → D1, 10 → D2, 11 → D3");
        return builder;
    }

    /** Decodificador 2 a 4: four rails (A1, ¬A1, A0, ¬A0) and one and2 per output; Y = 1 only for the number A1A0. */
    static CircuitBuilder decoder() {
        CircuitBuilder builder = new CircuitBuilder("Decodificador 2 a 4");
        Component high = builder.placeByPin(SWITCH, new GridPoint(6, 6), 0, Rotation.R0, OFF, "A1");
        Component low = builder.placeByPin(SWITCH, new GridPoint(6, 14), 0, Rotation.R0, OFF, "A0");
        Component notHigh = builder.placeByBox(NOT, 14, 8, Rotation.R0, NO_PARAMETERS, "");
        Component notLow = builder.placeByBox(NOT, 26, 16, Rotation.R0, NO_PARAMETERS, "");
        Component[] gates = new Component[4];
        for (int output = 0; output < 4; output++) {
            gates[output] = builder.placeByBox(AND2, 40, 22 + 8 * output, Rotation.R0, NO_PARAMETERS, "");
        }
        // rails: A1 at x = 12, ¬A1 at x = 20, A0 at x = 24, ¬A0 at x = 32; each ends at its last tap
        builder.route(new GridPoint(6, 6), new GridPoint(12, 6), new GridPoint(12, 46));
        builder.route(new GridPoint(12, 10), new GridPoint(14, 10));
        builder.link(high, 0, notHigh, 0);
        builder.route(new GridPoint(18, 10), new GridPoint(20, 10), new GridPoint(20, 30));
        builder.route(new GridPoint(6, 14), new GridPoint(24, 14), new GridPoint(24, 50));
        builder.route(new GridPoint(24, 18), new GridPoint(26, 18));
        builder.link(low, 0, notLow, 0);
        builder.route(new GridPoint(30, 18), new GridPoint(32, 18), new GridPoint(32, 42));

        // Y0 = ¬A1·¬A0, Y1 = ¬A1·A0, Y2 = A1·¬A0, Y3 = A1·A0
        Component[] highSources = {notHigh, notHigh, high, high};
        int[] highPins = {1, 1, 0, 0};
        int[] highX = {20, 20, 12, 12};
        Component[] lowSources = {notLow, low, notLow, low};
        int[] lowPins = {1, 0, 1, 0};
        int[] lowX = {32, 24, 32, 24};
        for (int output = 0; output < 4; output++) {
            int top = 22 + 8 * output;
            builder.route(new GridPoint(highX[output], top), new GridPoint(40, top));
            builder.link(highSources[output], highPins[output], gates[output], 0);
            builder.route(new GridPoint(lowX[output], top + 4), new GridPoint(40, top + 4));
            builder.link(lowSources[output], lowPins[output], gates[output], 1);
            builder.route(new GridPoint(44, top + 2), new GridPoint(48, top + 2), new GridPoint(48, top));
            Component led = builder.placeByPin(LED, new GridPoint(48, top), 0, Rotation.R0, Map.of("color", "red"),
                    "Y" + output);
            builder.link(gates[output], 2, led, 0);
        }
        builder.text(2, 0, "Decodificador 2 a 4");
        builder.text(2, 17, "número");
        builder.text(51, 17, "salidas");
        builder.text(2, 54, "Prueba: A1 A0 forman un número de 0 a 3: solo se enciende la salida Y de ese número");
        return builder;
    }

    /** Paridad de 3 bits: Impar = D2 xor D1 xor D0, Par = not Impar. */
    static CircuitBuilder parity() {
        CircuitBuilder builder = new CircuitBuilder("Paridad de 3 bits");
        Component high = builder.placeByPin(SWITCH, new GridPoint(6, 6), 0, Rotation.R0, OFF, "D2");
        Component middle = builder.placeByPin(SWITCH, new GridPoint(6, 12), 0, Rotation.R0, OFF, "D1");
        Component low = builder.placeByPin(SWITCH, new GridPoint(6, 18), 0, Rotation.R0, OFF, "D0");
        Component firstXor = builder.placeByBox(XOR2, 16, 6, Rotation.R0, NO_PARAMETERS, "");
        Component secondXor = builder.placeByBox(XOR2, 26, 12, Rotation.R0, NO_PARAMETERS, "");
        Component inverter = builder.placeByBox(NOT, 36, 20, Rotation.R0, NO_PARAMETERS, "");
        builder.route(new GridPoint(6, 6), new GridPoint(16, 6));
        builder.link(high, 0, firstXor, 0);
        builder.route(new GridPoint(6, 12), new GridPoint(12, 12), new GridPoint(12, 10), new GridPoint(16, 10));
        builder.link(middle, 0, firstXor, 1);
        builder.route(new GridPoint(20, 8), new GridPoint(23, 8), new GridPoint(23, 12), new GridPoint(26, 12));
        builder.link(firstXor, 2, secondXor, 0);
        builder.route(new GridPoint(6, 18), new GridPoint(24, 18), new GridPoint(24, 16), new GridPoint(26, 16));
        builder.link(low, 0, secondXor, 1);
        builder.route(new GridPoint(30, 14), new GridPoint(40, 14), new GridPoint(40, 12));
        Component odd = builder.placeByPin(LED, new GridPoint(40, 12), 0, Rotation.R0, Map.of("color", "green"),
                "Impar");
        builder.link(secondXor, 2, odd, 0);
        builder.route(new GridPoint(34, 14), new GridPoint(34, 22), new GridPoint(36, 22));
        builder.link(secondXor, 2, inverter, 0);
        builder.route(new GridPoint(40, 22), new GridPoint(46, 22), new GridPoint(46, 20));
        Component even = builder.placeByPin(LED, new GridPoint(46, 20), 0, Rotation.R0, Map.of("color", "yellow"),
                "Par");
        builder.link(inverter, 1, even, 0);
        builder.text(2, 0, "Paridad de 3 bits");
        builder.text(2, 27, "Prueba: cambia D2, D1 y D0: «Impar» se enciende con 1 o 3 unos, «Par» con 0 o 2");
        return builder;
    }

    /**
     * Multiplicador 2×2: four and2 make the partial products, two half adders add them: P0 = A0·B0,
     * P1 = A1·B0 xor A0·B1, P2 = A1·B1 xor c1, P3 = A1·B1·c1 with c1 = A1·B0·A0·B1. LEDs P0..P3 and a display.
     */
    static CircuitBuilder multiplier() {
        CircuitBuilder builder = new CircuitBuilder("Multiplicador 2×2");
        String[] names = {"A1", "A0", "B1", "B0"};
        int[] railX = {12, 16, 20, 24};
        int[] railBottom = {54, 46, 58, 42};
        Component[] inputs = new Component[4];
        for (int index = 0; index < 4; index++) {
            int row = 6 + 6 * index;
            inputs[index] = builder.placeByPin(SWITCH, new GridPoint(6, row), 0, Rotation.R0, OFF, names[index]);
            builder.route(new GridPoint(6, row), new GridPoint(railX[index], row),
                    new GridPoint(railX[index], railBottom[index]));
        }
        // partial products, top to bottom: A0·B0, A1·B0, A0·B1, A1·B1 (and2 in0 from an A rail, in1 from a B rail)
        int[][] factors = {{1, 3}, {0, 3}, {1, 2}, {0, 2}};
        Component[] products = new Component[4];
        for (int index = 0; index < 4; index++) {
            int top = 30 + 8 * index;
            products[index] = builder.placeByBox(AND2, 30, top, Rotation.R0, NO_PARAMETERS, "");
            builder.route(new GridPoint(railX[factors[index][0]], top), new GridPoint(30, top));
            builder.link(inputs[factors[index][0]], 0, products[index], 0);
            builder.route(new GridPoint(railX[factors[index][1]], top + 4), new GridPoint(30, top + 4));
            builder.link(inputs[factors[index][1]], 0, products[index], 1);
        }
        // first half adder: A1·B0 (row 40) and A0·B1 (row 48)
        Component firstXor = builder.placeByBox(XOR2, 42, 40, Rotation.R0, NO_PARAMETERS, "");
        Component firstAnd = builder.placeByBox(AND2, 42, 50, Rotation.R0, NO_PARAMETERS, "");
        builder.route(new GridPoint(34, 40), new GridPoint(42, 40));
        builder.route(new GridPoint(36, 40), new GridPoint(36, 54), new GridPoint(42, 54));
        builder.link(products[1], 2, firstXor, 0);
        builder.link(products[1], 2, firstAnd, 1);
        builder.route(new GridPoint(34, 48), new GridPoint(39, 48), new GridPoint(39, 44), new GridPoint(42, 44));
        builder.route(new GridPoint(39, 48), new GridPoint(39, 50), new GridPoint(42, 50));
        builder.link(products[2], 2, firstXor, 1);
        builder.link(products[2], 2, firstAnd, 0);
        // second half adder: c1 (row 52) and A1·B1 (row 56)
        Component secondXor = builder.placeByBox(XOR2, 54, 52, Rotation.R0, NO_PARAMETERS, "");
        Component secondAnd = builder.placeByBox(AND2, 54, 60, Rotation.R0, NO_PARAMETERS, "");
        builder.route(new GridPoint(46, 52), new GridPoint(54, 52));
        builder.route(new GridPoint(50, 52), new GridPoint(50, 60), new GridPoint(54, 60));
        builder.link(firstAnd, 2, secondXor, 0);
        builder.link(firstAnd, 2, secondAnd, 0);
        builder.route(new GridPoint(34, 56), new GridPoint(52, 56), new GridPoint(54, 56));
        builder.route(new GridPoint(52, 56), new GridPoint(52, 64), new GridPoint(54, 64));
        builder.link(products[3], 2, secondXor, 1);
        builder.link(products[3], 2, secondAnd, 1);
        // P0..P3 right to the display (P0 to its rightmost pin), each with an LED standing on its line
        Component display = builder.placeByBox(SEVEN_SEGMENTS_DISPLAY, 66, 70, Rotation.R0, NO_PARAMETERS, "");
        Component[] sources = {products[0], firstXor, secondXor, secondAnd};
        int[] rows = {32, 42, 54, 62};
        int[] startX = {34, 46, 58, 58};
        for (int bit = 0; bit < 4; bit++) {
            int column = 70 - bit;
            builder.route(new GridPoint(startX[bit], rows[bit]), new GridPoint(column, rows[bit]),
                    new GridPoint(column, 70));
            builder.link(sources[bit], 2, display, bit);
            builder.route(new GridPoint(62, rows[bit]), new GridPoint(62, rows[bit] - 2));
            Component led = builder.placeByPin(LED, new GridPoint(62, rows[bit] - 2), 0, Rotation.R0,
                    Map.of("color", "red"), "P" + bit);
            builder.link(sources[bit], 2, led, 0);
        }
        builder.text(2, 0, "Multiplicador 2×2");
        builder.text(30, 27, "productos parciales");
        builder.text(42, 46, "semisumador");
        builder.text(66, 77, "A × B");
        builder.text(2, 80, "Prueba: A = A1A0 y B = B1B0 valen de 0 a 3: los LED P y el display muestran A × B (3 × 3 = 9)");
        return builder;
    }
}
