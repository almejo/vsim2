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
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.CONSTANT;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.COUNTER;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.RAM;
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

    /**
     * RAM que se llena: a 4-bit address counter and an 8-bit pass counter (its EN is the address counter's carry, so it
     * goes up each time the address wraps) feed a 16 x 8 RAM (A at (0,4), D at (0,6), WE at (5,12), CLK at (9,12), Q at
     * (14,4)) that writes with WE = 1 on the falling clock edge. All three blocks see the same falling edge: the
     * counters' outputs change a delay later, so the RAM samples the address and the pass number of that edge and each
     * word of pass k gets k, with no off-by-one. Q goes to a double hex display through a bus splitter.
     */
    static CircuitBuilder ramFill() {
        CircuitBuilder builder = new CircuitBuilder("RAM que se llena");
        Component clock = builder.placeByPin(CLOCK, new GridPoint(10, 22), 0, Rotation.R0,
                Map.of("time-up", 500, "time-down", 500), "Reloj");
        Component address = builder.placeByBox(COUNTER, 14, 10, Rotation.R0, Map.of(WIDTH, 4), "Dirección");
        Component pass = builder.placeByBox(COUNTER, 30, 26, Rotation.R0, Map.of(WIDTH, 8), "Pasada");
        Component ram = builder.placeByBox(RAM, 48, 8, Rotation.R0,
                Map.of(MemoryTypes.WORDS, "16", MemoryTypes.WIDTH, 8), "Datos");
        Component splitter = builder.placeByPin(BUS_SPLITTER, new GridPoint(72, 26), 0, Rotation.R90, Map.of(WIDTH, 8),
                "");
        Component display = builder.placeByBox(SEVEN_SEGMENTS_DISPLAY_DOUBLE, 63, 31, Rotation.R0, Map.of(), "");
        // The clock: to the address counter along y = 22, and round the bottom to the pass counter and the RAM.
        builder.route(new GridPoint(10, 22), new GridPoint(24, 22), new GridPoint(24, 17));
        builder.link(clock, 0, address, 4);
        builder.route(new GridPoint(10, 22), new GridPoint(10, 40), new GridPoint(40, 40), new GridPoint(40, 33));
        builder.link(clock, 0, pass, 4);
        builder.route(new GridPoint(40, 40), new GridPoint(57, 40), new GridPoint(57, 20));
        builder.link(clock, 0, ram, 4);
        // The address counter's Q to A, and its carry (the address wraps) to the pass counter's EN, round its left side.
        builder.route(new GridPoint(26, 12), new GridPoint(48, 12));
        builder.link(address, 5, ram, 0);
        builder.route(new GridPoint(26, 15), new GridPoint(28, 15), new GridPoint(28, 36), new GridPoint(36, 36),
                new GridPoint(36, 33));
        builder.link(address, 6, pass, 2);
        // The pass counter's Q to D.
        builder.route(new GridPoint(42, 28), new GridPoint(44, 28), new GridPoint(44, 14), new GridPoint(48, 14));
        builder.link(pass, 5, ram, 1);
        // WE = 1: a one-bit constant below the pin (its pin points up: the registry's default constant is 8 bits wide).
        Map<String, Object> constantParameters = Map.of(WIDTH, 1, "value", "1");
        Component writeEnable = builder.registry().require(CONSTANT).specializedFor(constantParameters)
                .newComponent(builder.document().newComponentId(), new GridPoint(52, 27), Rotation.R270,
                        constantParameters, "WE");
        builder.document().add(writeEnable);
        builder.route(new GridPoint(53, 20), new GridPoint(53, 23));
        builder.link(ram, 2, writeEnable, 0);
        builder.route(new GridPoint(62, 12), new GridPoint(72, 12), new GridPoint(72, 26));
        builder.link(ram, 5, splitter, 0);
        for (int bit = 0; bit < 8; bit++) {
            builder.route(new GridPoint(71 - bit, 28), new GridPoint(71 - bit, 31));
            builder.link(splitter, 1 + bit, display, bit);
        }
        builder.text(2, 0, "RAM que se llena");
        builder.text(30, 6, "dirección");
        builder.text(38, 22, "dato");
        builder.text(2, 44, "Prueba: la RAM escribe con cada bajada del reloj (WE = 1) la palabra de la dirección que marca");
        builder.text(2, 46, "el contador; cada vez que la dirección da la vuelta, «Pasada» sube uno. Así la pasada 0 escribe 00");
        builder.text(2, 48, "en las 16 palabras, la 1 escribe 01 y así hasta FF, y luego vuelve a 00. En «Mapa de memoria» se ve");
        builder.text(2, 50, "cómo se va llenando; el display muestra la palabra que se acaba de escribir");
        return builder;
    }
}
