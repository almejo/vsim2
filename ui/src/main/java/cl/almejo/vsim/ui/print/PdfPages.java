package cl.almejo.vsim.ui.print;

import org.jfree.pdf.PDFDocument;
import org.jfree.pdf.PDFGraphics2D;
import org.jfree.pdf.PDFHints;
import org.jfree.pdf.Page;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Objects;

/**
 * Writes a vector PDF, one page per {@link PageSize}, each painted through a plain {@link Graphics2D}; the only class
 * that touches JFreePDF.
 *
 * <p>Each page's graphics is in points with the origin at the top-left corner of the sheet and y growing downwards,
 * like the Java2D canvas. Painters compose transforms ({@code translate}, {@code scale}, {@code transform}) and never
 * set an absolute one with {@code setTransform}: the page's starting transform is not the identity. JFreePDF 2.0.1
 * flips the y axis around the page height truncated to an integer, which would push a sheet of 841.89 pt (A4)
 * 0.89 pt down; each page's graphics starts translated up by that fraction to put y = 0 on the top edge.
 *
 * <p>Text is drawn as glyph outlines (JFreePDF's vector text mode), so any font and character prints exactly as on
 * screen and nothing needs embedding; the price is that the PDF's text cannot be selected or searched. JFreePDF's
 * standard text mode only knows the 14 base fonts and writes the string unescaped, which breaks on «ñ», «á» and
 * parentheses.
 *
 * <p>The painters get each page's graphics wrapped in a {@link SharedStreamGraphics}: JFreePDF's child graphics
 * share the page's stream and would otherwise leave their colour, stroke and transparency to their parent.
 *
 * <p>The document is assembled in memory and written to {@code output} only after every page painted, so a page that
 * throws (a failure, or a cancellation surfaced as an exception) leaves {@code output} untouched.
 */
public final class PdfPages {

    /** A page's size in points (1/72 in). */
    public record PageSize(double widthPoints, double heightPoints) {
        public PageSize {
            if (!(widthPoints > 0) || !(heightPoints > 0) || Double.isInfinite(widthPoints)
                    || Double.isInfinite(heightPoints)) {
                throw new IllegalArgumentException("page size must be positive: " + widthPoints + " × "
                        + heightPoints);
            }
        }
    }

    /** Paints one page; it may throw to abandon the whole document. */
    @FunctionalInterface
    public interface PageContent {
        void paint(int pageIndex, Graphics2D graphics) throws IOException;
    }

    private PdfPages() {
    }

    /**
     * Paints {@code pages.size()} pages in order and writes the PDF to {@code output} (which stays open).
     *
     * @throws IllegalArgumentException when {@code pages} is empty
     */
    public static void write(OutputStream output, List<PageSize> pages, PageContent content) throws IOException {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(content, "content");
        if (pages.isEmpty()) {
            throw new IllegalArgumentException("a PDF needs at least one page");
        }
        PDFDocument document = new PDFDocument();
        for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {
            PageSize size = pages.get(pageIndex);
            Page page = document.createPage(new Rectangle2D.Double(0, 0, size.widthPoints(), size.heightPoints()));
            PDFGraphics2D graphics = page.getGraphics2D();
            graphics.setRenderingHint(PDFHints.KEY_DRAW_STRING_TYPE, PDFHints.VALUE_DRAW_STRING_TYPE_VECTOR);
            double truncatedHeight = (int) size.heightPoints();
            graphics.translate(0, truncatedHeight - size.heightPoints());
            Graphics2D painted = SharedStreamGraphics.forPage(graphics);
            content.paint(pageIndex, painted);
            painted.dispose();
        }
        output.write(document.getPDFBytes());
        output.flush();
    }
}
