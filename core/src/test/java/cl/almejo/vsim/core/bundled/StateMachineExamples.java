package cl.almejo.vsim.core.bundled;

import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.StickyColor;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.core.document.StickyText;
import cl.almejo.vsim.core.examples.CircuitBuilder;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.logic.LogicValue;
import cl.almejo.vsim.core.machine.DiagramPoint;
import cl.almejo.vsim.core.machine.LoopSide;
import cl.almejo.vsim.core.machine.MachineDefinitions;
import cl.almejo.vsim.core.machine.MachineNote;
import cl.almejo.vsim.core.machine.MachineState;
import cl.almejo.vsim.core.machine.MachineTest;
import cl.almejo.vsim.core.machine.MachineTests;
import cl.almejo.vsim.core.machine.MachineTransition;
import cl.almejo.vsim.core.machine.OutputAssignment;
import cl.almejo.vsim.core.machine.StateMachine;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.CLOCK;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.LED;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.SWITCH;

/**
 * The examples of Archivo ▸ Ejemplos ▸ «Máquinas de estado»: «Semáforo» (Moore: the lights depend on the state alone)
 * and «Detector de secuencia» (Mealy: Z lights on the arrow that completes 1-0-1). Each is a circuit around one
 * machine piece, a sticky note saying what to try, the machine's own note and tests that all pass. The machine is
 * built with the app's model ({@link MachineDefinitions}), never typed as JSON. Machine piece box 12 x 8 at (30, 10):
 * inputs on the left at x = 30, the outputs on the right at x = 42, pins every 2 units from y = 12.
 */
public final class StateMachineExamples {

    private static final Map<String, Object> OFF = Map.of("value", "0");
    private static final Map<String, Object> SLOW_CLOCK = Map.of("time-up", 1000, "time-down", 1000);
    private static final int MACHINE_LEFT = 30;
    private static final int MACHINE_TOP = 10;
    private static final int OUTPUT_X = 42;

    private StateMachineExamples() {
    }

    /**
     * Semáforo: Rojo → Verde → Amarillo → Rojo on every falling edge of a slow clock, with no input but the reset.
     * The pins are R, A and V (Moore outputs of the states); the LEDs fan out to the right.
     */
    static CircuitBuilder semaforo() {
        CircuitBuilder builder = new CircuitBuilder("Semáforo");
        String machineId = "semaforo";
        builder.define(MachineDefinitions.create(machineId, "Semáforo").withMachine(semaforoMachine()));
        Component machine = builder.placeByBox(SubcircuitTypes.typeIdOf(machineId), MACHINE_LEFT, MACHINE_TOP,
                Rotation.R0, Map.of(), "");
        // pinout: RST 0, CLK 1, R 2, A 3, V 4
        Component reset = builder.placeByPin(SWITCH, new GridPoint(18, 8), 0, Rotation.R0, OFF, "Reiniciar");
        builder.route(new GridPoint(18, 8), new GridPoint(24, 8), new GridPoint(24, 12), new GridPoint(MACHINE_LEFT, 12));
        builder.link(reset, 0, machine, 0);
        Component clock = builder.placeByPin(CLOCK, new GridPoint(18, 20), 0, Rotation.R0, SLOW_CLOCK, "Reloj");
        builder.route(new GridPoint(18, 20), new GridPoint(26, 20), new GridPoint(26, 14), new GridPoint(MACHINE_LEFT, 14));
        builder.link(clock, 0, machine, 1);

        Component red = builder.placeByPin(LED, new GridPoint(54, 8), 0, Rotation.R0, Map.of("color", "red"), "Rojo");
        builder.route(new GridPoint(OUTPUT_X, 12), new GridPoint(46, 12), new GridPoint(46, 8), new GridPoint(54, 8));
        builder.link(machine, 2, red, 0);
        Component yellow = builder.placeByPin(LED, new GridPoint(54, 14), 0, Rotation.R0, Map.of("color", "yellow"),
                "Amarillo");
        builder.route(new GridPoint(OUTPUT_X, 14), new GridPoint(54, 14));
        builder.link(machine, 3, yellow, 0);
        Component green = builder.placeByPin(LED, new GridPoint(54, 20), 0, Rotation.R0, Map.of("color", "green"),
                "Verde");
        builder.route(new GridPoint(OUTPUT_X, 16), new GridPoint(50, 16), new GridPoint(50, 20), new GridPoint(54, 20));
        builder.link(machine, 4, green, 0);

        builder.text(2, 0, "Semáforo");
        builder.text(2, 25, "Prueba: mira cómo la luz pasa de rojo a verde y a amarillo con cada bajada del reloj");
        builder.document().add(new StickyNote(builder.document().newStickyNoteId(), new GridPoint(2, 28), 58, 14,
                StickyColor.YELLOW, new StickyText("""
                        Qué probar
                        1. Mira las luces: cada bajada del reloj cambia de estado (rojo, verde, amarillo). Es una máquina de Moore: la luz depende solo del estado.
                        2. Un clic en «Reiniciar» la manda a rojo y ahí se queda mientras esté encendido.
                        3. Doble clic en la máquina para ver su diagrama; con «Probar…» arriba ves su prueba, con todo ✓."""),
                false));
        return builder;
    }

