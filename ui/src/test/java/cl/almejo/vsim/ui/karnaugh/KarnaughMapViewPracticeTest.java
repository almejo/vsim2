package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.logic.DrawnGroup;
import cl.almejo.vsim.core.logic.KarnaughLayout;
import cl.almejo.vsim.core.logic.LogicValue;
import cl.almejo.vsim.core.logic.Minimization;
import cl.almejo.vsim.ui.canvas.ManualFrameTicker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.OptionalInt;

import static cl.almejo.vsim.core.logic.Minimization.Form.SUM_OF_PRODUCTS;
import static org.assertj.core.api.Assertions.assertThat;

class KarnaughMapViewPracticeTest {

    private KarnaughModel model;
    private PracticeModel practice;
    private KarnaughMapView view;
    private ManualFrameTicker ticker;

    @BeforeEach
    void create() {
        model = new KarnaughModel();
        practice = model.practice();
        withInputs(4);
    }

    private void withInputs(int inputs) {
        model.setInputCount(inputs);
        view = new KarnaughMapView(model, new HoverState(), 0, true);
        view.setSize(400, 400);
        ticker = new ManualFrameTicker();
        view.setTicker(ticker);
        view.setShowingCheck(() -> true);
        practice.setPractising(true);
        view.refreshPractice();
    }

    private KarnaughLayout layout() {
        return KarnaughLayout.of(model.inputCount());
    }

    private List<DrawnGroup> groups() {
        return practice.groups(0, SUM_OF_PRODUCTS);
    }

    /** The centre of map cell (row, column); either may be past the map, a cell beyond its edge. */
    private Point at(int row, int column) {
        MapGeometry geometry = view.geometry();
        Rectangle grid = geometry.grid();
        int cell = geometry.cellSize();
        return new Point(grid.x + column * cell + cell / 2, grid.y + row * cell + cell / 2);
    }

    private void press(Point point, int modifiers) {
        mouse(MouseEvent.MOUSE_PRESSED, point, modifiers);
    }

    private void drag(int fromRow, int fromColumn, int... path) {
        press(at(fromRow, fromColumn), 0);
        Point last = at(fromRow, fromColumn);
        for (int index = 0; index < path.length; index += 2) {
            last = at(path[index], path[index + 1]);
            mouse(MouseEvent.MOUSE_DRAGGED, last, InputEvent.BUTTON1_DOWN_MASK);
        }
        mouse(MouseEvent.MOUSE_RELEASED, last, 0);
    }

    private void click(Point point) {
        click(point, 0);
    }

    private void click(Point point, int modifiers) {
        press(point, modifiers);
        mouse(MouseEvent.MOUSE_RELEASED, point, modifiers);
    }

    /** Hands a mouse event to the view's own listeners (dispatching it would need a screen). */
    private void mouse(int id, Point point, int modifiers) {
        MouseEvent event = new MouseEvent(view, id, System.currentTimeMillis(), modifiers, point.x, point.y, 1, false,
                MouseEvent.BUTTON1);
        for (MouseListener listener : view.getMouseListeners()) {
            if (listener.getClass().getName().startsWith("cl.almejo")) {
                switch (id) {
                    case MouseEvent.MOUSE_PRESSED -> listener.mousePressed(event);
                    case MouseEvent.MOUSE_RELEASED -> listener.mouseReleased(event);
                    default -> { }
                }
            }
        }
        for (MouseMotionListener listener : view.getMouseMotionListeners()) {
            if (listener.getClass().getName().startsWith("cl.almejo")) {
                switch (id) {
                    case MouseEvent.MOUSE_DRAGGED -> listener.mouseDragged(event);
                    case MouseEvent.MOUSE_MOVED -> listener.mouseMoved(event);
                    default -> { }
                }
            }
        }
    }

    private void key(int keyCode, int modifiers) {
        Action action = boundAction(KeyStroke.getKeyStroke(keyCode, modifiers));
        assertThat(action.isEnabled()).as("enabled: %s", KeyEvent.getKeyText(keyCode)).isTrue();
        action.actionPerformed(new ActionEvent(view, ActionEvent.ACTION_PERFORMED, ""));
    }

