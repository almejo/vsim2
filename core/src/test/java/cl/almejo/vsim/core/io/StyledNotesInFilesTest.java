package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.bundled.BundledExamples;
import cl.almejo.vsim.core.command.PasteCommand;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.NoteColor;
import cl.almejo.vsim.core.document.NoteStyle;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.geometry.GridPoint;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Styled notes (Pulido T5) in circuit files and on the clipboard; a plain note writes no new key. */
class StyledNotesInFilesTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitFormat circuitFormat = new CircuitFormat(registry);
    private final FragmentFormat fragmentFormat = new FragmentFormat(registry);

    private final NoteStyle styled = new NoteStyle(NoteColor.YELLOW, NoteColor.NONE, NoteColor.RED);
    private final TextLabel styledNote = new TextLabel(new ElementId("l1"), new GridPoint(2, 3), "Ojo", styled);

    @Test
    void aPlainNoteWritesNoStyleKey() {
        CircuitDocument document = new CircuitDocument();
        document.add(new TextLabel(new ElementId("l1"), new GridPoint(2, 3), "nota"));

        assertThat(circuitFormat.write(document)).doesNotContain("style");
    }

    @Test
    void aStyledNoteRoundTripsAndWritesOnlyItsColours() throws CircuitFormatException {
        CircuitDocument document = new CircuitDocument();
        document.add(styledNote);

        String json = circuitFormat.write(document);

        assertThat(json).contains("\"background\" : \"YELLOW\"").contains("\"border\" : \"RED\"")
                .doesNotContain("\"text\" : \"NONE\"");
        assertThat(circuitFormat.read(json).labels()).containsExactly(styledNote);
    }

    @Test
    void anUnknownColourNameIsALoadErrorThatNamesIt() {
        String json = """
                {"formatVersion":2,"kind":"circuit","labels":[
                {"id":"l1","x":0,"y":0,"text":"x","style":{"background":"PINK"}}]}
                """;

        assertThatThrownBy(() -> circuitFormat.read(json)).isInstanceOf(CircuitFormatException.class)
                .hasMessageContaining("PINK").hasMessageContaining("background");
    }

    @Test
    void aFragmentKeepsTheStyleAndPastingKeepsIt() throws CircuitFormatException {
        CircuitFragment fragment = new CircuitFragment(List.of(), List.of(), List.of(styledNote));

        CircuitFragment read = fragmentFormat.read(fragmentFormat.write(fragment));
        CircuitDocument target = new CircuitDocument();
        new PasteCommand(read, new GridPoint(10, 10)).apply(target);

        assertThat(read.labels()).singleElement().extracting(TextLabel::style).isEqualTo(styled);
        assertThat(target.labels()).singleElement().extracting(TextLabel::style).isEqualTo(styled);
    }

    static Stream<String> exampleIds() {
        return BundledExamples.ids().stream();
    }

    @ParameterizedTest
    @MethodSource("exampleIds")
    void everyBundledExampleIsWrittenBackWithTheSameBytes(String id) throws Exception {
        String committed = resource(id);

        String rewritten = circuitFormat.write(circuitFormat.read(committed));

        assertThat(withoutSavedWith(rewritten)).isEqualTo(withoutSavedWith(committed));
    }

    private static String resource(String id) throws IOException {
        try (InputStream input = BundledExamples.class.getResourceAsStream(
                "/" + BundledExamples.RESOURCE_DIRECTORY + "/" + id + ".vsim")) {
            assertThat(input).as("resource of %s", id).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String withoutSavedWith(String json) {
        return json.replaceAll("\"savedWith\" : \"[^\"]*\"", "").strip();
    }
}
