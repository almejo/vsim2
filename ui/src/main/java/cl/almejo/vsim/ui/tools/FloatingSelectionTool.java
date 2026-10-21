package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.command.PasteCommand;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.tunnel.TunnelKind;
import cl.almejo.vsim.core.tunnel.Tunnels;

import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.util.Map;

/**
 * A pasted or dropped fragment that floats over the canvas. It is not part of the document until it is committed as
 * one {@link PasteCommand}. It follows the mouse (its origin at the pointer) until the first primary press. If that
 * first press is released without dragging, the fragment is committed right there (one click drops a paste). If it is
 * dragged, the fragment moves with the drag and stays floating; afterwards a press outside it commits and a press on
 * it grabs and drags it. Enter commits; Esc, Delete and Backspace cancel; R is consumed (no rotation while floating). Leaving the tool any other way (pasting again, choosing another
 * tool) commits it.
 */
public final class FloatingSelectionTool implements Tool {

    /** Extra grid units around the fragment bounds that still count as "on the fragment". */
    public static final double GRAB_MARGIN = 0.5;
    /** A press that moves less than this many grid units before release is a click. */
    public static final double CLICK_TOLERANCE = 0.5;

    private final CircuitFragment fragment;
    private GridPoint position;
    private GridPoint grabOffset = GridPoint.ORIGIN;
    private boolean following = true;
    private boolean dragging;
    private boolean firstPress;
    private boolean draggedSincePress;
    private Point2D pressPoint;
    private boolean finished;
    private ComponentTypeRegistry types;
    private CanvasCursor shownCursor;

    public FloatingSelectionTool(CircuitFragment fragment, GridPoint position) {
        this.fragment = fragment;
        this.position = position;
    }

    public CircuitFragment fragment() {
        return fragment;
    }

    /** Where the fragment's (0,0) currently is, in grid units. */
    public GridPoint position() {
        return position;
    }

    /** The document selection is cleared so that nothing but the floating fragment can be acted upon. */
    @Override
    public void activated(ToolContext context) {
        context.selection().clear();
    }

    @Override
    public void deactivated(ToolContext context) {
        if (!finished) {
            commit(context, false);
        }
    }

    @Override
    public void mouseMoved(ToolContext context, CanvasMouseEvent event) {
        if (finished) {
            return;
        }
        if (following) {
            moveTo(context, event.snapped());
        }
        // a press on the fragment grabs it; elsewhere a click fixes it where it is
        CanvasCursor next = following || covers(context, event.gridPosition()) ? CanvasCursor.MOVE
                : CanvasCursor.DEFAULT;
        if (next != shownCursor) {
            shownCursor = next;
            context.setCursor(next);
        }
    }

    @Override
    public void mouseDragged(ToolContext context, CanvasMouseEvent event) {
        if (finished) {
            return;
        }
        if (dragging && pressPoint != null && pressPoint.distance(event.gridPosition()) >= CLICK_TOLERANCE) {
            draggedSincePress = true;
        }
        if (following || dragging) {
            moveTo(context, event.snapped());
        }
    }

    @Override
    public void mousePressed(ToolContext context, CanvasMouseEvent event) {
        if (finished || event.button() != CanvasMouseEvent.PRIMARY) {
            return;
        }
        if (following || covers(context, event.gridPosition())) {
            firstPress = following;
            following = false;
            dragging = true;
            draggedSincePress = false;
            pressPoint = event.gridPosition();
            grabOffset = new GridPoint(event.snapped().x() - position.x(), event.snapped().y() - position.y());
        } else {
            commit(context, true);
        }
    }

    @Override
    public void mouseReleased(ToolContext context, CanvasMouseEvent event) {
        if (!dragging) {
            return;
        }
        dragging = false;
        if (firstPress && !draggedSincePress) {
            commit(context, true);
        }
        firstPress = false;
    }

    @Override
    public boolean keyPressed(ToolContext context, int keyCode, boolean shiftDown) {
        if (finished) {
            return false;
        }
        if (keyCode == KeyEvent.VK_ENTER) {
            commit(context, true);
            return true;
        }
        if (keyCode == KeyEvent.VK_ESCAPE || keyCode == KeyEvent.VK_DELETE || keyCode == KeyEvent.VK_BACK_SPACE) {
            finished = true;
            context.repaint();
            context.useDefaultTool();
            return true;
        }
        if (keyCode == KeyEvent.VK_R) {
            // Rotating a floating fragment is not supported; consume the key so canvas bindings do not act on the document.
            return true;
        }
        return false;
    }

    @Override
    public void paintOverlay(Graphics2D graphics, ToolContext context) {
        if (finished) {
            return;
        }
        // a pasted salida shows the colour it will have: its entrada's in the circuit, else a pasted entrada's
        Map<String, Integer> tunnelColors = hasSalida()
                ? Tunnels.colorsByName(context.openDocument().document(), fragment.components()) : Map.of();
        for (DocumentElement element : fragment.elements()) {
            context.paintGhost(graphics, element.translate(position.x(), position.y()), types(context), tunnelColors);
        }
    }

    /** True when the fragment holds a salida: only its ghost needs the circuit's tunnel colours. */
    private boolean hasSalida() {
        return fragment.components().stream()
                .anyMatch(component -> Tunnels.kindOf(component).orElse(null) == TunnelKind.EXIT);
    }

    @Override
    public Cursor cursor() {
        return Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
    }

    private void moveTo(ToolContext context, GridPoint snapped) {
        GridPoint next = new GridPoint(snapped.x() - grabOffset.x(), snapped.y() - grabOffset.y());
        if (!next.equals(position)) {
            position = next;
            context.repaint();
        }
    }

    private boolean covers(ToolContext context, Point2D gridPoint) {
        Bounds bounds = fragment.bounds(types(context));
        return gridPoint.getX() >= position.x() + bounds.x() - GRAB_MARGIN
                && gridPoint.getX() <= position.x() + bounds.maximumX() + GRAB_MARGIN
                && gridPoint.getY() >= position.y() + bounds.y() - GRAB_MARGIN
                && gridPoint.getY() <= position.y() + bounds.maximumY() + GRAB_MARGIN;
    }

    /** The document's types plus the fragment's own definitions (it is not pasted yet). */
    private ComponentTypeRegistry types(ToolContext context) {
        if (types == null) {
            types = fragment.registryFor(context.openDocument().registry());
        }
        return types;
    }

    /** Adds the fragment as one undo step and selects it; {@code switchTool} is false when called on deactivation. */
    private void commit(ToolContext context, boolean switchTool) {
        finished = true;
        if (!fragment.isEmpty()) {
            PasteCommand command = new PasteCommand(fragment, position);
            context.openDocument().execute(command);
            context.selection().set(command.pastedIds());
        }
        context.repaint();
        if (switchTool) {
            context.useDefaultTool();
        }
    }
}
