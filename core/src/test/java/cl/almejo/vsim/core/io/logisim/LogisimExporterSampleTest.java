package cl.almejo.vsim.core.io.logisim;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.TreeSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The export's round trip on the real Logisim file of {@link LogisimSampleTest} (manual, skipped without
 * {@code -Dvsim.logisim.sample}): import it, export it (written to {@code -Dvsim.logisim.exportOutput} when given, to
 * open it in Logisim), read the written file back and import it again. Fails when Logisim would join the exported
 * circuits otherwise than VSim does; prints the export notes and every difference in parts and nets between the two
 * imports.
 */
class LogisimExporterSampleTest {

    private static final String OUTPUT_PROPERTY = "vsim.logisim.exportOutput";

    @Test
    void theSampleGoesThroughLogisimAndComesBack() throws Exception {
        LogisimProject project = LogisimSampleTest.readSample();
        ComponentTypeRegistry registry = StandardComponentTypes.registry();
        CircuitDocument imported = LogisimImporter.importProject(project, registry).document();

        List<ExportedCircuit> exported = new ArrayList<>();
        long start = System.nanoTime();
        ExportResult result = LogisimExporter.exportDocument(imported, registry, exported::add);
        String text = LogisimWriter.toText(result.project());
        long milliseconds = (System.nanoTime() - start) / 1_000_000;
        printExport(result, text, milliseconds);
        String output = System.getProperty(OUTPUT_PROPERTY);
        if (output != null && !output.isBlank()) {
            Files.writeString(Path.of(output), text, StandardCharsets.UTF_8);
            System.out.println("   written to " + output);
        }

        List<String> mismatches = new ArrayList<>();
        exported.forEach(circuit -> mismatches.addAll(ExportEquivalence.mismatches(circuit, result.project())));
        System.out.println("== Logisim nets against VSim's: " + mismatches.size() + " mismatches " + mismatches);

        LogisimProject read = new LogisimReader().read(new ByteArrayInputStream(
                text.getBytes(StandardCharsets.UTF_8)));
        System.out.println("   read back: " + read.circuits().size() + " circuits, reader notes " + read.notes());
        CircuitDocument again = LogisimImporter.importProject(read, registry).document();
        printDifferences(RoundTrip.describe(imported, registry), RoundTrip.describe(again, registry));

        assertThat(mismatches).isEmpty();
        assertThat(read.notes()).isEmpty();
    }

    private static void printExport(ExportResult result, String text, long milliseconds) {
        System.out.println("== export of «" + result.project().mainCircuit() + "» in " + milliseconds + " ms: "
                + result.project().circuits().size() + " circuits, " + text.length() / 1024 + " KiB");
        for (LogisimCircuit circuit : result.project().circuits()) {
            System.out.println("   «" + circuit.name() + "»: " + circuit.components().size() + " components, "
                    + circuit.wires().size() + " wires, " + circuit.appearance().shapes().size() + " shapes");
        }
        Map<String, Long> notes = result.notes().stream().collect(Collectors.groupingBy(
                note -> note.kind() + " " + note.messageKey() + " " + note.arguments(), TreeMap::new,
                Collectors.counting()));
        System.out.println("== export notes: " + result.notes().size());
        notes.forEach((note, count) -> System.out.println("   " + note + ": " + count));
    }

    /** Per circuit, the parts and nets only one of the two imports has. */
    private static void printDifferences(Map<String, List<String>> before, Map<String, List<String>> after) {
        Set<String> circuits = new TreeSet<>(before.keySet());
        circuits.addAll(after.keySet());
        int differences = 0;
        for (String circuit : circuits) {
            List<String> onlyBefore = only(before.getOrDefault(circuit, List.of()), after.getOrDefault(circuit,
                    List.of()));
            List<String> onlyAfter = only(after.getOrDefault(circuit, List.of()), before.getOrDefault(circuit,
                    List.of()));
            differences += onlyBefore.size() + onlyAfter.size();
            if (!onlyBefore.isEmpty() || !onlyAfter.isEmpty()) {
                System.out.println("-- «" + circuit + "»: only in the first import " + onlyBefore);
                System.out.println("   only after the trip through Logisim " + onlyAfter);
            }
        }
        System.out.println("== round trip: " + before.size() + " circuits before, " + after.size() + " after, "
                + differences + " differing lines");
    }

    /** The lines of {@code first} that {@code second} lacks, counting repeats. */
    private static List<String> only(List<String> first, List<String> second) {
        List<String> remaining = new ArrayList<>(second);
        List<String> only = new ArrayList<>();
        for (String line : first) {
            if (!remaining.remove(line)) {
                only.add(line);
            }
        }
        return only;
    }
}
