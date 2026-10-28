package cl.almejo.vsim.core.bundled;

import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.examples.CircuitBuilder;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;

import java.util.Map;

import static cl.almejo.vsim.core.GateParameters.NEGATED;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.BUFFER;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.CLOCK;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.FLIP_FLOP_DATA;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.LED;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.SWITCH;

/**
 * The sequential examples of Archivo ▸ Ejemplos (spec M6.1 §3.1): registro de 4 bits, registro de desplazamiento,
 * contador de 4 bits (the classic ripple counter) and contador Johnson. D flip-flops latch on the falling clock edge
 * and start at Q = 0. Flip-flop pins: D (0,2), Q (4,2), clock (2,4).
 */
public final class SequentialExamples {

    private static final Map<String, Object> NO_PARAMETERS = Map.of();
    private static final Map<String, Object> RED = Map.of("color", "red");

    private SequentialExamples() {
    }

    /** Registro de 4 bits: «Cargar» drives the clocks of four D flip-flops; they load D0..D3 when it goes down. */
    static CircuitBuilder register() {
        CircuitBuilder builder = new CircuitBuilder("Registro de 4 bits");
        Component load = builder.placeByPin(SWITCH, new GridPoint(6, 48), 0, Rotation.R0, Map.of("value", "1"),
                "Cargar");
        builder.route(new GridPoint(6, 48), new GridPoint(14, 48), new GridPoint(14, 14));
        String[] dataValues = {"1", "0", "1", "1"};
        for (int bit = 0; bit < 4; bit++) {
            int top = 8 + 10 * bit;
            Component data = builder.placeByPin(SWITCH, new GridPoint(6, top + 2), 0, Rotation.R0,
                    Map.of("value", dataValues[bit]), "D" + bit);
            Component flipFlop = builder.placeByBox(FLIP_FLOP_DATA, 20, top, Rotation.R0, NO_PARAMETERS, "");
            builder.route(new GridPoint(6, top + 2), new GridPoint(20, top + 2));
            builder.link(data, 0, flipFlop, 0);
            builder.route(new GridPoint(14, top + 6), new GridPoint(22, top + 6), new GridPoint(22, top + 4));
            builder.link(load, 0, flipFlop, 2);
            builder.route(new GridPoint(24, top + 2), new GridPoint(28, top + 2), new GridPoint(28, top));
            Component led = builder.placeByPin(LED, new GridPoint(28, top), 0, Rotation.R0, RED, "Q" + bit);
            builder.link(flipFlop, 1, led, 0);
        }
        builder.text(2, 0, "Registro de 4 bits");
        builder.text(31, 3, "memoria");
        builder.text(2, 52, "Prueba: elige D0..D3 y haz clic en «Cargar» (baja): los LED Q guardan los datos;");
        builder.text(2, 54, "después cambiar D no cambia Q hasta la próxima bajada de «Cargar»");
        return builder;
    }

    /** Registro de desplazamiento: four flip-flops in a row on one clock; each Q feeds the next D. */
    static CircuitBuilder shiftRegister() {
        CircuitBuilder builder = new CircuitBuilder("Registro de desplazamiento");
        Component data = builder.placeByPin(SWITCH, new GridPoint(6, 12), 0, Rotation.R0, Map.of("value", "0"),
                "Dato");
        Component clock = builder.placeByPin(CLOCK, new GridPoint(10, 20), 0, Rotation.R0,
                Map.of("time-up", 500, "time-down", 500), "Reloj");
        Component previous = data;
        int previousPin = 0;
        int lastClockX = 0;
        for (int bit = 0; bit < 4; bit++) {
            int left = 14 + 12 * bit;
            Component flipFlop = builder.placeByBox(FLIP_FLOP_DATA, left, 10, Rotation.R0, NO_PARAMETERS, "");
            if (bit == 0) {
                builder.route(new GridPoint(6, 12), new GridPoint(left, 12));
            }
            builder.link(previous, previousPin, flipFlop, 0);
            int next = bit < 3 ? left + 12 : left + 7;
            builder.route(new GridPoint(left + 4, 12), new GridPoint(next, 12));
            builder.route(new GridPoint(left + 7, 12), new GridPoint(left + 7, 10));
            Component led = builder.placeByPin(LED, new GridPoint(left + 7, 10), 0, Rotation.R0, RED, "Q" + bit);
            builder.link(flipFlop, 1, led, 0);
            builder.route(new GridPoint(left + 2, 20), new GridPoint(left + 2, 14));
            builder.link(clock, 0, flipFlop, 2);
            previous = flipFlop;
            previousPin = 1;
            lastClockX = left + 2;
        }
        builder.route(new GridPoint(10, 20), new GridPoint(lastClockX, 20));
        builder.text(2, 0, "Registro de desplazamiento");
        builder.text(2, 24, "Prueba: enciende «Dato»: con cada bajada del reloj el 1 avanza un LED hacia la derecha;");
        builder.text(2, 26, "apágalo y avanzan ceros");
        return builder;
    }

