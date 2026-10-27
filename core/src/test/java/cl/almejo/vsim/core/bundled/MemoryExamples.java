package cl.almejo.vsim.core.bundled;

import cl.almejo.vsim.core.component.standard.MemoryTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.examples.CircuitBuilder;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.memory.MemoryContents;

import java.util.Map;

import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.BUS_SPLITTER;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.CLOCK;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.COUNTER;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.ROM;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY_DOUBLE;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.WIDTH;

/**
 * The memory example of Archivo ▸ Ejemplos: «Programa en ROM», a 16 x 8 ROM holding the Fibonacci numbers (mod 256),
 * a 4-bit counter on its address A and a double hex display on its output Q. Counter box 12 x 7 (Q at (12,2), CLK at
 * (10,7)); ROM box 14 x 12 in bus mode (A at (0,4), Q at (14,4), CS at (7,12) left unconnected = 1); display box 8 x 6
 * fed through a bus splitter, as in «Bus de 8 bits».
 */
public final class MemoryExamples {

    /** The program: 0, 1, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89, 144, 233, 121, 98 (Fibonacci mod 256) in hexadecimal. */
    static final String PROGRAM = "@0 00 01 01 02 03 05 08 0D 15 22 37 59 90 E9 79 62";

    private MemoryExamples() {
    }

    /** Programa en ROM: with each falling clock edge the counter moves to the next address and the display shows it. */
    static CircuitBuilder romProgram() {
        CircuitBuilder builder = new CircuitBuilder("Programa en ROM");
        Component clock = builder.placeByPin(CLOCK, new GridPoint(10, 22), 0, Rotation.R0,
                Map.of("time-up", 500, "time-down", 500), "Reloj");
        Component counter = builder.placeByBox(COUNTER, 14, 10, Rotation.R0, Map.of(WIDTH, 4), "Contador");
        Component rom = builder.placeByBox(ROM, 36, 8, Rotation.R0,
                Map.of(MemoryTypes.WORDS, "16", MemoryTypes.WIDTH, 8,
                        MemoryTypes.CONTENTS, MemoryContents.decode(PROGRAM).encode()),
                "Programa");
        Component splitter = builder.placeByPin(BUS_SPLITTER, new GridPoint(64, 26), 0, Rotation.R90, Map.of(WIDTH, 8),
                "");
        Component display = builder.placeByBox(SEVEN_SEGMENTS_DISPLAY_DOUBLE, 55, 31, Rotation.R0, Map.of(), "");
        builder.route(new GridPoint(10, 22), new GridPoint(24, 22), new GridPoint(24, 17));
        builder.link(clock, 0, counter, 4);
        builder.route(new GridPoint(26, 12), new GridPoint(36, 12));
        builder.link(counter, 5, rom, 0);
        builder.route(new GridPoint(50, 12), new GridPoint(64, 12), new GridPoint(64, 26));
        builder.link(rom, 2, splitter, 0);
        for (int bit = 0; bit < 8; bit++) {
            builder.route(new GridPoint(63 - bit, 28), new GridPoint(63 - bit, 31));
            builder.link(splitter, 1 + bit, display, bit);
        }
        builder.text(2, 0, "Programa en ROM");
        builder.text(28, 6, "dirección");
        builder.text(52, 8, "palabra");
        builder.text(2, 40, "Prueba: con cada bajada del reloj el contador pasa a la dirección siguiente y el display");
        builder.text(2, 42, "muestra la palabra de la ROM: Fibonacci en hexadecimal (00 01 01 02 03 05 08 0D 15 22 …).");
        builder.text(2, 44, "Doble clic en la ROM para cambiar el programa; en «Mapa de memoria» se ve qué dirección se lee");
        return builder;
    }
}
