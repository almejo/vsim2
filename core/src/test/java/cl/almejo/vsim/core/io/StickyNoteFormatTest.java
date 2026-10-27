package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.command.PasteCommand;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.StickyChecklist;
import cl.almejo.vsim.core.document.StickyColor;
import cl.almejo.vsim.core.document.StickyItem;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.core.document.StickyText;
import cl.almejo.vsim.core.geometry.GridPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class StickyNoteFormatTest {

    private static final Path EXAMPLES = Path.of("..", "examples");
    private static final Path BUNDLED_EXAMPLES = Path.of("src", "main", "resources", "cl", "almejo", "vsim", "core",
            "bundled", "examples");

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitFormat circuitFormat = new CircuitFormat(registry);
    private final FragmentFormat fragmentFormat = new FragmentFormat(registry);

    private final StickyNote text = new StickyNote(new ElementId("s1"), new GridPoint(2, 3), 9, 7,
            StickyColor.PINK, new StickyText("Falta probar\ncon Cin = 1"), false);
    private final StickyNote list = new StickyNote(new ElementId("s2"), new GridPoint(20, 3), 8, 6,
            StickyColor.GREEN, new StickyChecklist(List.of(new StickyItem("reloj", true),
            new StickyItem("display", false))), true);

    @ParameterizedTest
    @EnumSource(StickyColor.class)
    void everyColourRoundTrips(StickyColor color) throws CircuitFormatException {
        CircuitDocument document = new CircuitDocument();
        document.add(text.withColor(color));

        assertThat(circuitFormat.read(circuitFormat.write(document)).stickyNotes())
                .containsExactly(text.withColor(color));
    }

    @Test
    void aTextNoteAndAFoldedChecklistRoundTrip() throws CircuitFormatException {
        CircuitDocument document = new CircuitDocument();
        document.add(text);
        document.add(list);

        String json = circuitFormat.write(document);

        assertThat(json).contains("\"stickyNotes\"").contains("\"kind\" : \"text\"").contains("\"kind\" : \"list\"");
        assertThat(circuitFormat.read(json).stickyNotes()).containsExactly(text, list);
    }

    @Test
    void foldedIsWrittenOnlyWhenTrue() {
        CircuitDocument document = new CircuitDocument();
        document.add(text);
        assertThat(circuitFormat.write(document)).doesNotContain("folded");
        document.add(list);

        assertThat(circuitFormat.write(document)).contains("\"folded\" : true");
    }

    @Test
    void aFileWithoutStickyNotesWritesNoKey() throws CircuitFormatException {
        CircuitDocument document = circuitFormat.read("{\"formatVersion\":3,\"kind\":\"circuit\"}");

        assertThat(document.stickyNotes()).isEmpty();
        assertThat(circuitFormat.write(document)).doesNotContain("stickyNotes");
    }

    @Test
    void anUnknownColourReadsAsYellow() throws CircuitFormatException {
        CircuitDocument document = circuitFormat.read("""
                {"formatVersion":3,"kind":"circuit","stickyNotes":[
                {"id":"s1","x":0,"y":0,"width":8,"height":6,"color":"MAGENTA","kind":"text","text":"x"}]}
                """);

        assertThat(document.stickyNotes().getFirst().color()).isEqualTo(StickyColor.YELLOW);
    }

    @Test
    void aSizeUnderTheMinimumReadsClampedAndAnEmptyChecklistLoads() throws CircuitFormatException {
        CircuitDocument document = circuitFormat.read("""
                {"formatVersion":3,"kind":"circuit","stickyNotes":[
                {"id":"s1","x":3,"y":4,"width":1,"height":1,"color":"BLUE","kind":"list","items":[]},
                {"id":"s2","x":0,"y":0,"width":8,"height":6,"kind":"list"}]}
                """);

        assertThat(document.stickyNotes()).hasSize(2).allSatisfy(note -> {
            assertThat(note.content()).isEqualTo(new StickyChecklist(List.of()));
        });
        assertThat(document.stickyNotes().getFirst().width()).isEqualTo(StickyNote.MINIMUM_WIDTH);
        assertThat(document.stickyNotes().getFirst().height()).isEqualTo(StickyNote.MINIMUM_HEIGHT);
    }

    @Test
    void aFragmentRoundTripsStartsAtTheOriginAndPastesAsNewNotes() throws CircuitFormatException {
        CircuitFragment fragment = new CircuitFragment(List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(text, list));

        CircuitFragment read = fragmentFormat.read(fragmentFormat.write(fragment));

        assertThat(read.stickyNotes()).containsExactly(text.translate(-2, -3), list.translate(-2, -3));
        assertThat(read.isEmpty()).isFalse();
        CircuitDocument target = new CircuitDocument();
        new PasteCommand(read, new GridPoint(5, 5)).apply(target);
        assertThat(target.stickyNotes()).hasSize(2).extracting(StickyNote::position)
                .containsExactly(new GridPoint(5, 5), new GridPoint(23, 5));
    }

    @Test
    void copyingANoteFromADocumentSelectsIt() {
        CircuitDocument document = new CircuitDocument();
        document.add(text);

        CircuitFragment fragment = CircuitFragment.of(document, List.of(text.id()), registry);

        assertThat(fragment.stickyNotes()).containsExactly(text.translate(-2, -3));
    }

    @Test
    void everyCommittedExampleAndBundledCircuitIsWrittenBackByteForByte() throws IOException {
        int checked = 0;
        for (Path directory : List.of(EXAMPLES, BUNDLED_EXAMPLES)) {
            if (!Files.isDirectory(directory)) {
                continue;
            }
            try (Stream<Path> files = Files.list(directory)) {
                for (Path file : files.filter(path -> path.toString().endsWith(".vsim")).sorted().toList()) {
                    assertWrittenBackIdentically(file);
                    checked++;
                }
            }
        }
        assertThat(checked).isPositive();
    }

    private void assertWrittenBackIdentically(Path file) throws IOException {
        String committed = Files.readString(file, StandardCharsets.UTF_8);
        if (!committed.contains("\"formatVersion\" : " + CircuitFormat.FORMAT_VERSION + ",")) {
            // an older format (the user's untracked circuits in examples/, say) is migrated on save by design
            return;
        }
        String written;
        try {
            written = circuitFormat.write(circuitFormat.read(committed));
        } catch (CircuitFormatException exception) {
            // not a circuit file (a library part, say): nothing about sticky notes to prove
            return;
        }
        String expected = committed.endsWith("\n") ? written + "\n" : written;
        assertThat(SavedFiles.withoutSavedWith(expected)).as(file.toString())
                .isEqualTo(SavedFiles.withoutSavedWith(committed));
    }
}
