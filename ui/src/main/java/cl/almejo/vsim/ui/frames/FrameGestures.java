package cl.almejo.vsim.ui.frames;

import cl.almejo.vsim.core.command.ChangeFrameCommand;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.Guide;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.canvas.Selection;
import cl.almejo.vsim.ui.tools.CanvasCursor;
import cl.almejo.vsim.ui.tools.CanvasMouseEvent;
import cl.almejo.vsim.ui.tools.ElementDrag;
import cl.almejo.vsim.ui.tools.SelectToolDelegate;
import cl.almejo.vsim.ui.tools.ToolContext;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * What the select tool does with frames (spec §2, §3, §7): a press on the title bar selects the frame alone (locked
 * or not) and dragging moves what the selection acts on, so a locked frame carries its block (see
 * {@link FramesFeatures#lockedBlocks}); dragging an edge or corner of an unlocked frame resizes it. Each gesture is one
 * undo step. While a gesture drags, everything outside the frame is veiled and ghosts show what moves. Unlocked frames
 * show eight handles while hovered or selected. A move drags the attached wires like the select tool's (M6.7: the
 * follow mode, inverted by the configured key held while dragging). A move snaps like the select tool's (Ajustar,
 * before the walls of a locked frame, which still win) and draws its alignment lines; a resize does not snap (v1).
 */
public final class FrameGestures implements SelectToolDelegate {

    /** Side of a handle, in screen pixels. */
    static final double HANDLE_PIXELS = 6;

    private Frame grabbed;
    private FramePart part;
    private final ElementDrag drag = new ElementDrag();
    private ElementId hovered;

    @Override
    public Press pressed(ToolContext context, CanvasMouseEvent event) {
        CircuitDocument document = context.openDocument().document();
        Optional<FrameHit> hit = FrameHitTester.hit(document, event.gridPosition(),
                context.viewport().pixelsPerUnit());
        if (hit.isEmpty()) {
            return Press.IGNORED;
        }
        Frame frame = hit.get().frame();
        FramePart hitPart = hit.get().part();
        if (event.button() == CanvasMouseEvent.PRIMARY && (hitPart == FramePart.LOCK || hitPart == FramePart.MENU)) {
            pressControl(context, event, frame, hitPart);
            return Press.HANDLED;
        }
        Selection selection = context.selection();
        if (event.button() == CanvasMouseEvent.SECONDARY) {
            if (!selection.contains(frame.id())) {
                selection.select(frame.id());
            }
            context.showContextMenu(Optional.of(frame), event.screenPosition());
            return Press.HANDLED;
        }
        if (event.button() != CanvasMouseEvent.PRIMARY) {
            return Press.IGNORED;
        }
        if (event.shiftDown() && !event.controlDown() && hitPart == FramePart.TITLE_BAR) {
            // Shift+click toggles the frame; Shift+drag moves the selection with it (the key inverts the mode)
            grab(frame, hitPart, event);
            drag.toggleOnPress(selection, frame.id());
            return Press.GRABBED;
        }
        if (event.shiftDown() || event.controlDown()) {
            selection.toggle(frame.id());
            return Press.HANDLED;
        }
        if (event.clickCount() >= 2 && hitPart == FramePart.TITLE_BAR) {
            // handled without a grab: the editor opens on this press and the release is ignored, so none is awaited
            selection.select(frame.id());
            context.editLabel(frame);
            return Press.HANDLED;
        }
        grab(frame, hitPart, event);
        drag.selectOnPress(selection, frame.id());
        return Press.GRABBED;
    }

    private void grab(Frame frame, FramePart hitPart, CanvasMouseEvent event) {
        grabbed = frame;
        part = hitPart;
        drag.start(event, hitPart.resizes());
    }

    /** The padlock toggles the lock; the ⋯ button opens the frame menu. */
    private static void pressControl(ToolContext context, CanvasMouseEvent event, Frame frame, FramePart control) {
        if (control == FramePart.LOCK) {
            context.openDocument().execute(new ChangeFrameCommand(frame, frame.withLocked(!frame.locked())));
            return;
        }
        context.selection().select(frame.id());
        context.showContextMenu(Optional.of(frame), event.screenPosition());
    }

    @Override
    public void dragged(ToolContext context, CanvasMouseEvent event) {
        drag.dragged(context, event);
    }

    /** Redoes the drag at the last pointer position with the new keys, so the wires preview follows them. */
    @Override
    public boolean modifiersChanged(ToolContext context, boolean shiftDown, boolean controlDown, boolean altDown) {
        return drag.modifiersChanged(context, shiftDown, controlDown, altDown);
    }

    /** Redoes a move at the last pointer position after a snap preference, the zoom or the guides changed. */
    @Override
    public boolean refreshSnap(ToolContext context) {
        return drag.refreshSnap(context);
    }

    @Override
    public List<Guide> snappedGuides() {
        return drag.snappedGuides();
    }

    @Override
    public void released(ToolContext context, CanvasMouseEvent event) {
        if (grabbed == null) {
            return;
        }
        drag.dragged(context, event);
        if (part.resizes()) {
            finishResize(context);
        } else {
            drag.releaseMove(context, grabbed.id());
        }
        reset();
        context.repaint();
    }

    private void finishResize(ToolContext context) {
        Optional<Frame> current = context.openDocument().document().frame(grabbed.id());
        if (current.isEmpty() || current.get().locked()) {
            return;
        }
        Frame resized = resized(current.get());
        if (!resized.equals(current.get())) {
            context.openDocument().execute(new ChangeFrameCommand(current.get(), resized));
        }
    }

