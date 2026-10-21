package cl.almejo.vsim.ui.guides;

import cl.almejo.vsim.core.document.Guide;
import cl.almejo.vsim.core.document.Guide.Orientation;
import cl.almejo.vsim.ui.canvas.Viewport;
import cl.almejo.vsim.ui.canvas.edges.EdgeGesture;
import cl.almejo.vsim.ui.canvas.edges.EdgeHandle;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.tools.CanvasMouseEvent;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.util.Optional;

/**
 * The ruler along the top edge (pulls horizontal guides) or the left edge (pulls vertical guides) of one canvas
 * (canvas-edges spec §1–§3): 10 logical pixels, no numbers, faint ticks every N grid units (N from 1, 2, 5, 10, 20,
 * 50 … so that ticks are at least 16 px apart), following zoom and pan, and a triangle marker for each guide that
 * crosses it (the vertical guides on the top ruler, the horizontal ones on the left ruler). A press on a marker grabs
 * that guide; anywhere else it pulls a new guide. Colours come from the theme at paint time.
 */
final class RulerHandle implements EdgeHandle {

    /** Where the ruler lies. */
    enum Side { TOP, LEFT }

    static final int PRIORITY = 20;
    /** Ticks are at least this far apart, in logical pixels. */
    static final double MINIMUM_TICK_SPACING = 16;
    private static final int MINOR_TICK = 3;
    private static final int MAJOR_TICK = 5;
    private static final int TICKS_PER_MAJOR = 5;
    /** Half the width of a guide's marker, and how close a press must be to grab it. */
    static final int MARKER_HALF_WIDTH = 4;
    private static final int[] MANTISSAS = {1, 2, 5};

    private final PaneGuides features;
    private final Side side;

    RulerHandle(PaneGuides features, Side side) {
        this.features = features;
        this.side = side;
    }

    /** The guides this ruler pulls: horizontal ones from the top ruler, vertical ones from the left ruler. */
    Orientation pulls() {
        return side == Side.TOP ? Orientation.HORIZONTAL : Orientation.VERTICAL;
    }

    /** The guides marked on this ruler: those that cross it (the other orientation). */
    private Orientation marks() {
        return side == Side.TOP ? Orientation.VERTICAL : Orientation.HORIZONTAL;
    }

    /** The smallest N of 1, 2, 5, 10, 20, 50 … grid units whose ticks are at least 16 logical pixels apart. */
    static int tickSpacing(double pixelsPerUnit) {
        for (int magnitude = 1; magnitude > 0 && magnitude <= Integer.MAX_VALUE / 10; magnitude *= 10) {
            for (int mantissa : MANTISSAS) {
                if (mantissa * magnitude * pixelsPerUnit >= MINIMUM_TICK_SPACING) {
                    return mantissa * magnitude;
                }
            }
        }
        return Integer.MAX_VALUE;
    }

    @Override
    public Shape hitArea(Dimension canvasSize) {
        int ruler = GuidesFeatures.RULER_SIZE;
        return side == Side.TOP
                ? new Rectangle(ruler, 0, Math.max(0, canvasSize.width - ruler), ruler)
                : new Rectangle(0, ruler, ruler, Math.max(0, canvasSize.height - ruler));
    }

    @Override
    public int priority() {
        return PRIORITY;
    }

    @Override
    public Cursor cursor() {
        return GuideCursors.forOrientation(pulls());
    }

    @Override
    public Cursor cursorAt(CanvasMouseEvent event) {
        return markerAt(event).map(GuidesFeatures::moveCursor).orElseGet(this::cursor);
    }

    @Override
    public String tooltip() {
        return Messages.text("guides.ruler.tooltip");
    }

    @Override
    public EdgeGesture press(CanvasMouseEvent event) {
        Optional<Guide> marked = markerAt(event);
        return marked.isPresent()
                ? features.startDrag(marked.get().orientation(), marked, event)
                : features.startDrag(pulls(), Optional.empty(), event);
    }

