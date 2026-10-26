package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.ViewState;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.shortcuts.Scope;
import cl.almejo.vsim.ui.shortcuts.ShortcutCatalog;
import cl.almejo.vsim.ui.shortcuts.ShortcutEntry;
import cl.almejo.vsim.ui.tools.SelectTool;
import cl.almejo.vsim.ui.tools.ZoomTool;
import cl.almejo.vsim.ui.window.panes.CanvasPanes.Orientation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** The zoom tool in the window (zoom spec §4.1, §5, §7): button, Ver menu, Z, one pane at a time. */
class ZoomToolViewTest {

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

    private static void mouse(CircuitCanvas canvas, int id, int x, int y, int modifiers, int button) {
        MouseEvent event = new MouseEvent(canvas, id, 0, modifiers, x, y, 1, false, button);
        switch (id) {
            case MouseEvent.MOUSE_PRESSED -> canvas.getMouseListeners()[0].mousePressed(event);
            case MouseEvent.MOUSE_DRAGGED -> canvas.getMouseMotionListeners()[0].mouseDragged(event);
            case MouseEvent.MOUSE_RELEASED -> canvas.getMouseListeners()[0].mouseReleased(event);
            default -> throw new IllegalArgumentException("event " + id);
        }
    }

    private static void drag(CircuitCanvas canvas, int fromX, int fromY, int toX, int toY) {
        int down = InputEvent.BUTTON1_DOWN_MASK;
        mouse(canvas, MouseEvent.MOUSE_PRESSED, fromX, fromY, down, MouseEvent.BUTTON1);
        mouse(canvas, MouseEvent.MOUSE_DRAGGED, (fromX + toX) / 2, (fromY + toY) / 2, down, MouseEvent.NOBUTTON);
        mouse(canvas, MouseEvent.MOUSE_DRAGGED, toX, toY, down, MouseEvent.NOBUTTON);
        mouse(canvas, MouseEvent.MOUSE_RELEASED, toX, toY, 0, MouseEvent.BUTTON1);
    }

    private static void click(CircuitCanvas canvas, int x, int y) {
        mouse(canvas, MouseEvent.MOUSE_PRESSED, x, y, InputEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1);
        mouse(canvas, MouseEvent.MOUSE_RELEASED, x, y, 0, MouseEvent.BUTTON1);
    }

    @Test
    void theActionChoosesTheZoomToolOnEveryPaneAndPressesItsButton() {
        view.canvasPanes().split(Orientation.VERTICAL, 0.5);
        view.actions().zoomTool().actionPerformed(null);
        List<CircuitCanvas> panes = view.panes();
        assertThat(panes).allSatisfy(pane -> assertThat(pane.tool()).isInstanceOf(ZoomTool.class));
        assertThat(panes.get(0).tool()).as("one instance per pane").isNotSameAs(panes.get(1).tool());
        assertThat(zoomButton().isSelected()).isTrue();
        view.actions().selectTool().actionPerformed(null);
        assertThat(zoomButton().isSelected()).isFalse();
    }

    @Test
    void escapeLeavesTheToolWhenNoRectangleIsBeingDragged() {
        view.actions().zoomTool().actionPerformed(null);
        CircuitCanvas canvas = view.canvas();
        int down = InputEvent.BUTTON1_DOWN_MASK;
        mouse(canvas, MouseEvent.MOUSE_PRESSED, 100, 100, down, MouseEvent.BUTTON1);
        mouse(canvas, MouseEvent.MOUSE_DRAGGED, 300, 250, down, MouseEvent.NOBUTTON);
        assertThat(canvas.pressKeyOnTool(KeyEvent.VK_ESCAPE, false)).isTrue();
        assertThat(canvas.tool()).as("the first Esc drops the rectangle").isInstanceOf(ZoomTool.class);
        mouse(canvas, MouseEvent.MOUSE_RELEASED, 300, 250, 0, MouseEvent.BUTTON1);
        assertThat(canvas.viewState().zoom()).isEqualTo(1.0);
        assertThat(canvas.pressKeyOnTool(KeyEvent.VK_ESCAPE, false)).isTrue();
        assertThat(canvas.tool()).as("the second leaves the tool").isInstanceOf(SelectTool.class);
    }

