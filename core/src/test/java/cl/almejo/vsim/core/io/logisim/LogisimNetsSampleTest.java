package cl.almejo.vsim.core.io.logisim;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The nets step on the real Logisim file of {@link LogisimSampleTest} (manual, skipped without
 * {@code -Dvsim.logisim.sample}). With {@code -Dvsim.logisim.sampleDump=…}, Logisim's own dump of that file (every
 * end's place, written by Logisim 2.7.1's classes), it also checks {@link LogisimEnds} against every component.
 */
class LogisimNetsSampleTest {

    private static final String DUMP_PROPERTY = "vsim.logisim.sampleDump";

    @Test
    void everyEndOfTheSampleIsWhereLogisimPutsIt() throws Exception {
        LogisimProject project = LogisimSampleTest.readSample();
        String dump = System.getProperty(DUMP_PROPERTY);
        assumeTrue(dump != null && !dump.isBlank(), "no -D" + DUMP_PROPERTY);
        Map<String, List<String>> expected = dumpedEnds(Path.of(dump));

        int compared = 0;
        List<String> mismatches = new ArrayList<>();
        for (LogisimCircuit circuit : project.circuits()) {
            for (LogisimComponent component : circuit.components()) {
                List<String> wanted = expected.getOrDefault(key(circuit, component), List.of());
                String actual = describe(component, LogisimEnds.of(component, project));
                compared++;
                if (!wanted.contains(actual)) {
                    mismatches.add(key(circuit, component) + "\n   Logisim: " + wanted + "\n   ours:    " + actual);
                }
            }
        }
        System.out.println("== ends checked against Logisim's dump: " + compared + " components, "
                + mismatches.size() + " differ");
        mismatches.forEach(System.out::println);
        assertThat(mismatches).isEmpty();
    }

    @Test
    void splittersAndTunnelsOfTheSample() throws Exception {
        LogisimProject project = LogisimSampleTest.readSample();
        Map<String, Long> directions = new TreeMap<>();
        Map<String, Long> tunnels = new TreeMap<>();
        List<ImportNote> notes = new ArrayList<>();
        System.out.println("== splitters (circuit, location, facing, direction)");
        for (LogisimCircuit circuit : project.circuits()) {
            LogisimNetAnalysis analysis = LogisimNets.analyze(circuit, project);
            for (LogisimComponent component : circuit.components()) {
                if (component.name().equals("Splitter")) {
                    SplitterDirection direction = analysis.splitterDirection(component);
                    directions.merge(direction.name(), 1L, Long::sum);
                    System.out.println("   SPLITTER\t" + circuit.name() + "\t" + component.location().x() + ","
                            + component.location().y() + "\t" + component.attributes().text("facing") + "\t"
                            + direction + (analysis.isJunction(component) ? " (junction)" : ""));
                } else if (component.name().equals("Tunnel")) {
                    tunnels.merge(circuit.name(), 1L, Long::sum);
                }
            }
            notes.addAll(analysis.notes());
        }
        System.out.println("== splitter directions: " + directions);
        System.out.println("== tunnels by circuit (all without direction):");
        tunnels.forEach((circuitName, count) -> System.out.println("   " + circuitName + ": " + count));
        System.out.println("== nets notes:");
        notes.forEach(note -> System.out.println("   " + note.kind() + " " + note.circuit() + " "
                + note.where().map(point -> "(" + point.x() + "," + point.y() + ") ").orElse("")
                + note.messageKey() + " " + note.arguments()));

        assertThat(directions).containsEntry("SPLIT", 26L).containsEntry("JOIN", 12L);
        assertThat(notes).filteredOn(note -> note.messageKey().equals(LogisimNotes.SPLITTER_BOTH_SIDES)).hasSize(1);
    }

    private static String key(LogisimCircuit circuit, LogisimComponent component) {
        return circuit.name() + "\t" + component.name() + "\t" + component.location().x() + ","
                + component.location().y();
    }

    /** Our ends as the dump writes them, with Logisim's flags where VSim reads a direction otherwise. */
    private static String describe(LogisimComponent component, List<LogisimEnd> ends) {
        List<String> each = new ArrayList<>();
        for (int index = 0; index < ends.size(); index++) {
            LogisimEnd end = ends.get(index);
            String flag = switch (end.role()) {
                case INPUT -> "I";
                case OUTPUT -> "O";
                case BIDIRECTIONAL, ALIAS -> "IO";
            };
            boolean memoryData = (component.name().equals("RAM") || component.name().equals("ROM")) && index == 0;
            boolean carryOut = component.name().equals("Adder") && index == 4;
            if (memoryData) {
                flag = "IO";
            } else if (carryOut) {
                flag = "I";
            }
            each.add(end.point().x() + "," + end.point().y() + ":" + flag + ":" + end.width());
        }
        return String.join(" ", each);
    }

    /** The dump's ends of each component, by circuit, name and location (texts may share a place). */
    private static Map<String, List<String>> dumpedEnds(Path dump) throws Exception {
        Map<String, List<String>> ends = new HashMap<>();
        String circuit = "";
        for (String line : Files.readAllLines(dump, StandardCharsets.UTF_8)) {
            String[] columns = line.split("\t", -1);
            if (columns[0].equals("CIRCUIT")) {
                circuit = columns[1];
            } else if (columns[0].equals("COMP")) {
                String name = columns[1];
                String described = name.equals("Probe") ? ""
                        : List.of(columns).subList(4, columns.length).stream().collect(Collectors.joining(" "));
                String component = circuit + "\t" + name + "\t" + columns[2];
                ends.computeIfAbsent(component, key -> new ArrayList<>()).add(described);
            }
        }
        return ends;
    }
}
