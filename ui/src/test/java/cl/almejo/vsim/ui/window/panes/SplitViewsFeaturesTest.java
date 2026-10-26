package cl.almejo.vsim.ui.window.panes;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.edges.EdgeHandle;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.tools.CanvasMouseEvent;
import cl.almejo.vsim.ui.tools.Tool;
import cl.almejo.vsim.ui.tools.ToolContext;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import cl.almejo.vsim.ui.window.panes.CanvasPanes.Orientation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.Action;
import javax.swing.JMenu;
import java.awt.Cursor;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.ArrayList;
import java.util.List;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.texts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Split views, Task 5: the split grips on the canvas edges (canvas-edges §1–§4), Ventana ▸ «Dividir ▸» and «Siguiente
 * panel», and what the extension takes back. Nothing here ever changes the document.
 */
class SplitViewsFeaturesTest {

    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;

    private final AppPreferences preferences = AppPreferences.inMemory();
    private OpenDocument openDocument;
    private EditorView view;
    private Runnable dispose;
    private final RecordingTool tool = new RecordingTool();

    @BeforeEach
    void create() {
        EditorServices services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                preferences, new ComponentLibrary(), List.of());
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        view = new EditorView(openDocument, services, NO_WINDOW);
        view.setSize(1200, 800);
        view.doLayout();
        view.canvasPanes().setSize(WIDTH, HEIGHT);
        view.canvasPanes().doLayout();
        dispose = new SplitViewsExtension().install(view);
        view.canvas().setTool(tool);
    }

    @AfterEach
    void close() {
        if (dispose != null) {
            dispose.run();
        }
        view.dispose();
        openDocument.close();
    }

    private static final class RecordingTool implements Tool {
        final List<String> events = new ArrayList<>();

        @Override
        public void mousePressed(ToolContext context, CanvasMouseEvent event) {
            events.add("press");
        }

        @Override
        public void mouseDragged(ToolContext context, CanvasMouseEvent event) {
            events.add("drag");
        }

        @Override
        public void mouseReleased(ToolContext context, CanvasMouseEvent event) {
            events.add("release");
        }
    }

    // --- pointer on a pane -------------------------------------------------------------------------------------------

    private static MouseEvent mouse(CircuitCanvas pane, int id, int x, int y, int modifiers, int button) {
        return new MouseEvent(pane, id, 0, modifiers, x, y, 1, false, button);
    }

    private static void press(CircuitCanvas pane, int x, int y, int modifiers, int button) {
        MouseEvent event = mouse(pane, MouseEvent.MOUSE_PRESSED, x, y, modifiers, button);
        for (MouseListener listener : pane.getMouseListeners()) {
            listener.mousePressed(event);
        }
    }

    private static void press(CircuitCanvas pane, int x, int y) {
        press(pane, x, y, InputEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1);
    }

    private static void drag(CircuitCanvas pane, int x, int y, int modifiers) {
        MouseEvent event = mouse(pane, MouseEvent.MOUSE_DRAGGED, x, y, modifiers, MouseEvent.NOBUTTON);
        for (MouseMotionListener listener : pane.getMouseMotionListeners()) {
            listener.mouseDragged(event);
        }
    }

    private static void drag(CircuitCanvas pane, int x, int y) {
        drag(pane, x, y, InputEvent.BUTTON1_DOWN_MASK);
    }

    private static void release(CircuitCanvas pane, int x, int y, int button) {
        MouseEvent event = mouse(pane, MouseEvent.MOUSE_RELEASED, x, y, 0, button);
        for (MouseListener listener : pane.getMouseListeners()) {
            listener.mouseReleased(event);
        }
    }

    private static void release(CircuitCanvas pane, int x, int y) {
        release(pane, x, y, MouseEvent.BUTTON1);
    }

    private static void move(CircuitCanvas pane, int x, int y) {
        MouseEvent event = mouse(pane, MouseEvent.MOUSE_MOVED, x, y, 0, MouseEvent.NOBUTTON);
        for (MouseMotionListener listener : pane.getMouseMotionListeners()) {
            listener.mouseMoved(event);
        }
    }

    private static void escape(CircuitCanvas pane) {
        KeyEvent event = new KeyEvent(pane, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED);
        for (KeyListener listener : pane.getKeyListeners()) {
            listener.keyPressed(event);
        }
    }

    private static List<SplitGripHandle> grips(CircuitCanvas pane) {
        List<SplitGripHandle> grips = new ArrayList<>();
        for (EdgeHandle handle : pane.edgeHandles()) {
            if (handle instanceof SplitGripHandle grip) {
                grips.add(grip);
            }
        }
        return grips;
    }

    private static SplitGripHandle grip(CircuitCanvas pane, Orientation orientation) {
        return grips(pane).stream().filter(grip -> grip.orientation() == orientation).findFirst().orElseThrow();
    }

    private static Rectangle hitArea(CircuitCanvas pane, Orientation orientation) {
        return grip(pane, orientation).hitArea(pane.getSize()).getBounds();
    }

    private static int centerX(Rectangle area) {
        return (int) area.getCenterX();
    }

    private static int centerY(Rectangle area) {
        return (int) area.getCenterY();
    }

    /** Where a divider sits when its middle is at {@code position} of an area {@code length} long. */
    private static double fractionAt(int position, int length) {
        int thickness = PaneDivider.thickness();
        return (position - thickness / 2.0) / (length - thickness);
    }

    private static void perform(Action action) {
        assertThat(action).isNotNull();
        action.actionPerformed(new ActionEvent(SplitViewsFeaturesTest.class, ActionEvent.ACTION_PERFORMED, ""));
    }

    private void assertTheDocumentDidNotChange() {
        assertThat(openDocument.isModified()).isFalse();
        assertThat(openDocument.history().canUndo()).isFalse();
    }

    // --- grips ---------------------------------------------------------------------------------------------------------

    @Test
    void theGripsSitAtTheTopOfTheRightEdgeAndTheRightEndOfTheBottomEdge() {
        CircuitCanvas pane = view.canvas();
        Rectangle horizontal = hitArea(pane, Orientation.HORIZONTAL);
        Rectangle vertical = hitArea(pane, Orientation.VERTICAL);

        assertThat(horizontal).isEqualTo(new Rectangle(WIDTH - 20, SplitGripHandle.RULER_CLEARANCE, 20, 8));
        assertThat(vertical).isEqualTo(new Rectangle(WIDTH - SplitGripHandle.RULER_CLEARANCE - 8, HEIGHT - 20, 8,
                20));
        assertThat(grip(pane, Orientation.HORIZONTAL).cursor().getType()).isEqualTo(Cursor.N_RESIZE_CURSOR);
        assertThat(grip(pane, Orientation.VERTICAL).cursor().getType()).isEqualTo(Cursor.E_RESIZE_CURSOR);
        assertThat(grip(pane, Orientation.HORIZONTAL).tooltip()).isEqualTo("Arrastra para dividir la vista");

        move(pane, centerX(horizontal), centerY(horizontal));
        assertThat(pane.getCursor().getType()).isEqualTo(Cursor.N_RESIZE_CURSOR);
    }

    @Test
    void draggingTheRightGripDownSplitsTopAndBottomWhereItIsReleased() {
        CircuitCanvas pane = view.canvas();
        Rectangle grip = hitArea(pane, Orientation.HORIZONTAL);

        press(pane, centerX(grip), centerY(grip));
        drag(pane, centerX(grip), 200);
        assertThat(view.canvasPanes().splitPreview()).isPresent();
        assertThat(view.panes()).hasSize(1);
        drag(pane, centerX(grip), 300);
        release(pane, centerX(grip), 300);

        assertThat(view.panes()).hasSize(2);
        assertThat(view.canvasPanes().dividerFraction(Orientation.HORIZONTAL).getAsDouble())
                .isCloseTo(fractionAt(300, HEIGHT), within(0.01));
        assertThat(view.canvasPanes().splitPreview()).isEmpty();
        assertThat(tool.events).isEmpty();
        assertTheDocumentDidNotChange();
    }

    @Test
    void draggingTheBottomGripLeftSplitsSideBySide() {
        CircuitCanvas pane = view.canvas();
        Rectangle grip = hitArea(pane, Orientation.VERTICAL);

        press(pane, centerX(grip), centerY(grip));
        drag(pane, 500, centerY(grip));
        release(pane, 500, centerY(grip));

        assertThat(view.panes()).hasSize(2);
        assertThat(view.canvasPanes().dividerFraction(Orientation.VERTICAL).getAsDouble())
                .isCloseTo(fractionAt(500, WIDTH), within(0.01));
        assertThat(tool.events).isEmpty();
    }

    @Test
    void aReleaseWithin24PixelsOfTheEdgeSplitsNothing() {
        CircuitCanvas pane = view.canvas();
        Rectangle grip = hitArea(pane, Orientation.HORIZONTAL);

        press(pane, centerX(grip), centerY(grip));
        drag(pane, centerX(grip), 20);
        release(pane, centerX(grip), 20);
        Rectangle bottomGrip = hitArea(pane, Orientation.VERTICAL);
        press(pane, centerX(bottomGrip), centerY(bottomGrip));
        drag(pane, WIDTH - 15, centerY(bottomGrip));
        release(pane, WIDTH - 15, centerY(bottomGrip));

        assertThat(view.panes()).hasSize(1);
        assertThat(view.canvasPanes().splitPreview()).isEmpty();
        assertThat(tool.events).isEmpty();
    }

    @Test
    void thePreviewHidesWhileReleasingThereWouldSplitNothing() {
        CircuitCanvas pane = view.canvas();
        Rectangle grip = hitArea(pane, Orientation.HORIZONTAL);

        press(pane, centerX(grip), centerY(grip));
        drag(pane, centerX(grip), 200);
        assertThat(view.canvasPanes().splitPreview()).isPresent();
        drag(pane, centerX(grip), 20);
        assertThat(view.canvasPanes().splitPreview()).as("within 24 px of the edge").isEmpty();
        drag(pane, centerX(grip), 250);
        assertThat(view.canvasPanes().splitPreview()).as("back where a release splits").isPresent();
        release(pane, centerX(grip), 250);

        assertThat(view.panes()).hasSize(2);
    }

    @Test
    void escapeCancelsAGripDrag() {
        CircuitCanvas pane = view.canvas();
        Rectangle grip = hitArea(pane, Orientation.HORIZONTAL);

        press(pane, centerX(grip), centerY(grip));
        drag(pane, centerX(grip), 300);
        escape(pane);
        assertThat(view.canvasPanes().splitPreview()).isEmpty();
        drag(pane, centerX(grip), 350);
        release(pane, centerX(grip), 350);

        assertThat(view.panes()).hasSize(1);
        assertThat(tool.events).isEmpty();
    }

    @Test
    void panKeepsPrecedenceOverTheGrips() {
        CircuitCanvas pane = view.canvas();
        Rectangle grip = hitArea(pane, Orientation.HORIZONTAL);
        double before = pane.viewport().gridToScreen(0, 0).getY();

        press(pane, centerX(grip), centerY(grip), InputEvent.BUTTON2_DOWN_MASK, MouseEvent.BUTTON2);
        drag(pane, centerX(grip), centerY(grip) + 100, InputEvent.BUTTON2_DOWN_MASK);
        release(pane, centerX(grip), centerY(grip) + 100, MouseEvent.BUTTON2);

        assertThat(pane.viewport().gridToScreen(0, 0).getY()).isEqualTo(before + 100);
        assertThat(view.panes()).hasSize(1);
    }

    @Test
    void aGripIsOnlyOnTheOuterEdgeAndGoesOnceItsOrientationIsSplit() {
        view.canvasPanes().split(Orientation.VERTICAL, 0.5);
        CircuitCanvas left = view.panes().get(0);
        CircuitCanvas right = view.panes().get(1);
        assertThat(hitArea(left, Orientation.HORIZONTAL).isEmpty()).as("next to the divider").isTrue();
        assertThat(hitArea(right, Orientation.HORIZONTAL).isEmpty()).isFalse();
        assertThat(hitArea(left, Orientation.VERTICAL).isEmpty()).as("already split side by side").isTrue();
        assertThat(hitArea(right, Orientation.VERTICAL).isEmpty()).isTrue();

        Rectangle grip = hitArea(right, Orientation.HORIZONTAL);
        press(right, centerX(grip), centerY(grip));
        drag(right, centerX(grip), 300);
        release(right, centerX(grip), 300);

        assertThat(view.panes()).hasSize(4);
        assertThat(view.panes()).allSatisfy(pane -> {
            assertThat(grips(pane)).hasSize(2);
            assertThat(hitArea(pane, Orientation.HORIZONTAL).isEmpty()).isTrue();
            assertThat(hitArea(pane, Orientation.VERTICAL).isEmpty()).isTrue();
        });
    }

    @Test
    void theGripsStayWithTheGuidesHidden() {
        preferences.setShowGuides(false);
        assertThat(grips(view.canvas())).hasSize(2);
    }

    // --- menu ----------------------------------------------------------------------------------------------------------

    private JMenu splitMenu() {
        return (JMenu) view.menu(MenuId.WINDOW).getMenuComponent(1);
    }

    @Test
    void theWindowMenuHasTheSplitSubmenuAndTheNextPaneAfterNewView() {
        assertThat(texts(view.menu(MenuId.WINDOW))).containsExactly("Nueva vista", "Dividir", "Siguiente panel");
        assertThat(texts(splitMenu())).containsExactly("Arriba y abajo", "A los lados", "---", "Quitar división");
        assertThat(view.shortcutActions()).containsKeys("window.splitHorizontal", "window.splitVertical",
                "window.removeSplit", "window.nextPane");
        assertThat(view.shortcutActions().get("window.splitHorizontal").getValue(Action.ACCELERATOR_KEY)).isNull();
    }

    @Test
    void theMenuItemsPutTheDividersInTheMiddleAndRemoveBoth() {
        Action removeSplit = view.shortcutActions().get("window.removeSplit");
        Action nextPane = view.shortcutActions().get("window.nextPane");
        assertThat(removeSplit.isEnabled()).isFalse();
        assertThat(nextPane.isEnabled()).isFalse();

        perform(view.shortcutActions().get("window.splitHorizontal"));
        assertThat(view.canvasPanes().dividerFraction(Orientation.HORIZONTAL)).hasValue(0.5);
        assertThat(removeSplit.isEnabled()).isTrue();
        assertThat(nextPane.isEnabled()).isTrue();
        perform(view.shortcutActions().get("window.splitVertical"));
        assertThat(view.canvasPanes().dividerFraction(Orientation.VERTICAL)).hasValue(0.5);
        assertThat(view.panes()).hasSize(4);

        perform(removeSplit);
        assertThat(view.panes()).hasSize(1);
        assertThat(removeSplit.isEnabled()).isFalse();
        assertTheDocumentDidNotChange();
    }

    @Test
    void nextPaneGoesThroughThePanesInReadingOrder() {
        perform(view.shortcutActions().get("window.splitHorizontal"));
        perform(view.shortcutActions().get("window.splitVertical"));
        List<CircuitCanvas> panes = view.panes();
        assertThat(view.canvas()).isSameAs(panes.get(0));

        List<CircuitCanvas> visited = new ArrayList<>();
        for (int step = 0; step < 4; step++) {
            perform(view.shortcutActions().get("window.nextPane"));
            visited.add(view.canvas());
        }

        assertThat(visited).containsExactly(panes.get(1), panes.get(2), panes.get(3), panes.get(0));
    }

    @Test
    void disposeTakesBackTheGripsTheMenuItemsAndTheShortcutActions() {
        view.canvasPanes().split(Orientation.HORIZONTAL, 0.5);
        List<CircuitCanvas> panes = view.panes();

        dispose.run();
        dispose = null;

        assertThat(panes).allSatisfy(pane -> assertThat(grips(pane)).isEmpty());
        assertThat(texts(view.menu(MenuId.WINDOW))).containsExactly("Nueva vista");
        assertThat(view.shortcutActions()).doesNotContainKeys("window.splitHorizontal", "window.splitVertical",
                "window.removeSplit", "window.nextPane");
        view.canvasPanes().split(Orientation.VERTICAL, 0.5);
        assertThat(view.panes()).allSatisfy(pane -> assertThat(grips(pane)).isEmpty());
    }
}
