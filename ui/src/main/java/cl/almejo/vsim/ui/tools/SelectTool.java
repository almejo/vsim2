package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.command.PasteCommand;
import cl.almejo.vsim.core.command.SetLabelOffsetCommand;
import cl.almejo.vsim.core.component.ElementBounds;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Guide;
import cl.almejo.vsim.core.document.LabelOffset;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.SnapMatch;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.tunnel.Tunnels;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.core.wiring.NewContact;
import cl.almejo.vsim.core.wiring.WireStretch;
import cl.almejo.vsim.ui.canvas.ConnectionPoints;
import cl.almejo.vsim.ui.canvas.LabelLayout;
import cl.almejo.vsim.ui.paint.InterfacePinPainter;
import cl.almejo.vsim.ui.paint.PainterSupport;
import cl.almejo.vsim.ui.paint.TextFonts;
import cl.almejo.vsim.ui.canvas.SelectionBounds;
import cl.almejo.vsim.ui.snap.AlignmentLinesPainter;
import cl.almejo.vsim.ui.snap.MatchedGuides;
import cl.almejo.vsim.ui.snap.SnappedMove;
import cl.almejo.vsim.ui.wiring.MovePreview;
import cl.almejo.vsim.ui.wiring.SelectionMoves;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Everyday tool: select (Shift/Ctrl+click toggles, drag in empty space for a rubber band), move the selection by dragging any selected element, draw
 * wires by dragging from a pin or an unselected wire, toggle switches, context menu, double click to configure.
 *
 * <p>What a plain primary press grabs, in priority order: a pin (within the pin radius), then an unselected wire,
 * then the body of an element or an already selected element. Exception: a pin of a selected component grabs the
 * whole selection (instead of starting a wire) while the selection has more than one element. A press on an
 * element of a multi-selection that is released without moving (a plain click) narrows the selection to that
 * element; a press-and-drag moves the whole group. Ctrl held when a move is released copies the selection there
 * instead (one undo step).
 *
 * <p>M6.7: a move drags the wires attached to the selection when the follow mode, inverted while the configured key
 * is held, says so; the keys are read during the drag (the preview) and at the release (the move). A key held at the
 * press keeps its old meaning on a click (Shift toggles the selection); a Shift press that drags moves the selection
 * (adding the pressed element) with the mode inverted.
 *
 * <p>M6.7 §8: a press on the one selected wire that drags with the segment key held (Alt, or Shift when Alt is the
 * invert key; read during the drag and at the release) moves that wire sideways only, perpendicular to itself, and
 * the wires joined to its ends always stretch (the follow mode and the invert key do not apply).
 *
 * <p>M6.8: a double click on a wire selects its stretch and a triple click every wire connected to it (Shift adds
 * them); that press never starts a wire drawing, and a drag right after it moves the selection. A plain press on a
 * component's drawn label (not on a pin or a free text) drags only that label, its centre snapped to grid points (one
 * undo step); released without dragging, it selects the component.
 *
 * <p>M6.8: with Shift or Ctrl held and no press under way, the cursor tells what a press would do to the selection
 * (add or take out the element, or, on empty canvas, add with a band); Ctrl+Shift on empty canvas drags a band that
 * takes what it encloses out of the selection. The cursor follows the keys without moving the pointer.
 *
 * <p>Ajustar (alignment-guides spec §3): a move, a Ctrl copy and a segment drag go through the canvas's
 * {@link cl.almejo.vsim.ui.snap.Snapper} before the locked-frame walls and the segment axis lock, which still win;
 * the alignment lines that hold at the final delta are drawn after the move preview. {@link #refreshSnap} redoes the
 * drag at the last pointer position, so a release right after a toggle or a zoom commits what is shown. A drag the
 * magnet (or a locked frame's wall) brings back to a zero delta moves nothing and is no click either: it neither
 * narrows the selection nor toggles a switch.
 *
 * <p>{@link SelectToolDelegate}s (M6.3: frames) are asked first: a press one of them takes, with its drag and
 * release, is theirs alone.
 */
public final class SelectTool implements Tool {

    private static final double HOVER_RING_RADIUS = 4.5;
    private static final BasicStroke HOVER_RING = new BasicStroke(1.5f);
    private static final double HOVER_TAG_OFFSET = 7;
    private static final double BAND_THRESHOLD = 0.5;
    private static final float BAND_WIDTH = 1f;
    private static final int BAND_FILL_ALPHA = 40;
    private static final int SUBTRACT_BAND_FILL_ALPHA = 24;

    /** What a rubber band does with what it encloses: the mode is decided at the press. */
    private enum BandMode { REPLACE, ADD, REMOVE }

    private final Map<String, Boolean> interactiveByType = new HashMap<>();
    private final List<SelectToolDelegate> delegates = new CopyOnWriteArrayList<>();
    private SelectToolDelegate activeDelegate;
    private ConnectionPoints connectionPoints;
    private int pinSearches;
    private DocumentElement pressedElement;
    private GridPoint pressPoint;
    private WireDrawing wire;
    private GridPoint hoverPin;
    private int hoverWidth = 1;
    private Point2D bandStart;
    private Point2D bandEnd;
    private BandMode bandMode = BandMode.REPLACE;
    /** The last hover event (a move, or a release), so a modifier key changed without moving redoes the cursor. */
    private CanvasMouseEvent lastHover;
    private boolean ownPressSeen;
    /**
     * M7: the bus input pin the last click toggled and its value before that click, kept until the next press, so the
     * double click that click began can put the value back and edit it.
     */
    private ToggledBusInput lastBusInputToggle;
    private boolean narrowOnRelease;
    /** The press was a Shift click on an element: a release without moving only adjusts the selection. */
    private boolean selectionClick;
    /** That Shift click was on an element already selected: a release without moving takes it out. */
    private boolean removeOnRelease;
    private int deltaX;
    private int deltaY;
    /** The drag's delta before the locked-frame walls: what a Ctrl copy uses, as copies leave the originals. */
    private int copyDeltaX;
    private int copyDeltaY;
    /**
     * The pointer's own grid delta at the last drag, before the magnet and the locked-frame walls: when it is not
     * zero but they bring the move back to zero, the release moved nothing and was no click either.
     */
    private int pointerDeltaX;
    private int pointerDeltaY;
    private boolean followWires;
    /** The drag moves a single selected wire sideways (the segment key is held). */
    private boolean segmentDrag;
    /** The last drag event of a move, so a modifier key pressed or released without moving can redo it. */
    private CanvasMouseEvent lastDrag;
    private final MovePreview movePreview = new MovePreview();
    /** The magnet's answer for the current move (its lines are drawn after the move preview). */
    private SnappedMove snappedMove = SnappedMove.unsnapped(0, 0);
    /** M6.8: the component whose label the press grabbed; the label moves once the drag passes the threshold. */
    private Component labelComponent;
    private Point2D labelPressPosition;
    /** The drawn centre of that label at the press, in document pixels. */
    private Point2D labelPressCentre;
    /** Where the dragged label would land; {@code null} until the drag passes the threshold. */
    private LabelOffset labelOffset;
    private CanvasCursor cursor = CanvasCursor.DEFAULT;

    /** For tests: the delegates installed now, in the order they are asked. */
    public List<SelectToolDelegate> delegates() {
        return List.copyOf(delegates);
    }

    /** Adds a delegate asked before the tool's own behaviour; the returned runnable removes it. */
    public Runnable addDelegate(SelectToolDelegate delegate) {
        delegates.add(delegate);
        return () -> delegates.remove(delegate);
    }

    @Override
    public void activated(ToolContext context) {
        ownPressSeen = false;
        cursor = CanvasCursor.DEFAULT; // the canvas shows cursor(), the arrow, on activation
    }

    @Override
    public void deactivated(ToolContext context) {
        cancelDelegate(context);
        hoverPin = null;
        hoverWidth = 1;
        reset();
        context.repaint();
    }

    @Override
    public void mousePressed(ToolContext context, CanvasMouseEvent event) {
        if (delegatePress(context, event)) {
            return;
        }
        press(context, event);
        pressCursor().ifPresent(next -> showCursor(context, next));
    }

    /**
     * True when a delegate took the press. A primary press a delegate grabs makes it the active delegate until the
     * release; one it only handled leaves no pending state, so a lost release (a modal editor opened by a double
     * click) never blocks the next press.
     */
    private boolean delegatePress(ToolContext context, CanvasMouseEvent event) {
        if (activeDelegate != null) {
            return true; // another press while the delegate's press is held belongs to it
        }
        for (SelectToolDelegate delegate : delegates) {
            SelectToolDelegate.Press answer = delegate.pressed(context, event);
            if (answer == SelectToolDelegate.Press.IGNORED) {
                continue;
            }
            if (event.button() == CanvasMouseEvent.PRIMARY) {
                reset();
                ownPressSeen = true;
                if (answer == SelectToolDelegate.Press.GRABBED) {
                    activeDelegate = delegate;
                    hoverPin = null;
                    showCursor(context, delegate.pressCursor());
                }
            }
            return true;
        }
        return false;
    }

    private void cancelDelegate(ToolContext context) {
        if (activeDelegate != null) {
            SelectToolDelegate cancelled = activeDelegate;
            activeDelegate = null;
            cancelled.cancelled(context);
        }
    }

    private void press(ToolContext context, CanvasMouseEvent event) {
        Optional<DocumentElement> hit = context.hitTester()
                .elementAt(context.openDocument().document(), event.gridPosition());
        if (event.button() == CanvasMouseEvent.SECONDARY) {
            pressSecondary(context, event, hit);
            return;
        }
        if (event.button() != CanvasMouseEvent.PRIMARY) {
            return;
        }
        reset();
        boolean continuesOwnClick = ownPressSeen;
        ownPressSeen = true;
        ToggledBusInput toggledByFirstClick = lastBusInputToggle;
        lastBusInputToggle = null;
        if (pressRepeatedClick(context, event, hit, continuesOwnClick, toggledByFirstClick)) {
            return;
        }
        // A free text is drawn on top of everything, so a press on it never reaches a pin underneath.
        Optional<ConnectionPoints.PinHit> pin = hit.isPresent() && hit.get() instanceof TextLabel
                ? Optional.empty()
                : pinNear(context, event.gridPosition());
        if (!event.shiftDown() && !event.controlDown() && pin.isEmpty()) {
            Optional<Component> labelled = labelPressedAt(context, event.gridPosition(), hit);
            if (labelled.isPresent()) {
                pressLabel(context, event, labelled.get());
                return;
            }
        }
        if (event.shiftDown() || event.controlDown()) {
            pressWithModifier(context, event, hit, pin);
            return;
        }
        if (pin.isPresent()) {
            pressPin(context, event, pin.get());
            return;
        }
        pressedElement = hit.orElse(null);
        if (hit.isPresent() && hit.get() instanceof Wire pressedWire
                && !context.selection().contains(pressedWire.id())) {
            pressWire(event, pressedWire);
            return;
        }
        if (hit.isEmpty()) {
            startBand(event, BandMode.REPLACE);
            return;
        }
        pressElement(context, event, hit.get());
    }

    /** A secondary press selects what it hit (or clears the selection on empty canvas) and opens the context menu. */
    private void pressSecondary(ToolContext context, CanvasMouseEvent event, Optional<DocumentElement> hit) {
        hit.ifPresentOrElse(element -> {
            if (!context.selection().contains(element.id())) {
                context.selection().select(element.id());
            }
        }, () -> context.selection().clear());
        context.showContextMenu(hit, event.screenPosition());
    }

    /**
     * The second (or third) click of the tool's own click: edits a label or a value, selects a wire's stretch or
     * group, or configures a component. True when it took the press.
     */
    private boolean pressRepeatedClick(ToolContext context, CanvasMouseEvent event, Optional<DocumentElement> hit,
                                       boolean continuesOwnClick, ToggledBusInput toggledByFirstClick) {
        // a label beats a wire under it, as in a single press, so both clicks of a double click agree
        if (event.clickCount() >= 2 && continuesOwnClick) {
            Optional<Component> labelled = labelPressedAt(context, event.gridPosition(), hit);
            if (labelled.isPresent()) {
                context.selection().select(labelled.get().id());
                context.editLabel(labelled.get());
                return true;
            }
        }
        if (event.clickCount() >= 2 && continuesOwnClick && (hit.isEmpty() || hit.get() instanceof Wire)) {
            // M7: a bus input pin's value box lies beside its tag, outside the pin's bounds; it is drawn over the
            // wires, so it beats a wire under it
            Optional<Component> pinOfBox = busInputValueBoxAt(context, event.gridPosition());
            if (pinOfBox.isPresent()) {
                context.selection().select(pinOfBox.get().id());
                context.editValue(pinOfBox.get());
                return true;
            }
        }
        if (event.clickCount() >= 2 && continuesOwnClick && !event.controlDown()
                && hit.isPresent() && hit.get() instanceof Wire clickedWire) {
            selectWireGroup(context, event, clickedWire);
            return true;
        }
        if (event.clickCount() >= 2 && continuesOwnClick && hit.isPresent()) {
            if (hit.get() instanceof Component component) {
                context.selection().select(component.id());
                if (toggledByFirstClick != null && toggledByFirstClick.id().equals(component.id())
                        && InterfacePins.isBusInput(component)) {
                    // the first click toggled the pin: put its value back, then edit that value
                    context.openDocument().setInputValue(component.id(), toggledByFirstClick.valueBefore());
                    context.editValue(component, toggledByFirstClick.valueBefore());
                } else if (component.typeId().equals(StandardComponentTypes.CONSTANT)
                        || InterfacePins.isBusInput(component) || Tunnels.isTunnel(component.typeId())) {
                    context.editValue(component);
                } else {
                    context.configure(component);
                }
                return true;
            }
            if (hit.get() instanceof TextLabel label) {
                context.selection().select(label.id());
                context.editLabel(label);
                return true;
            }
        }
        return false;
    }

    /** A press with Shift or Ctrl held: toggles the pin's component or the element, or starts a band when empty. */
    private void pressWithModifier(ToolContext context, CanvasMouseEvent event, Optional<DocumentElement> hit,
                                   Optional<ConnectionPoints.PinHit> pin) {
        Optional<DocumentElement> target = pin.<DocumentElement>map(ConnectionPoints.PinHit::component).or(() -> hit);
        if (target.isPresent() && !event.controlDown()) {
            // Shift+click toggles the element; Shift+drag moves the selection with it (the key inverts the mode)
            pressedElement = target.get();
            selectionClick = true;
            if (context.selection().contains(target.get().id())) {
                removeOnRelease = true;
            } else {
                context.selection().add(target.get().id());
            }
            pressPoint = event.snapped();
            return;
        }
        if (target.isPresent()) {
            context.selection().toggle(target.get().id());
        } else {
            startBand(event, event.shiftDown() && event.controlDown() ? BandMode.REMOVE : BandMode.ADD);
        }
    }

    /** A plain press on a pin: grabs the selection (see {@link #grabsSelection}) or starts a wire from it. */
    private void pressPin(ToolContext context, CanvasMouseEvent event, ConnectionPoints.PinHit pin) {
        pressedElement = pin.component();
        if (grabsSelection(context, pin.component())) {
            pressPoint = event.snapped();
            narrowOnRelease = true;
        } else {
            wire = new WireDrawing(pin.point(), event.gridPosition(),
                    widthAt(context, pin.point()));
        }
    }

    /** A plain press on an unselected wire starts a new wire at the pressed point on it. */
    private void pressWire(CanvasMouseEvent event, Wire pressedWire) {
        wire = new WireDrawing(ConnectionPoints.pointOnWire(pressedWire, event.gridPosition()),
                event.gridPosition());
    }

    /** A plain press on an element: selects it, or keeps a selection that holds it (to move it or narrow it). */
    private void pressElement(ToolContext context, CanvasMouseEvent event, DocumentElement element) {
        if (context.selection().contains(element.id())) {
            narrowOnRelease = context.selection().size() > 1;
        } else {
            context.selection().select(element.id());
        }
        pressPoint = event.snapped();
    }

    @Override
    public void mouseDragged(ToolContext context, CanvasMouseEvent event) {
        if (activeDelegate != null) {
            activeDelegate.dragged(context, event);
            return;
        }
        if (bandStart != null) {
            bandEnd = event.gridPosition();
            context.repaint();
            return;
        }
        if (labelComponent != null) {
            dragLabel(context, event);
            return;
        }
        if (wire != null) {
            wire.dragTo(context, event);
            context.repaint();
            return;
        }
        if (pressPoint == null) {
            return;
        }
        lastDrag = event;
        dragTo(context, event);
        // Ctrl at the release copies, and a copy never drags wires: the preview must not show them following
        followWires = !event.controlDown()
                && (segmentDrag || context.selectionMoves().follows(event.shiftDown(), event.altDown()));
        showCursor(context, segmentDrag ? segmentWire(context).map(SelectTool::slideCursor).orElse(CanvasCursor.MOVE)
                : CanvasCursor.MOVE);
        context.repaint();
    }

    @Override
    public void mouseReleased(ToolContext context, CanvasMouseEvent event) {
        if (event.button() != CanvasMouseEvent.PRIMARY) {
            return;
        }
        if (activeDelegate != null) {
            SelectToolDelegate released = activeDelegate;
            activeDelegate = null;
            released.released(context, event);
            updateHover(context, event);
            context.repaint();
            return;
        }
        if (bandStart != null) {
            finishBand(context);
        } else if (labelComponent != null) {
            releaseLabel(context);
        } else if (wire != null) {
            releaseWire(context, event);
        } else if (pressPoint != null) {
            releaseMove(context, event);
        }
        reset();
        updateHover(context, event);
        context.repaint();
    }

    /** The release of a wire drawing: adds the wire, or (it never moved, or added nothing) is a plain click. */
    private void releaseWire(ToolContext context, CanvasMouseEvent event) {
        boolean added = wire.hasMoved() && wire.commit(context, event.snapped());
        if (!added) {
            click(context);
        }
    }

    /**
     * The release of a press that may move the selection: moves, slides or copies it when the drag has a delta;
     * otherwise a click that adjusts the selection or toggles a switch (unless the magnet pulled the drag back).
     */
    private void releaseMove(ToolContext context, CanvasMouseEvent event) {
        boolean pointerMoved = pointerDeltaX != 0 || pointerDeltaY != 0;
        if (deltaX != 0 || deltaY != 0 || copyDeltaX != 0 || copyDeltaY != 0) {
            commitMove(context, event);
        } else if (pointerMoved) {
            // the magnet (or a locked frame's wall) pulled the drag back to where it started: nothing to do
        } else if (selectionClick) {
            if (removeOnRelease) {
                context.selection().remove(pressedElement.id());
            }
        } else {
            if (narrowOnRelease) {
                context.selection().select(pressedElement.id());
            }
            toggleIfInteractive(context);
        }
    }

    /** A drag released with a delta: a Ctrl copy, a segment slide or a move of the selection (one undo step). */
    private void commitMove(ToolContext context, CanvasMouseEvent event) {
        if (segmentWire(context).isPresent()) {
            dragTo(context, event); // the segment key at the release decides the direction
        }
        if (!context.selection().isEmpty()) {
            if (event.controlDown()) {
                copySelection(context);
            } else if (segmentDrag) {
                context.selectionMoves().moveSegment(context.openDocument(), context.selection(),
                        pressedElement.id(), deltaX, deltaY);
            } else {
                context.selectionMoves().move(context.openDocument(), context.selection(),
                        context.selection().effectiveIds(), deltaX, deltaY,
                        context.selectionMoves().follows(event.shiftDown(), event.altDown()));
            }
        }
    }

    /**
     * The delta of the drag; a move (not a Ctrl copy) stops at the border of a locked frame it does not carry. A
     * segment drag keeps only the part of the delta perpendicular to the wire.
     */
    private void dragTo(ToolContext context, CanvasMouseEvent event) {
        int rawDeltaX = event.snapped().x() - pressPoint.x();
        int rawDeltaY = event.snapped().y() - pressPoint.y();
        pointerDeltaX = rawDeltaX;
        pointerDeltaY = rawDeltaY;
        Optional<Wire> segment = segmentWire(context)
                .filter(wire -> context.selectionMoves().segmentKeyHeld(event.shiftDown(), event.altDown()));
        segmentDrag = segment.isPresent();
        Set<ElementId> moved = context.selection().effectiveIds();
        boolean copy = event.controlDown();
        snappedMove = segmentDrag ? SnappedMove.segment(context, segment.get(), rawDeltaX, rawDeltaY)
                : SnappedMove.of(context, moved, copy, rawDeltaX, rawDeltaY);
        deltaX = snappedMove.deltaX();
        deltaY = snappedMove.deltaY();
        copyDeltaX = deltaX;
        copyDeltaY = deltaY;
        if (!copy && !context.selection().isEmpty()) {
            GridPoint limited = SelectionMoves.limited(context.openDocument(), moved, deltaX, deltaY);
            snappedMove = snappedMove.clampedTo(context, moved, false, limited.x(), limited.y());
            deltaX = limited.x();
            deltaY = limited.y();
        }
    }

    /**
     * The wire a segment drag would move: the press was on a horizontal or vertical wire that is, alone, the whole
     * selection.
     */
    private Optional<Wire> segmentWire(ToolContext context) {
        if (!(pressedElement instanceof Wire pressedWire)
                || !context.selection().effectiveIds().equals(Set.of(pressedWire.id()))) {
            return Optional.empty();
        }
        // every wire can slide: the Wire constructor already rejects zero-length and diagonal wires
        return context.openDocument().document().wire(pressedWire.id());
    }

    @Override
    public void mouseMoved(ToolContext context, CanvasMouseEvent event) {
        updateHover(context, event);
    }

    @Override
    public void mouseExited(ToolContext context) {
        lastHover = null;
        delegates.forEach(delegate -> delegate.exited(context));
        if (hoverPin != null) {
            hoverPin = null;
            context.repaint();
        }
    }

    private void updateHover(ToolContext context, CanvasMouseEvent event) {
        lastHover = event;
        Optional<CanvasCursor> delegateCursor = Optional.empty();
        for (SelectToolDelegate delegate : delegates) {
            Optional<CanvasCursor> answer = delegate.moved(context, event);
            if (delegateCursor.isEmpty()) {
                delegateCursor = answer;
            }
        }
        if (delegateCursor.isPresent()) {
            // a press here goes to the delegate, never to a pin
            if (hoverPin != null) {
                hoverPin = null;
                hoverWidth = 1;
                context.repaint();
            }
            showCursor(context, delegateCursor.get());
            return;
        }
        Optional<ConnectionPoints.PinHit> pin = pinNear(context, event.gridPosition());
        GridPoint nearest = pin.map(ConnectionPoints.PinHit::point).orElse(null);
        if (!Objects.equals(nearest, hoverPin)) {
            hoverPin = nearest;
            hoverWidth = nearest == null ? 1 : widthAt(context, nearest);
            context.repaint();
        }
        showCursor(context, cursorAt(context, event.gridPosition(), pin, event));
    }

    /** The cursor of a segment drag of {@code wire}: it slides across its own direction. */
    private static CanvasCursor slideCursor(Wire wire) {
        return wire.start().y() == wire.end().y() ? CanvasCursor.SLIDE_VERTICAL : CanvasCursor.SLIDE_HORIZONTAL;
    }

    /**
     * What a plain primary press at {@code gridPosition} would do, as a cursor: the priority of {@link #press}.
     * {@code nearestPin} is the pin nearest to {@code gridPosition}, if any.
     */
    private CanvasCursor cursorAt(ToolContext context, Point2D gridPosition,
                                  Optional<ConnectionPoints.PinHit> nearestPin, CanvasMouseEvent event) {
        CircuitDocument document = context.openDocument().document();
        Optional<DocumentElement> hit = context.hitTester().elementAt(document, gridPosition);
        // A free text is drawn on top of everything, so it hides a pin underneath.
        Optional<ConnectionPoints.PinHit> pin = hit.isPresent() && hit.get() instanceof TextLabel
                ? Optional.empty()
                : nearestPin;
        if (event.shiftDown() || event.controlDown()) {
            return cursorWithModifier(context, hit, pin, event);
        }
        if (pin.isPresent()) {
            return grabsSelection(context, pin.get().component()) ? CanvasCursor.MOVE : CanvasCursor.CROSSHAIR;
        }
        if (labelPressedAt(context, gridPosition, hit).isPresent()) {
            return CanvasCursor.MOVE;
        }
        if (hit.isEmpty()) {
            return CanvasCursor.DEFAULT;
        }
        return cursorOnElement(context, hit.get(), event);
    }

    /** The cursor with Shift or Ctrl held: a slide of the one selected wire, or what a press does to the selection. */
    private CanvasCursor cursorWithModifier(ToolContext context, Optional<DocumentElement> hit,
                                            Optional<ConnectionPoints.PinHit> pin, CanvasMouseEvent event) {
        Optional<CanvasCursor> slide = hit.filter(element -> !event.controlDown())
                .flatMap(element -> slidingWire(context, element, event)).map(SelectTool::slideCursor);
        if (slide.isPresent()) {
            return slide.get(); // the segment key is Shift (Alt inverts the follow mode): a drag slides the wire
        }
        // the press toggles the pin's component or the element, or bands on empty canvas (Ctrl+Shift subtracts)
        Optional<DocumentElement> target = pin.<DocumentElement>map(ConnectionPoints.PinHit::component).or(() -> hit);
        if (target.isEmpty()) {
            return event.shiftDown() && event.controlDown() ? CanvasCursor.REMOVE_FROM_SELECTION
                    : CanvasCursor.ADD_TO_SELECTION;
        }
        return context.selection().contains(target.get().id()) ? CanvasCursor.REMOVE_FROM_SELECTION
                : CanvasCursor.ADD_TO_SELECTION;
    }

    /** The cursor of a plain press on {@code element} (no pin, no label in the way). */
    private CanvasCursor cursorOnElement(ToolContext context, DocumentElement element, CanvasMouseEvent event) {
        boolean selected = context.selection().contains(element.id());
        if (element instanceof Wire && !selected) {
            return CanvasCursor.CROSSHAIR;
        }
        Optional<Wire> sliding = slidingWire(context, element, event);
        if (sliding.isPresent()) {
            return slideCursor(sliding.get());
        }
        if (element instanceof Component component && isInteractive(context, component)) {
            return CanvasCursor.HAND;
        }
        return selected ? CanvasCursor.MOVE : CanvasCursor.DEFAULT;
    }

    /** {@code element} as the one selected wire, when the segment key is held over it: a drag would slide it. */
    private static Optional<Wire> slidingWire(ToolContext context, DocumentElement element, CanvasMouseEvent event) {
        if (element instanceof Wire hoveredWire && context.selection().effectiveIds().equals(Set.of(hoveredWire.id()))
                && context.selectionMoves().segmentKeyHeld(event.shiftDown(), event.altDown())) {
            return Optional.of(hoveredWire);
        }
        return Optional.empty();
    }

    /**
     * While a press drags: a wire (crosshair), a rubber band (the arrow; with a «+» when it adds, a «−» when it
     * subtracts) or a move (move).
     */
    private Optional<CanvasCursor> pressCursor() {
        if (wire != null) {
            return Optional.of(CanvasCursor.CROSSHAIR);
        }
        if (bandStart != null) {
            return Optional.of(switch (bandMode) {
                case REPLACE -> CanvasCursor.DEFAULT;
                case ADD -> CanvasCursor.ADD_TO_SELECTION;
                case REMOVE -> CanvasCursor.REMOVE_FROM_SELECTION;
            });
        }
        if (pressPoint != null) {
            return Optional.of(CanvasCursor.MOVE);
        }
        return Optional.empty();
    }

    private void showCursor(ToolContext context, CanvasCursor next) {
        if (next != cursor) {
            cursor = next;
            context.setCursor(next);
        }
    }

    /**
     * During a move (or a delegate's drag), redoes the drag at the last pointer position with the new keys: the
     * preview, the hidden wires, the segment mode and the cursor follow Shift, Ctrl and Alt at once. With no press
     * under way, redoes the hover at the last pointer position, so the cursor shows what a press would do now.
     */
    @Override
    public boolean modifiersChanged(ToolContext context, boolean shiftDown, boolean controlDown, boolean altDown) {
        if (activeDelegate != null) {
            return activeDelegate.modifiersChanged(context, shiftDown, controlDown, altDown);
        }
        if (isIdle() && lastHover != null) {
            updateHover(context, lastHover.withModifiers(shiftDown, controlDown, altDown));
            return false; // only the cursor (and the hover ring) changed: no preview to animate
        }
        if (lastDrag == null || pressPoint == null || wire != null || bandStart != null) {
            return false;
        }
        mouseDragged(context, lastDrag.withModifiers(shiftDown, controlDown, altDown));
        return true;
    }

    /**
     * A snap preference, the zoom or the guides changed: a move (or a delegate's drag) is redone at the last pointer
     * position, so its preview, its lines and what a release commits follow at once.
     */
    @Override
    public boolean refreshSnap(ToolContext context) {
        if (activeDelegate != null) {
            return activeDelegate.refreshSnap(context);
        }
        if (wire != null) {
            boolean changed = wire.refreshSnap(context);
            if (changed) {
                context.repaint();
            }
            return changed;
        }
        if (lastDrag == null || pressPoint == null || wire != null || bandStart != null || labelComponent != null) {
            return false;
        }
        mouseDragged(context, lastDrag);
        return true;
    }

    @Override
    public List<Guide> snappedGuides() {
        if (activeDelegate != null) {
            return activeDelegate.snappedGuides();
        }
        if (wire != null) {
            return wire.snappedGuides();
        }
        return lastDrag != null && pressPoint != null ? MatchedGuides.of(snappedMove.matches()) : List.of();
    }

    /** A Ctrl copy is being dragged (Ctrl at the release copies). */
    private boolean copying() {
        return lastDrag != null && lastDrag.controlDown();
    }

    /** The new contacts the current move shows (spec §3.7): none when nothing is being moved. */
    List<NewContact> contacts(ToolContext context) {
        if (activeDelegate != null || pressPoint == null || wire != null || bandStart != null
                || labelOffset != null) {
            return List.of();
        }
        return movePreview.contacts(context, context.selection().effectiveIds(), deltaX, deltaY, followWires,
                copying());
    }

    /** For tests: the alignment lines the current move draws. */
    List<SnapMatch> alignmentMatches() {
        return snappedMove.matches();
    }

    @Override
    public boolean keyPressed(ToolContext context, int keyCode, boolean shiftDown) {
        if (keyCode != KeyEvent.VK_ESCAPE) {
            return false;
        }
        if (activeDelegate != null) {
            cancelDelegate(context);
            context.repaint();
            return true;
        }
        if (pressPoint != null || wire != null || bandStart != null || labelComponent != null) {
            reset();
            context.repaint();
        } else {
            context.selection().clear();
        }
        return true;
    }

    @Override
    public void paintOverlay(Graphics2D graphics, ToolContext context) {
        for (SelectToolDelegate delegate : delegates) {
            delegate.paintOverlay(graphics, context);
        }
        Rectangle2D band = rubberBand();
        if (band != null) {
            paintBand(graphics, context, band, bandMode == BandMode.REMOVE);
            return;
        }
        if (hoverPin != null && wire == null) {
            paintHover(graphics, context);
        }
        if (wire != null) {
            wire.paintPreview(graphics, context);
            return;
        }
        if (labelOffset != null) {
            paintLabelPreview(graphics, context);
            return;
        }
        if (pressPoint == null) {
            return;
        }
        movePreview.paint(graphics, context, context.selection().effectiveIds(), deltaX, deltaY, followWires,
                copying(), Optional.ofNullable(lastDrag).map(CanvasMouseEvent::gridPosition));
        if (lastDrag != null) {
            AlignmentLinesPainter.paint(graphics, context, snappedMove.movedBox(), snappedMove.matches());
        }
    }

    @Override
    public Set<ElementId> hiddenWires(ToolContext context) {
        if (activeDelegate != null) {
            return activeDelegate.hiddenWires(context);
        }
        if (pressPoint == null || wire != null || bandStart != null || !followWires) {
            return Set.of();
        }
        return movePreview.hiddenWires(context, context.selection().effectiveIds(), deltaX, deltaY);
    }

    /** A circle around a 1-bit pin; a square of the same footprint, tagged with the width, around a bus pin. */
    private void paintHover(Graphics2D graphics, ToolContext context) {
        double unit = GridPoint.PIXELS_PER_UNIT;
        double centerX = hoverPin.x() * unit;
        double centerY = hoverPin.y() * unit;
        graphics.setColor(context.scheme().selection());
        graphics.setStroke(HOVER_RING);
        if (hoverWidth <= 1) {
            graphics.draw(new Ellipse2D.Double(centerX - HOVER_RING_RADIUS, centerY - HOVER_RING_RADIUS,
                    2 * HOVER_RING_RADIUS, 2 * HOVER_RING_RADIUS));
            return;
        }
        graphics.draw(new Rectangle2D.Double(centerX - HOVER_RING_RADIUS, centerY - HOVER_RING_RADIUS,
                2 * HOVER_RING_RADIUS, 2 * HOVER_RING_RADIUS));
        graphics.setFont(TextFonts.tagFont());
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        PainterSupport.paintTag(graphics, graphics.getFontMetrics(), String.valueOf(hoverWidth),
                centerX + HOVER_TAG_OFFSET, centerY - HOVER_TAG_OFFSET + 2, context.scheme().selection(),
                context.scheme().background());
    }

    @Override
    public boolean animatesOutline() {
        return bandStart != null;
    }

    GridPoint hoverPin() {
        return hoverPin;
    }

    /** The rubber band in grid units, or {@code null} when none is being dragged. */
    Rectangle2D rubberBand() {
        if (bandStart == null) {
            return null;
        }
        return new Rectangle2D.Double(Math.min(bandStart.getX(), bandEnd.getX()),
                Math.min(bandStart.getY(), bandEnd.getY()),
                Math.abs(bandEnd.getX() - bandStart.getX()), Math.abs(bandEnd.getY() - bandStart.getY()));
    }

    private void startBand(CanvasMouseEvent event, BandMode mode) {
        bandStart = event.gridPosition();
        bandEnd = event.gridPosition();
        bandMode = mode;
    }

    /**
     * A band smaller than half a grid unit is a click: it clears the selection unless Shift or Ctrl was held. A
     * bigger one replaces the selection with what it encloses, adds that to it, or (Ctrl+Shift) takes that out of it.
     */
    private void finishBand(ToolContext context) {
        Rectangle2D band = rubberBand();
        if (Math.max(band.getWidth(), band.getHeight()) < BAND_THRESHOLD) {
            if (bandMode == BandMode.REPLACE) {
                context.selection().clear();
            }
            return;
        }
        Set<ElementId> inside = elementsInside(context, band);
        switch (bandMode) {
            case REPLACE -> context.selection().set(inside);
            case ADD -> {
                Set<ElementId> combined = new LinkedHashSet<>(context.selection().ids());
                combined.addAll(inside);
                context.selection().set(combined);
            }
            case REMOVE -> {
                Set<ElementId> remaining = new LinkedHashSet<>(context.selection().ids());
                remaining.removeAll(inside);
                context.selection().set(remaining);
            }
        }
    }

    private static Set<ElementId> elementsInside(ToolContext context, Rectangle2D band) {
        Set<ElementId> inside = new LinkedHashSet<>();
        for (DocumentElement element : context.openDocument().document().elements()) {
            Bounds bounds = ElementBounds.of(element, context.openDocument().registry());
            if (bounds.x() >= band.getMinX() && bounds.maximumX() <= band.getMaxX()
                    && bounds.y() >= band.getMinY() && bounds.maximumY() <= band.getMaxY()) {
                inside.add(element.id());
            }
        }
        return inside;
    }

    /** The band in the selection colour; a subtracting one in the scheme's conflict red, more faintly filled. */
    private static void paintBand(Graphics2D graphics, ToolContext context, Rectangle2D band, boolean subtracts) {
        double unit = GridPoint.PIXELS_PER_UNIT;
        Rectangle2D pixels = new Rectangle2D.Double(band.getX() * unit, band.getY() * unit,
                band.getWidth() * unit, band.getHeight() * unit);
        Color color = subtracts ? context.scheme().wireConflict() : context.scheme().selection();
        RubberBand.paint(graphics, pixels, color, subtracts ? SUBTRACT_BAND_FILL_ALPHA : BAND_FILL_ALPHA, BAND_WIDTH,
                context.selectionDashPhase());
    }

    /** A pin of a selected component moves the selection when the selection holds more than one element. */
    private static boolean grabsSelection(ToolContext context, Component component) {
        return context.selection().contains(component.id()) && context.selection().size() > 1;
    }

    /**
     * The snapped point where the current press on a selected element started, while that press may still become a
     * move of the selection; empty with no press, while drawing a wire or while dragging a rubber band. The canvas
     * uses it to start a drag out.
     */
    public Optional<GridPoint> movingFrom() {
        return wire == null && bandStart == null ? Optional.ofNullable(pressPoint) : Optional.empty();
    }

    /**
     * Every gesture under way goes on over the other panes of the window: a move or a Ctrl copy, a segment slide, a
     * wire, a rubber band, a label drag and a delegate's gesture (a frame moved or resized).
     */
    @Override
    public boolean crossesPanes() {
        return !isIdle();
    }

    /** True when no gesture is under way: no press, wire, rubber band or delegate (a frame resize, a label drag). */
    public boolean isIdle() {
        return pressPoint == null && wire == null && bandStart == null && activeDelegate == null
                && labelComponent == null;
    }

    /** Forgets the press in progress without changing the document: the drag goes on outside the canvas. */
    public void cancelMove(ToolContext context) {
        reset();
        context.repaint();
    }

    /**
     * True while a drag moves the selection, a rubber band or a delegate's press: the canvas scrolls when the pointer
     * nears its edge.
     */
    public boolean autoscrolls() {
        return bandStart != null || movingFrom().isPresent() || activeDelegate != null || labelComponent != null;
    }

    /**
     * M6.8: a double click on a wire selects its stretch, a triple click every wire connected to it; Shift adds them
     * to the selection. The press never starts a wire drawing: a drag that follows moves the selection.
     */
    private void selectWireGroup(ToolContext context, CanvasMouseEvent event, Wire clickedWire) {
        CircuitDocument document = context.openDocument().document();
        Set<ElementId> group = event.clickCount() >= 3
                ? WireStretch.connected(document, context.openDocument().registry(), clickedWire.id())
                : WireStretch.of(document, context.openDocument().registry(), clickedWire.id());
        if (event.shiftDown()) {
            Set<ElementId> extended = new LinkedHashSet<>(context.selection().ids());
            extended.addAll(group);
            context.selection().set(extended);
        } else {
            context.selection().set(group);
        }
        pressedElement = clickedWire;
        pressPoint = event.snapped();
    }

    /** A press on a pin or wire that did not become a wire: behave like a plain click on what was under it. */
    private void click(ToolContext context) {
        if (pressedElement == null) {
            context.selection().clear();
            return;
        }
        context.selection().select(pressedElement.id());
        toggleIfInteractive(context);
    }

    /** Ctrl held at the release: the selection stays put and a copy lands where the move would have put it. */
    private void copySelection(ToolContext context) {
        Set<ElementId> ids = context.selection().effectiveIds();
        Optional<GridPoint> topLeft = SelectionBounds.topLeft(context.openDocument(), ids);
        if (topLeft.isEmpty()) {
            return;
        }
        CircuitFragment fragment = CircuitFragment.of(context.openDocument().document(), ids,
                context.openDocument().registry());
        PasteCommand paste = new PasteCommand(fragment, topLeft.get().translate(copyDeltaX, copyDeltaY));
        context.openDocument().execute(paste);
        context.selection().set(paste.pastedIds());
    }

    private void toggleIfInteractive(ToolContext context) {
        if (pressedElement instanceof Component component && isInteractive(context, component)) {
            if (InterfacePins.isBusInput(component) && context.openDocument().latestSnapshot()
                    .componentState(component.id().value()) instanceof VisibleState.SwitchState state) {
                lastBusInputToggle = new ToggledBusInput(component.id(), state.value());
            }
            context.openDocument().interact(component.id());
        }
    }

    /** A bus input pin a click toggled, with the value it showed before. */
    private record ToggledBusInput(ElementId id, Value valueBefore) {
    }

    /** The pin nearest to {@code gridPosition}: a scan of every pin of the document, counted for tests. */
    private Optional<ConnectionPoints.PinHit> pinNear(ToolContext context, Point2D gridPosition) {
        pinSearches++;
        return connectionPoints(context).pinNear(context.openDocument().document(), gridPosition);
    }

    /** For tests: how many times the nearest pin was looked for. */
    int pinSearches() {
        return pinSearches;
    }

    /** The width a wire starting at {@code point} previews with. */
    private int widthAt(ToolContext context, GridPoint point) {
        return connectionPoints(context).widthAt(context.openDocument().document(), context.openDocument().netlist(),
                point);
    }

    private ConnectionPoints connectionPoints(ToolContext context) {
        if (connectionPoints == null) {
            connectionPoints = new ConnectionPoints(context.openDocument().registry());
        }
        return connectionPoints;
    }

    /**
     * The component whose label a press at {@code gridPosition} takes, given the element {@code hit} there: a free
     * text is drawn on top of labels, and a component's body wins over a label moved onto it (its own included), so
     * the body can still be dragged and configured from there. A label only beats a wire.
     */
    private static Optional<Component> labelPressedAt(ToolContext context, Point2D gridPosition,
                                                      Optional<DocumentElement> hit) {
        if (hit.isPresent() && (hit.get() instanceof TextLabel || hit.get() instanceof Component)) {
            return Optional.empty();
        }
        return componentLabelAt(context, gridPosition);
    }

    /** The bus input pin whose value box holds {@code gridPosition}, if any (the topmost one when boxes overlap). */
    private static Optional<Component> busInputValueBoxAt(ToolContext context, Point2D gridPosition) {
        double documentX = gridPosition.getX() * GridPoint.PIXELS_PER_UNIT;
        double documentY = gridPosition.getY() * GridPoint.PIXELS_PER_UNIT;
        List<Component> components = context.openDocument().document().components();
        for (int index = components.size() - 1; index >= 0; index--) {
            Component component = components.get(index);
            if (InterfacePins.isBusInput(component) && InterfacePinPainter.valueBox(
                    context.openDocument().registry().typeOf(component), component)
                    .map(box -> box.contains(documentX, documentY)).orElse(false)) {
                return Optional.of(component);
            }
        }
        return Optional.empty();
    }

    /** The component whose drawn label holds {@code gridPosition}, if any (the topmost one when labels overlap). */
    private static Optional<Component> componentLabelAt(ToolContext context, Point2D gridPosition) {
        double documentX = gridPosition.getX() * GridPoint.PIXELS_PER_UNIT;
        double documentY = gridPosition.getY() * GridPoint.PIXELS_PER_UNIT;
        List<Component> components = context.openDocument().document().components();
        for (int index = components.size() - 1; index >= 0; index--) {
            Component component = components.get(index);
            boolean onLabel = LabelLayout.componentLabel(context.openDocument().registry().typeOf(component), component)
                    .map(label -> label.bounds().contains(documentX, documentY))
                    .orElse(false);
            if (onLabel) {
                return Optional.of(component);
            }
        }
        return Optional.empty();
    }

    private boolean isInteractive(ToolContext context, Component component) {
        return interactiveByType.computeIfAbsent(component.typeId(), typeId -> context.openDocument().registry()
                .require(typeId).createBehavior(component.parameters()).isInteractive());
    }

    /** For tests: the dragged component as it would be with its label at the drag's place. */
    Optional<Component> labelDragPreview() {
        return labelOffset == null ? Optional.empty()
                : Optional.of(labelComponent.withLabelOffset(Optional.of(labelOffset)));
    }

    /** M6.8: a press on a component's drawn label; a drag moves only the label, a click selects the component. */
    private void pressLabel(ToolContext context, CanvasMouseEvent event, Component component) {
        Rectangle2D bounds = LabelLayout.componentLabel(context.openDocument().registry().typeOf(component), component)
                .orElseThrow().bounds();
        labelComponent = component;
        labelPressPosition = event.gridPosition();
        labelPressCentre = new Point2D.Double(bounds.getCenterX(), bounds.getCenterY());
    }

    /** The label's centre follows the pointer, snapped to grid points, once the drag passes the threshold. */
    private void dragLabel(ToolContext context, CanvasMouseEvent event) {
        double draggedX = event.gridPosition().getX() - labelPressPosition.getX();
        double draggedY = event.gridPosition().getY() - labelPressPosition.getY();
        if (labelOffset == null && Math.max(Math.abs(draggedX), Math.abs(draggedY)) < BAND_THRESHOLD) {
            return;
        }
        double unit = GridPoint.PIXELS_PER_UNIT;
        labelOffset = LabelLayout.offsetFor(labelComponent, labelPressCentre.getX() + draggedX * unit,
                labelPressCentre.getY() + draggedY * unit);
        showCursor(context, CanvasCursor.MOVE);
        context.repaint();
    }

    /** A drag sets the label's offset (one undo step); either way the component ends up selected. */
    private void releaseLabel(ToolContext context) {
        ElementId id = labelComponent.id();
        if (context.openDocument().document().component(id).isEmpty()) {
            return; // removed while the press was held (an undo): nothing to select or move
        }
        if (labelOffset == null) {
            context.selection().select(id);
            return;
        }
        if (!context.selection().contains(id)) {
            context.selection().select(id);
        }
        Optional<LabelOffset> moved = Optional.of(labelOffset);
        boolean changed = context.openDocument().document().component(id)
                .map(current -> !current.labelOffset().equals(moved)).orElseThrow();
        if (changed) {
            context.openDocument().execute(new SetLabelOffsetCommand(id, moved));
        }
    }

    /** The dragged label at its new place, in the selection colour (read at paint time). */
    private void paintLabelPreview(Graphics2D graphics, ToolContext context) {
        Component moved = labelComponent.withLabelOffset(Optional.of(labelOffset));
        LabelLayout.PlacedText placed = LabelLayout.componentLabelPlace(
                context.openDocument().registry().typeOf(moved), moved, moved.label());
        graphics.setColor(context.scheme().selection());
        graphics.setFont(LabelLayout.font());
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.drawString(placed.text(), placed.baselineX(), placed.baselineY());
        graphics.setStroke(new BasicStroke(BAND_WIDTH));
        graphics.draw(placed.bounds());
    }

    private void reset() {
        pressedElement = null;
        pressPoint = null;
        narrowOnRelease = false;
        selectionClick = false;
        removeOnRelease = false;
        wire = null;
        bandStart = null;
        bandEnd = null;
        bandMode = BandMode.REPLACE;
        deltaX = 0;
        deltaY = 0;
        copyDeltaX = 0;
        copyDeltaY = 0;
        pointerDeltaX = 0;
        pointerDeltaY = 0;
        followWires = false;
        segmentDrag = false;
        lastDrag = null;
        snappedMove = SnappedMove.unsnapped(0, 0);
        movePreview.clear();
        labelComponent = null;
        labelPressPosition = null;
        labelPressCentre = null;
        labelOffset = null;
    }
}
