package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.command.AddWireCommand;
import cl.almejo.vsim.core.command.CreateFrameCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.SnapResult;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.frames.FrameTool;
import cl.almejo.vsim.ui.frames.FramesExtension;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.snap.MovingSet;
import cl.almejo.vsim.ui.snap.PointResolution;
import cl.almejo.vsim.ui.snap.Snapper;
import cl.almejo.vsim.ui.tools.PanTool;
import cl.almejo.vsim.ui.tools.ToolContext;
import cl.almejo.vsim.ui.tools.WireTool;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.panes.CanvasPanes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static cl.almejo.vsim.ui.canvas.CanvasDragSupport.differingPixels;
import static cl.almejo.vsim.ui.canvas.CanvasDragSupport.paint;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A gesture started in one pane of a window and released over another (split views, widened by the user): the pane
 * pressed keeps the gesture, the pointer's grid point comes from the pane under it, the preview shows in every pane
 * and the release is one undo step. Headless: the events go straight to the pressed canvas's listeners, with points
 * in its own coordinates, as Swing delivers a drag.
 */
class CrossPaneGesturesTest {

    private OpenDocument openDocument;
    private EditorView view;
    private CircuitCanvas left;
    private CircuitCanvas right;

    @BeforeEach
    void open() {
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        EditorServices services = EditorServices.of(PainterRegistry.standard(), ColorScheme.LIGHT)
                .withExtensions(List.of(new FramesExtension()));
        view = new EditorView(openDocument, services, NO_WINDOW);
        CanvasPanes panes = view.canvasPanes();
        panes.setSize(800, 300);
        panes.split(CanvasPanes.Orientation.VERTICAL, 0.5);
        panes.doLayout();
        left = view.panes().get(0);
        right = view.panes().get(1);
        left.applyViewState(new ViewState(1, left.getWidth() / 16.0, left.getHeight() / 16.0)); // grid 0 at the corner
        right.applyViewState(new ViewState(1, 100, 20)); // another place of the circuit
        left.useManualAutoscrollClock();
        right.useManualAutoscrollClock();
    }

    @AfterEach
    void close() {
        view.dispose();
        openDocument.close();
    }

    // --- the hand-off -------------------------------------------------------------------------------------------