    @Test
    void aRectangleZoomsOnlyThePaneWhereItWasDragged() {
        view.canvasPanes().split(Orientation.VERTICAL, 0.5);
        CircuitCanvas left = view.panes().get(0);
        CircuitCanvas right = view.panes().get(1);
        left.applyViewState(new ViewState(1, 20, 30));
        right.applyViewState(new ViewState(1, 20, 30));
        view.actions().zoomTool().actionPerformed(null);
        int width = right.getWidth();
        int height = right.getHeight();
        assertThat(width).isPositive();

        int bandLeft = width / 4;
        int bandTop = height / 4;
        int bandRight = width * 3 / 4;
        int bandBottom = height * 3 / 4;
        Point2D centre = right.viewport().screenToGrid((bandLeft + bandRight) / 2.0, (bandTop + bandBottom) / 2.0);
        drag(right, bandLeft, bandTop, bandRight, bandBottom);

        double expectedZoom = Math.min(width / (double) (bandRight - bandLeft), height / (double) (bandBottom - bandTop));
        assertThat(expectedZoom).as("about half the pane each way: twice as big").isCloseTo(2.0, within(0.05));
        assertThat(right.viewState().zoom()).isCloseTo(expectedZoom, within(1e-9));
        assertThat(right.viewState().centerX()).isCloseTo(centre.getX(), within(1e-9));
        assertThat(right.viewState().centerY()).isCloseTo(centre.getY(), within(1e-9));
        assertThat(left.viewState()).isEqualTo(new ViewState(1, 20, 30));
        assertThat(view.canvas()).as("pressing a pane makes it the active one").isSameAs(right);
        assertThat(openDocument.isModified()).isFalse();
        assertThat(openDocument.history().canUndo()).isFalse();
    }

    @Test
    void aClickStepsInAndTheStatusBarFollows() {
        place(openDocument, StandardComponentTypes.NOT, 5, 5, Map.of());
        view.actions().zoomTool().actionPerformed(null);
        click(view.canvas(), 200, 150);
        assertThat(view.canvas().viewState().zoom()).isCloseTo(1.25, within(1e-9));
        assertThat(view.statusBar().text()).contains("125 %");
    }

    @Test
    void zIsACanvasKeyOfEveryPaneAndTheMenuItemSitsAfterMoverVista() {
        assertThat(ShortcutCatalog.find("view.zoomTool")).contains(new ShortcutEntry("view.zoomTool",
                "action.toolZoom", MenuId.VIEW, Scope.CANVAS, Optional.of(KeyStroke.getKeyStroke(KeyEvent.VK_Z, 0))));
        view.canvasPanes().split(Orientation.VERTICAL, 0.5);
        for (CircuitCanvas pane : view.panes()) {
            Object name = pane.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(KeyEvent.VK_Z, 0));
            assertThat(name).isNotNull();
            assertThat(pane.getActionMap().get(name)).isSameAs(view.actions().zoomTool());
        }
        List<String> texts = new ArrayList<>();
        List<JMenuItem> items = new ArrayList<>();
        for (int index = 0; index < view.menu(MenuId.VIEW).getItemCount(); index++) {
            JMenuItem item = view.menu(MenuId.VIEW).getItem(index);
            texts.add(item == null ? "---" : item.getText());
            items.add(item);
        }
        assertThat(texts.indexOf("Zoom")).isEqualTo(texts.indexOf("Mover vista") + 1);
        assertThat(items.get(texts.indexOf("Zoom")).getAccelerator()).as("a canvas key, like F8").isNull();
    }

    @Test
    void theButtonIsTheLastToolBeforeTheToggles() {
        List<java.awt.Component> children = List.of(view.toolBar().getComponents());
        int pan = indexOfAction(children, view.actions().panTool());
        int zoom = indexOfAction(children, view.actions().zoomTool());
        assertThat(zoom).isEqualTo(pan + 1);
        JToggleButton toggle = new JToggleButton("Ajustar");
        Runnable remove = view.addToolbarToggleAfterTools(toggle);
        assertThat(view.toolBar().getComponentIndex(toggle)).as("toggles come right after the zoom button")
                .isEqualTo(zoom + 1);
        remove.run();
        assertThat(view.actions().zoomTool().getValue(Action.NAME)).isEqualTo("Zoom");
        assertThat(view.actions().zoomTool().getValue(Action.SHORT_DESCRIPTION))
                .isEqualTo("Zoom: arrastra un recuadro para verlo en grande; clic acerca, Alt o Mayús + clic aleja");
    }

    private JToggleButton zoomButton() {
        List<java.awt.Component> children = List.of(view.toolBar().getComponents());
        return (JToggleButton) children.get(indexOfAction(children, view.actions().zoomTool()));
    }

    private static int indexOfAction(List<java.awt.Component> children, Action action) {
        for (int index = 0; index < children.size(); index++) {
            if (children.get(index) instanceof AbstractButton button && button.getAction() == action) {
                return index;
            }
        }
        return -1;
    }
}
