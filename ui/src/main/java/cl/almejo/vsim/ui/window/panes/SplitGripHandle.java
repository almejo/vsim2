package cl.almejo.vsim.ui.window.panes;

import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.edges.EdgeGesture;
import cl.almejo.vsim.ui.canvas.edges.EdgeHandle;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.tools.CanvasMouseEvent;
import cl.almejo.vsim.ui.window.panes.CanvasPanes.Orientation;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Point2D;
import java.util.Optional;

/**
 * A split grip of one pane (canvas-edges spec §1–§3): a small tab at the top of the right edge, just under the top
 * ruler's level, pulls a {@link Orientation#HORIZONTAL} divider down; one at the right end of the bottom edge (the
 * corner itself stays empty) pulls a {@link Orientation#VERTICAL} divider left. It is faint at rest, takes the window
 * dividers' hover colour and resize cursor, and while dragged shows the divider bar across the whole canvas area. A
 * release within 24 px of an edge, or Escape, splits nothing. Only the pane on the outer edge shows it, and it goes
 * while its orientation is split. Not a guide: it stays with «Mostrar guías» off. Colours are read at paint time.
 */
final class SplitGripHandle implements EdgeHandle {

    /** Room left for the top ruler (and, on the bottom edge, for the empty corner), in logical pixels. */
    static final int RULER_CLEARANCE = 10;
    /** Above the rulers' corner (30): where they could ever meet, the grip wins. */
    static final int PRIORITY = 40;
    private static final int LENGTH = 20;
    private static final int DEPTH = 8;
    private static final int DOTS = 3;
    private static final int DOT_SIZE = 2;
    private static final int DOT_GAP = 2;
    private static final double HOVER_SHADE = 0.12;
    /** How strong the tab is at rest: faint, it is always there but never in the way. */
    private static final int RESTING_ALPHA = 150;

    private final CanvasPanes panes;
    private final CircuitCanvas pane;
    private final Orientation orientation;

    SplitGripHandle(CanvasPanes panes, CircuitCanvas pane, Orientation orientation) {
        this.panes = panes;
        this.pane = pane;
        this.orientation = orientation;
    }

    /** The divider this grip pulls. */
    Orientation orientation() {
        return orientation;
    }

    /**
     * The tab, or nothing: while that divider exists, and on a pane that is not on the outer edge (the horizontal grip
     * lives on the rightmost pane, the vertical one on the bottom pane).
     */
    @Override
    public Shape hitArea(Dimension canvasSize) {
        if (panes.dividerFraction(orientation).isPresent() || !onTheOuterEdge()) {
            return new Rectangle();
        }
        return orientation == Orientation.HORIZONTAL
                ? new Rectangle(canvasSize.width - LENGTH, RULER_CLEARANCE, LENGTH, DEPTH)
                : new Rectangle(canvasSize.width - RULER_CLEARANCE - DEPTH, canvasSize.height - LENGTH, DEPTH,
                LENGTH);
    }

    private boolean onTheOuterEdge() {
        Optional<PanePosition> position = panes.positionOf(pane);
        if (position.isEmpty()) {
            return false;
        }
        return orientation == Orientation.HORIZONTAL
                ? panes.dividerFraction(Orientation.VERTICAL).isEmpty() || position.get() == PanePosition.TOP_RIGHT
                : panes.dividerFraction(Orientation.HORIZONTAL).isEmpty()
                || position.get() == PanePosition.BOTTOM_LEFT;
    }

    @Override
    public int priority() {
        return PRIORITY;
    }

    @Override
    public Cursor cursor() {
        return PaneDivider.resizeCursor(orientation);
    }

    @Override
    public String tooltip() {
        return Messages.text("split.grip.tooltip");
    }

    @Override
    public void paint(Graphics2D screenGraphics, Dimension canvasSize, boolean hovered) {
        Rectangle tab = hitArea(canvasSize).getBounds();
        if (tab.isEmpty()) {
            return;
        }
        Color background = PaneDivider.color("SplitPane.background", PaneDivider.color("Panel.background",
                Color.LIGHT_GRAY));
        Color foreground = PaneDivider.color("Label.foreground", Color.BLACK);
        Color fill = hovered ? mix(background, foreground, HOVER_SHADE) : background;
        screenGraphics.setColor(hovered ? fill : withAlpha(fill, RESTING_ALPHA));
        screenGraphics.fill(tab);
        Color border = PaneDivider.color("Component.borderColor", Color.GRAY);
        screenGraphics.setColor(hovered ? border : withAlpha(border, RESTING_ALPHA));
        screenGraphics.drawRect(tab.x, tab.y, tab.width - 1, tab.height - 1);
        screenGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color grip = PaneDivider.color("SplitPaneDivider.gripColor", Color.GRAY);
        screenGraphics.setColor(hovered ? grip : withAlpha(grip, RESTING_ALPHA));
        boolean wide = tab.width > tab.height;
        int length = DOTS * DOT_SIZE + (DOTS - 1) * DOT_GAP;
        int start = (wide ? tab.x + (tab.width - length) / 2 : tab.y + (tab.height - length) / 2);
        int middle = wide ? tab.y + (tab.height - DOT_SIZE) / 2 : tab.x + (tab.width - DOT_SIZE) / 2;
        for (int dot = 0; dot < DOTS; dot++) {
            int along = start + dot * (DOT_SIZE + DOT_GAP);
            if (wide) {
                screenGraphics.fillOval(along, middle, DOT_SIZE, DOT_SIZE);
            } else {
                screenGraphics.fillOval(middle, along, DOT_SIZE, DOT_SIZE);
            }
        }
    }

    @Override
    public EdgeGesture press(CanvasMouseEvent event) {
        return new Pull();
    }

    /** Where {@code event} is in the canvas area, across the divider being pulled. */
    private int positionInArea(CanvasMouseEvent event) {
        Point2D point = event.screenPosition();
        return orientation == Orientation.HORIZONTAL
                ? pane.getY() + (int) Math.round(point.getY())
                : pane.getX() + (int) Math.round(point.getX());
    }

    /**
     * Pulling the grip: the divider's preview follows the pointer, and hides next to an edge so the user sees that
     * releasing there does nothing (canvas-edges §3); the release splits, unless next to an edge.
     */
    private final class Pull implements EdgeGesture {

        @Override
        public void drag(CanvasMouseEvent event) {
            int position = positionInArea(event);
            if (panes.isNearAnEdge(orientation, position)) {
                panes.hideSplitPreview();
            } else {
                panes.showSplitPreview(orientation, position);
            }
        }

        @Override
        public void release(CanvasMouseEvent event) {
            panes.hideSplitPreview();
            int position = positionInArea(event);
            if (!panes.isNearAnEdge(orientation, position) && panes.dividerFraction(orientation).isEmpty()) {
                panes.split(orientation, panes.fractionFor(orientation, position));
            }
        }

        @Override
        public void cancel() {
            panes.hideSplitPreview();
        }
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private static Color mix(Color from, Color toward, double amount) {
        return new Color(
                (int) Math.round(from.getRed() + (toward.getRed() - from.getRed()) * amount),
                (int) Math.round(from.getGreen() + (toward.getGreen() - from.getGreen()) * amount),
                (int) Math.round(from.getBlue() + (toward.getBlue() - from.getBlue()) * amount));
    }
}
