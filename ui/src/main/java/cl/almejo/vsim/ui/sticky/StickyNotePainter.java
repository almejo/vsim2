package cl.almejo.vsim.ui.sticky;

import cl.almejo.vsim.core.document.StickyChecklist;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.ui.canvas.PaintPolicy;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.MonochromeScheme;
import cl.almejo.vsim.ui.paint.TextFonts;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

/**
 * Paints a sticky note as a piece of paper stuck on the circuit: a soft shadow below and to the right, the paper with
 * its bottom-right corner folded (a darker triangle), its text or checklist, and the ✕ at the top-right corner on the
 * screen. Works in document coordinates (8 px per grid unit) and reads every colour from the scheme it is given, so
 * nothing is cached. In black and white the paper is white with a black outline and black text.
 */
public final class StickyNotePainter {

    private static final BasicStroke BORDER = new BasicStroke(1f);
    private static final BasicStroke CONTROL = new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final int SHADOW_LAYERS = 3;
    private static final float CLOSE_REST_ALPHA = 0.55f;
    private static final float DONE_ALPHA = 0.55f;
    private static final float CHECKBOX_ALPHA = 0.85f;
    private static final double CLOSE_MARGIN = 2.5;
    private static final double STRIKE_HEIGHT_SHARE = 0.3;
    private static final double CHECK_MARGIN = 2;
    private static final Color MONOCHROME_FOLD = MonochromeScheme.LIGHT_GREY;

    private StickyNotePainter() {
    }

    /** Paints {@code note} in {@code scheme} under {@code policy}; the ✕ only where the policy paints buttons. */
    public static void paint(Graphics2D graphics, StickyNote note, ColorScheme scheme, double pixelsPerGrid,
                             PaintPolicy policy) {
        paint(graphics, note, scheme, pixelsPerGrid, policy, false);
    }

    /** Like the plain {@link #paint}; {@code closeHovered} draws the ✕ at full strength (the pointer is on it). */
    public static void paint(Graphics2D graphics, StickyNote note, ColorScheme scheme, double pixelsPerGrid,
                             PaintPolicy policy, boolean closeHovered) {
        Font font = TextFonts.labelFont();
        FontMetrics metrics = graphics.getFontMetrics(font);
        StickyNoteLayout layout = StickyNoteLayout.of(note, metrics, pixelsPerGrid);
        boolean monochrome = policy.monochrome();
        Color paper = monochrome ? Color.WHITE : scheme.stickyPaper(note.color());
        Color fold = monochrome ? MONOCHROME_FOLD : scheme.stickyFold(note.color());
        Color text = monochrome ? Color.BLACK : scheme.stickyText(note.color());
        Shape outline = paperShape(note);

        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (!monochrome) {
            paintShadow(graphics, outline, scheme.stickyShadow());
        }
        graphics.setColor(paper);
        graphics.fill(outline);
        graphics.setColor(fold);
        graphics.fill(layout.fold());
        graphics.setStroke(policy.widened(BORDER));
        graphics.setColor(monochrome ? Color.BLACK : fold);
        graphics.draw(outline);
        graphics.draw(layout.fold());

        paintText(graphics, note, layout, font, metrics, text);
        if (policy.paintFrameButtons() && !note.folded()
                && pixelsPerGrid >= StickyNoteLayout.MINIMUM_PIXELS_PER_GRID_FOR_DETAILS) {
            paintClose(graphics, layout.closeBox(), text, closeHovered);
        }
    }

    /** The rounded-free paper outline: a rectangle with its bottom-right corner cut where the fold is. */
    static Shape paperShape(StickyNote note) {
        Rectangle2D paper = StickyNoteLayout.paperOf(note);
        double cut = StickyNoteLayout.foldOf(note).getBounds2D().getWidth();
        Path2D.Double shape = new Path2D.Double();
        shape.moveTo(paper.getX(), paper.getY());
        shape.lineTo(paper.getMaxX(), paper.getY());
        shape.lineTo(paper.getMaxX(), paper.getMaxY() - cut);
        shape.lineTo(paper.getMaxX() - cut, paper.getMaxY());
        shape.lineTo(paper.getX(), paper.getMaxY());
        shape.closePath();
        return shape;
    }

