package cl.almejo.vsim.core.io.logisim;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.io.CircuitFormatException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * A Logisim file with an odd attribute value (a splitter with no ends, a pin whose {@code output} is {@code yes}, a
 * multiplexer with -5 select bits, an LED coloured {@code red}) still imports: that part is left out with a note and
 * the rest comes in (the importer's promise, {@link LogisimImporter}).
 */
class LogisimOddAttributesTest {

    private static final ComponentTypeRegistry REGISTRY = StandardComponentTypes.registry();
    /** Values no Logisim attribute of these parts may hold everywhere: empty, zero, negative, too big, not numbers. */
    private static final List<String> ODD_VALUES = List.of("", "0", "-1", "-5", "65", "2147483647", "2147483648",
            "abc", "yes", "red");
    /** The library numbers {@code every-part.circ} declares, by description. */
    private static final Map<String, String> LIBRARY_NUMBERS = libraryNumbers();
    private static final Duration QUICKLY = Duration.ofSeconds(5);
    /** Some 4000 one-part imports take a few seconds; a value that makes a loop or an array huge takes forever. */
    private static final Duration EVERY_ODD_VALUE = Duration.ofSeconds(90);

    @Test
    void noPartWithAnOddAttributeValueAbortsTheImport() throws Exception {
        LogisimCircuit everyPart = LogisimTestFiles.read("every-part.circ").circuits().getFirst();
        List<String> failures = assertTimeoutPreemptively(EVERY_ODD_VALUE, () -> oddImportFailures(everyPart));

        assertThat(failures).isEmpty();
    }

    /** Imports each part of {@code everyPart} alone with each odd value in each attribute: what threw. */
    private static List<String> oddImportFailures(LogisimCircuit everyPart) {
        List<String> failures = new ArrayList<>();
        for (LogisimComponent component : everyPart.components()) {
            for (String attribute : component.attributes().asMap().keySet()) {
                for (String value : ODD_VALUES) {
                    String where = component.library() + "/" + component.name() + " " + attribute + "=\"" + value
                            + "\"";
                    try {
                        importOnePart(component.library(), component.name(), attribute, value);
                    } catch (CircuitFormatException | RuntimeException | OutOfMemoryError exception) {
                        failures.add(where + " -> " + exception);
                    }
                }
            }
        }
        return failures;
    }

    @Test
    void aSplitterWithoutEndsIsLeftOutWithANoteAndTheRestImports() throws Exception {
        ImportResult result = importWithAPin(LogisimLibraries.WIRING, "Splitter", "fanout", "0");

        assertThat(result.document().components()).extracting(Component::typeId)
                .containsExactly(StandardComponentTypes.INPUT_PIN);
        assertThat(result.notes()).extracting(ImportNote::kind, ImportNote::messageKey)
                .containsExactly(tuple(ImportNote.Kind.INVALID,
                        LogisimNotes.INVALID_PART));
    }

    @Test
    void aPinWhoseOutputIsNotABooleanIsLeftOutWithANote() throws Exception {
        ImportResult result = importOnePart(LogisimLibraries.WIRING, "Pin", "output", "yes");

        assertThat(result.document().components()).isEmpty();
        assertThat(result.notes()).extracting(ImportNote::messageKey).containsExactly(LogisimNotes.INVALID_PART);
    }

    @Test
    void aBitSelectorTakingGroupsOfNoBitsIsLeftOutWithANote() throws Exception {
        ImportResult result = importOnePart(LogisimLibraries.PLEXERS, "BitSelector", "group", "0");

        assertThat(result.document().components()).isEmpty();
        assertThat(result.notes()).extracting(ImportNote::messageKey).containsExactly(LogisimNotes.INVALID_PART);
    }

    @Test
    void anLedWhoseColourIsNotRrggbbIsLeftOutWithANote() throws Exception {
        ImportResult result = importOnePart(LogisimLibraries.INPUT_OUTPUT, "LED", "color", "red");

        assertThat(result.document().components()).isEmpty();
        assertThat(result.notes()).extracting(ImportNote::messageKey).containsExactly(LogisimNotes.INVALID_PART);
    }

    @Test
    void anLedWithAnRrggbbColourStillImports() throws Exception {
        ImportResult result = importOnePart(LogisimLibraries.INPUT_OUTPUT, "LED", "color", "#00f000");

        assertThat(result.document().components()).hasSize(1);
        assertThat(result.notes()).isEmpty();
    }

    @Test
    void aMultiplexerWithANegativeSelectIsLeftOutWithANoteQuickly() {
        ImportResult result = assertTimeoutPreemptively(QUICKLY,
                () -> importOnePart(LogisimLibraries.PLEXERS, "Multiplexer", "select", "-5"));

        assertThat(result.document().components()).isEmpty();
        assertThat(result.notes()).extracting(ImportNote::messageKey).containsExactly(LogisimNotes.INVALID_PART);
    }

    @Test
    void selectBitsPastLogisimsFiveAreLeftOutWithANoteQuickly() {
        ImportResult result = assertTimeoutPreemptively(QUICKLY,
                () -> importOnePart(LogisimLibraries.PLEXERS, "Decoder", "select", "30"));

        assertThat(result.document().components()).isEmpty();
        assertThat(result.notes()).extracting(ImportNote::messageKey).containsExactly(LogisimNotes.INVALID_PART);
    }

    @Test
    void hugeCountsOfInputsEndsAndBitsAreLeftOutWithANoteQuickly() {
        String huge = String.valueOf(Integer.MAX_VALUE);
        for (String[] part : new String[][]{{LogisimLibraries.GATES, "AND Gate", "inputs"},
                {LogisimLibraries.ARITHMETIC, "BitAdder", "inputs"}, {LogisimLibraries.WIRING, "Splitter", "fanout"},
                {LogisimLibraries.WIRING, "Splitter", "incoming"}, {LogisimLibraries.WIRING, "Pin", "width"}}) {
            ImportResult result = assertTimeoutPreemptively(QUICKLY,
                    () -> importOnePart(part[0], part[1], part[2], huge), part[1] + " " + part[2]);

            assertThat(result.notes()).as(part[1] + " " + part[2]).extracting(ImportNote::messageKey)
                    .containsExactly(LogisimNotes.INVALID_PART);
        }
    }

    @Test
    void theWidestAndLargestValidValuesStillImport() throws Exception {
        assertThat(importOnePart(LogisimLibraries.WIRING, "Pin", "width", "64").document().components()).hasSize(1);
        assertThat(importOnePart(LogisimLibraries.WIRING, "Splitter", "incoming", "32").notes())
                .extracting(ImportNote::messageKey).doesNotContain(LogisimNotes.INVALID_PART);
        assertThat(importOnePart(LogisimLibraries.PLEXERS, "Multiplexer", "select", "3").document().components())
                .hasSize(1);
        assertThat(importOnePart(LogisimLibraries.PLEXERS, "Multiplexer", "select", "5").notes())
                .extracting(ImportNote::messageKey).containsExactly(LogisimNotes.TOO_MANY_SELECT_BITS);
        assertThat(importOnePart(LogisimLibraries.GATES, "AND Gate", "inputs", "32").notes())
                .extracting(ImportNote::messageKey).containsExactly(LogisimNotes.TOO_MANY_INPUTS);
    }

    @Test
    void anOddPinInsideACircuitLeavesThatPinOutAndTheInstanceImports() throws Exception {
        String file = project("""
                <circuit name="main">
                  <comp loc="(300,100)" name="inner"/>
                </circuit>
                <circuit name="inner">
                  <comp lib="0" loc="(100,100)" name="Pin">
                    <a name="output" val="yes"/>
                  </comp>
                  <comp lib="0" loc="(100,200)" name="Pin"/>
                </circuit>
                """);

        ImportResult result = importText(file);

        assertThat(result.document().definitions()).extracting(definition -> definition.pinout().size())
                .containsExactly(1);
        assertThat(result.notes()).extracting(ImportNote::circuit, ImportNote::messageKey)
                .contains(tuple("inner", LogisimNotes.INVALID_PART));
    }

    private static ImportResult importOnePart(String library, String name, String attribute, String value)
            throws CircuitFormatException {
        return importText(project("<circuit name=\"main\">" + part(library, name, attribute, value) + "</circuit>"));
    }

    private static ImportResult importWithAPin(String library, String name, String attribute, String value)
            throws CircuitFormatException {
        return importText(project("<circuit name=\"main\">" + part(library, name, attribute, value)
                + "<comp lib=\"0\" loc=\"(100,400)\" name=\"Pin\"/></circuit>"));
    }

    private static String part(String library, String name, String attribute, String value) {
        return "<comp lib=\"" + LIBRARY_NUMBERS.get(library) + "\" loc=\"(300,200)\" name=\"" + name + "\">"
                + "<a name=\"" + attribute + "\" val=\"" + value + "\"/></comp>";
    }

    private static String project(String circuits) {
        StringBuilder libraries = new StringBuilder();
        LIBRARY_NUMBERS.forEach((library, number) -> libraries.append("<lib desc=\"").append(library)
                .append("\" name=\"").append(number).append("\"/>"));
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><project source=\"2.7.1\" version=\"1.0\">" + libraries
                + "<main name=\"main\"/>" + circuits + "</project>";
    }

    private static ImportResult importText(String file) throws CircuitFormatException {
        return LogisimImporter.importFrom(new ByteArrayInputStream(file.getBytes(StandardCharsets.UTF_8)), REGISTRY);
    }

    private static Map<String, String> libraryNumbers() {
        Map<String, String> numbers = new LinkedHashMap<>();
        List<String> libraries = List.of(LogisimLibraries.WIRING, LogisimLibraries.GATES, LogisimLibraries.PLEXERS,
                LogisimLibraries.ARITHMETIC, LogisimLibraries.MEMORY, LogisimLibraries.INPUT_OUTPUT,
                LogisimLibraries.BASE);
        for (int index = 0; index < libraries.size(); index++) {
            numbers.put(libraries.get(index), String.valueOf(index));
        }
        return numbers;
    }
}
