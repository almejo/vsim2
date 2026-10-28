package cl.almejo.vsim.core.bundled;

import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.io.CircuitFormat;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GateShowcaseTest {

    /**
     * Writes the showcase circuit to the file named by the environment variable, from the repository root:
     * {@code VSIM_WRITE_SHOWCASE=/absolute/path/file.vsim ./gradlew :core:test --tests '*GateShowcaseTest.writeTheShowcase'}.
     */
    @Test
    @EnabledIfEnvironmentVariable(named = GateShowcase.TARGET_VARIABLE, matches = ".+")
    void writeTheShowcase() throws IOException {
        GateShowcase.main(new String[0]);
    }

    @Test
    void theShowcaseIsWiredAndLaidOutWithNoProblems() {
        GateShowcase showcase = GateShowcase.create();

        assertThat(ExampleChecks.wiringProblems(showcase.builder())).isEmpty();
        CircuitDocument document = showcase.builder().document();
        assertThat(document.frames()).hasSize(6).allSatisfy(frame -> assertThat(frame.title()).isNotBlank());
        for (Component component : document.components()) {
            var bounds = showcase.builder().registry().typeOf(component).bounds(component);
            assertThat(document.frames()).as("a frame around %s", component.id().value())
                    .anyMatch(frame -> bounds.x() >= frame.topLeft().x()
                            && bounds.x() + bounds.width() <= frame.topLeft().x() + frame.width()
                            && bounds.y() >= frame.topLeft().y()
                            && bounds.y() + bounds.height() <= frame.topLeft().y() + frame.height());
        }
    }

    @Test
    void theShowcaseReadsBackFromItsFileAsItWasWritten() throws Exception {
        GateShowcase showcase = GateShowcase.create();
        CircuitFormat format = new CircuitFormat(showcase.builder().registry());

        CircuitDocument read = format.read(showcase.text());

        assertThat(format.write(read) + "\n").isEqualTo(showcase.text());
    }

    @Test
    void theShowcaseCompilesSimulatesAndTheLedsShowTheExpectedValues() {
        GateShowcase showcase = GateShowcase.create();
        ExampleSimulation simulation = new ExampleSimulation(showcase.builder().document(), Map.of());
        simulation.settle();
        SimulationSnapshot snapshot = simulation.engine().snapshot();

        assertThat(snapshot.failure()).isEmpty();
        assertThat(snapshot.problems()).isEmpty();
        // 1 bit: the switches are on, off, on, on, off, on, on, off (A..H).
        assertThat(bits(simulation, showcase, "1bit:and:2")).containsExactly(0);
        assertThat(bits(simulation, showcase, "1bit:or:3")).containsExactly(1);
        assertThat(bits(simulation, showcase, "1bit:xor:3")).containsExactly(0);
        assertThat(bits(simulation, showcase, "1bit:nand:5")).containsExactly(1);
        assertThat(bits(simulation, showcase, "1bit:nor:6")).containsExactly(0);
        assertThat(bits(simulation, showcase, "1bit:xnor:8")).containsExactly(0);
        assertThat(bits(simulation, showcase, "1bit:buffer")).containsExactly(1);
        assertThat(bits(simulation, showcase, "1bit:not")).containsExactly(0);
        // Buses, LEDs from bit 0 up: 1100 & 1010 & 1110 = 1000; 11001100 ^ 10101010 ^ 11110000 = 10010110, negated.
        assertThat(bits(simulation, showcase, "bus:and:4:3")).containsExactly(0, 0, 0, 1);
        assertThat(bits(simulation, showcase, "bus:or:4:2")).containsExactly(0, 1, 1, 1);
        assertThat(bits(simulation, showcase, "bus:xnor:8:3")).containsExactly(1, 0, 0, 1, 0, 1, 1, 0);
        // The switches joined by bus joiners: 1100 AND 1010.
        assertThat(bits(simulation, showcase, "joiners:and")).containsExactly(0, 0, 0, 1);
        // The 8-input NAND: off for all ones, on when one bit is 0.
        assertThat(bits(simulation, showcase, "mixed:0")).containsExactly(0);
        assertThat(bits(simulation, showcase, "mixed:1")).containsExactly(1);
    }

    private static List<Integer> bits(ExampleSimulation simulation, GateShowcase showcase, String key) {
        List<Component> leds = showcase.leds().get(key);
        assertThat(leds).as("leds of " + key).isNotNull().isNotEmpty();
        return leds.stream().map(led -> {
            VisibleState state = simulation.engine().snapshot().componentState(led.id().value());
            var value = ((VisibleState.LedState) state).value();
            assertThat(value.isFullyDefined()).as(key + " is defined").isTrue();
            return (int) value.bits();
        }).toList();
    }
}
