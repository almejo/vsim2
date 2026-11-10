package cl.almejo.vsim.ui.whatsnew;

import cl.almejo.vsim.ui.whatsnew.NewsTargets.Side;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Where a bubble goes beside its target (mockup v3, 2026-11-10): on the target's own side first (below toolbar
 * buttons and menu titles, above the status bar, inside the side strips), then the opposite one, then below, above,
 * right and left; the first that fits inside the window, a margin from its edges. Along that side it is centred on the
 * target and moved inside the window; its arrow points at the target's centre. When no side fits it is centred in the
 * window without an arrow. Pure geometry, in the coordinates of the window's layered pane.
 */
final class BubblePlacement {

    /** The bubble's body and the side of the target it is on (empty: centred, no arrow). */
    record Placement(Rectangle bounds, Optional<Side> side) {

        Placement {
            bounds = new Rectangle(bounds);
            Objects.requireNonNull(side, "side");
        }

        @Override
        public Rectangle bounds() {
            return new Rectangle(bounds);
        }
    }

    private BubblePlacement() {
    }

    /**
     * The place of a bubble of {@code size} (narrowed to the window when wider) beside {@code target}, inside
     * {@code area} (the window under its menu bar), {@code gap} away from the target (room for the arrow) and at
     * least {@code margin} from the area's edges.
     */
    static Placement place(Rectangle target, Dimension size, Rectangle area, Side preferred, int gap, int margin) {
        int width = Math.max(0, Math.min(size.width, area.width - 2 * margin));
        Dimension fitted = new Dimension(width, size.height);
        for (Side side : candidates(preferred)) {
            if (fits(side, target, fitted, area, gap, margin)) {
                return new Placement(beside(side, target, fitted, area, gap, margin), Optional.of(side));
            }
        }
        return new Placement(centred(fitted, area, margin), Optional.empty());
    }

    private static Set<Side> candidates(Side preferred) {
        Set<Side> candidates = new LinkedHashSet<>();
        candidates.add(preferred);
        candidates.add(opposite(preferred));
        candidates.addAll(List.of(Side.BELOW, Side.ABOVE, Side.RIGHT, Side.LEFT));
        return candidates;
    }

    private static Side opposite(Side side) {
        return switch (side) {
            case BELOW -> Side.ABOVE;
            case ABOVE -> Side.BELOW;
            case LEFT -> Side.RIGHT;
            case RIGHT -> Side.LEFT;
        };
    }

    private static boolean fits(Side side, Rectangle target, Dimension size, Rectangle area, int gap, int margin) {
        boolean crossFits = switch (side) {
            case BELOW, ABOVE -> size.width <= area.width - 2 * margin;
            case LEFT, RIGHT -> size.height <= area.height - 2 * margin;
        };
        return crossFits && switch (side) {
            case BELOW -> bottom(target) + gap + size.height <= bottom(area) - margin;
            case ABOVE -> target.y - gap - size.height >= area.y + margin;
            case LEFT -> target.x - gap - size.width >= area.x + margin;
            case RIGHT -> right(target) + gap + size.width <= right(area) - margin;
        };
    }

    private static Rectangle beside(Side side, Rectangle target, Dimension size, Rectangle area, int gap,
                                    int margin) {
        return switch (side) {
            case BELOW, ABOVE -> new Rectangle(
                    clamp((int) Math.round(target.getCenterX() - size.width / 2.0), area.x + margin,
                            right(area) - margin - size.width),
                    side == Side.BELOW ? bottom(target) + gap : target.y - gap - size.height,
                    size.width, size.height);
            case LEFT, RIGHT -> new Rectangle(
                    side == Side.RIGHT ? right(target) + gap : target.x - gap - size.width,
                    clamp((int) Math.round(target.getCenterY() - size.height / 2.0), area.y + margin,
                            bottom(area) - margin - size.height),
                    size.width, size.height);
        };
    }

    /** In the middle of the area; a bubble taller than it starts at its top margin. */
    private static Rectangle centred(Dimension size, Rectangle area, int margin) {
        int x = area.x + (area.width - size.width) / 2;
        int y = size.height > area.height - 2 * margin ? area.y + margin : area.y + (area.height - size.height) / 2;
        return new Rectangle(x, y, size.width, size.height);
    }

    /**
     * Where the arrow's middle goes along the body's edge facing the target ({@code side}), measured from the body's
     * left (below/above) or top (left/right): in line with the target's centre, but at least {@code inset} from the
     * body's corners.
     */
    static int arrowOffset(Rectangle body, Rectangle target, Side side, int inset) {
        return switch (side) {
            case BELOW, ABOVE -> clamp((int) Math.round(target.getCenterX()) - body.x, inset, body.width - inset);
            case LEFT, RIGHT -> clamp((int) Math.round(target.getCenterY()) - body.y, inset, body.height - inset);
        };
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, Math.max(minimum, maximum)));
    }

    private static int right(Rectangle rectangle) {
        return rectangle.x + rectangle.width;
    }

    private static int bottom(Rectangle rectangle) {
        return rectangle.y + rectangle.height;
    }
}
