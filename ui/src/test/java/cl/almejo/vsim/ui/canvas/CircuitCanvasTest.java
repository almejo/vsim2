package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.command.RemoveElementsCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.io.FragmentFormat;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.simulation.DriverRef;
import cl.almejo.vsim.core.simulation.ShortCircuit;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.tunnel.Tunnels;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.clipboard.FileListTransferable;
import cl.almejo.vsim.ui.clipboard.FragmentTransferable;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.panels.PaletteTransfer;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.tools.CanvasCursor;
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
import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
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
import static cl.almejo.vsim.ui.canvas.CanvasDragSupport.differingPixels;
import static cl.almejo.vsim.ui.canvas.CanvasDragSupport.dragOut;
import static cl.almejo.vsim.ui.canvas.CanvasDragSupport.handlerOf;
import static cl.almejo.vsim.ui.canvas.CanvasDragSupport.paint;
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

        /** Each modifier change as "shift control alt", e.g. "true false false". */
        final List<String> modifierChanges = new ArrayList<>();

        @Override
        public boolean modifiersChanged(ToolContext context, boolean shiftDown, boolean controlDown,
                                        boolean altDown) {
            modifierChanges.add(shiftDown + " " + controlDown + " " + altDown);
            return true;
        }
    }

    private MouseEvent mouse(int id, int x, int y, int modifiers, int button) {
        return new MouseEvent(canvas, id, 0, modifiers, x, y, 1, false, button);
    }

    /**
     * An event of extra mouse button {@code button} (4, 5…). A headless toolkit refuses to build one directly (it has no
     * mouse to ask how many buttons there are), so the event is built as button 1 and reports {@code button}.
     */
    private MouseEvent sideways(int id, int button) {
        return new MouseEvent(canvas, id, 0, 0, 10, 10, 1, false, MouseEvent.BUTTON1) {
            @Override
            public int getButton() {
                return button;
            }
        };
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

    /**
     * A touchpad's sideways swipe reaches Swing on Linux as presses of mouse buttons 4 (left) and 5 (right), not as a
     * wheel turn: they scroll the view sideways like Shift+wheel and never reach the tool as a click.
     */
    @Test
    void sidewaysScrollButtonsPanHorizontallyAndNeverReachTheTool() {
        RecordingTool tool = new RecordingTool();
        canvas.setTool(tool);
        Point2D origin = canvas.viewport().gridToScreen(0, 0);
        int scrollLeft = 4;
        int scrollRight = 5;

        canvas.getMouseListeners()[0].mousePressed(sideways(MouseEvent.MOUSE_PRESSED, scrollLeft));
        canvas.getMouseListeners()[0].mouseReleased(sideways(MouseEvent.MOUSE_RELEASED, scrollLeft));
        canvas.getMouseListeners()[0].mouseClicked(sideways(MouseEvent.MOUSE_CLICKED, scrollLeft));
        assertThat(canvas.viewport().gridToScreen(0, 0).getX()).isGreaterThan(origin.getX());

        canvas.getMouseListeners()[0].mousePressed(sideways(MouseEvent.MOUSE_PRESSED, scrollRight));
        canvas.getMouseListeners()[0].mousePressed(sideways(MouseEvent.MOUSE_PRESSED, scrollRight));
        assertThat(canvas.viewport().gridToScreen(0, 0).getX()).isLessThan(origin.getX());
        assertThat(canvas.viewport().gridToScreen(0, 0).getY()).isEqualTo(origin.getY());
        assertThat(tool.pressed).isEmpty();
    }

    @Test
    void aSidewaysButtonScrollsAsFarAsAShiftWheelStep() {
        Point2D origin = canvas.viewport().gridToScreen(0, 0);
        canvas.getMouseWheelListeners()[0].mouseWheelMoved(new MouseWheelEvent(canvas, MouseEvent.MOUSE_WHEEL, 0,
                InputEvent.SHIFT_DOWN_MASK, 10, 10, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, -1));
        double wheelStep = canvas.viewport().gridToScreen(0, 0).getX() - origin.getX();
        canvas.getMouseListeners()[0].mousePressed(sideways(MouseEvent.MOUSE_PRESSED, 4));
        double buttonStep = canvas.viewport().gridToScreen(0, 0).getX() - origin.getX() - wheelStep;
        assertThat(buttonStep).isEqualTo(wheelStep);
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
    void aViewStateComesBackAfterPanningAndZoomingAndNotifiesViewListeners() {
        canvas.setSize(800, 600);
        List<Double> zooms = new ArrayList<>();
        canvas.addViewListener(() -> zooms.add(canvas.viewport().zoom()));
        canvas.applyViewState(new ViewState(2.0, 10.5, -4.0));
        ViewState shown = canvas.viewState();
        canvas.viewport().panBy(123, -45);
        canvas.zoomIn();
        canvas.applyViewState(shown);
        assertThat(canvas.viewState().zoom()).isCloseTo(2.0, within(1e-9));
        assertThat(canvas.viewState().centerX()).isCloseTo(10.5, within(1e-9));
        assertThat(canvas.viewState().centerY()).isCloseTo(-4.0, within(1e-9));
        assertThat(canvas.viewport().gridToScreen(10.5, -4.0)).isEqualTo(new Point2D.Double(400, 300));
        assertThat(zooms).containsExactly(2.0, 2.5, 2.0);
    }

    @Test
    void applyingAViewStateClampsItsZoomToTheViewportLimits() {
        canvas.setSize(800, 600);
        canvas.applyViewState(new ViewState(100.0, 0, 0));
        assertThat(canvas.viewState().zoom()).isEqualTo(Viewport.MAXIMUM_ZOOM);
        canvas.applyViewState(new ViewState(0.01, 0, 0));
        assertThat(canvas.viewState().zoom()).isEqualTo(Viewport.MINIMUM_ZOOM);
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

    /** A bus pin and a one-bit pin on one wire: a width mismatch, shown straight from the netlist. */
    private void makeAWidthMismatch() {
        place(openDocument, StandardComponentTypes.LED, 0, -2, Map.of());
        openDocument.document().add(new Wire(openDocument.document().newWireId(), new GridPoint(1, 1),
                new GridPoint(5, 1)));
        place(openDocument, StandardComponentTypes.BUS_SPLITTER, 5, 1, Map.of(StandardComponentTypes.WIDTH, 4));
    }

    @Test
    void theProblemPulseRunsOnlyWhileAProblemWireIsShown() {
        assertThat(canvas.isAnimatingProblems()).isFalse();
        makeAWidthMismatch();
        assertThat(canvas.isAnimatingProblems()).isTrue();
        assertThat(canvas.isAnimatingSelection()).as("the ants stay reserved for selections").isFalse();
        openDocument.document().remove(openDocument.document().wires().iterator().next().id());
        assertThat(canvas.isAnimatingProblems()).isFalse();
    }

    @Test
    void aShortCircuitAlsoStartsThePulseAndATickStopsItWhenTheProblemIsGone() {
        Component high = place(openDocument, StandardComponentTypes.SWITCH, -3, 0, Map.of("value", "1"));
        Wire wire = new Wire(openDocument.document().newWireId(), new GridPoint(1, 1), new GridPoint(5, 1));
        openDocument.document().add(wire);
        String netId = openDocument.netlist().netForWire(wire.id()).orElseThrow().id().value();
        ShortCircuit shortCircuit = new ShortCircuit(netId, List.of(new DriverRef(high.id().value(), 0, Value.ONE)), 0);
        canvas.display(new SimulationSnapshot(1, openDocument.netlistToken(), 0, true,
                Map.of(netId, Value.ONE.withConflict()), Map.of(), List.of(shortCircuit), Optional.empty()));
        assertThat(canvas.isAnimatingProblems()).isTrue();

        canvas.display(SimulationSnapshot.EMPTY);
        assertThat(canvas.isAnimatingProblems()).isFalse();
    }

    @Test
    void aTickAdvancesTheProblemPulsePhaseWhileAProblemIsShown() {
        makeAWidthMismatch();
        assertThat(canvas.problemPulsePhase()).isZero();
        canvas.problemPulseTick();
        assertThat(canvas.problemPulsePhase()).isGreaterThan(0).isLessThan(1);
        for (int tick = 0; tick < 1000; tick++) {
            canvas.problemPulseTick();
            assertThat(canvas.problemPulsePhase()).isBetween(0.0, 1.0).isLessThan(1.0);
        }
    }

    @Test
    void theProblemPulseNeverRunsOffScreenOrAfterDispose() {
        onScreen = false;
        makeAWidthMismatch();
        assertThat(canvas.isAnimatingProblems()).isFalse();
        onScreen = true;
        canvas.overrideOnScreen(() -> onScreen);
        assertThat(canvas.isAnimatingProblems()).isTrue();
        canvas.dispose();
        assertThat(canvas.isAnimatingProblems()).isFalse();
        canvas.overrideOnScreen(() -> onScreen);
        assertThat(canvas.isAnimatingProblems()).isFalse();
    }

    @Test
    void removingTheCanvasFromItsWindowStopsTheProblemPulse() {
        makeAWidthMismatch();
        canvas.removeNotify();
        assertThat(canvas.isAnimatingProblems()).isFalse();
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
    void aFileDropTargetGetsTheGridPointWhereTheFilesCame() {
        canvas.setSize(400, 300);
        List<Path> dropped = new ArrayList<>();
        List<GridPoint> points = new ArrayList<>();
        canvas.setFileDropTarget((files, point) -> {
            dropped.addAll(files);
            points.add(point);
        });
        assertThat(canvas.getTransferHandler().importData(new TransferHandler.TransferSupport(canvas,
                new FileListTransferable(List.of(new File("/tmp/a.vsim")))))).isTrue();
        assertThat(dropped).containsExactly(Path.of("/tmp/a.vsim"));
        assertThat(points).containsExactly(canvas.viewport().snap(200, 150));
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
    void losingFocusStopsTheAutoscroll() {
        canvas.setSize(400, 300);
        place(openDocument, StandardComponentTypes.NOT, 5, 5, Map.of());
        primaryPress(56, 56);
        primaryDrag(392, 64);
        assertThat(canvas.isAutoscrolling()).isTrue();
        for (FocusListener listener : canvas.getFocusListeners()) {
            listener.focusLost(new FocusEvent(canvas, FocusEvent.FOCUS_LOST));
        }
        assertThat(canvas.isAutoscrolling()).isFalse();
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
        assertThat(handler.getSourceActions(canvas)).isEqualTo(TransferHandler.COPY_OR_MOVE);
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

    @Test
    void theDragOutCarriesItsOrigin() throws Exception {
        canvas.setSize(400, 300);
        Component gate = place(openDocument, StandardComponentTypes.NOT, 5, 5, Map.of());
        Transferable transferable = dragOut(canvas, 56, 56);

        assertThat(transferable.isDataFlavorSupported(CanvasDragTransferable.ORIGIN_FLAVOR)).isTrue();
        DragOrigin origin = CanvasDragTransferable.originOf(transferable).orElseThrow();
        assertThat(origin.canvas()).isSameAs(canvas);
        assertThat(origin.ids()).containsExactly(gate.id());
        assertThat(origin.pressPoint()).isEqualTo(new GridPoint(7, 7));
        assertThat(origin.grabOffset()).isEqualTo(new GridPoint(2, 2));
        assertThat(transferable.getTransferData(FragmentTransferable.FRAGMENT_FLAVOR)).isInstanceOf(String.class);
        assertThat(CanvasDragTransferable.originOf(new StringSelection("hello"))).isEmpty();
    }

    @Test
    void aDropBackOnItsOwnCanvasMovesEvenAfterTheToolkitEndedTheDrag() {
        canvas.setSize(400, 300);
        Component gate = place(openDocument, StandardComponentTypes.NOT, 5, 5, Map.of());
        Transferable transferable = dragOut(canvas, 56, 56);
        canvas.endDragOut(); // exportDone arrived before the drop was handled

        assertThat(handlerOf(canvas).importFragment(transferable, new Point(80, 72), false)).isTrue();

        assertThat(openDocument.document().components()).singleElement()
                .satisfies(moved -> assertThat(moved.position()).isEqualTo(new GridPoint(8, 7)));
        assertThat(openDocument.history().undoDescription()).contains("command.move");
        assertThat(canvas.selection().ids()).containsExactly(gate.id());
    }

    @Test
    void aDragOutBackOverItsCanvasPreviewsTheMove() {
        canvas.setSize(400, 300);
        place(openDocument, StandardComponentTypes.NOT, 5, 5, Map.of());
        dragOut(canvas, 56, 56);
        BufferedImage before = paint(canvas);

        canvas.dragOutOver(new Point(200, 160));

        assertThat(canvas.dragOutPreview()).contains(new GridPoint(18, 13));
        assertThat(differingPixels(before, paint(canvas), 184, 144, 216, 176)).isGreaterThan(20);
        canvas.dragOutLeft();
        assertThat(canvas.dragOutPreview()).isEmpty();
        canvas.dragOutOver(new Point(200, 160));
        canvas.endDragOut();
        assertThat(canvas.dragOutPreview()).isEmpty();
    }

    @Test
    void thereIsNoPreviewWithoutADragOut() {
        canvas.setSize(400, 300);
        canvas.dragOutOver(new Point(200, 160));
        assertThat(canvas.dragOutPreview()).isEmpty();
    }

    @Test
    void onlyACopyFromASourceThatAlsoOffersMoveCountsAsCtrl() {
        assertThat(CanvasTransferHandler.copyRequested(TransferHandler.COPY, TransferHandler.COPY_OR_MOVE)).isTrue();
        assertThat(CanvasTransferHandler.copyRequested(TransferHandler.MOVE, TransferHandler.COPY_OR_MOVE)).isFalse();
        assertThat(CanvasTransferHandler.copyRequested(TransferHandler.COPY, TransferHandler.COPY)).isFalse();
    }

    private final CanvasHost quietHost = new CanvasHost() {
        @Override
        public void showContextMenu(CircuitCanvas source, Optional<DocumentElement> target, Point2D screenPosition) {
        }

        @Override
        public void configure(Component component) {
        }
    };

    private CircuitCanvas canvasOf(OpenDocument document) {
        CircuitCanvas view = new CircuitCanvas(document,
                new CircuitRenderer(document.registry(), PainterRegistry.standard(), ColorScheme.LIGHT), quietHost);
        view.setSize(400, 300);
        return view;
    }

    @Test
    void aDropOnAnotherViewOfTheSameDocumentMovesAsOneStep() {
        canvas.setSize(400, 300);
        Component gate = place(openDocument, StandardComponentTypes.NOT, 5, 5, Map.of());
        CircuitCanvas secondView = canvasOf(openDocument);
        try {
            Transferable transferable = dragOut(canvas, 56, 56);
            assertThat(handlerOf(secondView).importFragment(transferable, new Point(160, 160), false)).isTrue();

            assertThat(openDocument.document().components()).singleElement()
                    .satisfies(moved -> assertThat(moved.position()).isEqualTo(new GridPoint(18, 18)));
            assertThat(openDocument.history().undoDescription()).contains("command.move");
            assertThat(secondView.selection().ids()).containsExactly(gate.id());
            assertThat(canvas.isDraggingOut()).isFalse();
            openDocument.history().undo();
            assertThat(openDocument.document().component(gate.id()).orElseThrow().position())
                    .isEqualTo(new GridPoint(5, 5));
        } finally {
            secondView.dispose();
        }
    }

    @Test
    void controlAtTheDropCopiesIntoTheSameDocument() {
        canvas.setSize(400, 300);
        Component gate = place(openDocument, StandardComponentTypes.NOT, 5, 5, Map.of());
        CircuitCanvas secondView = canvasOf(openDocument);
        try {
            Transferable transferable = dragOut(canvas, 56, 56);
            assertThat(handlerOf(secondView).importFragment(transferable, new Point(160, 160), true)).isTrue();

            assertThat(openDocument.document().components()).hasSize(2);
            assertThat(openDocument.document().component(gate.id()).orElseThrow().position())
                    .isEqualTo(new GridPoint(5, 5));
            Component copy = openDocument.document().components().get(1);
            assertThat(copy.position()).isEqualTo(new GridPoint(18, 18));
            assertThat(secondView.selection().ids()).containsExactly(copy.id());
            assertThat(openDocument.history().undoDescription()).contains("command.paste");
            openDocument.history().undo();
            assertThat(openDocument.document().components()).containsExactly(gate);
        } finally {
            secondView.dispose();
        }
    }

    @Test
    void controlAtTheDropOnItsOwnCanvasCopiesToo() {
        canvas.setSize(400, 300);
        place(openDocument, StandardComponentTypes.NOT, 5, 5, Map.of());
        Transferable transferable = dragOut(canvas, 56, 56);
        assertThat(handlerOf(canvas).importFragment(transferable, new Point(160, 160), true)).isTrue();
        assertThat(openDocument.document().components()).extracting(Component::position)
                .containsExactly(new GridPoint(5, 5), new GridPoint(18, 18));
        assertThat(canvas.isDraggingOut()).isFalse();
    }

    @Test
    void controlAtTheDropCopiesIntoAnotherDocumentWhereTheGrabbedPointLands() {
        canvas.setSize(400, 300);
        place(openDocument, StandardComponentTypes.NOT, 5, 5, Map.of());
        try (OpenDocument other = OpenDocument.createEmpty(StandardComponentTypes.registry())) {
            CircuitCanvas otherView = canvasOf(other);
            try {
                Transferable transferable = dragOut(canvas, 56, 56);
                assertThat(handlerOf(otherView).importFragment(transferable, new Point(160, 160), true)).isTrue();

                assertThat(other.document().components()).singleElement()
                        .satisfies(copy -> assertThat(copy.position()).isEqualTo(new GridPoint(18, 18)));
                assertThat(other.history().undoDescription()).contains("command.paste");
                assertThat(otherView.tool()).isSameAs(otherView.defaultTool());
                assertThat(openDocument.document().components()).singleElement()
                        .satisfies(original -> assertThat(original.position()).isEqualTo(new GridPoint(5, 5)));
            } finally {
                otherView.dispose();
            }
        }
    }

    @Test
    void aDropOnAnotherDocumentFloatsACopy() {
        canvas.setSize(400, 300);
        place(openDocument, StandardComponentTypes.NOT, 5, 5, Map.of());
        try (OpenDocument other = OpenDocument.createEmpty(StandardComponentTypes.registry())) {
            CircuitCanvas otherView = canvasOf(other);
            try {
                Transferable transferable = dragOut(canvas, 56, 56);
                assertThat(handlerOf(otherView).importFragment(transferable, new Point(160, 160), false)).isTrue();

                assertThat(otherView.tool()).isInstanceOfSatisfying(FloatingSelectionTool.class,
                        floating -> assertThat(floating.position()).isEqualTo(new GridPoint(20, 20)));
                assertThat(other.document().isEmpty()).isTrue();
                assertThat(openDocument.document().components()).hasSize(1);
            } finally {
                otherView.dispose();
            }
        }
    }

    @Test
    void aMoveSkipsElementsThatNoLongerExist() {
        canvas.setSize(400, 300);
        Component gate = place(openDocument, StandardComponentTypes.NOT, 5, 5, Map.of());
        Component led = place(openDocument, StandardComponentTypes.LED, 20, 5, Map.of());
        canvas.selection().set(List.of(gate.id(), led.id()));
        CircuitCanvas secondView = canvasOf(openDocument);
        try {
            Transferable transferable = dragOut(canvas, 56, 56);
            openDocument.execute(new RemoveElementsCommand(List.of(led.id())));

            assertThat(handlerOf(secondView).importFragment(transferable, new Point(160, 160), false)).isTrue();
            assertThat(openDocument.document().components()).singleElement()
                    .satisfies(moved -> assertThat(moved.position()).isEqualTo(new GridPoint(18, 18)));
            assertThat(openDocument.history().undoDescription()).contains("command.move");

            Transferable second = dragOut(canvas, 160, 160);
            openDocument.execute(new RemoveElementsCommand(List.of(gate.id())));
            Optional<String> lastStep = openDocument.history().undoDescription();
            assertThat(handlerOf(secondView).importFragment(second, new Point(80, 80), false)).isTrue();
            assertThat(openDocument.history().undoDescription()).isEqualTo(lastStep);
            assertThat(openDocument.document().components()).isEmpty();
        } finally {
            secondView.dispose();
        }
    }

    private int cursorType() {
        return canvas.getCursor().getType();
    }

    private void spaceKey(int id) {
        KeyEvent event = new KeyEvent(canvas, id, 0, 0, KeyEvent.VK_SPACE, ' ');
        if (id == KeyEvent.KEY_PRESSED) {
            canvas.getKeyListeners()[0].keyPressed(event);
        } else {
            canvas.getKeyListeners()[0].keyReleased(event);
        }
    }

    @Test
    void theCanvasShowsTheCursorAToolAsksFor() {
        canvas.setCursor(CanvasCursor.HAND);
        assertThat(cursorType()).isEqualTo(Cursor.HAND_CURSOR);
        canvas.setCursor(CanvasCursor.CROSSHAIR);
        assertThat(cursorType()).isEqualTo(Cursor.CROSSHAIR_CURSOR);
        canvas.setCursor(CanvasCursor.MOVE);
        assertThat(cursorType()).isEqualTo(Cursor.MOVE_CURSOR);
        canvas.setCursor(CanvasCursor.DEFAULT);
        assertThat(cursorType()).isEqualTo(Cursor.DEFAULT_CURSOR);
    }

    @Test
    void theSelectionCursorsFallBackToTheArrowWhenCustomCursorsAreUnavailable() {
        canvas.setCursor(CanvasCursor.ADD_TO_SELECTION);
        assertThat(canvas.getCursor()).isNotNull();
        canvas.setCursor(CanvasCursor.REMOVE_FROM_SELECTION);
        assertThat(canvas.getCursor()).isNotNull();
    }

    @Test
    void choosingAToolShowsItsInitialCursor() {
        canvas.setTool(new PanTool());
        assertThat(cursorType()).isEqualTo(Cursor.MOVE_CURSOR);
        canvas.useDefaultTool();
        assertThat(cursorType()).isEqualTo(Cursor.DEFAULT_CURSOR);
    }

    @Test
    void choosingTheWireToolShowsTheCrosshair() {
        canvas.setTool(new cl.almejo.vsim.ui.tools.WireTool());
        assertThat(cursorType()).isEqualTo(Cursor.CROSSHAIR_CURSOR);
    }

    @Test
    void spaceAndTheMiddleButtonShowMoveAndThenGiveTheToolItsCursorBack() {
        canvas.setCursor(CanvasCursor.CROSSHAIR);
        spaceKey(KeyEvent.KEY_PRESSED);
        assertThat(cursorType()).isEqualTo(Cursor.MOVE_CURSOR);
        canvas.setCursor(CanvasCursor.HAND); // the tool's hover changes while Espacio is held
        assertThat(cursorType()).isEqualTo(Cursor.MOVE_CURSOR);
        spaceKey(KeyEvent.KEY_RELEASED);
        assertThat(cursorType()).isEqualTo(Cursor.HAND_CURSOR);

        canvas.getMouseListeners()[0].mousePressed(mouse(MouseEvent.MOUSE_PRESSED, 10, 10, InputEvent.BUTTON2_DOWN_MASK, MouseEvent.BUTTON2));
        assertThat(cursorType()).isEqualTo(Cursor.MOVE_CURSOR);
        canvas.getMouseListeners()[0].mouseReleased(mouse(MouseEvent.MOUSE_RELEASED, 30, 15, 0, MouseEvent.BUTTON2));
        assertThat(cursorType()).isEqualTo(Cursor.HAND_CURSOR);
    }

    @Test
    void losingFocusWhileSpaceIsHeldGivesTheToolItsCursorBack() {
        canvas.setCursor(CanvasCursor.CROSSHAIR);
        spaceKey(KeyEvent.KEY_PRESSED);
        canvas.getFocusListeners()[0].focusLost(new FocusEvent(canvas, FocusEvent.FOCUS_LOST));
        assertThat(cursorType()).isEqualTo(Cursor.CROSSHAIR_CURSOR);
    }

    @Test
    void aToolTipOverrideWinsOverTheProviderUntilItIsRemoved() {
        MouseEvent hover = mouse(MouseEvent.MOUSE_MOVED, 16, 24, 0, MouseEvent.NOBUTTON);
        canvas.setToolTipProvider(gridPoint -> Optional.of("drivers"));
        List<Point2D> asked = new ArrayList<>();
        Runnable removeFirst = canvas.addToolTipOverride(gridPoint -> {
            asked.add(gridPoint);
            return gridPoint.getX() > 1 ? Optional.of("marker") : Optional.empty();
        });
        Runnable removeSecond = canvas.addToolTipOverride(gridPoint -> Optional.of("second"));

        assertThat(canvas.getToolTipText(hover)).isEqualTo("marker");
        assertThat(asked).containsExactly(new Point2D.Double(2, 3));
        assertThat(canvas.getToolTipText(mouse(MouseEvent.MOUSE_MOVED, 4, 4, 0, MouseEvent.NOBUTTON)))
                .as("the first override has nothing there: the next one answers").isEqualTo("second");

        removeSecond.run();
        assertThat(canvas.getToolTipText(mouse(MouseEvent.MOUSE_MOVED, 4, 4, 0, MouseEvent.NOBUTTON)))
                .isEqualTo("drivers");
        removeFirst.run();
        assertThat(canvas.getToolTipText(hover)).isEqualTo("drivers");
        canvas.clearToolTipProvider();
        assertThat(canvas.getToolTipText(hover)).isNull();
    }

    @Test
    void anOverrideAloneRegistersTheCanvasForTooltipsAndItsRemovalUnregistersIt() {
        Runnable remove = canvas.addToolTipOverride(gridPoint -> Optional.of("marker"));
        assertThat(canvas.getToolTipText(mouse(MouseEvent.MOUSE_MOVED, 4, 4, 0, MouseEvent.NOBUTTON)))
                .isEqualTo("marker");
        assertThat(java.util.Arrays.asList(canvas.getMouseListeners()))
                .anyMatch(listener -> listener == javax.swing.ToolTipManager.sharedInstance());
        remove.run();
        assertThat(java.util.Arrays.asList(canvas.getMouseListeners()))
                .noneMatch(listener -> listener == javax.swing.ToolTipManager.sharedInstance());
    }

    @Test
    void aPressInterceptorConsumesAPrimaryPressAndItsDragAndReleaseBeforeTheTool() {
        List<String> toolEvents = new ArrayList<>();
        canvas.setTool(new Tool() {
            @Override
            public void mousePressed(ToolContext context, CanvasMouseEvent event) {
                toolEvents.add("press");
            }

            @Override
            public void mouseDragged(ToolContext context, CanvasMouseEvent event) {
                toolEvents.add("drag");
            }

            @Override
            public void mouseReleased(ToolContext context, CanvasMouseEvent event) {
                toolEvents.add("release");
            }
        });
        List<Point2D> intercepted = new ArrayList<>();
        Runnable remove = canvas.addPressInterceptor(event -> {
            intercepted.add(event.gridPosition());
            return event.gridPosition().getX() < 5;
        });

        primaryPress(16, 24);
        primaryDrag(20, 24);
        primaryRelease(20, 24);
        assertThat(intercepted).containsExactly(new Point2D.Double(2, 3));
        assertThat(toolEvents).as("consumed").isEmpty();

        primaryPress(80, 24);
        primaryRelease(80, 24);
        assertThat(toolEvents).as("the interceptor said no").containsExactly("press", "release");

        canvas.getMouseListeners()[0].mousePressed(
                mouse(MouseEvent.MOUSE_PRESSED, 16, 24, InputEvent.BUTTON3_DOWN_MASK, MouseEvent.BUTTON3));
        assertThat(intercepted).as("only primary presses are offered").hasSize(2);

        remove.run();
        toolEvents.clear();
        primaryPress(16, 24);
        primaryRelease(16, 24);
        assertThat(toolEvents).containsExactly("press", "release");
        assertThat(intercepted).hasSize(2);
    }

    private void modifierKey(int id, int keyCode, int modifiersReported) {
        KeyEvent event = new KeyEvent(canvas, id, 0, modifiersReported, keyCode, KeyEvent.CHAR_UNDEFINED);
        if (id == KeyEvent.KEY_PRESSED) {
            canvas.getKeyListeners()[0].keyPressed(event);
        } else {
            canvas.getKeyListeners()[0].keyReleased(event);
        }
    }

    @Test
    void shiftControlAndAltPressedOrReleasedTellTheToolTheModifiersAfterTheChange() {
        RecordingTool tool = new RecordingTool();
        canvas.setTool(tool);
        modifierKey(KeyEvent.KEY_PRESSED, KeyEvent.VK_SHIFT, InputEvent.SHIFT_DOWN_MASK);
        modifierKey(KeyEvent.KEY_PRESSED, KeyEvent.VK_CONTROL, InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK);
        // some platforms still report the released key as down: the change itself wins
        modifierKey(KeyEvent.KEY_RELEASED, KeyEvent.VK_SHIFT, InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK);
        modifierKey(KeyEvent.KEY_RELEASED, KeyEvent.VK_CONTROL, 0);
        modifierKey(KeyEvent.KEY_PRESSED, KeyEvent.VK_ALT, 0);
        modifierKey(KeyEvent.KEY_RELEASED, KeyEvent.VK_ALT, InputEvent.ALT_DOWN_MASK);
        modifierKey(KeyEvent.KEY_PRESSED, KeyEvent.VK_A, InputEvent.SHIFT_DOWN_MASK);
        modifierKey(KeyEvent.KEY_RELEASED, KeyEvent.VK_A, InputEvent.SHIFT_DOWN_MASK);

        assertThat(tool.modifierChanges).containsExactly("true false false", "true true false", "false true false",
                "false false false", "false false true", "false false false");
    }

    @Test
    void aHeldModifierRepeatingItsPressTellsTheToolOnceUntilTheFocusIsLost() {
        RecordingTool tool = new RecordingTool();
        canvas.setTool(tool);
        modifierKey(KeyEvent.KEY_PRESSED, KeyEvent.VK_SHIFT, InputEvent.SHIFT_DOWN_MASK);
        modifierKey(KeyEvent.KEY_PRESSED, KeyEvent.VK_SHIFT, InputEvent.SHIFT_DOWN_MASK);
        modifierKey(KeyEvent.KEY_PRESSED, KeyEvent.VK_SHIFT, InputEvent.SHIFT_DOWN_MASK);
        assertThat(tool.modifierChanges).containsExactly("true false false");

        for (java.awt.event.FocusListener listener : canvas.getFocusListeners()) {
            listener.focusLost(new java.awt.event.FocusEvent(canvas, java.awt.event.FocusEvent.FOCUS_LOST));
        }
        modifierKey(KeyEvent.KEY_PRESSED, KeyEvent.VK_SHIFT, InputEvent.SHIFT_DOWN_MASK);
        modifierKey(KeyEvent.KEY_RELEASED, KeyEvent.VK_SHIFT, 0);
        assertThat(tool.modifierChanges).containsExactly("true false false", "true false false", "false false false");
    }

    @Test
    void theGhostOfASalidaTakesTheColourOfTheEntradaOfItsNameInThisCircuit() {
        place(openDocument, StandardComponentTypes.TUNNEL_ENTRANCE, 30, 30,
                Map.of(Tunnels.NAME, "CLK", Tunnels.COLOR, 3));
        GridPoint position = new GridPoint(2, 2);
        Component exit = openDocument.registry().require(StandardComponentTypes.TUNNEL_EXIT).newComponent(
                new ElementId("ghost"), position, Rotation.R0, Map.of(Tunnels.NAME, "CLK"), "");
        CircuitRenderer renderer = new CircuitRenderer(openDocument.registry(), PainterRegistry.standard(),
                ColorScheme.LIGHT);

        BufferedImage onTheCanvas = ghostImage(graphics -> canvas.paintGhost(graphics, exit));
        BufferedImage joined = ghostImage(graphics ->
                renderer.paintGhost(graphics, exit, openDocument.registry(), Map.of("CLK", 3)));
        BufferedImage alone = ghostImage(graphics -> renderer.paintGhost(graphics, exit));

        int x = position.x() * GridPoint.PIXELS_PER_UNIT + 3 * GridPoint.PIXELS_PER_UNIT;
        int y = position.y() * GridPoint.PIXELS_PER_UNIT + 1;
        assertThat(onTheCanvas.getRGB(x, y)).isEqualTo(joined.getRGB(x, y)).isNotEqualTo(alone.getRGB(x, y));
    }

    @Test
    void theTunnelColoursOfTheGhostsAreWorkedOutOncePerDocumentChange() {
        place(openDocument, StandardComponentTypes.TUNNEL_ENTRANCE, 30, 30,
                Map.of(Tunnels.NAME, "CLK", Tunnels.COLOR, 3));
        Map<String, Integer> first = canvas.tunnelColors();

        assertThat(canvas.tunnelColors()).isSameAs(first).containsEntry("CLK", 3);

        place(openDocument, StandardComponentTypes.TUNNEL_ENTRANCE, 60, 30,
                Map.of(Tunnels.NAME, "RST", Tunnels.COLOR, 5));
        assertThat(canvas.tunnelColors()).isNotSameAs(first).containsEntry("CLK", 3).containsEntry("RST", 5);
    }

    private static BufferedImage ghostImage(java.util.function.Consumer<Graphics2D> painting) {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(ColorScheme.LIGHT.background());
        graphics.fillRect(0, 0, 100, 100);
        painting.accept(graphics);
        graphics.dispose();
        return image;
    }
}
