package cl.almejo.vsim.core.bundled;

import cl.almejo.vsim.core.component.standard.BreakpointTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.examples.CircuitBuilder;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;

import java.util.Map;

import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.CLOCK;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.COUNTER;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.LED;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.VALUE;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.WIDTH;

/**
 * The «Punto de parada» example of Archivo ▸ Ejemplos: a clock drives a 4-bit counter whose count shows on a 4-bit
 * LED; a breakpoint «igual a 9» is active and pauses the run each time the count reaches 9, and a second one,
 * «distinto de 0», is switched off so it can be turned on to see it pause at once. Counter box 12 x 7 at (14,10): Q at
 * (26,12), clock at (24,17); breakpoints 8 x 2 with their pin on the left.
 */
public final class BreakpointExamples {

    static final int COUNTER_BITS = 4;
    static final int NINE = 9;
    /** The clock: 250 up, 250 down, so the counter takes one step every 500 units (it changes on the falling edge). */
    static final int HALF_PERIOD = 250;

    private BreakpointExamples() {
    }

    static CircuitBuilder puntoDeParada() {
        CircuitBuilder builder = new CircuitBuilder("Punto de parada");
        Component clock = builder.placeByPin(CLOCK, new GridPoint(6, 22), 0, Rotation.R0,
                Map.of("time-up", HALF_PERIOD, "time-down", HALF_PERIOD), "Reloj");
        Component counter = builder.placeByBox(COUNTER, 14, 10, Rotation.R0, Map.of(WIDTH, COUNTER_BITS), "Contador");
        Component count = builder.placeByPin(LED, new GridPoint(34, 6), 0, Rotation.R0, Map.of(WIDTH, COUNTER_BITS),
                "Cuenta");
        Component equalsNine = builder.placeByPin(BreakpointTypes.BREAKPOINT, new GridPoint(34, 12), 0, Rotation.R0,
                Map.of(WIDTH, COUNTER_BITS, BreakpointTypes.CONDITION, BreakpointTypes.EQUALS, VALUE,
                        String.valueOf(NINE)), "igual a 9");
        Component differsFromZero = builder.placeByPin(BreakpointTypes.BREAKPOINT, new GridPoint(34, 18), 0,
                Rotation.R0, Map.of(WIDTH, COUNTER_BITS, BreakpointTypes.CONDITION, BreakpointTypes.DIFFERS, VALUE,
                        "0", BreakpointTypes.ACTIVE, false), "distinto de 0 (apagado)");

        builder.route(new GridPoint(6, 22), new GridPoint(24, 22), new GridPoint(24, 17));
        builder.link(clock, 0, counter, 4);
        builder.route(new GridPoint(26, 12), new GridPoint(30, 12));
        builder.route(new GridPoint(30, 12), new GridPoint(34, 12));
        builder.route(new GridPoint(30, 12), new GridPoint(30, 6), new GridPoint(34, 6));
        builder.route(new GridPoint(30, 12), new GridPoint(30, 18), new GridPoint(34, 18));
        builder.link(counter, 5, count, 0);
        builder.link(counter, 5, equalsNine, 0);
        builder.link(counter, 5, differsFromZero, 0);

        builder.text(2, 0, "Punto de parada");
        builder.text(2, 28, "Prueba: pon a correr la simulación: se detiene sola cuando la cuenta llega a 9 y la barra de estado dice por qué.");
        builder.text(2, 30, "Vuelve a correr: se detiene la próxima vez que la cuenta vuelve a ser 9 (no mientras siga siendo 9).");
        builder.text(2, 32, "Enciende «Activo» en el punto «distinto de 0»: se detiene apenas la cuenta sale de 0.");
        return builder;
    }
}
