package cl.almejo.vsim.ui.memory;

import cl.almejo.vsim.core.component.standard.MemoryTypes;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.memory.MemoryContents;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/** Linked memory files: the stored path, linking and unlinking, saving elsewhere and the external editor. */
class MemoryLinksTest {

    @TempDir
    Path folder;

    private final List<OpenDocument> documents = new ArrayList<>();
    private final FakeLinkPrompts prompts = new FakeLinkPrompts();
    private final List<String> opened = new ArrayList<>();
    private boolean editorOpens = true;
    private final MemoryLinks links = new MemoryLinks(prompts, (file, command) -> {
        opened.add(file + " with " + command.orElse("the system's editor"));
        return editorOpens;
    }, MemoryLinks.PollScheduler.MANUAL);

    @AfterEach
    void close() {
        documents.forEach(OpenDocument::close);
    }

    private OpenDocument track(OpenDocument document) {
        documents.add(document);
        return document;
    }

    private OpenDocument newDocument() {
        return track(OpenDocument.createEmpty(StandardComponentTypes.registry()));
    }

    private EditorView view(OpenDocument document, AppPreferences preferences) {
        EditorServices services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                preferences, new ComponentLibrary(), List.of(new MemoryEditorExtension(
                new MemoryEditorLauncher(links), links)));
        return new EditorView(document, services, NO_WINDOW);
    }

    private static String parameter(OpenDocument document, Component component, String name) {
        return (String) document.document().component(component.id()).orElseThrow().parameters().get(name);
    }

    private Path program(String relative, String text) throws IOException {
        Path file = folder.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
        return file;
    }

    @Test
    void theStoredPathIsRelativeToTheCircuitsFolderWithSlashes() {
        Path circuit = folder.resolve("curso/cpu.vsim");
        assertThat(MemoryLinks.storedLinkPath(folder.resolve("curso/programas/rom.hex"), Optional.of(circuit)))
                .isEqualTo("programas/rom.hex");
        assertThat(MemoryLinks.storedLinkPath(folder.resolve("otros/rom.hex"), Optional.of(circuit)))
                .isEqualTo("../otros/rom.hex");
    }

    @Test
    void anUnsavedCircuitStoresTheAbsolutePath() {
        Path file = folder.resolve("rom.hex");
        assertThat(MemoryLinks.storedLinkPath(file, Optional.empty()))
                .isEqualTo(file.toAbsolutePath().normalize().toString().replace('\\', '/'));
    }

    @Test
    void aStoredPathResolvesAgainstTheCircuitsFolderOrStaysAbsolute() {
        Path circuit = folder.resolve("curso/cpu.vsim");
        assertThat(MemoryLinks.resolve("programas/rom.hex", Optional.of(circuit)))
                .isEqualTo(folder.resolve("curso/programas/rom.hex").toAbsolutePath().normalize());
        String absolute = folder.resolve("rom.hex").toAbsolutePath().toString();
        assertThat(MemoryLinks.resolve(absolute, Optional.of(circuit))).isEqualTo(Path.of(absolute));
    }

    @Test
    void linkingCopiesTheFileAndIsOneUndoStep() throws IOException {
        OpenDocument document = newDocument();
        Component rom = place(document, StandardComponentTypes.ROM, 0, 0, Map.of(MemoryTypes.CONTENTS, "@0 7"));
        Path file = program("rom.hex", "3A\nC6\n");
        prompts.linkFile = Optional.of(file);

        links.link(view(document, AppPreferences.inMemory()), rom.id());

        assertThat(MemoryContents.decode(parameter(document, rom, MemoryTypes.CONTENTS)))
                .isEqualTo(MemoryContents.empty().with(0, 0x3A).with(1, 0xC6));
        assertThat(MemoryLinks.resolve(parameter(document, rom, MemoryTypes.LINK), document.path())).isEqualTo(file);
        document.history().undo();
        assertThat(parameter(document, rom, MemoryTypes.CONTENTS)).isEqualTo("@0 7");
        assertThat(parameter(document, rom, MemoryTypes.LINK)).isEmpty();
    }

    @Test
    void linkingAnUnreadableFileExplainsAndChangesNothing() throws IOException {
        OpenDocument document = newDocument();
        Component rom = place(document, StandardComponentTypes.ROM, 0, 0, Map.of());
        prompts.linkFile = Optional.of(program("rom.hex", "3A\nC6G1\n"));
        long changes = document.changeCount();

        links.link(view(document, AppPreferences.inMemory()), rom.id());

        assertThat(prompts.informed).singleElement().asString().contains("rom.hex, línea 2");
        assertThat(document.changeCount()).isEqualTo(changes);
    }

    @Test
    void unlinkingKeepsTheContentsAndIsOneUndoStep() throws IOException {
        OpenDocument document = newDocument();
        Path file = program("rom.hex", "05\n");
        Component rom = place(document, StandardComponentTypes.ROM, 0, 0,
                Map.of(MemoryTypes.CONTENTS, "@0 5", MemoryTypes.LINK, file.toString()));

        links.unlink(view(document, AppPreferences.inMemory()), rom.id());

        assertThat(parameter(document, rom, MemoryTypes.LINK)).isEmpty();
        assertThat(parameter(document, rom, MemoryTypes.CONTENTS)).isEqualTo("@0 5");
        document.history().undo();
        assertThat(parameter(document, rom, MemoryTypes.LINK)).isEqualTo(file.toString());
    }

    @Test
    void anUnsavedLinkBecomesRelativeOnSaveAndFollowsTheFileToAnotherFolder() throws Exception {
        OpenDocument document = newDocument();
        Path file = program("curso/programas/rom.hex", "3A\n");
        Component rom = place(document, StandardComponentTypes.ROM, 0, 0, Map.of());
        prompts.linkFile = Optional.of(file);
        links.link(view(document, AppPreferences.inMemory()), rom.id());

        Path first = folder.resolve("curso/cpu.vsim");
        document.save(first);
        assertThat(Files.readString(first)).contains("\"link\" : \"programas/rom.hex\"");

        Path second = folder.resolve("entregas/alumno/cpu.vsim");
        Files.createDirectories(second.getParent());
        document.save(second);
        assertThat(Files.readString(second)).contains("\"link\" : \"../../curso/programas/rom.hex\"");
        assertThat(MemoryLinks.resolve(parameter(document, rom, MemoryTypes.LINK), document.path())).isEqualTo(file);

        OpenDocument reopened = track(OpenDocument.open(second, StandardComponentTypes.registry()));
        Component again = reopened.document().components().get(0);
        assertThat(MemoryLinks.resolve(parameter(reopened, again, MemoryTypes.LINK), reopened.path()))
                .isEqualTo(file);
        assertThat(reopened.isModified()).isFalse();
    }

    @Test
    void reopeningAndSavingWithoutChangesKeepsTheFileByteForByte() throws Exception {
        OpenDocument document = newDocument();
        Path file = program("curso/programas/rom.hex", "3A\n");
        place(document, StandardComponentTypes.ROM, 0, 0, Map.of(MemoryTypes.CONTENTS, "@0 3A",
                MemoryTypes.LINK, file.toString()));
        Path circuit = folder.resolve("curso/cpu.vsim");
        document.save(circuit);
        track(OpenDocument.open(circuit, StandardComponentTypes.registry())).save(circuit); // named after its file
        String saved = Files.readString(circuit);

        track(OpenDocument.open(circuit, StandardComponentTypes.registry())).save(circuit);

        assertThat(Files.readString(circuit)).isEqualTo(saved).contains("\"link\" : \"programas/rom.hex\"");
    }

    @Test
    void theExternalEditorIsThePreferencesCommandOrTheSystems() throws IOException {
        OpenDocument document = newDocument();
        Path file = program("rom.hex", "05\n");
        Component rom = place(document, StandardComponentTypes.ROM, 0, 0,
                Map.of(MemoryTypes.CONTENTS, "@0 5", MemoryTypes.LINK, file.toString()));
        AppPreferences preferences = AppPreferences.inMemory();
        EditorView view = view(document, preferences);

        links.openExternal(view, rom.id());
        preferences.setExternalEditor(Optional.of("gedit"));
        links.openExternal(view, rom.id());

        assertThat(opened).containsExactly(file + " with the system's editor", file + " with gedit");
    }

    @Test
    void anEditorThatCannotOpenOrAMissingFileIsExplained() throws IOException {
        OpenDocument document = newDocument();
        Path file = program("rom.hex", "05\n");
        Component rom = place(document, StandardComponentTypes.ROM, 0, 0, Map.of(MemoryTypes.LINK, file.toString()));
        EditorView view = view(document, AppPreferences.inMemory());
        editorOpens = false;
        links.openExternal(view, rom.id());
        Files.delete(file);
        links.openExternal(view, rom.id());

        assertThat(prompts.informed).hasSize(2);
        assertThat(prompts.informed.get(1)).contains("No se encuentra");
        assertThat(opened).hasSize(1);
    }

    @Test
    void theSystemsEditorIsGuardedWhenThereIsNoDesktop() throws IOException {
        Path file = program("rom.hex", "05\n");
        assertThat(ExternalEditor.system().open(file, Optional.empty())).isFalse();
    }

    @Test
    void aCommandIsSplitIntoWordsKeepingQuotedPartsTogether() {
        assertThat(ExternalEditor.SystemExternalEditor.words("\"C:\\Program Files\\Notepad++\\notepad++.exe\" -multiInst"))
                .containsExactly("C:\\Program Files\\Notepad++\\notepad++.exe", "-multiInst");
    }
}
