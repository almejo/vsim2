package cl.almejo.vsim.core.component.appearance;

import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;

import java.util.List;
import java.util.Optional;

/**
 * Finds where a template writes the component's name: the largest lettering that fits inside the template's body
 * without touching any pin name, as close to the middle of the box as the pin names allow. The core cannot measure
 * fonts, so texts are estimated from their length with a margin; the painter still shrinks a text that is too wide.
 * Everything is in eighths of a grid unit, which are also the pixels of the painter.
 */
final class NamePlacement {
    private static final int PIN_TEXT_PIXELS = 6;
    private static final double LETTER_WIDTH = 0.62;
    private static final double LINE_HEIGHT = 1.2;
    private static final int TEXT_INSET = 5;
    private static final int STUB_ALLOWANCE = 3;
    private static final int MARGIN = 2;
    private static final int STEP = 2;
    private static final int[] SIZE_PIXELS = {14, 9, 6};
    private static final TextSize[] SIZES = {TextSize.LARGE, TextSize.MEDIUM, TextSize.SMALL};
    private static final int EDGE_SAMPLES = 4;

    /** An axis-aligned rectangle in eighths. */
    private record Area(double left, double top, double right, double bottom) {
        boolean overlaps(Area other) {
            return left < other.right && other.left < right && top < other.bottom && other.top < bottom;
        }

        double overlapWith(Area other) {
            double across = Math.min(right, other.right) - Math.max(left, other.left);
            double down = Math.min(bottom, other.bottom) - Math.max(top, other.top);
            return across > 0 && down > 0 ? across * down : 0;
        }

        Area grownBy(double amount) {
            return new Area(left - amount, top - amount, right + amount, bottom + amount);
        }
    }

    private NamePlacement() { }

    /**
     * The text element for a template whose body is {@code body}: {@code name} is what gets written, and
     * {@code fixedText} (when present) is stored in the element instead of following the component's name.
     */
    static SymbolText nameText(GridSize size, List<SymbolPoint> body, String name, List<SymbolTemplates.PinLabel> pins,
                               Optional<String> fixedText) {
        int width = size.width() * SymbolPoint.EIGHTHS;
        int height = size.height() * SymbolPoint.EIGHTHS;
        List<Area> occupied = pins.stream().map(pin -> pinArea(pin, width, height)).toList();
        for (int sizeIndex = 0; sizeIndex < SIZES.length; sizeIndex++) {
            Optional<SymbolPoint> spot = freeSpot(SIZE_PIXELS[sizeIndex], name, width, height, body, occupied);
            if (spot.isPresent()) {
                return text(spot.get(), SIZES[sizeIndex], fixedText);
            }
        }
        return text(leastCrowdedSpot(SIZE_PIXELS[SIZE_PIXELS.length - 1], name, width, height, body, occupied),
                TextSize.SMALL, fixedText);
    }

    /** No size fits clear of the pin names: the smallest lettering where it covers the least of them. */
    private static SymbolPoint leastCrowdedSpot(int pixels, String name, int width, int height,
                                                List<SymbolPoint> body, List<Area> occupied) {
        double halfWidth = Math.max(1, name.length()) * pixels * LETTER_WIDTH / 2;
        double halfHeight = pixels * LINE_HEIGHT / 2;
        SymbolPoint best = new SymbolPoint(width / 2, height / 2);
        double bestCovered = Double.MAX_VALUE;
        for (int centerY = height / 2 % STEP; centerY <= height; centerY += STEP) {
            for (int centerX = width / 2 % STEP; centerX <= width; centerX += STEP) {
                Area text = new Area(centerX - halfWidth, centerY - halfHeight, centerX + halfWidth,
                        centerY + halfHeight);
                double covered = occupied.stream().mapToDouble(area -> area.overlapWith(text)).sum();
                boolean closer = covered == bestCovered && Math.hypot(centerX - width / 2.0, centerY - height / 2.0)
                        < Math.hypot(best.x() - width / 2.0, best.y() - height / 2.0);
                if (isInside(text.grownBy(1), body) && (covered < bestCovered || closer)) {
                    best = new SymbolPoint(centerX, centerY);
                    bestCovered = covered;
                }
            }
        }
        return best;
    }

