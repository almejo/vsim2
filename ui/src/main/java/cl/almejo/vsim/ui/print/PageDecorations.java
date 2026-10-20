package cl.almejo.vsim.ui.print;

import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.MonochromeScheme;
import cl.almejo.vsim.ui.paint.WireState;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Optional;

/**
 * What VSim itself draws on a sheet around the circuit, in points from the sheet's top-left corner: the cut marks and
 * the «Fila 1 · Columna 2 — Hoja 2 de 6» label of a tiled sheet, the header and the footer, and the legend of the wire
 * states in black and white.
 *
 * <p>Its texts have fixed sizes, never below {@link #MINIMUM_TEXT_POINTS} and never scaled with the circuit; they are
 * measured with a fixed {@link FontRenderContext} and filled as outlines, so where they go and where they are cut
 * never depends on the destination (the preview's zoom, a thumbnail or the PDF). Everything is grey, so it is the
 * same in colour and in black and white.
 */
final class PageDecorations {

    /** Spec §3.3: text VSim draws on paper is never smaller. */
    static final float MINIMUM_TEXT_POINTS = 6;
    /** The marks' label; fits the 12 pt strip of {@link PageGeometry#STANDARD_MARKS_STRIP_POINTS}. */
    static final float MARK_TEXT_POINTS = 7;
    /** Header and footer; one line in the 20 pt band of {@link PageGeometry#STANDARD_HEADER_BAND_POINTS}. */
    static final float HEADER_TEXT_POINTS = 8;
    static final float LEGEND_TEXT_POINTS = 7;

    private static final Color INK = new Color(0x333333);
    private static final Color RULE = MonochromeScheme.LIGHT_GREY;
    private static final BasicStroke HAIRLINE = new BasicStroke(0.5f);
    /** A cut mark starts this far from the window, so it never touches the drawing, and is this long. */
    private static final double MARK_GAP_POINTS = 1;
    private static final double MARK_LENGTH_POINTS = 6;
    /** The rule between a band and the drawing sits this far inside the band. */
    private static final double RULE_INSET_POINTS = 3;
    private static final double TEXT_GAP_POINTS = 8;
    private static final double SAMPLE_LENGTH_POINTS = 18;
    private static final double SAMPLE_GAP_POINTS = 3;
    private static final double LEGEND_ITEM_GAP_POINTS = 9;
    /** Without a footer the legend goes this far under the drawing, in the bottom margin. */
    private static final double LEGEND_OFFSET_POINTS = 3;
    private static final FontRenderContext MEASURE = new FontRenderContext(null, true, true);
    private static final String ELLIPSIS = "…";
    private static final List<LegendEntry> LEGEND = List.of(
            new LegendEntry(WireState.ONE, "print.legend.one"),
            new LegendEntry(WireState.ZERO, "print.legend.zero"),
            new LegendEntry(WireState.HIGH_IMPEDANCE, "print.legend.highImpedance"),
            new LegendEntry(WireState.UNKNOWN, "print.legend.unknown"),
            new LegendEntry(WireState.CONFLICT, "print.legend.conflict"));

    private record LegendEntry(WireState state, String labelKey) {
    }

    private final Font markFont;
    private final Font headerFont;
    private final Font titleFont;
    private final Font legendFont;

    /** The decorations in {@code family} (the frozen text font's installed family). */
    PageDecorations(String family) {
        Font base = new Font(family, Font.PLAIN, 1);
        this.markFont = base.deriveFont(MARK_TEXT_POINTS);
        this.headerFont = base.deriveFont(HEADER_TEXT_POINTS);
        this.titleFont = base.deriveFont(Font.BOLD, HEADER_TEXT_POINTS);
        this.legendFont = base.deriveFont(LEGEND_TEXT_POINTS);
    }