    /** The guide whose marker is under the pointer, the nearest when two are close. */
    private Optional<Guide> markerAt(CanvasMouseEvent event) {
        double along = side == Side.TOP ? event.screenPosition().getX() : event.screenPosition().getY();
        Guide nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Guide guide : features.document().guides()) {
            if (guide.orientation() != marks()) {
                continue;
            }
            double distance = Math.abs(features.screenCoordinate(guide) - along);
            if (distance <= MARKER_HALF_WIDTH && distance < nearestDistance) {
                nearest = guide;
                nearestDistance = distance;
            }
        }
        return Optional.ofNullable(nearest);
    }

    @Override
    public void paint(Graphics2D screenGraphics, Dimension canvasSize, boolean hovered) {
        Rectangle area = (Rectangle) hitArea(canvasSize);
        if (area.isEmpty()) {
            return;
        }
        RulerColors colors = RulerColors.current(hovered);
        screenGraphics.setColor(colors.background());
        screenGraphics.fill(area);
        screenGraphics.setColor(colors.border());
        int ruler = GuidesFeatures.RULER_SIZE;
        if (side == Side.TOP) {
            screenGraphics.fillRect(area.x, ruler - 1, area.width, 1);
        } else {
            screenGraphics.fillRect(ruler - 1, area.y, 1, area.height);
        }
        paintTicks(screenGraphics, area, colors.tick());
        paintMarkers(screenGraphics, area);
    }

    private void paintTicks(Graphics2D graphics, Rectangle area, Color tick) {
        Viewport viewport = features.canvas().viewport();
        int spacing = tickSpacing(viewport.pixelsPerUnit());
        boolean top = side == Side.TOP;
        double from = top ? viewport.screenToGrid(area.x, 0).getX() : viewport.screenToGrid(0, area.y).getY();
        double to = top ? viewport.screenToGrid(area.getMaxX(), 0).getX()
                : viewport.screenToGrid(0, area.getMaxY()).getY();
        long first = (long) Math.floor(from / spacing);
        long last = (long) Math.ceil(to / spacing);
        int ruler = GuidesFeatures.RULER_SIZE;
        graphics.setColor(tick);
        for (long index = first; index <= last; index++) {
            double gridPosition = (double) index * spacing;
            int screen = (int) Math.round(top ? viewport.gridToScreen(gridPosition, 0).getX()
                    : viewport.gridToScreen(0, gridPosition).getY());
            int length = index % TICKS_PER_MAJOR == 0 ? MAJOR_TICK : MINOR_TICK;
            if (top && screen >= area.x && screen < area.getMaxX()) {
                graphics.fillRect(screen, ruler - 1 - length, 1, length);
            } else if (!top && screen >= area.y && screen < area.getMaxY()) {
                graphics.fillRect(ruler - 1 - length, screen, length, 1);
            }
        }
    }

    private void paintMarkers(Graphics2D graphics, Rectangle area) {
        Color guide = features.canvas().scheme().guide();
        graphics.setColor(new Color(guide.getRGB() & 0xFFFFFF | 0xFF000000, true));
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int ruler = GuidesFeatures.RULER_SIZE;
        for (Guide each : features.document().guides()) {
            if (each.orientation() != marks()) {
                continue;
            }
            int screen = (int) Math.round(features.screenCoordinate(each));
            Polygon triangle = side == Side.TOP
                    ? new Polygon(new int[]{screen - MARKER_HALF_WIDTH, screen + MARKER_HALF_WIDTH + 1, screen},
                    new int[]{1, 1, ruler - 1}, 3)
                    : new Polygon(new int[]{1, 1, ruler - 1},
                    new int[]{screen - MARKER_HALF_WIDTH, screen + MARKER_HALF_WIDTH + 1, screen}, 3);
            if (triangle.intersects(area)) {
                graphics.fill(triangle);
            }
        }
    }
}
