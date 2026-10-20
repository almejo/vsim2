package cl.almejo.vsim.ui.print;

import java.util.Objects;

/**
 * The fixed measures of a sheet, in points (1/72 in).
 *
 * @param marginPoints     blank border on the four sides, the same on every page
 * @param headerBandPoints height of the header band at the top <em>and</em> of the footer band at the bottom (each);
 *                         0 without header and footer. Subtracted before fitting or tiling.
 * @param marksStripPoints width of the strip kept on the four sides of the window of a tiled page for the cut marks
 *                         and the «Fila · Columna — Hoja n de N» label; not used on a single page
 * @param overlapPoints    how much neighbouring tiled pages repeat; {@link PageLayout} clamps it to half the window
 */
public record PageGeometry(Paper paper, PageOrientation orientation, double marginPoints, double headerBandPoints,
                           double marksStripPoints, double overlapPoints) {

    /** One centimetre. */
    public static final double CENTIMETRE_POINTS = 72 / 2.54;
    /** Each band holds one line of 8 pt text (title and file name above; date, sheet number and legend below). */
    public static final double STANDARD_HEADER_BAND_POINTS = 20;
    /** Room for 7 pt label text and the short cut marks, outside the drawing. */
    public static final double STANDARD_MARKS_STRIP_POINTS = 12;

    public PageGeometry {
        Objects.requireNonNull(paper, "paper");
        Objects.requireNonNull(orientation, "orientation");
        requireNonNegative(marginPoints, "marginPoints");
        requireNonNegative(headerBandPoints, "headerBandPoints");
        requireNonNegative(marksStripPoints, "marksStripPoints");
        requireNonNegative(overlapPoints, "overlapPoints");
    }

    /** 1 cm margins and overlap, the standard band (when {@code headerFooter}) and marks strip. */
    public static PageGeometry standard(Paper paper, PageOrientation orientation, boolean headerFooter) {
        return new PageGeometry(paper, orientation, CENTIMETRE_POINTS,
                headerFooter ? STANDARD_HEADER_BAND_POINTS : 0, STANDARD_MARKS_STRIP_POINTS, CENTIMETRE_POINTS);
    }

    /** The paper's width as turned by the orientation. */
    public double paperWidthPoints() {
        return orientation == PageOrientation.PORTRAIT ? paper.widthPoints() : paper.heightPoints();
    }

    /** The paper's height as turned by the orientation. */
    public double paperHeightPoints() {
        return orientation == PageOrientation.PORTRAIT ? paper.heightPoints() : paper.widthPoints();
    }

    private static void requireNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(name + " must be finite and >= 0: " + value);
        }
    }
}
