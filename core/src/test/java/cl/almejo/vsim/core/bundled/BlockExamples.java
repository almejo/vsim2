package cl.almejo.vsim.core.bundled;

import cl.almejo.vsim.core.component.standard.ConstantValue;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.examples.CircuitBuilder;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;

import java.util.Map;

import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.ADDER;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.ALU;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.CLOCK;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.INPUT_PIN;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.OUTPUT_PIN;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.REGISTER;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.WIDTH;

/**
 * The M7 examples of Archivo ▸ Ejemplos (spec M7 §4), made of «Bloques». «Mini ALU»: one ALU block with its inputs
 * and outputs on interface pins (values typed in with a double click). «Acumulador»: a register fed by an adder whose
 * other operand is an input pin, so each falling clock edge adds that number to the total.
 */
public final class BlockExamples {

    private static final Map<String, Object> ONE_BIT = Map.of();

    private BlockExamples() {
    }

    /**
     * Mini ALU, 8 bits. The ALU block (6 x 10: A (0,2), B (0,8), OP (3,10) at the bottom, Y Z N C V at (6, 3..7)) sits in a
     * locked frame «ALU»; A and B (hex) and OP (binary) come in from the left, and the five outputs leave to the right,
     * fanned out in nested L shapes (the lower an output, the nearer its turn) so no two wires cross.
     */
    static CircuitBuilder miniAlu() {
        CircuitBuilder builder = new CircuitBuilder("Mini ALU");
        Component alu = builder.placeByBox(ALU, 30, 10, Rotation.R0, Map.of(WIDTH, 8), "8 bits");
        Component inputA = builder.placeByPin(INPUT_PIN, new GridPoint(18, 12), 0, Rotation.R0,
                pin(8, ConstantValue.HEX), "A");
        builder.route(new GridPoint(18, 12), new GridPoint(30, 12));
        builder.link(inputA, 0, alu, 0);
        Component inputB = builder.placeByPin(INPUT_PIN, new GridPoint(18, 18), 0, Rotation.R0,
                pin(8, ConstantValue.HEX), "B");
        builder.route(new GridPoint(18, 18), new GridPoint(30, 18));
        builder.link(inputB, 0, alu, 1);
        Component operation = builder.placeByPin(INPUT_PIN, new GridPoint(18, 26), 0, Rotation.R0,
                pin(3, ConstantValue.BINARY), "OP");
        builder.route(new GridPoint(18, 26), new GridPoint(33, 26), new GridPoint(33, 20));
        builder.link(operation, 0, alu, 2);

        Component result = builder.placeByPin(OUTPUT_PIN, new GridPoint(52, 13), 0, Rotation.R0,
                pin(8, ConstantValue.SIGNED), "Y");
        builder.route(new GridPoint(36, 13), new GridPoint(52, 13));
        builder.link(alu, 3, result, 0);
        String[] flags = {"Z", "N", "C", "V"};
        for (int flag = 0; flag < flags.length; flag++) {
            int aluRow = 14 + flag;
            int turnColumn = 48 - 2 * flag;
            int pinRow = 19 + 5 * flag;
            builder.route(new GridPoint(36, aluRow), new GridPoint(turnColumn, aluRow),
                    new GridPoint(turnColumn, pinRow), new GridPoint(52, pinRow));
            Component flagPin = builder.placeByPin(OUTPUT_PIN, new GridPoint(52, pinRow), 0, Rotation.R0, ONE_BIT,
                    flags[flag]);
            builder.link(alu, 4 + flag, flagPin, 0);
        }

        // Locked, like the frames of «ALU de 4 bits» (a gate example, still 4 bits): it holds only the block, and its title band (rows 5 to 7) has
        // no wire; the wires cross its sides.
        frame(builder, 24, 5, 15, 18, "ALU", FrameTint.BLUE);

        builder.text(2, 0, "Mini ALU");
        builder.text(58, 18, "Z: Y es 0");
        builder.text(58, 23, "N: Y es negativo");
        builder.text(58, 28, "C: acarreo o préstamo");
        builder.text(58, 33, "V: desborde con signo");
        builder.text(2, 38, "OP: 000 A + B, 001 A − B, 010 AND, 011 OR, 100 XOR, 101 NOT A, 110 A << 1, 111 A >> 1");
        builder.text(2, 40, "Doble clic en A, B u OP para escribir un valor (por ejemplo 0x5 o 0b001)");
        builder.text(2, 43, "Prueba: A = 5, B = 3, OP = 001 → Y = 2; OP = 000 con A = 0x7F, B = 1 → Y = −128 y V = 1");
        return builder;
    }

