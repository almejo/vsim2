package cl.almejo.vsim.ui.whatsnew;

import cl.almejo.vsim.ui.panels.Keycaps;
import cl.almejo.vsim.ui.shortcuts.KeyNames;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Words and keycaps flowing in lines of a given width, as a bubble's text («{space} cambia el codo») and its «Atajo»
 * line draw them: each key a solid keycap in the bubble's ink. Measures and paints with the same layout.
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
    private record Placed(Object piece, int x, int lineTop) {
    }

    /** The height the flow takes in {@code width}. */
    int height(Component metricsSource, Font font, int width) {
        List<Placed> placed = layOut(metricsSource, font, width);
        if (placed.isEmpty()) {
            return 0;
        }
        return placed.get(placed.size() - 1).lineTop() + lineHeight(metricsSource, font);
    }

    /** Paints the flow from ({@code left}, {@code top}) in {@code width}; returns the y just under it. */
    int paint(Graphics2D painter, Component metricsSource, Font font, int left, int top, int width, Color ink,
              Color fill) {
        int lineHeight = lineHeight(metricsSource, font);
        FontMetrics metrics = metricsSource.getFontMetrics(font);
        int capHeight = Keycaps.height(metricsSource, font, KEYCAP_SCALE);
        int bottom = top;
        for (Placed placed : layOut(metricsSource, font, width)) {
            int lineTop = top + placed.lineTop();
            if (placed.piece() instanceof String word) {
                painter.setFont(font);
                painter.setColor(ink);
                painter.drawString(word, left + placed.x(),
                        lineTop + (lineHeight - metrics.getHeight()) / 2 + metrics.getAscent());
            } else if (placed.piece() instanceof Keys keys) {
                Keycaps.paintSolid(painter, font, KEYCAP_SCALE, keys.keys(), !KeyNames.MAC_NOTATION,
                        left + placed.x(), lineTop + (lineHeight - capHeight) / 2, ink, fill);
            }
            bottom = lineTop + lineHeight;
        }
        return bottom;
    }

    private static int lineHeight(Component metricsSource, Font font) {
        return Math.max(metricsSource.getFontMetrics(font).getHeight(),
                Keycaps.height(metricsSource, font, KEYCAP_SCALE)) + LINE_GAP;
    }

    private List<Placed> layOut(Component metricsSource, Font font, int width) {
        FontMetrics metrics = metricsSource.getFontMetrics(font);
        int lineHeight = lineHeight(metricsSource, font);
        List<Placed> placed = new ArrayList<>();
        int x = 0;
        int lineTop = 0;
        for (Object piece : pieces()) {
            int pieceWidth = piece instanceof String word ? metrics.stringWidth(word.stripTrailing())
                    : Keycaps.width(metricsSource, font, KEYCAP_SCALE, ((Keys) piece).keys(), !KeyNames.MAC_NOTATION);
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
            x += drawn instanceof String word ? metrics.stringWidth(word) : pieceWidth;
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