    private static StateMachine semaforoMachine() {
        List<MachineState> states = List.of(
                new MachineState("rojo", "Rojo", new DiagramPoint(0, 0), List.of(new OutputAssignment("R", true))),
                new MachineState("verde", "Verde", new DiagramPoint(260, 0), List.of(new OutputAssignment("V", true))),
                new MachineState("amarillo", "Amarillo", new DiagramPoint(130, 200),
                        List.of(new OutputAssignment("A", true))));
        List<MachineTransition> transitions = List.of(
                new MachineTransition("t1", "rojo", "verde", "", List.of()),
                new MachineTransition("t2", "verde", "amarillo", "", List.of()),
                new MachineTransition("t3", "amarillo", "rojo", "", List.of()));
        MachineTest cycle = MachineTest.empty("p1", "Un ciclo completo", 7);
        String[] lights = {"R", "V", "A"};
        for (int column = 0; column < 7; column++) {
            for (String light : lights) {
                cycle = MachineTests.withExpected(cycle, column, light,
                        light.equals(lights[column % 3]) ? LogicValue.ONE : LogicValue.ZERO);
            }
        }
        MachineNote note = new MachineNote("n1", new DiagramPoint(340, 120), 230, 100, StickyColor.YELLOW,
                "Máquina de Moore: cada estado enciende su luz (R, V o A) y las flechas no tienen condición: "
                        + "con cada bajada del reloj pasa al siguiente.");
        return new StateMachine(states, transitions, Optional.of("rojo"), List.of(), List.of("R", "A", "V"), false,
                false, StateMachine.EDGE_FALLING, List.of(cycle)).withNotes(List.of(note));
    }

