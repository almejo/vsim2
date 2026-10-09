package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.io.FragmentFormat;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.clipboard.FileListTransferable;
import cl.almejo.vsim.ui.clipboard.FragmentTransferable;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.panels.PaletteTransfer;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.tools.CanvasMouseEvent;
import cl.almejo.vsim.ui.tools.FloatingSelectionTool;
import cl.almejo.vsim.ui.tools.PanTool;
import cl.almejo.vsim.ui.tools.SelectTool;
import cl.almejo.vsim.ui.tools.Tool;
import cl.almejo.vsim.ui.tools.ToolContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.TransferHandler;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.io.File;
import java.nio.file.Path;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static cl.almejo.vsim.ui.UiTestSupport.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CircuitCanvasTest {

    private final OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private final List<Optional<DocumentElement>> menus = new ArrayList<>();
    private final CircuitCanvas canvas = new CircuitCanvas(openDocument,
            new CircuitRenderer(openDocument.registry(), PainterRegistry.standard(), ColorScheme.LIGHT),
            new CanvasHost() {
                @Override
                public void showContextMenu(CircuitCanvas source, Optional<DocumentElement> target, Point2D screenPosition) {
                    menus.add(target);
                }

                @Override
                public void configure(Component component) {
                }
            });

    /** Headless, the canvas never shows: the tests decide whether it counts as on screen (review M9). */
    private boolean onScreen = true;

    @BeforeEach
    void showOnScreen() {
        canvas.overrideOnScreen(() -> onScreen);
        canvas.useManualAutoscrollClock();
    }

    @AfterEach
    void close() {
        canvas.dispose();
        openDocument.close();
    }

    private static final class RecordingTool implements Tool {
        final List<CanvasMouseEvent> pressed = new ArrayList<>();
        final List<Integer> keys = new ArrayList<>();

        @Override
        public void mousePressed(ToolContext context, CanvasMouseEvent event) {
            pressed.add(event);
        }

        @Override
        public boolean keyPressed(ToolContext context, int keyCode, boolean shiftDown) {
            keys.add(keyCode);
            return keyCode == KeyEvent.VK_ESCAPE;
        }
    }

    private MouseEvent mouse(int id, int x, int y, int modifiers, int button) {
        return new MouseEvent(canvas, id, 0, modifiers, x, y, 1, false, button);
    }

    @Test
    void pressReachesTheToolInGridCoordinates() {
        RecordingTool tool = new RecordingTool();
        canvas.setTool(tool);
        canvas.getMouseListeners()[0].mousePressed(mouse(MouseEvent.MOUSE_PRESSED, 17, 23, InputEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1));
        CanvasMouseEvent event = tool.pressed.get(0);
        assertThat(event.gridPosition().getX()).isCloseTo(17 / 8.0, within(1e-9));
        assertThat(event.snapped().x()).isEqualTo(2);
        assertThat(event.snapped().y()).isEqualTo(3);
        assertThat(event.button()).isEqualTo(CanvasMouseEvent.PRIMARY);
    }

    @Test
    void middleDragPansInsteadOfUsingTheTool() {
        RecordingTool tool = new RecordingTool();
        canvas.setTool(tool);
        canvas.getMouseListeners()[0].mousePressed(mouse(MouseEvent.MOUSE_PRESSED, 10, 10, InputEvent.BUTTON2_DOWN_MASK, MouseEvent.BUTTON2));
        canvas.getMouseMotionListeners()[0].mouseDragged(mouse(MouseEvent.MOUSE_DRAGGED, 30, 15, InputEvent.BUTTON2_DOWN_MASK, MouseEvent.NOBUTTON));
        canvas.getMouseListeners()[0].mouseReleased(mouse(MouseEvent.MOUSE_RELEASED, 30, 15, 0, MouseEvent.BUTTON2));
        assertThat(tool.pressed).isEmpty();
        assertThat(canvas.viewport().gridToScreen(0, 0)).isEqualTo(new Point2D.Double(20, 5));
    }

    @Test
    void controlWheelZoomsAroundTheCursorAndNotifiesViewListeners() {
        List<Double> zooms = new ArrayList<>();
        canvas.addViewListener(() -> zooms.add(canvas.viewport().zoom()));
        Point2D before = canvas.viewport().screenToGrid(100, 50);
        canvas.getMouseWheelListeners()[0].mouseWheelMoved(new MouseWheelEvent(canvas, MouseEvent.MOUSE_WHEEL, 0,
                InputEvent.CTRL_DOWN_MASK, 100, 50, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, -1));
        assertThat(canvas.viewport().zoom()).isCloseTo(1.25, within(1e-9));
        assertThat(canvas.viewport().screenToGrid(100, 50).getX()).isCloseTo(before.getX(), within(1e-9));
        assertThat(zooms).containsExactly(1.25);
    }

    @Test
    void losingFocusForgetsSpaceAndAPanInProgress() {
        RecordingTool tool = new RecordingTool();
        canvas.setTool(tool);
        canvas.getKeyListeners()[0].keyPressed(new KeyEvent(canvas, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_SPACE, ' '));
        canvas.getFocusListeners()[0].focusLost(new java.awt.event.FocusEvent(canvas, java.awt.event.FocusEvent.FOCUS_LOST));
        canvas.getMouseListeners()[0].mousePressed(mouse(MouseEvent.MOUSE_PRESSED, 10, 10, InputEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1));
        assertThat(tool.pressed).hasSize(1);

        canvas.getMouseListeners()[0].mousePressed(mouse(MouseEvent.MOUSE_PRESSED, 10, 10, InputEvent.BUTTON2_DOWN_MASK, MouseEvent.BUTTON2));
        canvas.getFocusListeners()[0].focusLost(new java.awt.event.FocusEvent(canvas, java.awt.event.FocusEvent.FOCUS_LOST));
        canvas.getMouseMotionListeners()[0].mouseDragged(mouse(MouseEvent.MOUSE_DRAGGED, 30, 15, InputEvent.BUTTON2_DOWN_MASK, MouseEvent.NOBUTTON));
        assertThat(canvas.viewport().gridToScreen(0, 0)).isEqualTo(new Point2D.Double(0, 0));
    }

    @Test
    void keysGoToTheToolFirst() {
        RecordingTool tool = new RecordingTool();
        canvas.setTool(tool);
        KeyEvent escape = new KeyEvent(canvas, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED);
        canvas.getKeyListeners()[0].keyPressed(escape);
        assertThat(tool.keys).containsExactly(KeyEvent.VK_ESCAPE);
        assertThat(escape.isConsumed()).isTrue();
    }

    @Test
    void toolChangesAreAnnouncedAndUseDefaultToolRestoresSelection() {
        List<Tool> announced = new ArrayList<>();
        canvas.addToolListener(announced::add);
        PanTool pan = new PanTool();
        canvas.setTool(pan);
        canvas.useDefaultTool();
        assertThat(announced).containsExactly(pan, canvas.defaultTool());
        assertThat(canvas.tool()).isSameAs(canvas.defaultTool());
    }

    @Test
    void rightClickAsksTheHostForAContextMenu() {
        Component gate = place(openDocument, StandardComponentTypes.NOT, 0, 0, Map.of());
        canvas.getMouseListeners()[0].mousePressed(mouse(MouseEvent.MOUSE_PRESSED, 16, 16, InputEvent.BUTTON3_DOWN_MASK, MouseEvent.BUTTON3));
        assertThat(menus).containsExactly(Optional.of(gate));
    }

    @Test
    void refreshSnapshotPublishesEachNewSnapshotOnce() {
        List<SimulationSnapshot> received = new ArrayList<>();
        canvas.addSnapshotListener(received::add);
        waitUntil(() -> openDocument.latestSnapshot() != SimulationSnapshot.EMPTY);
        openDocument.close(); // stops the simulation thread, so the latest snapshot no longer changes
        canvas.refreshSnapshot();
        canvas.refreshSnapshot();
        assertThat(received).containsExactly(openDocument.latestSnapshot());
        assertThat(canvas.displayedSnapshot()).isSameAs(openDocument.latestSnapshot());
    }

    @Test
    void deletingTheSelectedElementPrunesTheSelection() {
        Component gate = place(openDocument, StandardComponentTypes.NOT, 0, 0, Map.of());
        canvas.selection().select(gate.id());
        openDocument.history().undo();
        assertThat(canvas.selection().isEmpty()).isTrue();
    }

    @Test
    void droppingAPaletteEntryPlacesItAtTheDropPoint() {
        canvas.setTool(new PanTool());
        assertThat(canvas.dropComponent(PaletteTransfer.encode("led"), new Point(41, 23))).isTrue();
        assertThat(openDocument.document().components()).singleElement()
                .satisfies(led -> assertThat(led.position()).isEqualTo(new GridPoint(5, 3)));
        assertThat(canvas.tool()).isSameAs(canvas.defaultTool());
        assertThat(openDocument.history().undoDescription()).contains("command.add");
    }

    @Test
    void dropOfForeignTextIsRejected() {
        assertThat(canvas.dropComponent("hello", new Point(10, 10))).isFalse();
        assertThat(canvas.dropComponent(PaletteTransfer.encode("no-such-type"), new Point(10, 10))).isFalse();
        assertThat(openDocument.document().isEmpty()).isTrue();
    }

    @Test
    void removingASelectedElementDropsItFromTheSelection() {
        Component gate = place(openDocument, StandardComponentTypes.NOT, 0, 0, Map.of());
        canvas.selection().select(gate.id());
        openDocument.history().undo();
        assertThat(canvas.selection().isEmpty()).isTrue();
    }

    @Test
    void switchingToolCommitsAFloatingFragment() {
        CircuitFragment fragment = new CircuitFragment(
                List.of(openDocument.registry().require(StandardComponentTypes.NOT)
                        .newComponent(new ElementId("c1"), GridPoint.ORIGIN, Rotation.R0, Map.of(), "")),
                List.of(), List.of());
        canvas.setTool(new FloatingSelectionTool(fragment, new GridPoint(3, 3)));
        canvas.setTool(new FloatingSelectionTool(fragment, new GridPoint(20, 3)));
        assertThat(openDocument.document().components()).hasSize(1);
        canvas.setTool(new PanTool());
        assertThat(openDocument.document().components()).hasSize(2);
        assertThat(openDocument.history().undoDescription()).contains("command.paste");
    }

    @Test
    void tracksTheGridPointUnderTheMouse() {
        canvas.setSize(400, 300);
        assertThat(canvas.mouseGridPoint()).isEmpty();
        canvas.getMouseMotionListeners()[0].mouseMoved(mouse(MouseEvent.MOUSE_MOVED, 80, 48, 0, MouseEvent.NOBUTTON));
        assertThat(canvas.mouseGridPoint()).contains(new GridPoint(10, 6));
        canvas.getMouseMotionListeners()[0].mouseDragged(
                mouse(MouseEvent.MOUSE_DRAGGED, 500, 48, InputEvent.BUTTON1_DOWN_MASK, MouseEvent.NOBUTTON));
        assertThat(canvas.mouseGridPoint()).isEmpty();
        canvas.getMouseMotionListeners()[0].mouseMoved(mouse(MouseEvent.MOUSE_MOVED, 80, 48, 0, MouseEvent.NOBUTTON));
        canvas.getMouseListeners()[0].mouseExited(mouse(MouseEvent.MOUSE_EXITED, 80, 48, 0, MouseEvent.NOBUTTON));
        assertThat(canvas.mouseGridPoint()).isEmpty();
    }

    /** Review M9: a hidden or minimized window does not tick; showing it again restarts the clock. */
    @Test
    void theAntsClockRunsOnlyWhileTheCanvasIsOnScreen() {
        onScreen = false;
        Component gate = place(openDocument, StandardComponentTypes.NOT, 0, 0, Map.of());
        canvas.selection().select(gate.id());
        assertThat(canvas.isAnimatingSelection()).isFalse();
        onScreen = true;
        canvas.overrideOnScreen(() -> onScreen);
        assertThat(canvas.isAnimatingSelection()).isTrue();
        onScreen = false;
        canvas.animationTick();
        assertThat(canvas.isAnimatingSelection()).isFalse();
        assertThat(canvas.selectionDashPhase()).isZero();
    }

    @Test
    void aCanvasThatWasNeverShownDoesNotAnimate() {
        CircuitCanvas hidden = new CircuitCanvas(openDocument,
                new CircuitRenderer(openDocument.registry(), PainterRegistry.standard(), ColorScheme.LIGHT),
                new CanvasHost() {
                    @Override
                    public void showContextMenu(CircuitCanvas source, Optional<DocumentElement> target,
                                                Point2D screenPosition) {
                    }

                    @Override
                    public void configure(Component component) {
                    }
                });
        Component gate = place(openDocument, StandardComponentTypes.NOT, 0, 0, Map.of());
        hidden.selection().select(gate.id());
        assertThat(hidden.isAnimatingSelection()).isFalse();
        hidden.dispose();
    }

    @Test
    void theAntsClockRunsOnlyWhileSomethingIsSelected() {
        Component gate = place(openDocument, StandardComponentTypes.NOT, 0, 0, Map.of());
        assertThat(canvas.isAnimatingSelection()).isFalse();
        canvas.selection().select(gate.id());
        assertThat(canvas.isAnimatingSelection()).isTrue();
        canvas.selection().clear();
        assertThat(canvas.isAnimatingSelection()).isFalse();
    }

    @Test
    void theAntsClockRunsWhileABandIsBeingDragged() {
        canvas.getMouseListeners()[0].mousePressed(mouse(MouseEvent.MOUSE_PRESSED, 300, 300, InputEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1));
        canvas.getMouseMotionListeners()[0].mouseDragged(mouse(MouseEvent.MOUSE_DRAGGED, 400, 380, InputEvent.BUTTON1_DOWN_MASK, MouseEvent.NOBUTTON));
        assertThat(canvas.isAnimatingSelection()).isTrue();
        canvas.getMouseListeners()[0].mouseReleased(mouse(MouseEvent.MOUSE_RELEASED, 400, 380, 0, MouseEvent.BUTTON1));
        assertThat(canvas.isAnimatingSelection()).isFalse();
    }

    @Test
    void aTickStopsTheClockWhenNothingIsLeftToAnimate() {
        canvas.getMouseListeners()[0].mousePressed(mouse(MouseEvent.MOUSE_PRESSED, 300, 300, InputEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1));
        assertThat(canvas.isAnimatingSelection()).isTrue();
        canvas.tool().keyPressed(canvas, KeyEvent.VK_ESCAPE, false); // the band ends without going through the canvas
        assertThat(canvas.isAnimatingSelection()).as("still running until the next tick").isTrue();
        canvas.animationTick();
        assertThat(canvas.isAnimatingSelection()).isFalse();
    }

    @Test
    void aTickAdvancesThePhaseWhileSomethingIsSelected() {
        Component gate = place(openDocument, StandardComponentTypes.NOT, 0, 0, Map.of());
        canvas.selection().select(gate.id());
        canvas.animationTick();
        assertThat(canvas.selectionDashPhase()).isEqualTo(1.0);
    }

    @Test
    void aDisposedCanvasNeverRestartsTheClock() {
        Component gate = place(openDocument, StandardComponentTypes.NOT, 0, 0, Map.of());
        canvas.selection().select(gate.id());
        canvas.dispose();
        assertThat(canvas.isAnimatingSelection()).isFalse();
        canvas.selection().clear();
        canvas.selection().select(gate.id());
        assertThat(canvas.isAnimatingSelection()).isFalse();
    }

    @Test
    void removingTheCanvasFromItsWindowStopsTheClock() {
        Component gate = place(openDocument, StandardComponentTypes.NOT, 0, 0, Map.of());
        canvas.selection().select(gate.id());
        canvas.removeNotify();
        assertThat(canvas.isAnimatingSelection()).isFalse();
    }

    private static String fragmentJsonOf(OpenDocument source, Component component) {
        return new FragmentFormat(source.registry())
                .write(CircuitFragment.of(source.document(), List.of(component.id()), source.registry()));
    }

    @Test
    void droppingAFragmentFromAnotherWindowFloatsItAtTheDropPoint() {
        try (OpenDocument source = OpenDocument.createEmpty(StandardComponentTypes.registry())) {
            Component gate = place(source, StandardComponentTypes.NOT, 3, 3, Map.of());
            assertThat(canvas.dropFragment(fragmentJsonOf(source, gate), new Point(80, 72))).isTrue();
            assertThat(canvas.tool()).isInstanceOfSatisfying(FloatingSelectionTool.class, floating -> {
                assertThat(floating.position()).isEqualTo(new GridPoint(10, 9));
                assertThat(floating.fragment().components()).hasSize(1);
            });
            assertThat(openDocument.document().isEmpty()).isTrue();
        }
    }

    @Test
    void theTransferHandlerTakesTheFragmentFlavorAndFragmentText() {
        canvas.setSize(400, 300);
        try (OpenDocument source = OpenDocument.createEmpty(StandardComponentTypes.registry())) {
            String json = fragmentJsonOf(source, place(source, StandardComponentTypes.NOT, 0, 0, Map.of()));
            TransferHandler handler = canvas.getTransferHandler();
            assertThat(handler.importData(new TransferHandler.TransferSupport(canvas, new FragmentTransferable(json))))
                    .isTrue();
            assertThat(canvas.tool()).isInstanceOfSatisfying(FloatingSelectionTool.class,
                    floating -> assertThat(floating.position()).isEqualTo(canvas.viewport().snap(200, 150)));
            canvas.useDefaultTool();
            assertThat(handler.importData(new TransferHandler.TransferSupport(canvas, new StringSelection(json))))
                    .isTrue();
            assertThat(canvas.tool()).isInstanceOf(FloatingSelectionTool.class);
        }
    }

    @Test
    void theTransferHandlerStillPlacesPaletteEntries() {
        canvas.setSize(400, 300);
        assertThat(canvas.getTransferHandler().importData(new TransferHandler.TransferSupport(canvas,
                new StringSelection(PaletteTransfer.encode("led"))))).isTrue();
        assertThat(openDocument.document().components()).hasSize(1);
    }

    @Test
    void droppedCircuitFilesGoToTheFileDropHandler() {
        List<Path> dropped = new ArrayList<>();
        canvas.setFileDropHandler(dropped::addAll);
        assertThat(canvas.getTransferHandler().importData(new TransferHandler.TransferSupport(canvas,
                new FileListTransferable(List.of(new File("/tmp/a.vsim"), new File("/tmp/b.txt")))))).isTrue();
        assertThat(dropped).containsExactly(Path.of("/tmp/a.vsim"));
    }

    @Test
    void dropOfForeignTextOrABrokenFragmentIsRefused() {
        assertThat(canvas.dropFragment("hello", new Point(10, 10))).isFalse();
        assertThat(canvas.dropFragment("{}", new Point(10, 10))).isFalse();
        assertThat(canvas.dropFragment("[1, 2]", new Point(10, 10))).isFalse();
        assertThat(canvas.getTransferHandler().importData(new TransferHandler.TransferSupport(canvas,
                new StringSelection("hello")))).isFalse();
        assertThat(canvas.tool()).isInstanceOf(SelectTool.class);
        assertThat(openDocument.document().isEmpty()).isTrue();
    }

    private void primaryPress(int x, int y) {
        canvas.getMouseListeners()[0].mousePressed(
                mouse(MouseEvent.MOUSE_PRESSED, x, y, InputEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1));
    }

    private void primaryDrag(int x, int y) {
        canvas.getMouseMotionListeners()[0].mouseDragged(
                mouse(MouseEvent.MOUSE_DRAGGED, x, y, InputEvent.BUTTON1_DOWN_MASK, MouseEvent.NOBUTTON));
    }

    private void primaryRelease(int x, int y) {
        canvas.getMouseListeners()[0].mouseReleased(mouse(MouseEvent.MOUSE_RELEASED, x, y, 0, MouseEvent.BUTTON1));
    }

    @Test
    void draggingTheSelectionOverAnotherWindowStartsTheDragOut() {
        canvas.setSize(400, 300);
        canvas.overrideWindowProbe(point -> true);
        Component gate = place(openDocument, StandardComponentTypes.NOT, 5, 5, Map.of());
        primaryPress(56, 56);
        primaryDrag(72, 64);
        primaryDrag(392, 64);
        assertThat(canvas.isAutoscrolling()).isTrue();
        primaryDrag(-16, 64);
        assertThat(canvas.isAutoscrolling()).isFalse(); // the drag out stops it, not only the release
        primaryRelease(-16, 64);
        assertThat(openDocument.document().component(gate.id()).orElseThrow().position())
                .isEqualTo(new GridPoint(5, 5));
        assertThat(openDocument.history().undoDescription()).contains("command.add");
        assertThat(canvas.selection().ids()).containsExactly(gate.id());
        assertThat(((SelectTool) canvas.tool()).movingFrom()).isEmpty();
        assertThat(canvas.isDraggingOut()).isFalse(); // headless Swing ends the drag at once (exportDone, NONE)
        assertThat(canvas.isAutoscrolling()).isFalse();
    }

    @Test
    void aMoveHeldAtTheEdgeScrollsTheViewAndKeepsMoving() {
        canvas.setSize(400, 300);
        Component gate = place(openDocument, StandardComponentTypes.NOT, 5, 5, Map.of());
        primaryPress(56, 56);
        primaryDrag(392, 64);
        assertThat(canvas.isAutoscrolling()).isTrue();
        canvas.autoscrollTick();
        assertThat(canvas.viewport().gridToScreen(0, 0)).isEqualTo(new Point2D.Double(-7, 0));
        primaryRelease(392, 64);
        assertThat(canvas.isAutoscrolling()).isFalse();
        assertThat(openDocument.document().component(gate.id()).orElseThrow().position())
                .isEqualTo(new GridPoint(48, 6));
        assertThat(openDocument.history().undoDescription()).contains("command.move");
    }

    @Test
    void aMovePastTheEdgeOverItsOwnWindowKeepsMovingInsteadOfDraggingOut() {
        canvas.setSize(400, 300);
        List<Point> asked = new ArrayList<>();
        canvas.overrideWindowProbe(point -> {
            asked.add(point);
            return false;
        });
        Component gate = place(openDocument, StandardComponentTypes.NOT, 5, 5, Map.of());
        primaryPress(56, 56);
        primaryDrag(72, 64);
        primaryDrag(-16, 64);
        assertThat(asked).containsExactly(new Point(-16, 64));
        assertThat(canvas.isDraggingOut()).isFalse();
        assertThat(((SelectTool) canvas.tool()).movingFrom()).isPresent();
        primaryRelease(-16, 64);
        assertThat(canvas.isAutoscrolling()).isFalse();
        assertThat(openDocument.document().component(gate.id()).orElseThrow().position())
                .isEqualTo(new GridPoint(-4, 6));
    }

    @Test
    void aRubberBandHeldAtTheEdgeScrollsAndReachesWhatWasOffScreen() {
        canvas.setSize(400, 300);
        Component gate = place(openDocument, StandardComponentTypes.NOT, 24, 36, Map.of());
        primaryPress(180, 150);
        primaryDrag(240, 295);
        assertThat(canvas.isAutoscrolling()).isTrue();
        for (int tick = 0; tick < 4; tick++) {
            canvas.autoscrollTick();
        }
        assertThat(canvas.viewport().gridToScreen(0, 0)).isEqualTo(new Point2D.Double(0, -32));
        primaryRelease(240, 295);
        assertThat(canvas.selection().ids()).containsExactly(gate.id());
    }

    @Test
    void comingBackInsideOrReleasingStopsTheAutoscroll() {
        canvas.setSize(400, 300);
        place(openDocument, StandardComponentTypes.NOT, 5, 5, Map.of());
        primaryPress(56, 56);
        primaryDrag(392, 64);
        assertThat(canvas.isAutoscrolling()).isTrue();
        primaryDrag(200, 150);
        assertThat(canvas.isAutoscrolling()).isFalse();
        primaryDrag(5, 150);
        assertThat(canvas.isAutoscrolling()).isTrue();
        primaryRelease(5, 150);
        assertThat(canvas.isAutoscrolling()).isFalse();
        Point2D origin = canvas.viewport().gridToScreen(0, 0);
        canvas.autoscrollTick();
        assertThat(canvas.viewport().gridToScreen(0, 0)).isEqualTo(origin);
    }

    @Test
    void aDisposedCanvasStopsAutoscrolling() {
        canvas.setSize(400, 300);
        place(openDocument, StandardComponentTypes.NOT, 5, 5, Map.of());
        primaryPress(56, 56);
        primaryDrag(392, 64);
        canvas.dispose();
        assertThat(canvas.isAutoscrolling()).isFalse();
    }

    @Test
    void theSelectionIsExportedAsAFragment() throws Exception {
        Component gate = place(openDocument, StandardComponentTypes.NOT, 5, 5, Map.of());
        CanvasTransferHandler handler = (CanvasTransferHandler) canvas.getTransferHandler();
        assertThat(handler.createTransferable(canvas)).isNull();
        canvas.selection().select(gate.id());
        Transferable transferable = handler.createTransferable(canvas);
        String json = (String) transferable.getTransferData(FragmentTransferable.FRAGMENT_FLAVOR);
        assertThat(new FragmentFormat(openDocument.registry()).read(json).components())
                .singleElement().satisfies(copy -> assertThat(copy.typeId()).isEqualTo(StandardComponentTypes.NOT));
        assertThat(handler.getSourceActions(canvas)).isEqualTo(TransferHandler.COPY);
    }

    @Test
    void droppingADraggedOutSelectionBackOnItsCanvasMovesIt() throws Exception {
        canvas.setSize(400, 300);
        Component gate = place(openDocument, StandardComponentTypes.NOT, 5, 5, Map.of());
        primaryPress(56, 56);
        primaryDrag(72, 64);
        assertThat(canvas.beginDragOut()).isTrue();
        Transferable transferable = ((CanvasTransferHandler) canvas.getTransferHandler()).createTransferable(canvas);
        String json = (String) transferable.getTransferData(FragmentTransferable.FRAGMENT_FLAVOR);
        assertThat(canvas.dropFragment(json, new Point(80, 72))).isTrue();
        assertThat(openDocument.document().component(gate.id()).orElseThrow().position())
                .isEqualTo(new GridPoint(8, 7));
        assertThat(openDocument.history().undoDescription()).contains("command.move");
        assertThat(canvas.tool()).isInstanceOf(SelectTool.class);
        assertThat(canvas.isDraggingOut()).isFalse();
        assertThat(openDocument.document().components()).hasSize(1);
    }

    @Test
    void overlaysPaintAfterTheCircuitInDocumentCoordinates() {
        List<RenderState> states = new ArrayList<>();
        List<AffineTransform> transforms = new ArrayList<>();
        canvas.addOverlay((graphics, state) -> {
            states.add(state);
            transforms.add(graphics.getTransform());
        });
        canvas.viewport().panBy(10, 20);
        canvas.setSize(100, 100);
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        canvas.paint(graphics);
        graphics.dispose();
        assertThat(states).singleElement()
                .satisfies(state -> assertThat(state.document()).isSameAs(openDocument.document()));
        assertThat(transforms.get(0).getTranslateX()).isEqualTo(10);
        assertThat(transforms.get(0).getTranslateY()).isEqualTo(20);
    }

    @Test
    void aFloatingFragmentWithItsOwnDefinitionPaintsEvenThoughTheDocumentDoesNotHoldIt() {
        ComponentDefinition inverter = ExampleDefinitions.inverter();
        Component instance = new Component(new ElementId("c1"), SubcircuitTypes.typeIdOf(inverter.id()),
                new GridPoint(0, 0), Rotation.R0, Map.of(), "");
        String json = new FragmentFormat(openDocument.registry())
                .write(new CircuitFragment(List.of(instance), List.of(), List.of(), List.of(inverter)));
        assertThat(openDocument.document().definition(inverter.id())).isEmpty();
        canvas.setSize(400, 300);
        assertThat(canvas.dropFragment(json, new Point(80, 72))).isTrue();

        BufferedImage image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            canvas.paint(graphics);
        } finally {
            graphics.dispose();
        }

        assertThat(openDocument.document().definition(inverter.id())).isEmpty();
    }

    @Test
    void centerOnPutsTheBoundsInTheMiddleAndNotifiesViewListeners() {
        List<Double> zooms = new ArrayList<>();
        canvas.addViewListener(() -> zooms.add(canvas.viewport().zoom()));
        canvas.setSize(400, 200);
        canvas.centerOn(new Bounds(10, 20, 4, 2));
        assertThat(canvas.viewport().gridToScreen(12, 21)).isEqualTo(new Point2D.Double(200, 100));
        assertThat(zooms).containsExactly(1.0);
    }

    @Test
    void tooltipComesFromTheProviderInGridCoordinates() {
        MouseEvent hover = mouse(MouseEvent.MOUSE_MOVED, 16, 24, 0, MouseEvent.NOBUTTON);
        assertThat(canvas.getToolTipText(hover)).isNull();
        List<Point2D> asked = new ArrayList<>();
        canvas.setToolTipProvider(gridPoint -> {
            asked.add(gridPoint);
            return gridPoint.getX() > 1 ? Optional.of("drivers") : Optional.empty();
        });
        assertThat(canvas.getToolTipText(hover)).isEqualTo("drivers");
        assertThat(asked).containsExactly(new Point2D.Double(2, 3));
        assertThat(canvas.getToolTipText(mouse(MouseEvent.MOUSE_MOVED, 4, 4, 0, MouseEvent.NOBUTTON))).isNull();
    }

    @Test
    void aStaleDragOutIsForgottenOnTheNextPress() {
        canvas.setSize(400, 300);
        Component gate = place(openDocument, StandardComponentTypes.NOT, 5, 5, Map.of());
        primaryPress(56, 56);
        primaryDrag(72, 64);
        assertThat(canvas.beginDragOut()).isTrue(); // and the toolkit never calls exportDone
        primaryPress(300, 250);
        primaryRelease(300, 250);
        assertThat(canvas.isDraggingOut()).isFalse();
        try (OpenDocument source = OpenDocument.createEmpty(StandardComponentTypes.registry())) {
            Component other = place(source, StandardComponentTypes.NOT, 0, 0, Map.of());
            assertThat(canvas.dropFragment(fragmentJsonOf(source, other), new Point(160, 160))).isTrue();
        }
        assertThat(canvas.tool()).isInstanceOf(FloatingSelectionTool.class);
        assertThat(openDocument.document().component(gate.id()).orElseThrow().position())
                .isEqualTo(new GridPoint(5, 5));
    }

    @Test
    void noDragOutBeginsWithoutAFragmentToTransfer() {
        canvas.setSize(400, 300);
        place(openDocument, StandardComponentTypes.NOT, 5, 5, Map.of());
        primaryPress(56, 56);
        primaryDrag(72, 64);
        canvas.selection().set(List.of(new ElementId("c999"))); // nothing that a fragment could carry
        assertThat(canvas.beginDragOut()).isFalse();
        assertThat(canvas.isDraggingOut()).isFalse();
        assertThat(((SelectTool) canvas.tool()).movingFrom()).isPresent();
    }

    @Test
    void aFragmentTheReaderChokesOnIsRefusedWithoutAnException() {
        // Valid JSON whose bounds overflow an int: the reader throws an unchecked exception, not a format error.
        String overflowing = "{\"formatVersion\":2,\"kind\":\"fragment\",\"components\":["
                + "{\"id\":\"c1\",\"type\":\"not\",\"x\":-2147483648,\"y\":0},"
                + "{\"id\":\"c2\",\"type\":\"not\",\"x\":2147483600,\"y\":0}],\"wires\":[],\"labels\":[]}";
        assertThat(canvas.dropFragment(overflowing, new Point(10, 10))).isFalse();
        assertThat(canvas.getTransferHandler().importData(new TransferHandler.TransferSupport(canvas,
                new FragmentTransferable(overflowing)))).isFalse();
        assertThat(canvas.tool()).isInstanceOf(SelectTool.class);
        assertThat(openDocument.document().isEmpty()).isTrue();
    }
}
