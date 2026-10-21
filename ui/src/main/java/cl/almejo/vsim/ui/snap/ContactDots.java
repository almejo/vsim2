package cl.almejo.vsim.ui.snap;

import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Snapping;
import cl.almejo.vsim.core.wiring.NewContact;
import cl.almejo.vsim.ui.tools.ToolContext;

import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The new contacts of a gesture (spec §3.7): a dot of radius 4.5 logical pixels in {@code scheme.wireConflict()},
 * ringed 1.5 px with {@code scheme.background()}, on each point where the move would join two nets that are not
 * joined today; when the contact is on a named pin and the pointer is within the magnet's reach of it, the pin's name
 * next to the dot. Sizes are logical pixels whatever the zoom (the document transform is taken off) and every colour
 * is read at paint time. The tools paint it in their overlay after the move preview and before the alignment lines;
 * tool overlays never reach image export or paper.
 */
public final class ContactDots {

    static final double RADIUS = 4.5;
    private static final BasicStroke RING = new BasicStroke(1.5f);
    /** Logical pixels between the dot and its name, and around the name's backing. */
    private static final int NAME_GAP = 4;
    private static final int NAME_PADDING = 2;

    private ContactDots() {
    }

    /**
     * Paints {@code contacts} on {@code graphics} in document coordinates (the tool overlay); {@code pointer} is where
     * the pointer is, in grid units, for the pin names.
     */
    public static void paint(Graphics2D graphics, ToolContext context, List<NewContact> contacts,
                             Optional<Point2D> pointer) {
        if (contacts.isEmpty()) {
            return;
        }
        AffineTransform current = graphics.getTransform();
        AffineTransform document = context.viewport().documentTransform();
        AffineTransform screen = new AffineTransform(current);
        try {
            screen.concatenate(document.createInverse());
        } catch (NoninvertibleTransformException exception) {
            return; // a zero zoom never happens; nothing sensible to draw
        }
        Object antialiasing = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        Stroke stroke = graphics.getStroke();
        Font font = graphics.getFont();
        graphics.setTransform(screen);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setStroke(RING);
        double unit = GridPoint.PIXELS_PER_UNIT;
        for (NewContact contact : contacts) {
            Point2D centre = document.transform(new Point2D.Double(contact.point().x() * unit,
                    contact.point().y() * unit), null);
            Ellipse2D dot = new Ellipse2D.Double(centre.getX() - RADIUS, centre.getY() - RADIUS, 2 * RADIUS,
                    2 * RADIUS);
            graphics.setColor(context.scheme().wireConflict());
            graphics.fill(dot);
            graphics.setColor(context.scheme().background());
            graphics.draw(dot);
        }
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        for (NewContact contact : contacts) {
            if (nameShown(context, contact, pointer)) {
                Point2D centre = document.transform(new Point2D.Double(contact.point().x() * unit,
                        contact.point().y() * unit), null);
                paintName(graphics, context, contact.pinName().orElseThrow(), centre);
            }
        }
        graphics.setFont(font);
        graphics.setStroke(stroke);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                Objects.requireNonNullElse(antialiasing, RenderingHints.VALUE_ANTIALIAS_DEFAULT));
        graphics.setTransform(current);
    }

    /** True when the contact is on a named pin and the pointer is within the magnet's reach of it, on both axes. */
    public static boolean nameShown(ToolContext context, NewContact contact, Optional<Point2D> pointer) {
        if (pointer.isEmpty() || contact.pinName().filter(name -> !name.isBlank()).isEmpty()) {
            return false;
        }
        int reach = Snapping.thresholdUnits(context.viewport().pixelsPerUnit());
        return Math.abs(pointer.get().getX() - contact.point().x()) <= reach
                && Math.abs(pointer.get().getY() - contact.point().y()) <= reach;
    }

    /** The name up and to the right of the dot, on a backing of the board colour so it reads over wires. */
    private static void paintName(Graphics2D graphics, ToolContext context, String name, Point2D centre) {
        Font labelFont = UIManager.getFont("Label.font");
        if (labelFont != null) {
            graphics.setFont(labelFont);
        }
        FontMetrics metrics = graphics.getFontMetrics();
        int left = (int) Math.round(centre.getX() + RADIUS + NAME_GAP);
        int baseline = (int) Math.round(centre.getY() - RADIUS - NAME_GAP);
        graphics.setColor(context.scheme().background());
        graphics.fillRect(left - NAME_PADDING, baseline - metrics.getAscent() - NAME_PADDING,
                metrics.stringWidth(name) + 2 * NAME_PADDING, metrics.getAscent() + metrics.getDescent()
                        + 2 * NAME_PADDING);
        graphics.setColor(context.scheme().text());
        graphics.drawString(name, left, baseline);
    }
}