    @Test
    void aWireDrawnWithTheWireToolEndsWhereThePointerIsInTheOtherPaneAsOneStep() {
        view.setToolOnEveryPane(WireTool::new);
        press(left, 40, 40, 0);
        dragOverRight(100, 96, 0);
        releaseOverRight(100, 96, 0);

        GridPoint end = right.viewport().snap(100, 96);
        assertThat(openDocument.document().wires()).extracting(Wire::start, Wire::end).containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple(new GridPoint(5, 5), new GridPoint(end.x(), 5)),
                org.assertj.core.groups.Tuple.tuple(new GridPoint(end.x(), 5), end));
        openDocument.history().undo();
        assertThat(openDocument.document().wires()).isEmpty();
    }

    @Test
    void aWireFromAWireWithSelectCrossesToo() {
        Wire existing = new Wire(openDocument.document().newWireId(), new GridPoint(5, 2), new GridPoint(5, 10));
        openDocument.execute(new AddWireCommand(existing));
        press(left, 40, 48, 0);
        dragOverRight(100, 96, 0);
        releaseOverRight(100, 96, 0);

        GridPoint end = right.viewport().snap(100, 96);
        assertThat(openDocument.document().wires()).anySatisfy(wire -> assertThat(wire.end()).isEqualTo(end));
    }

    @Test
    void movingBackOverTheFirstPaneHandsTheEndBack() {
        view.setToolOnEveryPane(WireTool::new);
        press(left, 40, 40, 0);
        dragOverRight(100, 96, 0);
        drag(left, 40, 120, 0);
        release(left, 40, 120, 0);

        assertThat(openDocument.document().wires()).singleElement()
                .satisfies(wire -> assertThat(Set.of(wire.start(), wire.end()))
                        .containsExactlyInAnyOrder(new GridPoint(5, 5), new GridPoint(5, 15)));
    }

    @Test
    void thePreviewShowsInBothPanesAndEscapeTakesItAwayFromBoth() {
        view.setToolOnEveryPane(WireTool::new);
        BufferedImage rightBefore = paint(right);
        press(left, 40, 40, 0);
        dragOverRight(100, 96, 0);
        BufferedImage rightDuring = paint(right);
        assertThat(differingPixels(rightBefore, rightDuring, 0, 0, right.getWidth(), right.getHeight()))
                .isGreaterThan(0);

        assertThat(left.pressKeyOnTool(KeyEvent.VK_ESCAPE, false)).isTrue();
        assertThat(differingPixels(rightBefore, paint(right), 0, 0, right.getWidth(), right.getHeight()))
                .isZero();
        releaseOverRight(100, 96, 0);
        assertThat(openDocument.document().wires()).isEmpty();
    }

    @Test
    void theEndSnapsWithThePaneUnderThePointer() {
        view.setToolOnEveryPane(WireTool::new);
        List<ToolContext> asked = new ArrayList<>();
        right.useSnapper(recordingSnapper(asked));
        press(left, 40, 40, 0);
        dragOverRight(100, 96, 0);

        assertThat(asked).isNotEmpty();
        assertThat(asked.get(asked.size() - 1).viewport()).isSameAs(right.viewport());
    }

    @Test
    void theFirstPaneStopsAutoscrollingWhileThePointerIsOverTheOther() {
        Component gate = place(openDocument, StandardComponentTypes.NOT, 5, 5, Map.of());
        left.selection().select(gate.id());
        press(left, 56, 56, 0);
        drag(left, left.getWidth() - 4, 56, 0);
        assertThat(left.isAutoscrolling()).isTrue();
        dragOverRight(100, 96, 0);
        assertThat(left.isAutoscrolling()).isFalse();
        release(left, left.getWidth() - 4, 56, 0); // back in the first pane: as today
    }

    // --- every gesture ------------------------------------------------------------------------------------------

    @Test
    void aRubberBandFromOnePaneToTheOtherSelectsTheDocumentRectangle() {
        Component near = place(openDocument, StandardComponentTypes.NOT, 5, 5, Map.of());
        Component far = place(openDocument, StandardComponentTypes.NOT, 90, 10, Map.of());
        Component outside = place(openDocument, StandardComponentTypes.NOT, 5, 60, Map.of());
        press(left, 8, 8, 0);
        dragOverRight(300, 250, 0);
        releaseOverRight(300, 250, 0);

        assertThat(left.selection().ids()).containsExactlyInAnyOrder(near.id(), far.id())
                .doesNotContain(outside.id());
    }

    @Test
    void movingTheSelectionIntoTheOtherPaneMovesItThereAsOneStep() {
        Component gate = place(openDocument, StandardComponentTypes.NOT, 5, 5, Map.of());
        left.selection().select(gate.id());
        GridPoint pressed = left.viewport().snap(56, 56);
        press(left, 56, 56, 0);
        dragOverRight(100, 96, 0);
        assertThat(left.isDraggingOut()).isFalse();
        releaseOverRight(100, 96, 0);

        GridPoint dropped = right.viewport().snap(100, 96);
        assertThat(openDocument.document().component(gate.id()).orElseThrow().position())
                .isEqualTo(new GridPoint(5 + dropped.x() - pressed.x(), 5 + dropped.y() - pressed.y()));
        openDocument.history().undo();
        assertThat(openDocument.document().component(gate.id()).orElseThrow().position())
                .isEqualTo(new GridPoint(5, 5));
    }

    @Test
    void ctrlAtTheReleaseOverTheOtherPaneCopies() {
        Component gate = place(openDocument, StandardComponentTypes.NOT, 5, 5, Map.of());
        left.selection().select(gate.id());
        press(left, 56, 56, 0);
        dragOverRight(100, 96, InputEvent.CTRL_DOWN_MASK);
        releaseOverRight(100, 96, InputEvent.CTRL_DOWN_MASK);

        GridPoint dropped = right.viewport().snap(100, 96);
        GridPoint pressed = left.viewport().snap(56, 56);
        assertThat(openDocument.document().components()).extracting(Component::position).containsExactlyInAnyOrder(
                new GridPoint(5, 5), new GridPoint(5 + dropped.x() - pressed.x(), 5 + dropped.y() - pressed.y()));
    }

    @Test
    void theSegmentKeySlidesAWireIntoTheOtherPane() {
        Wire wire = new Wire(openDocument.document().newWireId(), new GridPoint(2, 5), new GridPoint(10, 5));
        openDocument.execute(new AddWireCommand(wire));
        left.selection().select(wire.id());
        press(left, 48, 40, InputEvent.ALT_DOWN_MASK);
        dragOverRight(100, 96, InputEvent.ALT_DOWN_MASK);
        releaseOverRight(100, 96, InputEvent.ALT_DOWN_MASK);

        GridPoint dropped = right.viewport().snap(100, 96);
        assertThat(openDocument.document().wires()).anySatisfy(moved -> {
            assertThat(moved.start().y()).isEqualTo(dropped.y());
            assertThat(moved.end().y()).isEqualTo(dropped.y());
        });
    }

    @Test
    void theFrameToolDrawsAFrameFromOnePaneToTheOther() {
        view.setToolOnEveryPane(FrameTool::new);
        press(left, 16, 16, 0);
        dragOverRight(100, 96, 0);
        releaseOverRight(100, 96, 0);

        GridPoint corner = right.viewport().snap(100, 96);
        assertThat(openDocument.document().frames()).singleElement().satisfies(frame -> {
            assertThat(frame.topLeft()).isEqualTo(new GridPoint(2, 2));
            assertThat(frame.width()).isEqualTo(corner.x() - 2);
            assertThat(frame.height()).isEqualTo(corner.y() - 2);
        });
    }

    @Test
    void aFrameMovedByItsTitleBarLandsInTheOtherPane() {
        Frame frame = addFrame();
        GridPoint pressed = left.viewport().snap(80, 26);
        press(left, 80, 26, 0);
        dragOverRight(100, 96, 0);
        releaseOverRight(100, 96, 0);

        GridPoint dropped = right.viewport().snap(100, 96);
        assertThat(openDocument.document().frame(frame.id()).orElseThrow().topLeft())
                .isEqualTo(new GridPoint(2 + dropped.x() - pressed.x(), 2 + dropped.y() - pressed.y()));
    }

    @Test
    void aLockedFrameMovedIntoTheOtherPaneCarriesItsBlock() {
        Frame frame = new Frame(openDocument.document().newFrameId(), new GridPoint(2, 2), 20, 10, "Bloque",
                FrameTint.NONE, true);
        openDocument.execute(new CreateFrameCommand(frame));
        Component inside = place(openDocument, StandardComponentTypes.NOT, 6, 6, Map.of());
        GridPoint pressed = left.viewport().snap(80, 26);
        press(left, 80, 26, 0);
        dragOverRight(100, 96, 0);
        releaseOverRight(100, 96, 0);

        GridPoint dropped = right.viewport().snap(100, 96);
        int deltaX = dropped.x() - pressed.x();
        int deltaY = dropped.y() - pressed.y();
        assertThat(openDocument.document().frame(frame.id()).orElseThrow().topLeft())
                .isEqualTo(new GridPoint(2 + deltaX, 2 + deltaY));
        assertThat(openDocument.document().component(inside.id()).orElseThrow().position())
                .isEqualTo(new GridPoint(6 + deltaX, 6 + deltaY));
        openDocument.history().undo();
        assertThat(openDocument.document().component(inside.id()).orElseThrow().position())
                .isEqualTo(new GridPoint(6, 6));
    }

    @Test
    void aFrameResizedByItsCornerReachesIntoTheOtherPane() {
        Frame frame = addFrame();
        press(left, 176, 96, 0);
        dragOverRight(100, 96, 0);
        releaseOverRight(100, 96, 0);

        GridPoint corner = right.viewport().snap(100, 96);
        Frame resized = openDocument.document().frame(frame.id()).orElseThrow();
        assertThat(resized.topLeft()).isEqualTo(new GridPoint(2, 2));
        assertThat(resized.width()).isEqualTo(corner.x() - 2);
        assertThat(resized.height()).isEqualTo(corner.y() - 2);
    }

    @Test
    void panningStaysInThePaneItStartedIn() {
        view.setToolOnEveryPane(PanTool::new);
        ViewState rightView = right.viewState();
        press(left, 40, 40, 0);
        dragOverRight(100, 96, 0);
        releaseOverRight(100, 96, 0);
        assertThat(right.viewState()).isEqualTo(rightView);
    }

    // --- helpers ------------------------------------------------------------------------------------------------

    private Frame addFrame() {
        Frame frame = new Frame(openDocument.document().newFrameId(), new GridPoint(2, 2), 20, 10, "Bloque",
                FrameTint.NONE, false);
        openDocument.execute(new CreateFrameCommand(frame));
        return frame;
    }

    private static Snapper recordingSnapper(List<ToolContext> asked) {
        return new Snapper() {
            @Override
            public SnapResult snapBox(ToolContext context, MovingSet moving, int rawDeltaX, int rawDeltaY) {
                return SnapResult.unsnapped(rawDeltaX, rawDeltaY);
            }

            @Override
            public PointResolution snapPoint(ToolContext context, GridPoint point) {
                asked.add(context);
                return new PointResolution(point, PointResolution.Reason.GRID);
            }

            @Override
            public SnapResult snapWires(ToolContext context, Set<ElementId> wireIds, int rawDeltaX, int rawDeltaY) {
                return SnapResult.unsnapped(rawDeltaX, rawDeltaY);
            }
        };
    }

    /** A drag of the left pane's press to (x, y) of the right pane. */
    private void dragOverRight(int x, int y, int modifiers) {
        Point inLeft = SwingUtilities.convertPoint(right, x, y, left);
        drag(left, inLeft.x, inLeft.y, modifiers);
    }

    private void releaseOverRight(int x, int y, int modifiers) {
        Point inLeft = SwingUtilities.convertPoint(right, x, y, left);
        release(left, inLeft.x, inLeft.y, modifiers);
    }

    private static void press(CircuitCanvas canvas, int x, int y, int modifiers) {
        MouseEvent event = new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 0,
                InputEvent.BUTTON1_DOWN_MASK | modifiers, x, y, 1, false, MouseEvent.BUTTON1);
        for (MouseListener listener : canvas.getMouseListeners()) {
            listener.mousePressed(event);
        }
    }

    private static void drag(CircuitCanvas canvas, int x, int y, int modifiers) {
        MouseEvent event = new MouseEvent(canvas, MouseEvent.MOUSE_DRAGGED, 0,
                InputEvent.BUTTON1_DOWN_MASK | modifiers, x, y, 1, false, MouseEvent.NOBUTTON);
        for (MouseMotionListener listener : canvas.getMouseMotionListeners()) {
            listener.mouseDragged(event);
        }
    }

    private static void release(CircuitCanvas canvas, int x, int y, int modifiers) {
        MouseEvent event = new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, 0, modifiers, x, y, 1, false,
                MouseEvent.BUTTON1);
        for (MouseListener listener : canvas.getMouseListeners()) {
            listener.mouseReleased(event);
        }
    }
}
