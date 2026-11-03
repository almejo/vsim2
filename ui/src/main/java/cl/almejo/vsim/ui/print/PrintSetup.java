package cl.almejo.vsim.ui.print;

import java.util.Objects;

/**
 * The sheet a printer really prints on: its paper, its orientation and the margin left blank on the four sides, at
 * least 1 cm and never less than the printer's largest hardware margin (spec 2026-10-19 §4, «formato efectivo»).
 */
public record PrintSetup(Paper paper, PageOrientation orientation, double marginPoints) {

    /** Two margins this close, in points, are the same margin (they come from millimetres through doubles). */
    static final double MARGIN_TOLERANCE_POINTS = 0.01;

    public PrintSetup {
        Objects.requireNonNull(paper, "paper");
        Objects.requireNonNull(orientation, "orientation");
        if (!Double.isFinite(marginPoints) || marginPoints < 0) {
            throw new IllegalArgumentException("marginPoints must be finite and >= 0: " + marginPoints);
        }
    }

    /** Whether this setup prints the sheets the dialog laid out with {@code options} and {@code marginPoints}. */
    public boolean matches(PrintOptions options, double marginPoints) {
        return paper == options.paper() && orientation == options.orientation()
                && Math.abs(this.marginPoints - marginPoints) <= MARGIN_TOLERANCE_POINTS;
    }
}