    /** A soft shadow: a few translucent copies of the paper, each a pixel further down and to the right. */
    private static void paintShadow(Graphics2D graphics, Shape outline, Color shadow) {
        int alpha = Math.max(1, shadow.getAlpha() / SHADOW_LAYERS);
        graphics.setColor(new Color(shadow.getRed(), shadow.getGreen(), shadow.getBlue(), alpha));
        for (int layer = SHADOW_LAYERS; layer >= 1; layer--) {
            graphics.fill(AffineTransform.getTranslateInstance(layer, layer).createTransformedShape(outline));
        }
    }

    private static void paintText(Graphics2D graphics, StickyNote note, StickyNoteLayout layout, Font font,
                                  FontMetrics metrics, Color text) {
        if (layout.lines().isEmpty() && layout.checkboxes().isEmpty()) {
            return;
        }
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setFont(font);
        StickyChecklist checklist = note.content() instanceof StickyChecklist list ? list : null;
        for (StickyNoteLayout.Line line : layout.lines()) {
            boolean done = checklist != null && line.itemIndex() >= 0 && line.itemIndex() < checklist.items().size()
                    && checklist.items().get(line.itemIndex()).done();
            Composite previous = graphics.getComposite();
            if (done) {
                graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, DONE_ALPHA));
            }
            graphics.setColor(text);
            if (!line.text().isEmpty()) {
                graphics.drawString(line.text(), (float) line.x(), (float) line.baseline());
            }
            if (done && !line.text().isEmpty()) {
                double strikeY = line.baseline() - metrics.getAscent() * STRIKE_HEIGHT_SHARE;
                graphics.setStroke(BORDER);
                graphics.draw(new Line2D.Double(line.x(), strikeY, line.x() + metrics.stringWidth(line.text()),
                        strikeY));
            }
            graphics.setComposite(previous);
        }
        if (checklist != null) {
            paintCheckboxes(graphics, checklist, layout, text);
        }
    }

    private static void paintCheckboxes(Graphics2D graphics, StickyChecklist checklist, StickyNoteLayout layout,
                                        Color text) {
        Composite previous = graphics.getComposite();
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, CHECKBOX_ALPHA));
        graphics.setColor(text);
        graphics.setStroke(CONTROL);
        for (int index = 0; index < layout.checkboxes().size() && index < checklist.items().size(); index++) {
            Rectangle2D box = layout.checkboxes().get(index);
            graphics.draw(box);
            if (checklist.items().get(index).done()) {
                Path2D.Double tick = new Path2D.Double();
                tick.moveTo(box.getX() + CHECK_MARGIN, box.getCenterY());
                tick.lineTo(box.getX() + box.getWidth() * 0.42, box.getMaxY() - CHECK_MARGIN);
                tick.lineTo(box.getMaxX() - CHECK_MARGIN, box.getY() + CHECK_MARGIN);
                graphics.draw(tick);
            }
        }
        graphics.setComposite(previous);
    }

    private static void paintClose(Graphics2D graphics, Rectangle2D box, Color text, boolean hovered) {
        Composite previous = graphics.getComposite();
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, hovered ? 1f : CLOSE_REST_ALPHA));
        graphics.setColor(text);
        graphics.setStroke(CONTROL);
        graphics.draw(new Line2D.Double(box.getX() + CLOSE_MARGIN, box.getY() + CLOSE_MARGIN,
                box.getMaxX() - CLOSE_MARGIN, box.getMaxY() - CLOSE_MARGIN));
        graphics.draw(new Line2D.Double(box.getMaxX() - CLOSE_MARGIN, box.getY() + CLOSE_MARGIN,
                box.getX() + CLOSE_MARGIN, box.getMaxY() - CLOSE_MARGIN));
        graphics.setComposite(previous);
    }

    /** The preview of a note being dragged: its outline and fold in {@code color}, no fill. */
    public static void paintGhost(Graphics2D graphics, StickyNote note, Color color) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setStroke(BORDER);
        graphics.setColor(color);
        graphics.draw(paperShape(note));
        graphics.draw(StickyNoteLayout.foldOf(note));
    }
}
