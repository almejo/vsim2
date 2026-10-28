package cl.almejo.vsim.ui.memory;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
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

    private EditorView view() {
        EditorServices services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                AppPreferences.inMemory(), new ComponentLibrary(), List.of(new MemoryEditorExtension(launcher,
                new MemoryLinks(new FakeLinkPrompts(), (file, command) -> true, MemoryLinks.PollScheduler.MANUAL))));
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
}
