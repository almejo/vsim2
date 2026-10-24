package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitBounds;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.DrawnBounds;
import cl.almejo.vsim.ui.canvas.ViewState;
import cl.almejo.vsim.ui.canvas.Viewport;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.shortcuts.Scope;
import cl.almejo.vsim.ui.shortcuts.ShortcutCatalog;
import cl.almejo.vsim.ui.shortcuts.ShortcutEntry;
import cl.almejo.vsim.ui.window.panes.CanvasPanes.Orientation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** «Ver todo el circuito» and «Zoom a la selección» (zoom spec §5): one-shot zooms of the active pane. */
class ZoomActionsTest {

    private static final double MARGIN_FRACTION = 0.9;

    private final WindowCommands window = new WindowCommands() {
        @Override public void newDocument() { }
        @Override public void open() { }
        @Override public void save() { }
        @Override public void saveAs() { }
        @Override public void closeWindow() { }
        @Override public void exit() { }
        @Override public void newView() { }
    };
    private OpenDocument openDocument;
    private EditorView view;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        view = new EditorView(openDocument, PainterRegistry.standard(), ColorScheme.LIGHT, window);
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

    /** The screen rectangle {@code bounds} covers in {@code pane}: left, top, right, bottom. */
    private static double[] onScreen(CircuitCanvas pane, Bounds bounds) {
        Point2D corner = pane.viewport().gridToScreen(bounds.x(), bounds.y());
        Point2D end = pane.viewport().gridToScreen(bounds.x() + bounds.width(), bounds.y() + bounds.height());
        return new double[]{corner.getX(), corner.getY(), end.getX(), end.getY()};
    }

    /** {@code bounds} lies inside the pane, centred, and fills 90 % of it on one side (the other side fits). */
    private static void assertFittedWithTheMargin(CircuitCanvas pane, Bounds bounds) {
        double[] box = onScreen(pane, bounds);
        double width = pane.getWidth();
        double height = pane.getHeight();
        double widthShare = (box[2] - box[0]) / width;
        double heightShare = (box[3] - box[1]) / height;
        assertThat(Math.max(widthShare, heightShare)).isCloseTo(MARGIN_FRACTION, within(1e-6));
        assertThat(widthShare).isLessThanOrEqualTo(MARGIN_FRACTION + 1e-9);
        assertThat(heightShare).isLessThanOrEqualTo(MARGIN_FRACTION + 1e-9);
        assertThat((box[0] + box[2]) / 2).isCloseTo(width / 2, within(1e-6));
        assertThat((box[1] + box[3]) / 2).isCloseTo(height / 2, within(1e-6));
    }

    @Test
    void verTodoElCircuitoFitsTheWholeCircuitWithATenPercentMargin() {
        place(openDocument, StandardComponentTypes.NOT, 0, 0, Map.of());
        place(openDocument, StandardComponentTypes.AND2, 120, 40, Map.of());
        view.canvas().applyViewState(new ViewState(3, 500, 500));
        Bounds circuit = CircuitBounds.of(openDocument.document(), openDocument.registry()).orElseThrow();

        view.actions().zoomToFit().actionPerformed(null);

        assertThat(view.canvas().viewState().zoom()).isLessThan(3);
        assertFittedWithTheMargin(view.canvas(), circuit);
    }

    @Test
    void verTodoElCircuitoZoomsInOnASmallCircuitButNotPastFourHundredPercent() {
        place(openDocument, StandardComponentTypes.NOT, 10, 10, Map.of());
        view.canvas().applyViewState(new ViewState(0.5, 0, 0));

        view.actions().zoomToFit().actionPerformed(null);

        assertThat(view.canvas().viewState().zoom()).isEqualTo(Viewport.MAXIMUM_ZOOM);
    }

    @Test
    void verTodoElCircuitoOnAnEmptyCircuitLeavesTheViewWhereItIs() {
        ViewState before = new ViewState(2, 7, 9);
        view.canvas().applyViewState(before);

        view.actions().zoomToFit().actionPerformed(null);

        assertThat(view.canvas().viewState()).isEqualTo(before);
        assertThat(openDocument.history().canUndo()).as("a zoom is no undo step").isFalse();
    }

    @Test
    void zoomALaSeleccionFitsWhatIsSelectedNotTheWholeCircuit() {
        place(openDocument, StandardComponentTypes.NOT, 0, 0, Map.of());
        Component and = place(openDocument, StandardComponentTypes.AND2, 120, 40, Map.of());
        Component or = place(openDocument, StandardComponentTypes.OR2, 120, 60, Map.of());
        view.selection().set(List.of(and.id(), or.id()));
        Bounds selected = DrawnBounds.of(List.of(and, or), openDocument.registry()).orElseThrow();

        view.actions().zoomToSelection().actionPerformed(null);

        assertFittedWithTheMargin(view.canvas(), selected);
    }

    @Test
    void zoomALaSeleccionIsEnabledOnlyWhileSomethingIsSelected() {
        Action zoomToSelection = view.actions().zoomToSelection();
        assertThat(zoomToSelection.isEnabled()).isFalse();
        Component gate = place(openDocument, StandardComponentTypes.NOT, 0, 0, Map.of());

        view.selection().select(gate.id());
        assertThat(zoomToSelection.isEnabled()).isTrue();

        view.selection().clear();
        assertThat(zoomToSelection.isEnabled()).isFalse();
        assertThat(view.actions().zoomToFit().isEnabled()).as("«Ver todo» needs no selection").isTrue();
    }