    /** {@code frame} with the grabbed edges moved by the drag, snapped to the grid, never under the minimum size. */
    private Frame resized(Frame frame) {
        int left = frame.topLeft().x();
        int top = frame.topLeft().y();
        int right = left + frame.width();
        int bottom = top + frame.height();
        if (part.movesWest()) {
            left = Math.min(left + drag.deltaX(), right - Frame.MINIMUM_WIDTH);
        }
        if (part.movesEast()) {
            right = Math.max(right + drag.deltaX(), left + Frame.MINIMUM_WIDTH);
        }
        if (part.movesNorth()) {
            top = Math.min(top + drag.deltaY(), bottom - Frame.MINIMUM_HEIGHT);
        }
        if (part.movesSouth()) {
            bottom = Math.max(bottom + drag.deltaY(), top + Frame.MINIMUM_HEIGHT);
        }
        return frame.withRectangle(new GridPoint(left, top), right - left, bottom - top);
    }

    @Override
    public Set<ElementId> hiddenWires(ToolContext context) {
        return drag.hiddenWires(context);
    }

    @Override
    public List<DocumentElement> movingElements(ToolContext context) {
        return drag.movingElements(context);
    }

    @Override
    public void cancelled(ToolContext context) {
        reset();
        context.repaint();
    }

    @Override
    public CanvasCursor pressCursor() {
        return part == null ? CanvasCursor.MOVE : part.cursor();
    }

    @Override
    public Optional<CanvasCursor> moved(ToolContext context, CanvasMouseEvent event) {
        CircuitDocument document = context.openDocument().document();
        double pixelsPerGrid = context.viewport().pixelsPerUnit();
        ElementId under = FrameHitTester.frameUnder(document, event.gridPosition(), pixelsPerGrid)
                .map(Frame::id).orElse(null);
        if (!Objects.equals(under, hovered)) {
            hovered = under;
            context.repaint();
        }
        return FrameHitTester.hit(document, event.gridPosition(), pixelsPerGrid)
                .map(hit -> hit.part().cursor());
    }

    @Override
    public void exited(ToolContext context) {
        if (hovered != null) {
            hovered = null;
            context.repaint();
        }
    }

    @Override
    public void paintOverlay(Graphics2D graphics, ToolContext context) {
        Optional<Rectangle2D> veil = veil();
        if (veil.isPresent()) {
            FrameVeil.paint(graphics, veil.get(), context.scheme());
            if (part.resizes()) {
                context.paintGhost(graphics, resized(grabbed));
            } else {
                drag.paintMove(graphics, context);
            }
        }
        double pixelsPerGrid = context.viewport().pixelsPerUnit();
        for (Frame frame : context.openDocument().document().frames()) {
            // the faint padlock of the other locked frames comes from FrameLockOverlay, whatever the tool
            if (frame.id().equals(hovered) || context.selection().contains(frame.id())) {
                FramePainter.paintControls(graphics, frame, true, pixelsPerGrid);
            }
        }
        double handleSize = HANDLE_PIXELS / Math.max(graphics.getTransform().getScaleX(), Double.MIN_VALUE);
        graphics.setColor(context.scheme().selection());
        for (Frame frame : framesWithHandles(context)) {
            for (Rectangle2D handle : handles(frame, handleSize)) {
                graphics.fill(handle);
            }
        }
    }

    /**
     * The rectangle left clear by the veil, in document pixels, while a gesture is dragging: the frame at its new
     * place or size. Empty with no gesture or before the pointer has moved a grid unit.
     */
    Optional<Rectangle2D> veil() {
        if (grabbed == null || !drag.displaced()) {
            return Optional.empty();
        }
        Frame shown = part.resizes() ? resized(grabbed) : grabbed.translate(drag.deltaX(), drag.deltaY());
        return Optional.of(FramePainter.bounds(shown));
    }

    /**
     * The unlocked frames that show their handles now: the hovered one and the selected ones; during a resize the
     * grabbed frame at its new size, during a move none.
     */
    List<Frame> framesWithHandles(ToolContext context) {
        List<Frame> frames = new ArrayList<>();
        boolean dragging = grabbed != null && drag.displaced();
        if (dragging) {
            if (part.resizes()) {
                frames.add(resized(grabbed));
            }
            return frames;
        }
        for (Frame frame : context.openDocument().document().frames()) {
            if (!frame.locked() && (frame.id().equals(hovered) || context.selection().contains(frame.id()))) {
                frames.add(frame);
            }
        }
        return frames;
    }

    /** The eight handles of {@code frame}: corners and edge midpoints, centered on the border. */
    static List<Rectangle2D> handles(Frame frame, double size) {
        Rectangle2D bounds = FramePainter.bounds(frame);
        double[] columns = {bounds.getMinX(), bounds.getCenterX(), bounds.getMaxX()};
        double[] rows = {bounds.getMinY(), bounds.getCenterY(), bounds.getMaxY()};
        List<Rectangle2D> handles = new ArrayList<>();
        for (int row = 0; row < rows.length; row++) {
            for (int column = 0; column < columns.length; column++) {
                if (row == 1 && column == 1) {
                    continue;
                }
                handles.add(new Rectangle2D.Double(columns[column] - size / 2, rows[row] - size / 2, size, size));
            }
        }
        return handles;
    }

    private void reset() {
        grabbed = null;
        part = null;
        drag.reset();
    }
}
