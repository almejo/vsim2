package cl.almejo.vsim.ui.memory;

import cl.almejo.vsim.core.component.standard.MemoryTypes;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.simulation.LinkedFileProblem;
import cl.almejo.vsim.core.simulation.Problem;
import cl.almejo.vsim.ui.problems.ProblemText;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.library.ComponentLibrary;
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
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/** The extension that opens the content editor from a double click and from the part's context menu. */
class MemoryEditorExtensionTest {

    private final OpenDocument document = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private final List<ElementId> opened = new ArrayList<>();
    private final MemoryEditorLauncher launcher = new MemoryEditorLauncher(NoLinkActions.INSTANCE) {
        @Override
        public void open(EditorView view, ElementId componentId, OptionalLong address) {
            opened.add(componentId);
        }
    };

    @AfterEach
    void close() {
        document.close();
    }

    private final MemoryLinks links =
            new MemoryLinks(new FakeLinkPrompts(), (file, command) -> true, MemoryLinks.PollScheduler.MANUAL);

    private EditorView view() {
        EditorServices services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                AppPreferences.inMemory(), new ComponentLibrary(), List.of(new MemoryEditorExtension(launcher, links)));
        return new EditorView(document, services, NO_WINDOW);
    }

    private static List<String> texts(JPopupMenu menu) {
        List<String> texts = new ArrayList<>();
        for (java.awt.Component child : menu.getComponents()) {
            if (child instanceof JMenuItem item) {
                texts.add(item.getText());
            }
        }
        return texts;
    }

    @Test
    void theViewsMemoryEditorOpensTheLauncherForAMemory() {
        Component ram = place(document, StandardComponentTypes.RAM, 0, 0, Map.of());
        view().editMemory(ram);
        assertThat(opened).containsExactly(ram.id());
    }

    @Test
    void aRamsContextMenuHasEditRestoreAndDumpAndARomsOnlyEdit() {
        Component ram = place(document, StandardComponentTypes.RAM, 0, 0, Map.of());
        Component rom = place(document, StandardComponentTypes.ROM, 30, 0, Map.of());
        EditorView view = view();
        view.canvas().selection().select(ram.id());
        List<String> ramItems = texts(view.contextMenu(Optional.of(ram), new Point2D.Double(1, 1)));
        assertThat(ramItems).contains("Editar contenido…", "Restaurar contenido inicial", "Volcar…");
        view.canvas().selection().select(rom.id());
        List<String> romItems = texts(view.contextMenu(Optional.of(rom), new Point2D.Double(31, 1)));
        assertThat(romItems).contains("Editar contenido…").doesNotContain("Restaurar contenido inicial", "Volcar…");
    }

    @Test
    void otherPartsGetNoMemoryItems() {
        Component gate = place(document, StandardComponentTypes.BUFFER, 0, 0, Map.of(StandardComponentTypes.NEGATE, true));
        EditorView view = view();
        view.canvas().selection().select(gate.id());
        assertThat(texts(view.contextMenu(Optional.of(gate), new Point2D.Double(1, 1))))
                .doesNotContain("Editar contenido…");
    }

    private List<Problem> memoryProblems(EditorView view) {
        return view.problems().navigator().problems().stream().filter(LinkedFileProblem.class::isInstance).toList();
    }

    @Test
    void aMissingLinkedFileIsAProblemOfThePartThatGoesWhenTheFileComesBackOrTheLinkGoes(@TempDir Path folder)
            throws IOException {
        Path file = folder.resolve("rom.hex");
        Component rom = place(document, StandardComponentTypes.ROM, 0, 0,
                Map.of(MemoryTypes.LINK, file.toString()));
        place(document, StandardComponentTypes.RAM, 30, 0, Map.of());
        EditorView view = view();
        LinkedMemoryWatcher watcher = links.watcherOf(document).orElseThrow();

        watcher.poll();

        assertThat(memoryProblems(view)).singleElement().satisfies(problem -> {
            assertThat(((LinkedFileProblem) problem).elementId()).isEqualTo(rom.id().value());
            assertThat(ProblemText.describe(problem, document.document(), document.registry()))
                    .contains(file.toString(), "no se encuentra");
        });
        assertThat(view.problems().nextProblem().isEnabled()).isTrue();
        assertThat(view.problems().navigator().navigateTo(memoryProblems(view).getFirst())).isTrue();
        assertThat(view.canvas().selection().ids()).containsExactly(rom.id());

        Files.writeString(file, "01\n");
        watcher.poll();
        assertThat(memoryProblems(view)).isEmpty();

        Files.delete(file);
        watcher.poll();
        assertThat(memoryProblems(view)).hasSize(1);
        document.execute(MemoryLinks.changing(document, rom, Map.of(MemoryTypes.LINK, ""), "command.unlinkMemory"));
        assertThat(memoryProblems(view)).isEmpty();
    }
}
