package cl.almejo.vsim.core.io.logisim;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.memory.MemoryContents;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A local, manual run against a real Logisim file that must never be in the repository (the LRV32IM is its author's
 * work): {@code ./gradlew :core:test --tests '*LogisimSampleTest' -Dvsim.logisim.sample=/path/to/lrv32im-up.circ}.
 * Skipped without the property. It prints what it read; the counts it checks are the gap analysis's (§1).
 */
class LogisimSampleTest {

    private static final String SAMPLE_PROPERTY = "vsim.logisim.sample";

    @Test
    void readsTheSample() throws Exception {
        LogisimProject project = readSample();

        Map<String, Long> counts = countsPerType(project);
        System.out.println("== Logisim sample: " + project.circuits().size() + " circuits, main «"
                + project.mainCircuit() + "»");
        counts.forEach((type, count) -> System.out.println("   " + type + ": " + count));
        System.out.println("   reader notes: " + project.notes());
        printSplitterEnds(project);

        assertThat(project.circuits()).hasSize(11);
        assertThat(counts).containsEntry("#Wiring/Pin", 71L).containsEntry("#Wiring/Tunnel", 90L)
                .containsEntry("#Wiring/Splitter", 38L).containsEntry("#Wiring/Constant", 35L)
                .containsEntry("#Wiring/Probe", 30L).containsEntry("#Base/Text", 197L)
                .containsEntry("#Plexers/Multiplexer", 35L).containsEntry("#Gates/AND Gate", 24L)
                .containsEntry("#Arithmetic/Comparator", 17L).containsEntry("#Memory/Register", 10L)
                .containsEntry("#Memory/RAM", 4L).containsEntry("#Memory/ROM", 2L);
        assertThat(project.notes()).isEmpty();
    }

    @Test
    void mapsEveryComponentOfTheSample() throws Exception {
        LogisimProject project = readSample();
        LogisimPartMapper mapper = new LogisimPartMapper();
        ComponentTypeRegistry registry = StandardComponentTypes.registry();
        Map<String, Long> results = new TreeMap<>();
        List<ImportNote> notes = new ArrayList<>();
        for (LogisimCircuit circuit : project.circuits()) {
            MappingContext context = MappingContext.of(circuit.name(), registry);
            for (LogisimComponent component : circuit.components()) {
                LogisimMapping mapping = mapper.map(component, context);
                String result = switch (mapping) {
                    case MappedPart part -> part.typeId();
                    case MappedText text -> text.probe() ? "(probe text)" : "(text)";
                    case MappedSubcircuit subcircuit -> "(subcircuit)";
                    case NotMapped notMapped -> "(not mapped)";
                };
                results.merge(component.name() + " -> " + result, 1L, Long::sum);
                if (mapping instanceof MappedPart part && part.typeId().equals(StandardComponentTypes.ROM)) {
                    MemoryContents contents = MemoryContents.decode((String) part.parameters().get("contents"));
                    System.out.println("   ROM in «" + circuit.name() + "»: " + part.parameters().get("words")
                            + " words, " + contents.count() + " not zero, highest address "
                            + contents.highestAddress());
                }
                notes.addAll(mapping.notes());
            }
        }
        Map<String, Long> notesByKind = notes.stream().collect(Collectors.groupingBy(
                note -> note.kind() + " " + note.messageKey(), TreeMap::new, Collectors.counting()));
        System.out.println("== Logisim sample mapped:");
        results.forEach((result, count) -> System.out.println("   " + result + ": " + count));
        System.out.println("== notes by kind:");
        notesByKind.forEach((kind, count) -> System.out.println("   " + kind + ": " + count));

        assertThat(notes).noneMatch(note -> note.kind() != ImportNote.Kind.APPROXIMATED);
        assertThat(results.keySet()).noneMatch(result -> result.endsWith("(not mapped)"));
    }

    /** Each splitter's bits per end, to compare with Logisim's own load of the file. */
    private static void printSplitterEnds(LogisimProject project) {
        for (LogisimCircuit circuit : project.circuits()) {
            for (LogisimComponent component : circuit.components()) {
                if (component.name().equals("Splitter")) {
                    LogisimAttributes attributes = component.attributes();
                    int[] widths = new int[attributes.integer("fanout")];
                    for (int bit = 0; bit < attributes.integer("incoming"); bit++) {
                        String end = attributes.text("bit" + bit);
                        if (!end.equals("none")) {
                            widths[Integer.parseInt(end)]++;
                        }
                    }
                    System.out.println("   SPLITTER\t" + circuit.name() + "\t" + component.location().x() + ","
                            + component.location().y() + "\t" + Arrays.toString(widths));
                }
            }
        }
    }

    private static Map<String, Long> countsPerType(LogisimProject project) {
        return project.circuits().stream().flatMap(circuit -> circuit.components().stream())
                .collect(Collectors.groupingBy(component -> component.isSubcircuit() ? "sub/" + component.name()
                        : component.library() + "/" + component.name(), TreeMap::new, Collectors.counting()));
    }

    static LogisimProject readSample() throws Exception {
        String sample = System.getProperty(SAMPLE_PROPERTY);
        assumeTrue(sample != null && !sample.isBlank(), "no -D" + SAMPLE_PROPERTY);
        Path path = Path.of(sample);
        assumeTrue(Files.isRegularFile(path), "no file at " + path);
        try (InputStream input = Files.newInputStream(path)) {
            return new LogisimReader().read(input);
        } catch (IOException exception) {
            throw new AssertionError("cannot read " + path, exception);
        }
    }
}
