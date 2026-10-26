package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.core.command.SetLabelCommand;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.library.LibraryService;
import cl.almejo.vsim.ui.library.SwingLibraryDialogs;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.shortcuts.KeyBindings;
import cl.almejo.vsim.ui.shortcuts.ShortcutCatalog;
import cl.almejo.vsim.ui.waveform.ProbeActions;
import cl.almejo.vsim.ui.waveform.WaveformPanel;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static cl.almejo.vsim.ui.UiTestSupport.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * M6.1-E with every production extension installed: live channel names, markers and the status-bar zoom; pin
 * tooltips, the 10-unit step and toolbar tooltips with shortcuts (§6b.4).
 */
class PolishEndToEndTest {

    @TempDir
    Path directory;

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final AppPreferences preferences = AppPreferences.inMemory();
    private final List<OpenDocument> opened = new ArrayList<>();
    private LibraryService service;
    private EditorServices services;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        preferences.setLibraryDirectory(Optional.of(directory.resolve("library")));
        ComponentLibrary library = new ComponentLibrary();
        service = new LibraryService(preferences, library, registry, Runnable::run);
        services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT), preferences,
                library, Application.editorExtensions(service, SwingLibraryDialogs.INSTANCE));
    }

    @AfterEach
    void close() {
        opened.forEach(OpenDocument::close);
        service.close();
    }

    private OpenDocument track(OpenDocument document) {
        opened.add(document);
        return document;
    }

    private static MouseEvent mouse(EditorView view, int id, int x, int y, int modifiers, int button) {
        return new MouseEvent(view.canvas(), id, 0, modifiers, x, y, 1, false, button);
    }

    private static void click(java.awt.Component target, MouseListener listener, int x, int y) {
        listener.mouseClicked(new MouseEvent(target, MouseEvent.MOUSE_CLICKED, 0, 0, x, y, 1, false,
                MouseEvent.BUTTON1));
    }

    @Test
    void liveNamesSurviveSaveAndReopenAndMarkersAndTheZoomLabelWork() throws Exception {
        OpenDocument original = track(OpenDocument.createEmpty(registry));
        Component toggle = place(original, StandardComponentTypes.SWITCH, 0, 10, Map.of()); // out (4,11)
        Component gate = place(original, StandardComponentTypes.NOT, 4, 9, Map.of());       // (4,11) → (8,11)
        ProbeActions actions = new ProbeActions(original);
        List<String> ids = actions.addSelection(List.of(toggle.id(), gate.id())).addedIds();
        original.execute(new SetLabelCommand(gate.id(), "A"));
        actions.rename(ids.get(0), "entrada");
        Path file = directory.resolve("cadena.vsim");
        original.save(file);
        String saved = Files.readString(file);
        assertThat(saved).contains("\"formatVersion\" : 3").doesNotContain("A.out");
        assertThat(saved.replaceAll("\\s", "")).as("the live name is left out")
                .contains("{\"id\":\"p1\",\"name\":\"entrada\",\"component\":\"c1\",\"pin\":0}")
                .contains("{\"id\":\"p2\",\"component\":\"c2\",\"pin\":1}");

        OpenDocument reopened = track(OpenDocument.open(file, registry));
        assertThat(reopened.isModified()).isFalse();
        int changeListeners = reopened.changeListenerCount();
        int probeListeners = reopened.document().probeListenerCount();
        EditorView view = new EditorView(reopened, services, NO_WINDOW);
        try {
            WaveformPanel panel = (WaveformPanel) view.bottomDock().content().orElseThrow();
            assertThat(panel.channelNames()).containsExactly("entrada", "A.out");
            reopened.execute(new SetLabelCommand(gate.id(), "B"));
            assertThat(panel.channelNames()).containsExactly("entrada", "B.out");

            // The NOT output (8,11) has its badge at grid x 8.5–10, y 9–10: screen (68–80, 72–80) at 100 %.
            assertThat(view.canvas().getToolTipText(mouse(view, MouseEvent.MOUSE_MOVED, 74, 76, 0,
                    MouseEvent.NOBUTTON))).isEqualTo("En formas de onda: B.out");
            view.bottomDock().hideDock();
            view.canvas().getMouseListeners()[0].mousePressed(mouse(view, MouseEvent.MOUSE_PRESSED, 74, 76,
                    InputEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1));
            view.canvas().getMouseListeners()[0].mouseReleased(mouse(view, MouseEvent.MOUSE_RELEASED, 74, 76, 0,
                    MouseEvent.BUTTON1));
            assertThat(view.bottomDock().isDockShowing()).isTrue();
            assertThat(view.canvas().selection().isEmpty()).isTrue();

            view.canvas().setSize(800, 600);
            view.canvas().zoomIn();
            assertThat(view.statusBar().text()).endsWith("125 %");
            view.zoomControl().resetButton().doClick();
            assertThat(view.canvas().viewport().zoom()).isEqualTo(1.0);
            assertThat(view.statusBar().text()).endsWith("100 %");
        } finally {
            view.dispose();
        }
        assertThat(reopened.changeListenerCount()).isEqualTo(changeListeners);
        assertThat(reopened.document().probeListenerCount()).isEqualTo(probeListeners);
    }

    private static AbstractButton toolbarButton(EditorView view, Action action) {
        return Arrays.stream(view.toolBar().getComponents())
                .filter(AbstractButton.class::isInstance)
                .map(AbstractButton.class::cast)
                .filter(button -> button.getAction() == action)
                .findFirst().orElseThrow();
    }

    @Test
    void clearPinsAndSelfExplainingTextsWorkWithEveryExtension() {
        OpenDocument document = track(OpenDocument.createEmpty(registry));
        place(document, StandardComponentTypes.LED, 0, 0, Map.of()); // its input (1, 3) is loose
        EditorView view = new EditorView(document, services, NO_WINDOW);
        try {
            waitUntil(() -> document.latestSnapshot().running()
                    && document.latestSnapshot().token() == document.netlistToken());
            view.problems().showSnapshot(document.latestSnapshot());
            assertThat(view.canvas().getToolTipText(mouse(view, MouseEvent.MOUSE_MOVED, 8, 24, 0,
                    MouseEvent.NOBUTTON))).isEqualTo("in = Z (sin conectar)");

            Action undo = view.actions().undo();
            KeyStroke undoKey = KeyStroke.getKeyStroke(KeyEvent.VK_Z, ShortcutCatalog.menuMask());
            assertThat(toolbarButton(view, undo).getToolTipText())
                    .isEqualTo(undo.getValue(Action.NAME) + " (" + KeyBindings.describe(undoKey) + ")");
            assertThat(toolbarButton(view, view.actions().step()).getToolTipText())
                    .isEqualTo("Avanza la simulación 10 unidades de tiempo (F6)");
            assertThat(view.actions().step().getValue(Action.NAME)).isEqualTo("Avanzar 10 t");
        } finally {
            view.dispose();
        }
    }
}