    /**
     * Contador de 4 bits, the classic ripple counter: each flip-flop toggles (D = ¬Q through a NOT standing on its
     * left) and clocks the next one with its Q, so bit k changes when bit k-1 goes from 1 to 0: it counts up. Bit 0
     * is on the right, next to the clock; LEDs above, the display at the bottom right.
     */
    static CircuitBuilder counter() {
        CircuitBuilder builder = new CircuitBuilder("Contador de 4 bits");
        Component clock = builder.placeByPin(CLOCK, new GridPoint(74, 30), 0, Rotation.R0,
                Map.of("time-up", 250, "time-down", 250), "Reloj");
        Component display = builder.placeByBox(SEVEN_SEGMENTS_DISPLAY, 78, 44, Rotation.R0, NO_PARAMETERS, "");
        Component previous = clock;
        int previousPin = 0;
        for (int bit = 0; bit < 4; bit++) {
            int left = 64 - 18 * bit;
            Component flipFlop = builder.placeByBox(FLIP_FLOP_DATA, left, 20, Rotation.R0, NO_PARAMETERS, "");
            Component inverter = builder.placeByPin(BUFFER, new GridPoint(left - 4, 15), 0, Rotation.R90, NEGATED,
                    "");
            // Q goes up, over the flip-flop to the left (the top line carries Q) and on to the tap at left - 8
            builder.route(new GridPoint(left + 4, 22), new GridPoint(left + 6, 22), new GridPoint(left + 6, 14),
                    new GridPoint(left - 8, 14));
            builder.route(new GridPoint(left - 4, 14), new GridPoint(left - 4, 15));
            builder.link(flipFlop, 1, inverter, 0);
            builder.route(new GridPoint(left - 4, 19), new GridPoint(left - 4, 22), new GridPoint(left, 22));
            builder.link(inverter, 1, flipFlop, 0);
            builder.route(new GridPoint(left + 2, 14), new GridPoint(left + 2, 12));
            Component led = builder.placeByPin(LED, new GridPoint(left + 2, 12), 0, Rotation.R0, RED, "Q" + bit);
            builder.link(flipFlop, 1, led, 0);
            // the clock: bit 0 from the clock on the right, bit k from the tap of bit k-1 (Q of bit k-1)
            if (bit == 0) {
                // down from the clock's corner first, so the wire does not run along the clock's edge
                builder.route(new GridPoint(74, 30), new GridPoint(74, 32), new GridPoint(left + 2, 32),
                        new GridPoint(left + 2, 24));
            } else {
                builder.route(new GridPoint(left + 10, 30), new GridPoint(left + 2, 30), new GridPoint(left + 2, 24));
            }
            builder.link(previous, previousPin, flipFlop, 2);
            // the tap of bit k goes down to its display pin (bit 0 turns highest, so nothing crosses)
            int column = 82 - bit;
            int turn = 34 + 2 * bit;
            builder.route(new GridPoint(left - 8, 14), new GridPoint(left - 8, turn), new GridPoint(column, turn),
                    new GridPoint(column, 44));
            builder.link(flipFlop, 1, display, bit);
            previous = flipFlop;
            previousPin = 1;
        }
        builder.text(2, 0, "Contador de 4 bits");
        builder.text(84, 46, "cuenta");
        builder.text(2, 52, "Prueba: cada bajada del reloj suma 1: Q0 cambia en cada bajada, Q1 cuando Q0 baja, y así;");
        builder.text(2, 54, "el display muestra la cuenta en hexadecimal (0 … F y vuelve a 0)");
        return builder;
    }

    /**
     * Contador Johnson: four flip-flops in a row on one clock, each Q to the next D and ¬Q3 back to D0 over the
     * top. From 0000 it fills with ones and then with zeros: eight states.
     */
    static CircuitBuilder johnsonCounter() {
        CircuitBuilder builder = new CircuitBuilder("Contador Johnson");
        Component clock = builder.placeByPin(CLOCK, new GridPoint(8, 22), 0, Rotation.R0,
                Map.of("time-up", 250, "time-down", 250), "Reloj");
        Component[] flipFlops = new Component[4];
        for (int bit = 0; bit < 4; bit++) {
            int left = 14 + 12 * bit;
            flipFlops[bit] = builder.placeByBox(FLIP_FLOP_DATA, left, 14, Rotation.R0, NO_PARAMETERS, "");
            if (bit > 0) {
                builder.link(flipFlops[bit - 1], 1, flipFlops[bit], 0);
            }
            builder.route(new GridPoint(left + 4, 16), new GridPoint(bit < 3 ? left + 12 : left + 9, 16));
            builder.route(new GridPoint(left + 7, 16), new GridPoint(left + 7, 14));
            Component led = builder.placeByPin(LED, new GridPoint(left + 7, 14), 0, Rotation.R0, RED, "Q" + bit);
            builder.link(flipFlops[bit], 1, led, 0);
            builder.route(new GridPoint(left + 2, 22), new GridPoint(left + 2, 18));
            builder.link(clock, 0, flipFlops[bit], 2);
        }
        builder.route(new GridPoint(8, 22), new GridPoint(52, 22));
        Component inverter = builder.placeByBox(BUFFER, 59, 14, Rotation.R0, NEGATED, "");
        builder.link(flipFlops[3], 1, inverter, 0);
        builder.route(new GridPoint(63, 16), new GridPoint(65, 16), new GridPoint(65, 6), new GridPoint(10, 6),
                new GridPoint(10, 16), new GridPoint(14, 16));
        builder.link(inverter, 1, flipFlops[0], 0);
        builder.text(2, 0, "Contador Johnson");
        builder.text(24, 3, "¬Q3 vuelve a D0");
        builder.text(2, 26, "Prueba: mira los LED: se llenan de unos de izquierda a derecha y luego de ceros;");
        builder.text(2, 28, "son 8 estados distintos con 4 flip-flops (0000, 1000, 1100, 1110, 1111, 0111, 0011, 0001)");
        return builder;
    }
}
