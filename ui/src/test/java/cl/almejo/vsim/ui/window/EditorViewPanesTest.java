package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.ViewState;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.tools.CanvasMouseEvent;
import cl.almejo.vsim.ui.tools.Tool;
import cl.almejo.vsim.ui.tools.ToolContext;
import cl.almejo.vsim.ui.tools.WireTool;
import cl.almejo.vsim.ui.window.panes.CanvasPanes.Orientation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** One window, several panes (split views, Task 1): shared selection, window-wide tool choice, the active pane. */
class EditorViewPanesTest {

    private OpenDocument openDocument;
    private EditorView view;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        view = new EditorView(openDocument, PainterRegistry.standard(), ColorScheme.LIGHT, NO_WINDOW);
        view.setSize(1200, 800);
        view.doLayout();
        view.canvasPanes().setSize(800, 600);
        view.canvasPanes().doLayout();
    }

    @AfterEach
    void close() {
        view.dispose();
        openDocument.close();
    }

    private static void press(CircuitCanvas canvas) {
        MouseEvent event = new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 0, 0, 20, 20, 1, false,
                MouseEvent.BUTTON1);
        for (var listener : canvas.getMouseListeners()) {
            listener.mousePressed(event);
        }
    }

    /** A tool that records the presses it gets. */
    private static final class RecordingTool implements Tool {
        final List<String> presses = new ArrayList<>();

        @Override
        public void mousePressed(ToolContext context, CanvasMouseEvent event) {
            presses.add("pressed");
        }

        @Override
        public void mouseDragged(ToolContext context, CanvasMouseEvent event) {
        }

        @Override
        public void mouseReleased(ToolContext context, CanvasMouseEvent event) {
        }
    }

    @Test
    void theCanvasIsTheActivePaneAndEveryPaneSharesTheWindowsSelection() {
        CircuitCanvas first = view.canvas();
        view.canvasPanes().split(Orientation.VERTICAL, 0.5);
        assertThat(view.panes()).hasSize(2).startsWith(first);
        CircuitCanvas second = view.panes().get(1);
        assertThat(view.canvas()).isSameAs(first);
        press(second);
        assertThat(view.canvas()).isSameAs(second);

        Component gate = place(openDocument, StandardComponentTypes.NOT, 0, 0, Map.of());
        first.selection().select(gate.id());
        assertThat(second.selection()).isSameAs(first.selection()).isSameAs(view.selection());
        assertThat(second.selection().ids()).containsExactly(gate.id());
        assertThat(view.statusBar().text()).contains(Messages.format("status.selected", 1));
    }

    @Test
    void separateWindowsOfOneDocumentKeepTheirOwnSelection() {
        EditorView other = new EditorView(openDocument, PainterRegistry.standard(), ColorScheme.LIGHT, NO_WINDOW);
        try {
            assertThat(other.selection()).isNotSameAs(view.selection());
        } finally {
            other.dispose();
        }
    }

    @Test
    void aToolChoiceReachesEveryPaneWithItsOwnInstanceAndAGestureStaysInItsPane() {
        view.canvasPanes().split(Orientation.HORIZONTAL, 0.5);
        CircuitCanvas top = view.panes().get(0);
        CircuitCanvas bottom = view.panes().get(1);
        view.setToolOnEveryPane(RecordingTool::new);
        assertThat(top.tool()).isInstanceOf(RecordingTool.class);
        assertThat(bottom.tool()).isInstanceOf(RecordingTool.class).isNotSameAs(top.tool());

        press(top);
        assertThat(((RecordingTool) top.tool()).presses).containsExactly("pressed");
        assertThat(((RecordingTool) bottom.tool()).presses).isEmpty();

        view.canvasPanes().split(Orientation.VERTICAL, 0.5);
        assertThat(view.panes()).allSatisfy(pane -> assertThat(pane.tool()).isInstanceOf(RecordingTool.class));
    }

    @Test
    void theActivePaneGoingBackToItsDefaultToolTakesEveryPaneBack() {
        view.canvasPanes().split(Orientation.HORIZONTAL, 0.5);
        view.setToolOnEveryPane(WireTool::new);
        view.canvas().useDefaultTool();
        assertThat(view.panes()).allSatisfy(pane -> assertThat(pane.tool()).isSameAs(pane.defaultTool()));
        view.canvasPanes().split(Orientation.VERTICAL, 0.5);
        assertThat(view.panes()).allSatisfy(pane -> assertThat(pane.tool()).isSameAs(pane.defaultTool()));

        view.setToolOnEveryPane(WireTool::new);
        view.useDefaultToolOnEveryPane();
        assertThat(view.panes()).allSatisfy(pane -> assertThat(pane.tool()).isSameAs(pane.defaultTool()));
    }

    @Test
    void forEachPaneInstallsOnLaterPanesAndUndoesThePanesThatGo() {
        List<String> log = new ArrayList<>();
        Runnable disposeAll = view.forEachPane(pane -> {
            int number = view.panes().indexOf(pane);
            log.add("install " + number);
            return () -> log.add("dispose " + number);
        });
        assertThat(log).containsExactly("install 0");
        view.canvasPanes().split(Orientation.HORIZONTAL, 0.5);
        assertThat(log).containsExactly("install 0", "install 1");
        view.canvasPanes().removeDivider(Orientation.HORIZONTAL);
        assertThat(log).containsExactly("install 0", "install 1", "dispose 1");
        view.canvasPanes().split(Orientation.VERTICAL, 0.5);
        disposeAll.run();
        assertThat(log).containsExactly("install 0", "install 1", "dispose 1", "install 1", "dispose 0",
                "dispose 1");
        view.canvasPanes().split(Orientation.HORIZONTAL, 0.5);
        assertThat(log).hasSize(6);
    }

    @Test
    void activePaneListenersHearEveryChange() {
        List<String> heard = new ArrayList<>();
        Runnable remove = view.addActivePaneListener(() -> heard.add("changed"));
        view.canvasPanes().split(Orientation.VERTICAL, 0.5);
        press(view.panes().get(1));
        assertThat(heard).containsExactly("changed");
        remove.run();
        press(view.panes().get(0));
        assertThat(heard).containsExactly("changed");
    }

    @Test
    void theStatusBarZoomFollowsTheActivePane() {
        view.canvasPanes().split(Orientation.VERTICAL, 0.5);
        CircuitCanvas left = view.panes().get(0);
        CircuitCanvas right = view.panes().get(1);
        right.applyViewState(new ViewState(2, 0, 0));
        assertThat(view.statusBar().text()).doesNotContain(Messages.format("zoom.percent", 200));
        press(right);
        assertThat(view.statusBar().text()).endsWith(Messages.format("zoom.percent", 200));
        left.applyViewState(new ViewState(0.5, 0, 0));
        assertThat(view.statusBar().text()).endsWith(Messages.format("zoom.percent", 200));
        press(left);
        assertThat(view.statusBar().text()).endsWith(Messages.format("zoom.percent", 50));
    }

    @Test
    void zoomOneHundredPercentActsOnTheActivePaneOnly() {
        view.canvasPanes().split(Orientation.VERTICAL, 0.5);
        CircuitCanvas left = view.panes().get(0);
        CircuitCanvas right = view.panes().get(1);
        left.applyViewState(new ViewState(2, 3, 4));
        right.applyViewState(new ViewState(2, 5, 6));
        press(right);

        view.actions().zoomReset().actionPerformed(null);

        assertThat(right.isTravelling()).as("a pane that is not on screen jumps").isFalse();
        assertThat(right.viewState().zoom()).isEqualTo(1.0);
        assertThat(right.viewState().centerX()).isCloseTo(5, within(1e-9));
        assertThat(left.viewState()).isEqualTo(new ViewState(2, 3, 4));
    }

    @Test
    void removedPanesAreDisposedAndStopFollowingTheDocument() {
        view.canvasPanes().split(Orientation.HORIZONTAL, 0.5);
        CircuitCanvas bottom = view.panes().get(1);
        view.canvasPanes().removeDivider(Orientation.HORIZONTAL);
        assertThat(view.panes()).doesNotContain(bottom);
        Component gate = place(openDocument, StandardComponentTypes.NOT, 0, 0, Map.of());
        view.selection().select(gate.id());
        openDocument.history().undo();
        assertThat(view.selection().isEmpty()).isTrue(); // the surviving pane still prunes the shared selection
    }
}
