package cl.almejo.vsim.ui.frames;

import cl.almejo.vsim.core.command.ChangeFrameCommand;
import cl.almejo.vsim.core.command.MoveElementsCommand;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameContents;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.canvas.Selection;
import cl.almejo.vsim.ui.tools.CanvasCursor;
import cl.almejo.vsim.ui.tools.CanvasMouseEvent;
import cl.almejo.vsim.ui.tools.SelectToolDelegate;
import cl.almejo.vsim.ui.tools.ToolContext;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * What the select tool does with frames (spec §2, §3, §7): a press on the title bar selects the frame, or the block
 * (frame and contents) when it is locked, and dragging moves the selection; dragging an edge or corner of an unlocked
 * frame resizes it. Each gesture is one undo step. While a gesture drags, everything outside the frame is veiled.
 * Unlocked frames show eight handles while hovered or selected.
 */
public final class FrameGestures implements SelectToolDelegate {

    /** Side of a handle, in screen pixels. */
    static final double HANDLE_PIXELS = 6;

    private Frame grabbed;
    private FramePart part;
    private Set<ElementId> grabbedIds = Set.of();
    private GridPoint pressPoint;
    private boolean narrowOnRelease;
    private int deltaX;
    private int deltaY;
    private ElementId hovered;

    @Override
    public boolean pressed(ToolContext context, CanvasMouseEvent event) {
        CircuitDocument document = context.openDocument().document();
        Optional<FrameHit> hit = FrameHitTester.hit(document, event.gridPosition(),
                context.viewport().pixelsPerUnit());
        if (hit.isEmpty() || hit.get().part() == FramePart.LOCK || hit.get().part() == FramePart.MENU) {
            return false; // the controls arrive with the frame menu
        }
        Frame frame = hit.get().frame();
        Set<ElementId> ids = grabbedBy(context, frame);
        Selection selection = context.selection();
        if (event.button() == CanvasMouseEvent.SECONDARY) {
            if (!selection.ids().containsAll(ids)) {
                selection.set(ids);
            }
            context.showContextMenu(Optional.of(frame), event.screenPosition());
            return true;
        }
        if (event.button() != CanvasMouseEvent.PRIMARY) {
            return false;
        }
        if (event.shiftDown() || event.controlDown()) {
            toggle(selection, ids);
            return true;
        }
        if (event.clickCount() >= 2 && hit.get().part() == FramePart.TITLE_BAR) {
            selection.select(frame.id());
            context.editLabel(frame);
            return true;
        }
        grabbed = frame;
        part = hit.get().part();
        grabbedIds = ids;
        pressPoint = event.snapped();
        if (part.resizes()) {
            if (!selection.contains(frame.id())) {
                selection.select(frame.id());
            }
        } else if (selection.ids().containsAll(ids)) {
            narrowOnRelease = selection.size() > ids.size();
        } else {
            selection.set(ids);
        }
        return true;
    }

    /** What a press on the title bar grabs: the frame, or the frame and its contents when it is locked. */
    private static Set<ElementId> grabbedBy(ToolContext context, Frame frame) {
        return frame.locked()
                ? FrameContents.block(context.openDocument().document(), context.openDocument().registry(), frame)
                : Set.of(frame.id());
    }

    private static void toggle(Selection selection, Set<ElementId> ids) {
        Set<ElementId> combined = new LinkedHashSet<>(selection.ids());
        if (combined.containsAll(ids)) {
            combined.removeAll(ids);
        } else {
            combined.addAll(ids);
        }
        selection.set(combined);
    }

    @Override
    public void dragged(ToolContext context, CanvasMouseEvent event) {
        if (grabbed == null) {
            return;
        }
        deltaX = event.snapped().x() - pressPoint.x();
        deltaY = event.snapped().y() - pressPoint.y();
        context.repaint();
    }

    @Override
    public void released(ToolContext context, CanvasMouseEvent event) {
        if (grabbed == null) {
            return;
        }
        dragged(context, event);
        if (part.resizes()) {
            finishResize(context);
        } else if (deltaX != 0 || deltaY != 0) {
            if (!context.selection().isEmpty()) {
                context.openDocument().execute(
                        new MoveElementsCommand(context.selection().ids(), deltaX, deltaY));
            }
        } else if (narrowOnRelease) {
            context.selection().set(grabbedIds);
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
            left = Math.min(left + deltaX, right - Frame.MINIMUM_WIDTH);
        }
        if (part.movesEast()) {
            right = Math.max(right + deltaX, left + Frame.MINIMUM_WIDTH);
        }
        if (part.movesNorth()) {
            top = Math.min(top + deltaY, bottom - Frame.MINIMUM_HEIGHT);
        }
        if (part.movesSouth()) {
            bottom = Math.max(bottom + deltaY, top + Frame.MINIMUM_HEIGHT);
        }
        return frame.withRectangle(new GridPoint(left, top), right - left, bottom - top);
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
                for (ElementId id : context.selection().ids()) {
                    context.openDocument().document().element(id)
                            .ifPresent(element -> context.paintGhost(graphics, element.translate(deltaX, deltaY)));
                }
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
        if (grabbed == null || (deltaX == 0 && deltaY == 0)) {
            return Optional.empty();
        }
        Frame shown = part.resizes() ? resized(grabbed) : grabbed.translate(deltaX, deltaY);
        return Optional.of(FramePainter.bounds(shown));
    }

    /**
     * The unlocked frames that show their handles now: the hovered one and the selected ones; during a resize the
     * grabbed frame at its new size, during a move none.
     */
    List<Frame> framesWithHandles(ToolContext context) {
        List<Frame> frames = new ArrayList<>();
        boolean dragging = grabbed != null && (deltaX != 0 || deltaY != 0);
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
        double[] xs = {bounds.getMinX(), bounds.getCenterX(), bounds.getMaxX()};
        double[] ys = {bounds.getMinY(), bounds.getCenterY(), bounds.getMaxY()};
        List<Rectangle2D> handles = new ArrayList<>();
        for (int row = 0; row < ys.length; row++) {
            for (int column = 0; column < xs.length; column++) {
                if (row == 1 && column == 1) {
                    continue;
                }
                handles.add(new Rectangle2D.Double(xs[column] - size / 2, ys[row] - size / 2, size, size));
            }
        }
        return handles;
    }

    /** The frame under the pointer, for tests. */
    Optional<ElementId> hovered() {
        return Optional.ofNullable(hovered);
    }

    private void reset() {
        grabbed = null;
        part = null;
        grabbedIds = Set.of();
        pressPoint = null;
        narrowOnRelease = false;
        deltaX = 0;
        deltaY = 0;
    }
}
