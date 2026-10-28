package cl.almejo.vsim.ui.memory;

import cl.almejo.vsim.core.command.SetLabelCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.paint.ColorScheme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/** One name per memory (I2): the canvas, the memory map, the editor's title and Volcar's file name agree. */
class MemoryNamesTest {

    private final OpenDocument document = OpenDocument.createEmpty(StandardComponentTypes.registry());

    @AfterEach
    void close() {
        document.close();
    }

    @Test
    void unnamedMemoriesAreNumberedPerKindInPlacementOrder() {
        Component firstRam = place(document, StandardComponentTypes.RAM, 0, 0, Map.of());
        place(document, StandardComponentTypes.BUFFER, 30, 0, Map.of(StandardComponentTypes.NEGATE, true));
        Component firstRom = place(document, StandardComponentTypes.ROM, 60, 0, Map.of());
        Component secondRam = place(document, StandardComponentTypes.RAM, 90, 0, Map.of());

        assertThat(MemoryNames.displayName(document.document(), firstRam)).isEqualTo("RAM1");
        assertThat(MemoryNames.displayName(document.document(), firstRom)).as("not ROM2 after a RAM")
                .isEqualTo("ROM1");
        assertThat(MemoryNames.displayName(document.document(), secondRam)).isEqualTo("RAM2");
    }

    @Test
    void aLabelIsTheNameAndStillCountsInTheNumbering() {
        Component sprites = place(document, StandardComponentTypes.ROM, 0, 0, Map.of());
        Component unnamed = place(document, StandardComponentTypes.ROM, 40, 0, Map.of());
        document.execute(new SetLabelCommand(sprites.id(), "Sprites"));
        document.execute(new SetLabelCommand(unnamed.id(), "  "));

        assertThat(MemoryNames.displayName(document.document(), current(sprites))).isEqualTo("Sprites");
        assertThat(MemoryNames.displayName(document.document(), current(unnamed))).isEqualTo("ROM2");
    }

    @Test
    void theNumberingWalkedInDocumentOrderGivesTheSameNames() {
        place(document, StandardComponentTypes.ROM, 0, 0, Map.of());
        place(document, StandardComponentTypes.RAM, 40, 0, Map.of());
        place(document, StandardComponentTypes.ROM, 80, 0, Map.of());

        MemoryNames.Numbering numbering = new MemoryNames.Numbering();
        List<String> walked = new ArrayList<>();
        List<String> each = new ArrayList<>();
        for (Component component : document.document().components()) {
            walked.add(numbering.nameOf(component));
            each.add(MemoryNames.displayName(document.document(), component));
        }
        assertThat(walked).containsExactly("ROM1", "RAM1", "ROM2").isEqualTo(each);
    }

    @Test
    void theMapTitleAndTheEditorUseTheSameName() {
        place(document, StandardComponentTypes.RAM, 0, 0, Map.of());
        Component rom = place(document, StandardComponentTypes.ROM, 40, 0, Map.of());
        String name = MemoryNames.displayName(document.document(), rom);

        MemoryMapPanel panel = new MemoryMapPanel(document, new MemoryMapActions() { }, () -> ColorScheme.LIGHT,
                System::nanoTime, () -> true);
        try {
            panel.follow(Set.of(rom.id()));
            assertThat(panel.title()).contains(" · " + name + " (");
        } finally {
            panel.dispose();
        }

        SuggestedNamePrompts prompts = new SuggestedNamePrompts();
        new MemoryEditorDialog(document, rom.id(), null, new NoLinkActions(), prompts).dumpToFile();
        assertThat(prompts.suggested).containsExactly(name);
    }

    private Component current(Component component) {
        return document.document().component(component.id()).orElseThrow();
    }

    /** Records the file name Volcar suggests and cancels. */
    private static final class SuggestedNamePrompts implements MemoryEditorPrompts {
        final List<String> suggested = new ArrayList<>();

        @Override
        public Optional<Path> chooseImport() {
            return Optional.empty();
        }

        @Override
        public Optional<SaveChoice> chooseSave(String suggestedName, String titleKey,
                Function<Path, Optional<String>> refusal) {
            suggested.add(suggestedName);
            return Optional.empty();
        }

        @Override
        public Optional<FillRequest> askFill(String firstAddress, String lastAddress) {
            return Optional.empty();
        }

        @Override
        public Optional<String> askAddress() {
            return Optional.empty();
        }

        @Override
        public boolean confirm(String message) {
            return false;
        }

        @Override
        public void inform(String message) {
        }

        @Override
        public void copyToClipboard(String text) {
        }

        @Override
        public Optional<String> clipboardText() {
            return Optional.empty();
        }
    }

    private static final class NoLinkActions implements MemoryLinkActions {
        @Override
        public void link(cl.almejo.vsim.ui.window.EditorView view, ElementId componentId) {
        }

        @Override
        public void unlink(cl.almejo.vsim.ui.window.EditorView view, ElementId componentId) {
        }

        @Override
        public void openExternal(cl.almejo.vsim.ui.window.EditorView view, ElementId componentId) {
        }
    }
}
