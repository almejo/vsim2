package cl.almejo.vsim.ui.snap;

import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.wiring.NewContact;
import cl.almejo.vsim.ui.i18n.Messages;
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
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The new contacts of a gesture (spec §3.7): a dot of radius 4.5 logical pixels in {@code scheme.wireConflict()},
 * ringed 1.5 px with {@code scheme.background()}, on each point where the move would join two nets that are not
 * joined today; next to the dot, with no need to bring the pointer there, the moving pin's name and «→ other» when it
 * meets a named pin of another part (on at most the four labelled dots nearest the pointer). Sizes are logical pixels whatever the zoom (the document transform is taken off) and every colour
 * is read at paint time. The tools paint it in their overlay after the move preview and before the alignment lines;
 * tool overlays never reach image export or paper.
 */
public final class ContactDots {

    static final double RADIUS = 4.5;
    private static final BasicStroke RING = new BasicStroke(1.5f);
    /** Logical pixels between the dot and its name, and around the name's backing. */
    private static final int NAME_GAP = 4;
    private static final int NAME_PADDING = 2;
    /** At most this many dots get a label, so a crowded drag stays readable. */
    static final int MAXIMUM_LABELS = 4;

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
        for (NewContact contact : labelled(contacts, pointer)) {
            Point2D centre = document.transform(new Point2D.Double(contact.point().x() * unit,
                    contact.point().y() * unit), null);
            paintName(graphics, context, label(contact).orElseThrow(), centre);
        }
        graphics.setFont(font);
        graphics.setStroke(stroke);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                Objects.requireNonNullElse(antialiasing, RenderingHints.VALUE_ANTIALIAS_DEFAULT));
        graphics.setTransform(current);
    }

    /**
     * What is written next to the dot: the moving pin's name, «→ other» added when it meets a named pin of another
     * part (e.g. «Cin → S»), or only «→ other» when no named pin moves there; empty when no named pin is there.
     */
    public static Optional<String> label(NewContact contact) {
        Optional<String> moving = contact.movingPinName().filter(name -> !name.isBlank());
        Optional<String> met = contact.metPinName().filter(name -> !name.isBlank());
        if (moving.isPresent() && met.isPresent()) {
            return Optional.of(Messages.format("contacts.label.meets", moving.get(), met.get()));
        }
        if (met.isPresent()) {
            return Optional.of(Messages.format("contacts.label.metOnly", met.get()));
        }
        return moving;
    }

    /**
     * The contacts that get a label: of those with one, at most {@link #MAXIMUM_LABELS}, the nearest to the pointer
     * first (in contact order without a pointer, or between equally near ones).
     */
    public static List<NewContact> labelled(List<NewContact> contacts, Optional<Point2D> pointer) {
        List<NewContact> named = contacts.stream().filter(contact -> label(contact).isPresent()).toList();
        if (pointer.isEmpty()) {
            return named.stream().limit(MAXIMUM_LABELS).toList();
        }
        Point2D at = pointer.get();
        return named.stream()
                .sorted(Comparator.comparingDouble(contact -> at.distanceSq(contact.point().x(),
                        contact.point().y())))
                .limit(MAXIMUM_LABELS)
                .toList();
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
