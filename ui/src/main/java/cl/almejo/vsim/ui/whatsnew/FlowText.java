package cl.almejo.vsim.ui.whatsnew;

import cl.almejo.vsim.ui.panels.Keycaps;
import cl.almejo.vsim.ui.shortcuts.KeyNames;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.font.FontRenderContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Words and keycaps flowing in lines of a given width, as a bubble's text («{space} cambia el codo») and its «Atajo»
 * line draw them (each key a solid keycap in the bubble's ink), or a tip of the day's text (light outlined keycaps).
 * Measures and paints with the same layout.
 */
final class FlowText {

    /** A piece of the flow. */
    sealed interface Item permits Words, Keys {
    }

    /** Words, broken at their spaces when the line is full. */
    record Words(String text) implements Item {

        Words {
            Objects.requireNonNull(text, "text");
        }
    }

    /** The keys of one shortcut, drawn as keycaps that never break apart. */
    record Keys(List<String> keys) implements Item {

        Keys {
            keys = List.copyOf(keys);
        }
    }

    /** How big the keycaps are against the UI's (the sheet draws them at 1). */
    static final float KEYCAP_SCALE = 0.7f;
    private static final Pattern WORD = Pattern.compile("\\s*\\S+\\s*|\\s+");
    private static final int LINE_GAP = 2;

    private final List<Item> items;

    FlowText(List<Item> items) {
        this.items = List.copyOf(items);
    }

    List<Item> items() {
        return items;
    }

    /** A piece placed: what, and the left and top of its line. */
    private record Placed(Object piece, double x, int lineTop) {
    }

    /**
     * How the flow measures text: the font metrics for the keycaps and the line, and the rendering context for the
     * words, measured in fractions so a line of many words lands where one {@code drawString} of it would. Painting
     * measures with the painter's own (a HiDPI transform, its hints), so what is drawn is what was laid out.
     */
    private record Measure(Function<Font, FontMetrics> metrics, FontRenderContext context) {

        static Measure of(Component metricsSource, Font font) {
            return new Measure(metricsSource::getFontMetrics,
                    metricsSource.getFontMetrics(font).getFontRenderContext());
        }

        static Measure of(Graphics2D painter) {
            return new Measure(painter::getFontMetrics, painter.getFontRenderContext());
        }

        double width(Font font, String text) {
            return font.getStringBounds(text, context).getWidth();
        }
    }

    /** The height the flow takes in {@code width}. */
    int height(Component metricsSource, Font font, int width) {
        Measure measure = Measure.of(metricsSource, font);
        List<Placed> placed = layOut(measure, font, width);
        if (placed.isEmpty()) {
            return 0;
        }
        return placed.get(placed.size() - 1).lineTop() + lineHeight(measure, font);
    }

    /** How one row of keycaps is drawn from (x, top). */
    @FunctionalInterface
    private interface CapPainter {
        void paint(Graphics2D painter, Font font, List<String> keys, int x, int top);
    }

    /**
     * Paints the flow from ({@code left}, {@code top}) in {@code width}, the keys as solid caps of {@code ink} with
     * their names in {@code fill} (a bubble); returns the y just under it.
     */
    int paint(Graphics2D painter, Font font, int left, int top, int width, Color ink, Color fill) {
        return paint(painter, font, left, top, width, ink,
                (capPainter, capFont, keys, x, capTop) -> Keycaps.paintSolid(capPainter, capFont, KEYCAP_SCALE, keys,
                        !KeyNames.MAC_NOTATION, x, capTop, ink, fill));
    }

    /**
     * Paints the flow like {@link #paint(Graphics2D, Font, int, int, int, Color, Color)}, the words in
     * {@code ink} and the keys as light caps of {@code keyColour} with their names in {@code keyInk} (a tip of the
     * day's text).
     */
    int paintOutlined(Graphics2D painter, Font font, int left, int top, int width, Color ink, Color keyColour,
                      Color keyInk) {
        return paint(painter, font, left, top, width, ink,
                (capPainter, capFont, keys, x, capTop) -> Keycaps.paintOutlined(capPainter, capFont, KEYCAP_SCALE,
                        keys, !KeyNames.MAC_NOTATION, x, capTop, keyColour, keyInk));
    }

    private int paint(Graphics2D painter, Font font, int left, int top, int width, Color ink, CapPainter caps) {
        Measure measure = Measure.of(painter);
        int lineHeight = lineHeight(measure, font);
        FontMetrics metrics = measure.metrics().apply(font);
        int capHeight = Keycaps.height(measure.metrics(), font, KEYCAP_SCALE);
        int bottom = top;
        for (Placed placed : layOut(measure, font, width)) {
            int lineTop = top + placed.lineTop();
            if (placed.piece() instanceof String word) {
                painter.setFont(font);
                painter.setColor(ink);
                painter.drawString(word, (float) (left + placed.x()),
                        lineTop + (lineHeight - metrics.getHeight()) / 2 + metrics.getAscent());
            } else if (placed.piece() instanceof Keys keys) {
                caps.paint(painter, font, keys.keys(), (int) Math.round(left + placed.x()),
                        lineTop + (lineHeight - capHeight) / 2);
            }
            bottom = lineTop + lineHeight;
        }
        return bottom;
    }

    private static int lineHeight(Measure measure, Font font) {
        return Math.max(measure.metrics().apply(font).getHeight(),
                Keycaps.height(measure.metrics(), font, KEYCAP_SCALE)) + LINE_GAP;
    }

    private List<Placed> layOut(Measure measure, Font font, int width) {
        int lineHeight = lineHeight(measure, font);
        List<Placed> placed = new ArrayList<>();
        double x = 0;
        int lineTop = 0;
        for (Object piece : pieces()) {
            double pieceWidth = piece instanceof String word ? measure.width(font, word.stripTrailing())
                    : Keycaps.width(measure.metrics(), font, KEYCAP_SCALE, ((Keys) piece).keys(),
                    !KeyNames.MAC_NOTATION);
            if (x > 0 && x + pieceWidth > width) {
                x = 0;
                lineTop += lineHeight;
            }
            String shown = piece instanceof String word && x == 0 ? word.stripLeading() : null;
            if (shown != null && shown.isEmpty()) {
                continue; // a line never starts with a space
            }
            Object drawn = shown != null ? shown : piece;
            placed.add(new Placed(drawn, x, lineTop));
            x += drawn instanceof String word ? measure.width(font, word) : pieceWidth;
        }
        return placed;
    }

    /** The words one by one (each with the spaces around it) and the keys, in order. */
    private List<Object> pieces() {
        List<Object> pieces = new ArrayList<>();
        for (Item item : items) {
            switch (item) {
                case Words words -> {
                    Matcher matcher = WORD.matcher(words.text());
                    while (matcher.find()) {
                        pieces.add(matcher.group());
                    }
                }
                case Keys keys -> pieces.add(keys);
            }
        }
        return pieces;
    }
}
