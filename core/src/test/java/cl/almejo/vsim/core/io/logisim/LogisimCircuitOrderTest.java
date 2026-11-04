package cl.almejo.vsim.core.io.logisim;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LogisimCircuitOrderTest {

    @Test
    void theCircuitsAComponentUsesComeBeforeIt() throws Exception {
        LogisimProject project = LogisimTestFiles.read("subcircuits.circ");

        LogisimCircuitOrder order = LogisimCircuitOrder.of(project);

        assertThat(order.circuits()).extracting(LogisimCircuit::name).containsExactly("leaf", "middle", "top");
        assertThat(order.notes()).isEmpty();
    }

    @Test
    void circuitsThatUseEachOtherAreLeftOutWithANote() {
        LogisimProject project = new LogisimProject("main", Map.of(), List.of(
                circuit("main", "first", "alone"), circuit("first", "second"), circuit("second", "first"),
                circuit("alone"), circuit("itself", "itself")), List.of());

        LogisimCircuitOrder order = LogisimCircuitOrder.of(project);

        assertThat(order.circuits()).extracting(LogisimCircuit::name).containsExactly("alone", "main");
        assertThat(order.notes()).hasSize(2).allMatch(note -> note.kind() == ImportNote.Kind.INVALID
                && note.messageKey().equals(LogisimNotes.RECURSIVE_CIRCUIT));
        assertThat(order.notes()).extracting(note -> note.arguments().getFirst())
                .containsExactly("first › second › first", "itself › itself");
    }

    @Test
    void anInstanceOfACircuitTheFileDoesNotHaveAddsNoOrder() {
        LogisimProject project = new LogisimProject("main", Map.of(),
                List.of(circuit("main", "ghost", "part"), circuit("part")), List.of());

        assertThat(LogisimCircuitOrder.of(project).circuits()).extracting(LogisimCircuit::name)
                .containsExactly("part", "main");
    }

    private static LogisimCircuit circuit(String name, String... uses) {
        List<LogisimComponent> components = java.util.stream.IntStream.range(0, uses.length)
                .mapToObj(index -> new LogisimComponent(LogisimLibraries.SUBCIRCUIT, uses[index],
                        new LogisimPoint(100, 100 + 100 * index), LogisimAttributes.of(Map.of())))
                .toList();
        return new LogisimCircuit(name, components, List.of(), LogisimAppearance.NONE);
    }
}
