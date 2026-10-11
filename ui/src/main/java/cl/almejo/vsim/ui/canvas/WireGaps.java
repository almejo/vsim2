package cl.almejo.vsim.ui.canvas;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Where to draw a vertical wire that other wires cross: the stretches between the gaps. Pure geometry in document
 * pixels along the wire's axis, so the renderer only turns each span into a line.
 */
final class WireGaps {

    /** Clear space between the horizontal stroke and the visible end of the vertical one, in document pixels. */
    static final double CLEARANCE = 2;

    /** A horizontal wire crossing at {@code center} (document pixels) drawn {@code horizontalWidth} pixels wide. */
    record Cut(double center, double horizontalWidth) {
    }

    /** A stretch of the vertical wire to draw, {@code from < to}, in document pixels. */
    record Span(double from, double to) {
    }

    private WireGaps() {
    }

    /**
     * The spans to draw of a vertical wire running from {@code from} to {@code to} ({@code from < to}). Around each
     * cut it leaves out {@code center ± h} with {@code h = horizontalWidth / 2 + CLEARANCE + verticalCapReach}, where
     * {@code verticalCapReach} is how far the vertical stroke paints past the end of a span (half its width for a
     * round cap, 0 for a butt cap); that term cancels the cap, so {@code CLEARANCE} pixels stay clear on both sides
     * of the horizontal stroke for every stroke. Gaps that touch or overlap merge; cuts outside the wire are ignored;
     * a span is never empty.
     */
    static List<Span> visibleSpans(double from, double to, List<Cut> cuts, double verticalCapReach) {
        List<Cut> sorted = new ArrayList<>(cuts);
        sorted.sort(Comparator.comparingDouble(Cut::center));
        List<Span> spans = new ArrayList<>();
        double start = from;
        for (Cut cut : sorted) {
            if (cut.center() <= from || cut.center() >= to) {
                continue;
            }
            double half = cut.horizontalWidth() / 2 + CLEARANCE + verticalCapReach;
            double gapStart = Math.max(from, cut.center() - half);
            double gapEnd = Math.min(to, cut.center() + half);
            if (gapStart > start) {
                spans.add(new Span(start, gapStart));
            }
            start = Math.max(start, gapEnd);
        }
        if (to > start) {
            spans.add(new Span(start, to));
        }
        return List.copyOf(spans);
    }
}
