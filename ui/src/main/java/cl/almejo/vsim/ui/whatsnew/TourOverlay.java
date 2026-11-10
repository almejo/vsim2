package cl.almejo.vsim.ui.whatsnew;

import cl.almejo.vsim.ui.whatsnew.NewsTargets.Side;
import com.formdev.flatlaf.util.UIScale;

import javax.swing.AbstractButton;
import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.Optional;

/**
 * The window during a tour: dimmed all over but the target (the scrim is the window minus the target's rounded
 * rectangle, with a ring in the bubble's colour around it), and the bubble with its shadow and its arrow toward the
 * target's centre; what the bubble says is its child {@link NewsBubble}. It has no mouse listeners, so a click on the
 * dimmed window passes through to what is under it (starting to edit ends the tour); only the bubble keeps its clicks.
 */
final class TourOverlay extends JComponent {

    private static final int HOLE_PADDING = 4;
    private static final int HOLE_CORNER = 12;
    private static final int RING_WIDTH = 3;
    private static final int BODY_CORNER = 20;
    /** Half the arrow's base and its height: the mockup's 14 px square turned 45°. */
    static final int ARROW = 8;
    private static final int ARROW_INSET = 18;
    private static final int SHADOW_LAYERS = 4;

    private final NewsBubble bubble;
    private Rectangle target = new Rectangle();
    private Optional<Side> side = Optional.empty();

    TourOverlay(NewsBubble bubble) {
        this.bubble = bubble;
        setLayout(null);
        setOpaque(false);
        add(bubble);
    }

    /** Points at {@code shownTarget} (in this overlay's coordinates) from {@code bubbleSide}; empty: no arrow. */
    void pointAt(Rectangle shownTarget, Optional<Side> bubbleSide) {
        this.target = new Rectangle(shownTarget);
        this.side = bubbleSide;
        repaint();
    }

    /** Moves the bubble's body to {@code bounds}. */
    void moveBubble(Rectangle bounds) {
        Rectangle old = bubble.getBounds();
        bubble.setBounds(bounds);
        bubble.doLayout();
        repaint(old.union(bounds).union(new Rectangle(0, 0, getWidth(), getHeight())));
    }

    Rectangle bubbleBounds() {
        return bubble.getBounds();
    }

    Optional<Side> side() {
        return side;
    }

    List<AbstractButton> buttons() {
        return bubble.buttons();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D painter = (Graphics2D) graphics.create();
        try {
            painter.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            RoundRectangle2D hole = hole();
            Area scrim = new Area(new Rectangle(0, 0, getWidth(), getHeight()));
            scrim.subtract(new Area(hole));
            painter.setColor(BubbleColors.scrim());
            painter.fill(scrim);
            painter.setColor(BubbleColors.fill());
            painter.setStroke(new BasicStroke(UIScale.scale((float) RING_WIDTH)));
            painter.draw(hole);
            paintBody(painter);
        } finally {
            painter.dispose();
        }
    }

    private RoundRectangle2D hole() {
        int padding = UIScale.scale(HOLE_PADDING);
        int corner = UIScale.scale(HOLE_CORNER);
        return new RoundRectangle2D.Float(target.x - padding, target.y - padding, target.width + 2 * padding,
                target.height + 2 * padding, corner, corner);
    }

    private void paintBody(Graphics2D painter) {
        Rectangle body = bubble.getBounds();
        int corner = UIScale.scale(BODY_CORNER);
        Area shape = new Area(new RoundRectangle2D.Float(body.x, body.y, body.width, body.height, corner, corner));
        side.ifPresent(arrowSide -> shape.add(new Area(arrow(body, arrowSide))));
        for (int layer = SHADOW_LAYERS; layer >= 1; layer--) {
            painter.setColor(BubbleColors.shadow());
            Area shadow = new Area(shape);
            shadow.transform(AffineTransform.getTranslateInstance(0, UIScale.scale(layer * 2)));
            painter.fill(shadow);
        }
        painter.setColor(BubbleColors.fill());
        painter.fill(shape);
    }

    /** The triangle from the body's edge facing the target, toward the target's centre. */
    private Polygon arrow(Rectangle body, Side arrowSide) {
        int size = UIScale.scale(ARROW);
        int offset = BubblePlacement.arrowOffset(body, target, arrowSide, UIScale.scale(ARROW_INSET));
        Polygon arrow = new Polygon();
        switch (arrowSide) {
            case BELOW -> {
                arrow.addPoint(body.x + offset - size, body.y + 1);
                arrow.addPoint(body.x + offset, body.y - size);
                arrow.addPoint(body.x + offset + size, body.y + 1);
            }
            case ABOVE -> {
                int bottom = body.y + body.height;
                arrow.addPoint(body.x + offset - size, bottom - 1);
                arrow.addPoint(body.x + offset, bottom + size);
                arrow.addPoint(body.x + offset + size, bottom - 1);
            }
            case RIGHT -> {
                arrow.addPoint(body.x + 1, body.y + offset - size);
                arrow.addPoint(body.x - size, body.y + offset);
                arrow.addPoint(body.x + 1, body.y + offset + size);
            }
            case LEFT -> {
                int right = body.x + body.width;
                arrow.addPoint(right - 1, body.y + offset - size);
                arrow.addPoint(right + size, body.y + offset);
                arrow.addPoint(right - 1, body.y + offset + size);
            }
        }
        return arrow;
    }
}
