package cl.almejo.vsim.ui.ai.present;

import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.TaskInfo;
import cl.almejo.vsim.automation.edit.TaskStage;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import cl.almejo.vsim.ui.window.WindowCommands;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JLayeredPane;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static cl.almejo.vsim.ui.ai.present.FakePresentationEvents.step;
import static org.assertj.core.api.Assertions.assertThat;

/** «Presentar» in a window (spec §4, §5): the Ver ▸ and toolbar toggle and the floating panel of the AI's task. */
class PresentationExtensionTest {

    private static final WindowCommands NO_WINDOW = new WindowCommands() {
        @Override public void newDocument() { }
        @Override public void open() { }
        @Override public void save() { }
        @Override public void saveAs() { }
        @Override public void closeWindow() { }
        @Override public void exit() { }
    };
    private static final DocumentId THIS_CIRCUIT = new DocumentId("this");
    private static final TaskInfo TASK = new TaskInfo("t1", THIS_CIRCUIT, "Sumador de 4 bits");
    private static final List<TaskStage> STAGES = List.of(
            new TaskStage("Sumador de 1 bit", List.of("Pongo las XOR", "Pongo las AND")),
            new TaskStage("Sumador de 4 bits", List.of()));

    private final AppPreferences saved = AppPreferences.inMemory();
    private final FakePresentationEvents events = new FakePresentationEvents();
    private final RecordingControls controls = new RecordingControls();
    private final Deque<Runnable> editorQueue = new ArrayDeque<>();
    private OpenDocument document;
    private EditorView view;
    private JRootPane root;
    private PresentationExtension extension;
    private Runnable dispose;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        document = OpenDocument.createEmpty(StandardComponentTypes.registry());
        view = new EditorView(document, new EditorServices(PainterRegistry.standard(),
                new Appearance(ColorScheme.LIGHT), saved.layered(), new ComponentLibrary(), List.of()), NO_WINDOW);
        root = new JRootPane();
        root.getContentPane().add(view);
        root.setSize(1200, 800);
        layOut(root);
        extension = new PresentationExtension(events, controls, any -> THIS_CIRCUIT, editorQueue::add);
        dispose = extension.install(view);
    }

    @AfterEach
    void close() {
        if (dispose != null) {
            dispose.run();
        }
        view.dispose();
        document.close();
    }

    private PresentationFeatures features() {
        return extension.featuresOf(view).orElseThrow();
    }

    /** Runs what the events queued for the editor thread. */
    private void runEditorThread() {
        while (!editorQueue.isEmpty()) {
            editorQueue.poll().run();
        }
    }

    private void presentATask() {
        saved.setAiPresenting(true);
        events.start(TASK);
        events.plan(TASK, STAGES, 0, 0);
        runEditorThread();
    }

    @Test
    void itAddsTheToggleToVerAndTheToolbarAndTakesEverythingBack() {
        dispose.run();
        dispose = null;
        JMenu viewMenu = view.menu(MenuId.VIEW);
        List<String> menuBefore = texts(viewMenu);
        int toolbarBefore = view.toolBar().getComponentCount();
        int preferenceListeners = saved.listenerCount();

        Runnable again = extension.install(view);

        assertThat(texts(viewMenu)).endsWith("---", "Presentar lo que hace la IA");
        assertThat(viewMenu.getItem(viewMenu.getItemCount() - 1)).isInstanceOf(JCheckBoxMenuItem.class);
        assertThat(view.toolBar().getComponentCount()).isEqualTo(toolbarBefore + 2);
        assertThat(view.shortcutActions()).containsKey(PresentationExtension.SHORTCUT_ID);
        assertThat(events.presentationListeners).hasSize(1);
        assertThat(events.taskListeners).hasSize(1);

        again.run();

        assertThat(texts(viewMenu)).isEqualTo(menuBefore);
        assertThat(view.toolBar().getComponentCount()).isEqualTo(toolbarBefore);
        assertThat(view.shortcutActions()).doesNotContainKey(PresentationExtension.SHORTCUT_ID);
        assertThat(events.presentationListeners).isEmpty();
        assertThat(events.taskListeners).isEmpty();
        assertThat(saved.listenerCount()).isEqualTo(preferenceListeners);
        assertThat(extension.featuresOf(view)).isEmpty();
    }

    @Test
    void theToggleIsTheRememberedPreferenceInEveryWindow() {
        Action toggle = features().toggle();
        assertThat(toggle.getValue(Action.SELECTED_KEY)).isEqualTo(false);

        toggle.actionPerformed(null);

        assertThat(saved.aiPresenting()).isTrue();
        assertThat(toggle.getValue(Action.SELECTED_KEY)).isEqualTo(true);
        saved.setAiPresenting(false);
        assertThat(toggle.getValue(Action.SELECTED_KEY)).isEqualTo(false);
        assertThat(toggle.getValue(Action.LARGE_ICON_KEY)).isNotNull();
    }

    @Test
    void thePanelShowsOnlyWhileATaskOfThisCircuitRunsInPresentar() {
        events.start(TASK);
        events.plan(TASK, STAGES, 0, 0);
        runEditorThread();
        assertThat(features().isPanelShown()).as("working mode").isFalse();

        saved.setAiPresenting(true);
        assertThat(features().isPanelShown()).isTrue();
        assertThat(features().panel().headerText()).isEqualTo("IA · Sumador de 4 bits");

        saved.setAiPresenting(false);
        assertThat(features().isPanelShown()).isFalse();
    }

    @Test
    void anotherCircuitsTaskIsNotShownHere() {
        saved.setAiPresenting(true);
        TaskInfo elsewhere = new TaskInfo("t9", new DocumentId("other"), "Otra cosa");

        events.start(elsewhere);
        events.plan(elsewhere, STAGES, 0, 0);
        events.propose(step("t9", 0, 0, "Pongo las XOR"));
        runEditorThread();

        assertThat(features().isPanelShown()).isFalse();
    }

    @Test
    void theEventsReachThePanelOnlyThroughTheEditorThread() {
        saved.setAiPresenting(true);
        events.start(TASK);
        events.plan(TASK, STAGES, 0, 0);
        assertThat(features().isPanelShown()).isFalse();

        runEditorThread();
        events.propose(step("t1", 0, 0, "Pongo las XOR"));
        assertThat(features().panel().nextButton().isEnabled()).isFalse();
        runEditorThread();
        assertThat(features().panel().nextButton().isEnabled()).isTrue();

        features().panel().nextButton().doClick();
        assertThat(controls.calls).containsExactly("next t1");
    }

    @Test
    void aStoppedTaskClosesThePanelAndAnEndedOneKeepsItUntilClosed() {
        presentATask();
        events.check("t1", 0, true, "cumple la tabla");
        events.end(TASK);
        runEditorThread();

        assertThat(features().isPanelShown()).isTrue();
        assertThat(features().panel().keepOnlyResultButton().isVisible()).isTrue();
        assertThat(features().panel().cards().get(0).lineTexts()).contains("✓ cumple la tabla");

        features().panel().closeButton().doClick();
        assertThat(features().isPanelShown()).isFalse();

        TaskInfo next = new TaskInfo("t2", THIS_CIRCUIT, "Otra");
        events.start(next);
        runEditorThread();
        assertThat(features().isPanelShown()).isTrue();
        events.stop(next);
        runEditorThread();
        assertThat(features().isPanelShown()).isFalse();
    }

    @Test
    void thePanelFloatsAtTheTopRightOfTheActivePaneAndStaysWhereTheUserMovesIt() {
        presentATask();
        PresentationPanel panel = features().panel();
        JLayeredPane layered = root.getLayeredPane();
        assertThat(panel.getParent()).isSameAs(layered);
        assertThat(JLayeredPane.getLayer(panel)).isEqualTo(JLayeredPane.PALETTE_LAYER);

        CircuitCanvas pane = view.canvas();
        Rectangle paneBounds = SwingUtilities.convertRectangle(pane.getParent(), pane.getBounds(), layered);
        Rectangle panelBounds = panel.getBounds();
        assertThat(panelBounds.width).isPositive();
        assertThat(panelBounds.x + panelBounds.width).isEqualTo(paneBounds.x + paneBounds.width
                - FloatingPlacement.MARGIN);
        assertThat(panelBounds.y).isEqualTo(paneBounds.y + FloatingPlacement.MARGIN);

        features().placement().moveBy(-100, 40);
        events.propose(step("t1", 0, 0, "Pongo las XOR"));
        runEditorThread();

        assertThat(panel.getLocation()).isEqualTo(new Point(panelBounds.x - 100, panelBounds.y + 40));
    }

    @Test
    void theAiSwitchingToPresentarTurnsTheToggleOnAndThePanelSaysSo() {
        events.start(TASK);
        events.plan(TASK, STAGES, 0, 0);
        runEditorThread();

        extension.aiAskedForPresenting(THIS_CIRCUIT);

        assertThat(saved.aiPresenting()).isTrue();
        assertThat(features().isPanelShown()).isTrue();
        assertThat(features().panel().notice().getText()).isEqualTo("La IA pasó a Presentar");
        events.apply(step("t1", 0, 0, "Pongo las XOR"));
        runEditorThread();
        assertThat(features().panel().notice().isVisible()).isFalse();
    }

    @Test
    void disposingTakesThePanelOutOfTheWindow() {
        presentATask();
        PresentationPanel panel = features().panel();
        events.propose(step("t1", 0, 0, "Pongo las XOR")); // queued before the dispose, run after it

        dispose.run();
        dispose = null;
        runEditorThread();

        assertThat(panel.getParent()).isNull();
        assertThat(panel.nextButton().isEnabled()).isFalse();
    }

    private static List<String> texts(JMenu menu) {
        List<String> texts = new ArrayList<>();
        for (Component child : menu.getMenuComponents()) {
            texts.add(child instanceof JMenuItem item ? item.getText() : "---");
        }
        return texts;
    }

    private static void layOut(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container inner) {
                layOut(inner);
            }
        }
    }
}