    private static SymbolText text(SymbolPoint anchor, TextSize size, Optional<String> fixedText) {
        return new SymbolText(anchor, fixedText, size, TextAlignment.CENTER);
    }

    private static Optional<SymbolPoint> freeSpot(int pixels, String name, int width, int height,
                                                  List<SymbolPoint> body, List<Area> occupied) {
        double halfWidth = Math.max(1, name.length()) * pixels * LETTER_WIDTH / 2;
        double halfHeight = pixels * LINE_HEIGHT / 2;
        SymbolPoint best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int centerY = height / 2 % STEP; centerY <= height; centerY += STEP) {
            for (int centerX = width / 2 % STEP; centerX <= width; centerX += STEP) {
                Area text = new Area(centerX - halfWidth, centerY - halfHeight, centerX + halfWidth,
                        centerY + halfHeight);
                double distance = Math.hypot(centerX - width / 2.0, centerY - height / 2.0);
                if (distance < bestDistance && isInside(text.grownBy(1), body) && isFree(text, occupied)) {
                    best = new SymbolPoint(centerX, centerY);
                    bestDistance = distance;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean isFree(Area text, List<Area> occupied) {
        return occupied.stream().noneMatch(text.grownBy(MARGIN)::overlaps);
    }

    private static boolean isInside(Area text, List<SymbolPoint> body) {
        for (int index = 0; index <= EDGE_SAMPLES; index++) {
            double x = text.left + (text.right - text.left) * index / EDGE_SAMPLES;
            double y = text.top + (text.bottom - text.top) * index / EDGE_SAMPLES;
            if (!contains(body, x, text.top) || !contains(body, x, text.bottom)
                    || !contains(body, text.left, y) || !contains(body, text.right, y)) {
                return false;
            }
        }
        return true;
    }

    /** Ray casting; the points on the outline count as outside, which keeps the text off the strokes. */
    private static boolean contains(List<SymbolPoint> polygon, double x, double y) {
        boolean inside = false;
        for (int index = 0, previous = polygon.size() - 1; index < polygon.size(); previous = index++) {
            SymbolPoint current = polygon.get(index);
            SymbolPoint last = polygon.get(previous);
            boolean crosses = (current.y() > y) != (last.y() > y)
                    && x < (double) (last.x() - current.x()) * (y - current.y()) / (last.y() - current.y())
                    + current.x();
            if (crosses) {
                inside = !inside;
            }
        }
        return inside;
    }

    /** Where the painter writes a pin's name: beside the pin on the side it enters from, inside the box. */
    private static Area pinArea(SymbolTemplates.PinLabel pin, int width, int height) {
        GridPoint offset = pin.offset();
        double x = offset.x() * (double) SymbolPoint.EIGHTHS;
        double y = offset.y() * (double) SymbolPoint.EIGHTHS;
        double textWidth = pin.text().length() * PIN_TEXT_PIXELS * LETTER_WIDTH;
        double halfHeight = PIN_TEXT_PIXELS * LINE_HEIGHT / 2;
        boolean onSide = Math.min(x, width - x) <= Math.min(y, height - y);
        if (onSide) {
            double reach = TEXT_INSET + textWidth + STUB_ALLOWANCE;
            boolean left = x <= width / 2.0;
            return new Area(left ? x : x - reach, y - halfHeight, left ? x + reach : x, y + halfHeight);
        }
        double reach = TEXT_INSET + halfHeight * 2 + STUB_ALLOWANCE;
        boolean top = y <= height / 2.0;
        return new Area(x - textWidth / 2, top ? y : y - reach, x + textWidth / 2, top ? y + reach : y);
    }
}