    private Action boundAction(KeyStroke stroke) {
        Object name = view.getInputMap(JComponent.WHEN_FOCUSED).get(stroke);
        assertThat(name).as("bound: %s", stroke).isNotNull();
        return view.getActionMap().get(name);
    }

    @Test
    void aDragDrawsTheRectangleCoveringBothCells() {
        drag(1, 1, 2, 2);
        assertThat(groups()).containsExactly(layout().drawn(1, 1, 2, 2));
        drag(2, 3, 0, 1); // dragging up and left: the first cell moves to the pointer
        assertThat(groups().get(1)).isEqualTo(layout().drawn(0, 1, 3, 3));
        assertThat(practice.selected()).isEmpty();
    }

    @Test
    void draggingOutOfAnEdgeGoesOnFromTheOppositeEdgeOnTheFourByFourMap() {
        drag(1, 3, 1, 4);
        drag(0, 0, 0, -1);
        drag(3, 1, 4, 1);
        drag(0, 2, -1, 2);
        assertThat(groups()).containsExactly(layout().drawn(1, 3, 1, 2), layout().drawn(0, 3, 1, 2),
                layout().drawn(3, 1, 2, 1), layout().drawn(3, 2, 2, 1));
        assertThat(groups().get(0).rectangles()).hasSize(2);
    }

    @Test
    void draggingOutOfAnEdgeWrapsOnTheTwoByFourMap() {
        withInputs(3);
        drag(0, 0, 0, -1);
        drag(1, 0, 2, 0); // two rows: the whole axis
        drag(1, 3, 2, 4);
        assertThat(groups()).containsExactly(layout().drawn(0, 3, 1, 2), layout().drawn(0, 0, 2, 1),
                layout().drawn(0, 3, 2, 2));
    }

    @Test
    void draggingBackAndForthAcrossAnEdgeFollowsThePointerAndCapsAtTheMap() {
        press(at(1, 0), 0);
        mouse(MouseEvent.MOUSE_DRAGGED, at(1, -1), InputEvent.BUTTON1_DOWN_MASK);
        assertThat(view.pendingRectangle()).contains(layout().drawn(1, 3, 1, 2));
        mouse(MouseEvent.MOUSE_DRAGGED, at(1, 1), InputEvent.BUTTON1_DOWN_MASK);
        assertThat(view.pendingRectangle()).contains(layout().drawn(1, 0, 1, 2));
        mouse(MouseEvent.MOUSE_DRAGGED, at(1, -2), InputEvent.BUTTON1_DOWN_MASK);
        assertThat(view.pendingRectangle()).contains(layout().drawn(1, 2, 1, 3));
        mouse(MouseEvent.MOUSE_DRAGGED, at(1, -6), InputEvent.BUTTON1_DOWN_MASK);
        assertThat(view.pendingRectangle()).contains(layout().drawn(1, 0, 1, 4));
        mouse(MouseEvent.MOUSE_DRAGGED, at(1, -2), InputEvent.BUTTON1_DOWN_MASK);
        mouse(MouseEvent.MOUSE_RELEASED, at(1, -2), 0);
        assertThat(groups()).containsExactly(layout().drawn(1, 2, 1, 3)); // 3 wide: drawn anyway (red)
        assertThat(view.pendingRectangle()).isEmpty();
    }

    @Test
    void aClickAddsOneCellSelectsAGroupAndClearsOnNothingAndSuprDeletes() {
        click(at(2, 1));
        assertThat(groups()).containsExactly(layout().drawn(2, 1, 1, 1));
        drag(0, 0, 1, 1);

        click(at(2, 1));
        assertThat(groups()).hasSize(2);
        assertThat(practice.selected()).isEqualTo(OptionalInt.of(0));
        click(at(0, 0));
        assertThat(practice.selected()).isEqualTo(OptionalInt.of(1));

        click(new Point(2, 2)); // the corner header: nothing
        assertThat(practice.selected()).isEmpty();
        assertThat(boundAction(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0)).isEnabled()).isFalse();