    @Test
    void bothActionsZoomTheActivePaneOnly() {
        place(openDocument, StandardComponentTypes.NOT, 0, 0, Map.of());
        Component and = place(openDocument, StandardComponentTypes.AND2, 120, 40, Map.of());
        Component or = place(openDocument, StandardComponentTypes.OR2, 120, 60, Map.of());
        view.canvasPanes().split(Orientation.VERTICAL, 0.5);
        CircuitCanvas left = view.panes().get(0);
        CircuitCanvas right = view.panes().get(1);
        assertThat(right.getWidth()).isPositive();
        left.applyViewState(new ViewState(2, 3, 4));
        right.applyViewState(new ViewState(2, 5, 6));
        press(right);

        view.actions().zoomToFit().actionPerformed(null);

        assertFittedWithTheMargin(right, CircuitBounds.of(openDocument.document(), openDocument.registry())
                .orElseThrow());
        assertThat(left.viewState()).isEqualTo(new ViewState(2, 3, 4));

        press(left);
        view.selection().set(List.of(and.id(), or.id()));
        ViewState rightBefore = right.viewState();
        view.actions().zoomToSelection().actionPerformed(null);

        assertFittedWithTheMargin(left, DrawnBounds.of(List.of(and, or), openDocument.registry()).orElseThrow());
        assertThat(right.viewState()).isEqualTo(rightBefore);
    }

    @Test
    void mayusOneAndMayusTwoAreCanvasKeysOfEveryPane() {
        int shift = InputEvent.SHIFT_DOWN_MASK;
        assertThat(ShortcutCatalog.find("view.zoomToFit")).contains(new ShortcutEntry("view.zoomToFit",
                "action.zoomToFit", MenuId.VIEW, Scope.CANVAS, Optional.of(KeyStroke.getKeyStroke(KeyEvent.VK_1,
                shift))));
        assertThat(ShortcutCatalog.find("view.zoomToSelection")).contains(new ShortcutEntry("view.zoomToSelection",
                "action.zoomToSelection", MenuId.VIEW, Scope.CANVAS, Optional.of(KeyStroke.getKeyStroke(KeyEvent.VK_2,
                shift))));
        view.canvasPanes().split(Orientation.VERTICAL, 0.5);
        for (CircuitCanvas pane : view.panes()) {
            assertThat(boundAction(pane, KeyStroke.getKeyStroke(KeyEvent.VK_1, shift)))
                    .isSameAs(view.actions().zoomToFit());
            assertThat(boundAction(pane, KeyStroke.getKeyStroke(KeyEvent.VK_2, shift)))
                    .isSameAs(view.actions().zoomToSelection());
        }
    }

    private static Action boundAction(CircuitCanvas pane, KeyStroke stroke) {
        Object name = pane.getInputMap(JComponent.WHEN_FOCUSED).get(stroke);
        assertThat(name).as("binding of " + stroke).isNotNull();
        return pane.getActionMap().get(name);
    }

    @Test
    void theVerMenuListsThemAfterZoomOneHundredPercentWithoutAnAccelerator() {
        List<String> texts = new ArrayList<>();
        List<JMenuItem> items = new ArrayList<>();
        for (int index = 0; index < view.menu(MenuId.VIEW).getItemCount(); index++) {
            JMenuItem item = view.menu(MenuId.VIEW).getItem(index);
            texts.add(item == null ? "---" : item.getText());
            items.add(item);
        }
        assertThat(texts).containsSubsequence("Zoom 100 %", "Ver todo el circuito", "Zoom a la selección",
                "Centrar circuito");
        assertThat(texts.indexOf("Ver todo el circuito")).isEqualTo(texts.indexOf("Zoom 100 %") + 1);
        assertThat(texts.indexOf("Centrar circuito")).isEqualTo(texts.indexOf("Zoom a la selección") + 1);
        JMenuItem fit = items.get(texts.indexOf("Ver todo el circuito"));
        assertThat(fit.getAction()).isSameAs(view.actions().zoomToFit());
        assertThat(fit.getAccelerator()).as("a canvas key, like F8").isNull();
    }

    @Test
    void theToolbarEndsWithVerTodoAfterCentrarCircuitoAndHasNoSelectionButton() {
        List<Action> toolBarActions = new ArrayList<>();
        for (java.awt.Component child : view.toolBar().getComponents()) {
            if (child instanceof AbstractButton button && button.getAction() != null) {
                toolBarActions.add(button.getAction());
            }
        }
        assertThat(toolBarActions).containsSubsequence(view.actions().zoomOut(), view.actions().zoomIn(),
                view.actions().centerView(), view.actions().zoomToFit());
        assertThat(toolBarActions.indexOf(view.actions().zoomToFit()))
                .isEqualTo(toolBarActions.indexOf(view.actions().centerView()) + 1);
        assertThat(toolBarActions).doesNotContain(view.actions().zoomToSelection());
    }

    @Test
    void neitherIsCalledAjustar() {
        assertThat(view.actions().zoomToFit().getValue(Action.NAME)).isEqualTo("Ver todo el circuito");
        assertThat(view.actions().zoomToSelection().getValue(Action.NAME)).isEqualTo("Zoom a la selección");
        assertThat(view.actions().zoomToFit().getValue(Action.SHORT_DESCRIPTION)).isEqualTo("Ver todo el circuito");
        assertThat(view.actions().zoomToSelection().getValue(Action.SHORT_DESCRIPTION))
                .isEqualTo("Zoom a la selección (necesita algo seleccionado)");
    }
}
