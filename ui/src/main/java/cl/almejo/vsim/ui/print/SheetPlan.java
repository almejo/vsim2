package cl.almejo.vsim.ui.print;

import cl.almejo.vsim.core.geometry.GridPoint;

import java.awt.geom.Rectangle2D;
import java.util.Objects;
import java.util.Optional;

/**
 * The one layout the canvas's sheets, the print dialog and the AI's PDF export share (sheets spec §4.3 with §0a): the
 * automatic {@link PageLayout}, or, while the sheets are shown on the canvas, the same sheets held on the lattice of
 * an anchor.
 * <p>
 * The anchor is a sheet corner: sheet borders pass through it every step ({@code window − overlap}) in both
 * directions, and the sheets are the cells of that lattice the area touches. The area is widened back to the
 * nearest lattice line at or before its left and top edges and laid out as usual, so when the circuit grows right
 * or down sheets are added there, when it grows left or up a column or row is added on that side, and the existing
 * borders never move. Only a tiled layout is anchored: a single sheet is centred on the circuit as always.
 */
public final class SheetPlan {

    /** Steps of slack, as {@link PageLayout#EPSILON}, so an area starting exactly on a lattice line adds no column. */
    private static final double EPSILON = 1e-6;

    private SheetPlan() {
    }

    /**
     * Lays {@code area} out on sheets of {@code geometry}: {@link PageLayout#of} when the anchor does not apply
     * ({@link #anchorApplies}), else the area widened to the anchor's lattice. The widened layout is tiled with the
     * same scale, window and overlap, so its first sheet starts on a lattice line.
     */
    public static PageLayout layout(Rectangle2D area, PageGeometry geometry, ScaleMode mode,
                                    Optional<GridPoint> anchor) {
        Objects.requireNonNull(anchor, "anchor");
        PageLayout automatic = PageLayout.of(area, geometry, mode);
        if (!anchorApplies(automatic, anchor)) {
            return automatic;
        }
        GridPoint corner = anchor.get();
        double pointsPerUnit = GridPoint.PIXELS_PER_UNIT * automatic.scale();
        double stepX = (automatic.window().getWidth() - automatic.overlapPoints()) / pointsPerUnit;
        double stepY = (automatic.window().getHeight() - automatic.overlapPoints()) / pointsPerUnit;
        double left = latticeLineAtOrBefore(area.getX(), corner.x(), stepX);
        double top = latticeLineAtOrBefore(area.getY(), corner.y(), stepY);
        Rectangle2D widened = new Rectangle2D.Double(left, top, area.getMaxX() - left, area.getMaxY() - top);
        return PageLayout.of(widened, geometry, mode);
    }

    /**
     * The last line {@code anchor + k · step} at or before {@code edge}. Never past the edge, even by a rounding
     * error, so the widened area always holds the original one (and so stays tiled).
     */
    private static double latticeLineAtOrBefore(double edge, int anchor, double step) {
        double line = anchor + Math.floor((edge - anchor) / step + EPSILON) * step;
        return Math.min(line, edge);
    }

    /**
     * Whether {@code anchor} changes the {@code automatic} layout's sheets: there is one and the layout is tiled (one
     * sheet, «Ajustar a una hoja» and a paper with no room are never anchored). The dialog's «Se usan las hojas que
     * ves en el circuito.» shows exactly then.
     */
    public static boolean anchorApplies(PageLayout automatic, Optional<GridPoint> anchor) {
        Objects.requireNonNull(automatic, "automatic");
        Objects.requireNonNull(anchor, "anchor");
        return anchor.isPresent() && automatic.tiled();
    }

    /**
     * The anchor that holds {@code tiledLayout}'s sheets where they are: its first sheet's top-left corner. The
     * «Todo» area's corner is a whole grid point; a fractional one is taken on the grid point before it, which keeps
     * the first column and row (the sheets then start up to a unit earlier).
     *
     * @throws IllegalArgumentException when the layout is not tiled (a single sheet has no anchor)
     */
    public static GridPoint anchorOf(PageLayout tiledLayout) {
        Objects.requireNonNull(tiledLayout, "tiledLayout");
        if (!tiledLayout.tiled()) {
            throw new IllegalArgumentException("only a tiled layout has an anchor");
        }
        Rectangle2D area = tiledLayout.gridArea();
        return new GridPoint((int) Math.floor(area.getX()), (int) Math.floor(area.getY()));
    }
}