        click(at(1, 1));
        key(KeyEvent.VK_DELETE, 0);
        assertThat(groups()).containsExactly(layout().drawn(2, 1, 1, 1));
        assertThat(practice.selected()).isEmpty();
    }

    @Test
    void theSmallestGroupUnderTheClickIsSelected() {
        click(at(1, 1));
        drag(0, 0, 3, 3);
        click(at(1, 1));
        assertThat(groups()).hasSize(2);
        assertThat(practice.selected()).isEqualTo(OptionalInt.of(0));
        click(at(2, 2));
        assertThat(practice.selected()).isEqualTo(OptionalInt.of(1));
    }

    @Test
    void ctrlClickOnACornerAddsTheFourCorners() {
        click(at(1, 1), InputEvent.CTRL_DOWN_MASK);
        assertThat(groups()).isEmpty();
        click(at(3, 0), InputEvent.CTRL_DOWN_MASK);
        assertThat(groups()).containsExactly(layout().corners().orElseThrow());
        assertThat(groups().getFirst().cells()).hasSize(4);
    }

    @Test
    void ctrlClickOnACornerOfTheTwoByFourMapAddsTheEdgeColumnsAndDoesNothingOnTwoByTwo() {
        withInputs(3);
        click(at(1, 3), InputEvent.CTRL_DOWN_MASK);
        assertThat(groups()).containsExactly(layout().corners().orElseThrow());

        withInputs(2);
        click(at(0, 0), InputEvent.CTRL_DOWN_MASK);
        assertThat(groups()).isEmpty();
    }

    @Test
    void theKeyboardMovesACursorExtendsARectangleWithWrapAndAddsIt() {
        assertThat(view.cursor()).isEqualTo(new KarnaughLayout.GridCell(0, 0));
        key(KeyEvent.VK_LEFT, 0);
        key(KeyEvent.VK_UP, 0);
        assertThat(view.cursor()).isEqualTo(new KarnaughLayout.GridCell(3, 3));
        assertThat(boundAction(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0)).isEnabled()).isFalse();

        key(KeyEvent.VK_RIGHT, InputEvent.SHIFT_DOWN_MASK);
        assertThat(view.pendingRectangle()).contains(layout().drawn(3, 3, 1, 2));
        key(KeyEvent.VK_DOWN, InputEvent.SHIFT_DOWN_MASK);
        assertThat(view.pendingRectangle()).contains(layout().drawn(3, 3, 2, 2));
        key(KeyEvent.VK_ENTER, 0);
        assertThat(groups()).containsExactly(layout().corners().orElseThrow());
        assertThat(view.pendingRectangle()).isEmpty();

        key(KeyEvent.VK_RIGHT, 0);
        assertThat(view.cursor()).isEqualTo(new KarnaughLayout.GridCell(3, 0));
        key(KeyEvent.VK_LEFT, InputEvent.SHIFT_DOWN_MASK);
        assertThat(view.pendingRectangle()).contains(layout().drawn(3, 3, 1, 2));
        key(KeyEvent.VK_ESCAPE, 0);
        assertThat(view.pendingRectangle()).isEmpty();
        assertThat(groups()).hasSize(1);

        key(KeyEvent.VK_ENTER, 0); // no rectangle: the cursor's cell
        assertThat(groups().get(1)).isEqualTo(layout().drawn(3, 0, 1, 1));
    }

    @Test
    void aCursorLeftOutsideTheMapByFewerInputsGoesBackToTheFirstCell() {
        key(KeyEvent.VK_LEFT, 0);
        key(KeyEvent.VK_UP, 0);
        assertThat(view.cursor()).isEqualTo(new KarnaughLayout.GridCell(3, 3));
        key(KeyEvent.VK_RIGHT, InputEvent.SHIFT_DOWN_MASK);

        model.setInputCount(3);
        view.refreshPractice();

        assertThat(view.cursor()).isEqualTo(new KarnaughLayout.GridCell(0, 0));
        assertThat(view.pendingRectangle()).isEmpty();
        key(KeyEvent.VK_ENTER, 0);
        assertThat(groups()).containsExactly(layout().drawn(0, 0, 1, 1));
        view.paint(new BufferedImage(400, 400, BufferedImage.TYPE_INT_ARGB).createGraphics());
    }

    @Test
    void aCursorLeftOutsideTheMapIsFixedEvenBeforeTheMapIsRefreshed() {
        key(KeyEvent.VK_LEFT, 0);
        key(KeyEvent.VK_RIGHT, InputEvent.SHIFT_DOWN_MASK);
        model.setInputCount(2);
        assertThat(view.pendingRectangle()).isEmpty();
        key(KeyEvent.VK_ENTER, 0);
        assertThat(groups()).containsExactly(layout().drawn(0, 0, 1, 1));
    }

    @Test
    void enterAddsAGroupOnlyWhileTheKeyboardIsInUseSoAfterAClickItReachesSiguiente() {
        Action enter = boundAction(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));
        assertThat(enter.isEnabled()).isFalse();
        click(at(1, 1));
        assertThat(enter.isEnabled()).isFalse();
        key(KeyEvent.VK_RIGHT, 0);
        assertThat(enter.isEnabled()).isTrue();
        click(new Point(2, 2));
        assertThat(enter.isEnabled()).isFalse();
    }

    @Test
    void escapeLetsTheSelectedGroupGoBeforeItReachesTheWindow() {
        Action escape = boundAction(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
        click(at(1, 1));
        assertThat(escape.isEnabled()).isFalse();
        click(at(1, 1));
        assertThat(practice.selected()).isEqualTo(OptionalInt.of(0));
        assertThat(escape.isEnabled()).isTrue();

        key(KeyEvent.VK_ESCAPE, 0);

        assertThat(practice.selected()).isEmpty();
        assertThat(groups()).hasSize(1);
        assertThat(escape.isEnabled()).isFalse();
    }

    @Test
    void theSmallSolutionMapEmphasisesNoGroupAndIgnoresTheMouseOnGroups() {
        model.setValue(0, 6, LogicValue.ONE);
        model.setValue(0, 7, LogicValue.ONE);
        HoverState hover = new HoverState();
        KarnaughMapView solution = new KarnaughMapView(model, hover, 0, true);
        solution.setSize(400, 400);
        solution.showSolutionOnly();
        assertThat(solution.explainedGroup()).isEmpty();

        KarnaughLayout.GridCell six = layout().cell(6);
        MapGeometry geometry = solution.geometry();
        Point inside = new Point(geometry.grid().x + six.column() * geometry.cellSize() + geometry.cellSize() / 2,
                geometry.grid().y + six.row() * geometry.cellSize() + geometry.cellSize() / 2);
        solution.pointAt(inside);
        solution.clickAt(inside);
        assertThat(hover.pinnedGroup()).isEqualTo(-1);
        assertThat(solution.explainedGroup()).isEmpty();
    }

    @Test
    void theAntsRunOnlyWhileSomethingIsOutlinedAndTheMapShows() {
        assertThat(ticker.isRunning()).isFalse();
        click(at(0, 0));
        assertThat(ticker.isRunning()).isFalse(); // a new group is not selected

        press(at(1, 1), 0);
        mouse(MouseEvent.MOUSE_DRAGGED, at(1, 2), InputEvent.BUTTON1_DOWN_MASK);
        assertThat(ticker.isRunning()).isTrue();
        mouse(MouseEvent.MOUSE_RELEASED, at(1, 2), 0);
        assertThat(ticker.isRunning()).isFalse();

        click(at(0, 0));
        assertThat(ticker.isRunning()).isTrue();
        double phase = view.antsPhase();
        ticker.advance(16); // one frame (seven bring the dashes back where they were)
        assertThat(view.antsPhase()).isNotEqualTo(phase);

        practice.clearSelection();
        view.refreshPractice();
        ticker.advance(16);
        assertThat(ticker.isRunning()).isFalse();

        click(at(0, 0));
        view.setShowingCheck(() -> false);
        view.refreshPractice();
        assertThat(ticker.isRunning()).isFalse();
        view.setShowingCheck(() -> true);
        view.refreshPractice();
        assertThat(ticker.isRunning()).isTrue();

        practice.setPractising(false);
        view.refreshPractice();
        assertThat(ticker.isRunning()).isFalse();
    }

    @Test
    void theKeyboardCursorIsOutlinedUntilTheMouseTakesOver() {
        key(KeyEvent.VK_RIGHT, 0);
        assertThat(ticker.isRunning()).isTrue();
        click(new Point(2, 2));
        assertThat(ticker.isRunning()).isFalse();
    }

    @Test
    void theSolutionModeIsUnchanged() {
        model.setValue(0, 6, LogicValue.ONE);
        model.setValue(0, 7, LogicValue.ONE);
        practice.add(0, SUM_OF_PRODUCTS, layout().drawn(0, 0, 1, 1));
        assertThat(view.drawnGroups()).containsExactly(layout().drawn(0, 0, 1, 1));

        practice.setPractising(false);
        view.refreshPractice();
        assertThat(view.drawnGroups()).hasSize(1);
        assertThat(view.drawnGroups().getFirst().cells()).hasSize(2); // VSim's group of 6 and 7
        assertThat(boundAction(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0)).isEnabled()).isFalse();
        assertThat(view.isFocusable()).isFalse();
        KarnaughLayout.GridCell six = layout().cell(6);
        click(at(six.row(), six.column()));
        assertThat(groups()).hasSize(1);
    }

    @Test
    void aSolutionOnlyMapShowsVsimsGroupsWhilePractising() {
        model.setValue(0, 6, LogicValue.ONE);
        model.setValue(0, 7, LogicValue.ONE);
        view.showSolutionOnly();
        view.refreshPractice();
        assertThat(view.drawnGroups()).hasSize(1);
        assertThat(view.isFocusable()).isFalse();
        click(at(0, 0));
        assertThat(groups()).isEmpty();
    }

    @Test
    void practicePaintsTheStudentsGroupsAndRingsTheUncoveredCells() {
        model.setValue(0, 6, LogicValue.ONE);
        model.setValue(0, 7, LogicValue.ONE);
        KarnaughLayout.GridCell six = layout().cell(6);
        KarnaughLayout.GridCell seven = layout().cell(7);
        Color before = pixelNearTheBottom(six);

        practice.add(0, SUM_OF_PRODUCTS, layout().drawn(six.row(), six.column(), 1, 1));
        assertThat(pixelNearTheBottom(six)).isNotEqualTo(before);
        assertThat(practice.result(0, SUM_OF_PRODUCTS).uncoveredRows()).containsExactly(7);
        assertThat(ringPixel(seven)).isNotEqualTo(ringPixel(layout().cell(0)));

        practice.select(0);
        practice.pressHint();
        practice.pressHint();
        view.refreshPractice();
        paint(); // every overlay at once paints without trouble
        assertThat(model.options().form()).isEqualTo(Minimization.Form.SUM_OF_PRODUCTS);
    }

    private BufferedImage paint() {
        BufferedImage image = new BufferedImage(400, 400, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        view.paint(graphics);
        graphics.dispose();
        return image;
    }

    private Color pixelNearTheBottom(KarnaughLayout.GridCell cell) {
        Rectangle area = view.geometry().cell(cell.row(), cell.column());
        return new Color(paint().getRGB((int) area.getCenterX(), area.y + area.height - 7), true);
    }

    /** A pixel on the ring of an uncovered cell (its left edge, half way down). */
    private Color ringPixel(KarnaughLayout.GridCell cell) {
        Rectangle area = view.geometry().cell(cell.row(), cell.column());
        Rectangle ring = KarnaughMapView.ringBounds(area);
        BufferedImage image = paint();
        for (int x = ring.x - 1; x <= ring.x + 1; x++) {
            for (int y = (int) area.getCenterY() - 3; y <= area.getCenterY() + 3; y++) {
                Color color = new Color(image.getRGB(x, y), true);
                if (!color.equals(new Color(image.getRGB(area.x + 3, area.y + area.height - 3), true))) {
                    return color;
                }
            }
        }
        return new Color(image.getRGB(ring.x, (int) area.getCenterY()), true);
    }
}