    /**
     * Detector de secuencia: Z lights while the bit on x would complete 1-0-1 (overlapping). A Mealy machine: Z is on
     * the arrow S2 -x-> S1. Switch x, a slow clock and the reset on the left; Z on an LED on the right.
     */
    static CircuitBuilder sequenceDetector() {
        CircuitBuilder builder = new CircuitBuilder("Detector de secuencia");
        String machineId = "detector-101";
        builder.define(MachineDefinitions.create(machineId, "Detector 101").withMachine(detectorMachine()));
        Component machine = builder.placeByBox(SubcircuitTypes.typeIdOf(machineId), MACHINE_LEFT, MACHINE_TOP,
                Rotation.R0, Map.of(), "");
        // pinout: x 0, RST 1, CLK 2, Z 3
        Component data = builder.placeByPin(SWITCH, new GridPoint(18, 6), 0, Rotation.R0, OFF, "x");
        builder.route(new GridPoint(18, 6), new GridPoint(24, 6), new GridPoint(24, 12), new GridPoint(MACHINE_LEFT, 12));
        builder.link(data, 0, machine, 0);
        Component reset = builder.placeByPin(SWITCH, new GridPoint(18, 14), 0, Rotation.R0, OFF, "Reiniciar");
        builder.route(new GridPoint(18, 14), new GridPoint(MACHINE_LEFT, 14));
        builder.link(reset, 0, machine, 1);
        Component clock = builder.placeByPin(CLOCK, new GridPoint(18, 22), 0, Rotation.R0, SLOW_CLOCK, "Reloj");
        builder.route(new GridPoint(18, 22), new GridPoint(26, 22), new GridPoint(26, 16), new GridPoint(MACHINE_LEFT, 16));
        builder.link(clock, 0, machine, 2);

        Component detected = builder.placeByPin(LED, new GridPoint(54, 12), 0, Rotation.R0, Map.of("color", "green"),
                "Z");
        builder.route(new GridPoint(OUTPUT_X, 12), new GridPoint(54, 12));
        builder.link(machine, 3, detected, 0);

        builder.text(2, 0, "Detector de secuencia");
        builder.text(2, 27, "Prueba: pon x en 1, 0, 1 en tres bajadas seguidas del reloj: Z se enciende con el último 1");
        builder.document().add(new StickyNote(builder.document().newStickyNoteId(), new GridPoint(2, 30), 66, 15,
                StickyColor.YELLOW, new StickyText("""
                        Qué probar
                        1. Cambia x entre una bajada del reloj y la siguiente: la máquina mira x justo en la bajada.
                        2. Z se enciende con el 1 que completa 1-0-1, antes de la bajada: es una máquina de Mealy, la salida está en la flecha.
                        3. Prueba 1 0 1 0 1: las secuencias se pueden traslapar y Z se enciende dos veces.
                        4. Doble clic en la máquina para ver su diagrama; con «Probar…» arriba ves sus pruebas, todas ✓."""),
                false));
        return builder;
    }

    private static StateMachine detectorMachine() {
        List<MachineState> states = List.of(
                // S1 and S2 side by side: their two curved arrows run level, so the wide «x / Z=1» sits above or
                // below its curve instead of beside it (where it touched the other curve)
                new MachineState("s0", "S0", new DiagramPoint(130, 0), List.of()),
                new MachineState("s1", "S1", new DiagramPoint(0, 200), List.of()),
                new MachineState("s2", "S2", new DiagramPoint(260, 200), List.of()));
        List<MachineTransition> transitions = List.of(
                new MachineTransition("t1", "s0", "s1", "x", List.of()),
                new MachineTransition("t2", "s0", "s0", "x'", List.of(), Optional.of(LoopSide.TOP)),
                new MachineTransition("t3", "s1", "s1", "x", List.of(), Optional.of(LoopSide.LEFT)),
                new MachineTransition("t4", "s1", "s2", "x'", List.of()),
                new MachineTransition("t5", "s2", "s1", "x", List.of(new OutputAssignment("Z", true))),
                new MachineTransition("t6", "s2", "s0", "x'", List.of()));
        MachineTest overlapping = machineTest("p1", "101 con traslape", new int[]{1, 0, 1, 0, 1, 1, 0, 1},
                new int[]{0, 0, 1, 0, 1, 0, 0, 1});
        MachineTest never = machineTest("p2", "Sin 101", new int[]{1, 1, 0, 0, 1, 0}, new int[6]);
        MachineNote note = new MachineNote("n1", new DiagramPoint(360, 70), 250, 110, StickyColor.YELLOW,
                "S0: nada útil todavía. S1: llegó un 1. S2: llegaron 1 y 0. Z sale en la flecha de S2 a S1 "
                        + "(x / Z=1): el 1 que completa 1-0-1.");
        return new StateMachine(states, transitions, Optional.of("s0"), List.of("x"), List.of("Z"), false, false,
                StateMachine.EDGE_FALLING, List.of(overlapping, never)).withNotes(List.of(note));
    }

    private static MachineTest machineTest(String id, String name, int[] inputs, int[] expectedZ) {
        MachineTest test = MachineTest.empty(id, name, inputs.length);
        for (int column = 0; column < inputs.length; column++) {
            test = MachineTests.withInput(test, column, "x", inputs[column] == 1);
            test = MachineTests.withExpected(test, column, "Z", expectedZ[column] == 1 ? LogicValue.ONE : LogicValue.ZERO);
        }
        return test;
    }
}
