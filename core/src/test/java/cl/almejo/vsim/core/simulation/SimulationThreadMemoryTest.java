package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.MemoryTypes;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.memory.MemoryContents;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/** The memory requests of the simulation thread: «Restaurar contenido inicial» and reading the live words. */
class SimulationThreadMemoryTest {

    private static final Map<String, Object> RAM = Map.of(MemoryTypes.WORDS, "16", MemoryTypes.CONTENTS, "@1 11");

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final SimulationThread thread = new SimulationThread(new SimulationEngine(), () -> 0L);

    /** A 16 x 8 RAM "ram" with A, D, WE and CLK from input pins of the same names (CS unconnected), Q on net "q". */
    private FlatCircuit circuit() {
        ComponentType ram = registry.require(StandardComponentTypes.RAM).specializedFor(RAM);
        List<FlatComponent> components = new ArrayList<>(List.of(new FlatComponent("ram", ram, RAM)));
        List<FlatNet> nets = new ArrayList<>();
        int pinIndex = 0;
        for (String input : List.of("A", "D", "WE", "CS", "CLK", "Q")) {
            int width = ram.pin(pinIndex).width();
            if (input.equals("Q")) {
                nets.add(new FlatNet("q", List.of(new FlatPinRef("ram", pinIndex)), width, false));
            } else if (!input.equals("CS")) {
                Map<String, Object> parameters = Map.of("value", "0", InterfacePins.WIDTH, width);
                components.add(new FlatComponent(input, registry.require(InterfacePins.INPUT_PIN)
                        .specializedFor(parameters), parameters));
                nets.add(new FlatNet("n-" + input, List.of(new FlatPinRef(input, 0),
                        new FlatPinRef("ram", pinIndex)), width, false));
            }
            pinIndex++;
        }
        return new FlatCircuit(components, nets);
    }

    private void writeAtThree(int value) {
        thread.setInputs(Map.of("A", Value.of(4, 3), "D", Value.of(8, value), "WE", Value.ONE, "CLK", Value.ONE));
        thread.step(10);
        thread.setInputs(Map.of("CLK", Value.ZERO));
        thread.step(10);
        thread.setInputs(Map.of("WE", Value.ZERO, "A", Value.of(4, 1)));
        thread.step(10);
        thread.tick();
    }

    @Test
    void theLiveContentsHoldWhatTheSimulationWrote() {
        thread.update(circuit());
        writeAtThree(0x33);

        CompletableFuture<Optional<MemoryContents>> contents = thread.requestMemoryContents("ram");
        assertThat(contents).as("answered on the simulation thread").isNotDone();
        thread.tick();

        assertThat(contents).isCompletedWithValue(Optional.of(MemoryContents.decode("@1 11 0 33")));
    }

    @Test
    void anIdThatIsNotAMemoryGetsNothing() {
        thread.update(circuit());
        CompletableFuture<Optional<MemoryContents>> notAMemory = thread.requestMemoryContents("A");
        CompletableFuture<Optional<MemoryContents>> unknown = thread.requestMemoryContents("nobody");
        thread.tick();
        assertThat(notAMemory).isCompletedWithValue(Optional.empty());
        assertThat(unknown).isCompletedWithValue(Optional.empty());
    }

    @Test
    void restoringAMemoryPutsBackItsInitialWordsAndKeepsTheTime() {
        thread.update(circuit());
        writeAtThree(0x33);
        long time = thread.latestSnapshot().time();

        thread.restoreMemory("ram");
        CompletableFuture<Optional<MemoryContents>> contents = thread.requestMemoryContents("ram");
        thread.tick();

        assertThat(contents).isCompletedWithValue(Optional.of(MemoryContents.decode("@1 11")));
        assertThat(thread.latestSnapshot().time()).isEqualTo(time);
        assertThat(thread.latestSnapshot().netValue("q")).isEqualTo(Value.of(8, 0x11));
    }

    @Test
    void aClosedThreadAnswersNothing() {
        thread.close();
        assertThat(thread.requestMemoryContents("ram")).isCompletedWithValue(Optional.empty());
    }
}