    /**
     * The cut marks of a tiled sheet, in the strip around the window at the middle of each overlap with a neighbour
     * (cut one sheet there and lay it on the other with the marks matching), and {@code label} centred under the
     * window, clear of the marks.
     */
    void paintMarks(Graphics2D graphics, PageLayout layout, PrintPage page, String label) {
        Rectangle2D window = layout.window();
        double halfOverlap = layout.overlapPoints() / 2;
        graphics.setColor(INK);
        graphics.setStroke(HAIRLINE);
        if (page.column() > 0) {
            verticalMarks(graphics, window, window.getX() + halfOverlap);
        }
        if (page.column() < layout.columns() - 1) {
            verticalMarks(graphics, window, window.getMaxX() - halfOverlap);
        }
        if (page.row() > 0) {
            horizontalMarks(graphics, window, window.getY() + halfOverlap);
        }
        if (page.row() < layout.rows() - 1) {
            horizontalMarks(graphics, window, window.getMaxY() - halfOverlap);
        }
        double strip = layout.geometry().marksStripPoints();
        String fitted = fitted(label, markFont, window.getWidth() - layout.overlapPoints() - 2 * TEXT_GAP_POINTS);
        drawText(graphics, fitted, markFont, window.getCenterX() - width(fitted, markFont) / 2,
                baselineIn(window.getMaxY(), strip, markFont));
    }

    private static void verticalMarks(Graphics2D graphics, Rectangle2D window, double x) {
        graphics.draw(new Line2D.Double(x, window.getY() - MARK_GAP_POINTS, x,
                window.getY() - MARK_GAP_POINTS - MARK_LENGTH_POINTS));
        graphics.draw(new Line2D.Double(x, window.getMaxY() + MARK_GAP_POINTS, x,
                window.getMaxY() + MARK_GAP_POINTS + MARK_LENGTH_POINTS));
    }

    private static void horizontalMarks(Graphics2D graphics, Rectangle2D window, double y) {
        graphics.draw(new Line2D.Double(window.getX() - MARK_GAP_POINTS, y,
                window.getX() - MARK_GAP_POINTS - MARK_LENGTH_POINTS, y));
        graphics.draw(new Line2D.Double(window.getMaxX() + MARK_GAP_POINTS, y,
                window.getMaxX() + MARK_GAP_POINTS + MARK_LENGTH_POINTS, y));
    }

    /** The title (bold) on the left and the file name, when there is one, on the right, over a thin rule. */
    void paintHeader(Graphics2D graphics, PageGeometry geometry, String title, Optional<String> fileName) {
        Rectangle2D band = headerBand(geometry);
        double textHeight = band.getHeight() - RULE_INSET_POINTS;
        double baseline = baselineIn(band.getY(), textHeight, headerFont);
        graphics.setColor(RULE);
        graphics.setStroke(HAIRLINE);
        double ruleY = band.getMaxY() - RULE_INSET_POINTS;
        graphics.draw(new Line2D.Double(band.getX(), ruleY, band.getMaxX(), ruleY));
        graphics.setColor(INK);
        String right = fitted(fileName.orElse(""), headerFont, band.getWidth() / 2 - TEXT_GAP_POINTS / 2);
        double rightWidth = width(right, headerFont);
        drawText(graphics, right, headerFont, band.getMaxX() - rightWidth, baseline);
        String left = fitted(title, titleFont, band.getWidth() - rightWidth - TEXT_GAP_POINTS);
        drawText(graphics, left, titleFont, band.getX(), baseline);
    }

    /**
     * The date on the left and «Hoja n de N» on the right, under a thin rule; with {@code legend}, the legend in the
     * middle when it fits between them, else under the footer, in the margin.
     */
    void paintFooter(Graphics2D graphics, PageGeometry geometry, String date, String sheetNumber, boolean legend) {
        Rectangle2D band = footerBand(geometry);
        double textTop = band.getY() + RULE_INSET_POINTS;
        double textHeight = band.getHeight() - RULE_INSET_POINTS;
        double baseline = baselineIn(textTop, textHeight, headerFont);
        graphics.setColor(RULE);
        graphics.setStroke(HAIRLINE);
        graphics.draw(new Line2D.Double(band.getX(), textTop, band.getMaxX(), textTop));
        graphics.setColor(INK);
        String right = fitted(sheetNumber, headerFont, band.getWidth() / 2 - TEXT_GAP_POINTS / 2);
        double rightWidth = width(right, headerFont);
        drawText(graphics, right, headerFont, band.getMaxX() - rightWidth, baseline);
        String left = fitted(date, headerFont, band.getWidth() - rightWidth - TEXT_GAP_POINTS);
        double leftWidth = width(left, headerFont);
        drawText(graphics, left, headerFont, band.getX(), baseline);
        if (!legend) {
            return;
        }
        double legendWidth = legendWidth();
        double legendLeft = band.getCenterX() - legendWidth / 2;
        boolean fitsBetween = legendLeft >= band.getX() + leftWidth + TEXT_GAP_POINTS
                && legendLeft + legendWidth <= band.getMaxX() - rightWidth - TEXT_GAP_POINTS;
        if (fitsBetween) {
            paintLegend(graphics, legendLeft, baselineIn(textTop, textHeight, legendFont));
        } else {
            paintLegendBelow(graphics, geometry, band.getMaxY());
        }
    }