    /**
     * Acumulador. The adder (4 x 8: A (0,3), B (0,5), Cin (2,0), S (4,4), Cout (2,8)) adds the register's Q (fed back
     * over the top) and «Sumando»; S goes straight into the register's D (12 x 7: D (0,2), Q (12,2), EN (6,7),
     * CLR (8,7), CLK (10,7)). Cin and EN are left unconnected (rest values 0 and 1); Cout is not used.
     */
    static CircuitBuilder accumulator() {
        CircuitBuilder builder = new CircuitBuilder("Acumulador");
        Component adder = builder.placeByBox(ADDER, 24, 8, Rotation.R0, Map.of(WIDTH, 8), "Sumador");
        Component register = builder.placeByBox(REGISTER, 34, 10, Rotation.R0,
                Map.of(WIDTH, 8, ConstantValue.FORMAT, ConstantValue.DECIMAL), "Registro");
        Component addend = builder.placeByPin(INPUT_PIN, new GridPoint(14, 13), 0, Rotation.R0,
                pin(8, ConstantValue.DECIMAL), "Sumando");
        builder.route(new GridPoint(14, 13), new GridPoint(24, 13));
        builder.link(addend, 0, adder, 1);
        builder.route(new GridPoint(28, 12), new GridPoint(34, 12));
        builder.link(adder, 3, register, 0);

        Component total = builder.placeByPin(OUTPUT_PIN, new GridPoint(56, 12), 0, Rotation.R0,
                pin(8, ConstantValue.DECIMAL), "Total");
        builder.route(new GridPoint(46, 12), new GridPoint(56, 12));
        builder.link(register, 4, total, 0);
        builder.route(new GridPoint(50, 12), new GridPoint(50, 4), new GridPoint(20, 4), new GridPoint(20, 11),
                new GridPoint(24, 11));
        builder.link(register, 4, adder, 0);

        Component clear = builder.placeByPin(INPUT_PIN, new GridPoint(30, 21), 0, Rotation.R0, ONE_BIT, "CLR");
        builder.route(new GridPoint(30, 21), new GridPoint(42, 21), new GridPoint(42, 17));
        builder.link(clear, 0, register, 2);
        Component clock = builder.placeByPin(CLOCK, new GridPoint(36, 27), 0, Rotation.R0,
                Map.of("time-up", 500, "time-down", 500), "Reloj");
        builder.route(new GridPoint(36, 27), new GridPoint(44, 27), new GridPoint(44, 17));
        builder.link(clock, 0, register, 3);

        builder.text(2, 0, "Acumulador");
        builder.text(52, 6, "el total vuelve al sumador");
        builder.text(2, 31, "Prueba: doble clic en «Sumando» y escribe 3: con cada bajada del reloj el total suma 3");
        builder.text(2, 33, "(3, 6, 9, …; son 8 bits: pasado 255 da la vuelta); un clic en «CLR» lo deja en 0");
        return builder;
    }

    private static Map<String, Object> pin(int width, String format) {
        return Map.of(WIDTH, width, ConstantValue.FORMAT, format);
    }

    private static void frame(CircuitBuilder builder, int left, int top, int width, int height, String title,
                              FrameTint tint) {
        builder.document().add(new Frame(builder.document().newFrameId(), new GridPoint(left, top), width, height,
                title, tint, true));
    }
}
