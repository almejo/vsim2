package cl.almejo.vsim.core.io.logisim;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A local, manual run of the subcircuit drawings against a real Logisim file that must never be in the repository:
 * {@code ./gradlew :core:test --tests '*LogisimAppearanceSampleTest' -Dvsim.logisim.sample=/path/to/file.circ}.
 * Skipped without the property. It prints, per circuit, the box, the figures and texts and what was noted.
 */
class LogisimAppearanceSampleTest {

    private static final String SAMPLE_PROPERTY = "vsim.logisim.sample";

    @Test
    void convertsTheDrawingsOfTheSample() throws Exception {
        String sample = System.getProperty(SAMPLE_PROPERTY, "");
        assumeTrue(!sample.isBlank(), "set -D" + SAMPLE_PROPERTY + " to run against a real file");
        LogisimProject project;
        try (InputStream input = Files.newInputStream(Path.of(sample))) {
            project = new LogisimReader().read(input);
        }

        LogisimCircuitOrder order = LogisimCircuitOrder.of(project);
        System.out.println("== Logisim sample, circuits leaves first: "
                + order.circuits().stream().map(LogisimCircuit::name).collect(Collectors.joining(", ")));
        System.out.println("   order notes: " + order.notes());
        for (LogisimCircuit circuit : order.circuits()) {
            if (circuit.name().equals(project.mainCircuit())) {
                continue;
            }
            ConvertedAppearance converted = LogisimAppearanceConverter.convert(circuit);
            Map<String, Long> shapes = new TreeMap<>(circuit.appearance().shapes().stream()
                    .collect(Collectors.groupingBy(shape -> shape.getClass().getSimpleName(),
                            Collectors.counting())));
            System.out.println("-- «" + circuit.name() + "»: Logisim " + shapes + " -> box "
                    + converted.size().width() + " x " + converted.size().height() + ", "
                    + converted.drawing().paths().size() + " figures, " + converted.drawing().texts().size()
                    + " texts, " + converted.pins().size() + " pins, anchor " + converted.anchor().x() + ","
                    + converted.anchor().y() + " facing " + converted.facing());
            converted.pins().forEach(pin -> System.out.println("     " + pin.spec().name() + " "
                    + pin.spec().direction() + " " + pin.spec().width() + " bits at " + pin.spec().position().x()
                    + "," + pin.spec().position().y()));
            converted.notes().forEach(note -> System.out.println("     note " + note.kind() + " "
                    + note.messageKey() + " " + note.arguments()));
            assertThat(converted.pins()).hasSize(LogisimInterfacePin.of(circuit).size());
        }
        assertThat(order.circuits()).hasSize(project.circuits().size());
        assertThat(order.circuits().getLast().name()).isEqualTo(project.mainCircuit());
    }
}