    /** The legend centred on the sheet, just under {@code top} (the bottom of the drawing or of the footer). */
    void paintLegendBelow(Graphics2D graphics, PageGeometry geometry, double top) {
        LineMetrics metrics = legendFont.getLineMetrics("Ag", MEASURE);
        double left = Math.max(geometry.marginPoints(), (geometry.paperWidthPoints() - legendWidth()) / 2);
        paintLegend(graphics, left, top + LEGEND_OFFSET_POINTS + metrics.getAscent());
    }

    /** «Estados de los cables:», then a short wire of each state drawn as on paper, with its name. */
    private void paintLegend(Graphics2D graphics, double left, double baseline) {
        LineMetrics metrics = legendFont.getLineMetrics("Ag", MEASURE);
        double sampleY = baseline - metrics.getAscent() * 0.35;
        String title = Messages.text("print.legend.title");
        graphics.setColor(INK);
        drawText(graphics, title, legendFont, left, baseline);
        double x = left + width(title, legendFont) + SAMPLE_GAP_POINTS * 2;
        for (LegendEntry entry : LEGEND) {
            MonochromeScheme.WireLook look = MonochromeScheme.monochromeLook(entry.state(), false);
            graphics.setColor(look.color());
            graphics.setStroke(look.stroke());
            graphics.draw(new Line2D.Double(x, sampleY, x + SAMPLE_LENGTH_POINTS, sampleY));
            x += SAMPLE_LENGTH_POINTS + SAMPLE_GAP_POINTS;
            String label = Messages.text(entry.labelKey());
            graphics.setColor(INK);
            drawText(graphics, label, legendFont, x, baseline);
            x += width(label, legendFont) + LEGEND_ITEM_GAP_POINTS;
        }
    }

    private double legendWidth() {
        double total = width(Messages.text("print.legend.title"), legendFont) + SAMPLE_GAP_POINTS * 2;
        for (LegendEntry entry : LEGEND) {
            total += SAMPLE_LENGTH_POINTS + SAMPLE_GAP_POINTS + width(Messages.text(entry.labelKey()), legendFont)
                    + LEGEND_ITEM_GAP_POINTS;
        }
        return total - LEGEND_ITEM_GAP_POINTS;
    }

    private static Rectangle2D headerBand(PageGeometry geometry) {
        return new Rectangle2D.Double(geometry.marginPoints(), geometry.marginPoints(),
                geometry.paperWidthPoints() - 2 * geometry.marginPoints(), geometry.headerBandPoints());
    }

    private static Rectangle2D footerBand(PageGeometry geometry) {
        return new Rectangle2D.Double(geometry.marginPoints(),
                geometry.paperHeightPoints() - geometry.marginPoints() - geometry.headerBandPoints(),
                geometry.paperWidthPoints() - 2 * geometry.marginPoints(), geometry.headerBandPoints());
    }

    /** The baseline that centres one line of {@code font} in the band from {@code top}, {@code height} high. */
    private static double baselineIn(double top, double height, Font font) {
        LineMetrics metrics = font.getLineMetrics("Ag", MEASURE);
        return top + (height + metrics.getAscent() - metrics.getDescent()) / 2;
    }

    private static double width(String text, Font font) {
        return text.isEmpty() ? 0 : new TextLayout(text, font, MEASURE).getAdvance();
    }

    /** {@code text}, or its start with «…» when wider than {@code maximumWidth}; empty when not even «…» fits. */
    private static String fitted(String text, Font font, double maximumWidth) {
        if (width(text, font) <= maximumWidth) {
            return text;
        }
        for (int length = text.length() - 1; length > 0; length--) {
            String shortened = text.substring(0, length).stripTrailing() + ELLIPSIS;
            if (width(shortened, font) <= maximumWidth) {
                return shortened;
            }
        }
        return width(ELLIPSIS, font) <= maximumWidth ? ELLIPSIS : "";
    }

    private static void drawText(Graphics2D graphics, String text, Font font, double x, double baseline) {
        if (text.isEmpty()) {
            return;
        }
        graphics.fill(new TextLayout(text, font, MEASURE).getOutline(AffineTransform.getTranslateInstance(x,
                baseline)));
    }
}
