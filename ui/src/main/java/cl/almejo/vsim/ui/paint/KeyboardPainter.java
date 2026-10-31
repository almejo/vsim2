package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.KeyboardTypes;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.terminal.KeyEncoding;
import cl.almejo.vsim.core.terminal.TerminalEncoding;
import cl.almejo.vsim.ui.i18n.Messages;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * «Teclado»: the queue drawn as a row of keycaps. The first key, the one D shows now, is in the memories' read colour
 * with a small «D» under it; a bar says how full the queue is and the title bar's chip counts bytes («3/64», «llena»);
 * AV's dot is lit while the queue has data. While the keyboard is captured for the part ({@link #paintWithCapture})
 * it gets a border and halo in the capture colour («Color al escribir»), a title bar filled with it that says
 * «Escribiendo… Esc para salir», and a still caret after the last key. Colours come from the scheme at paint time;
 * the characters and the chip use the bundled Plex Mono. No animation.
 */
public final class KeyboardPainter implements ComponentPainter {

    /** The text colour on a light capture colour. */
    static final Color NEAR_BLACK = new Color(0x1B1D22);
    /** Above this luminance (0 to 1) of the capture colour its text is {@link #NEAR_BLACK}. */
    private static final double LIGHT_LUMINANCE = 0.62;

    private static final double UNIT = GridPoint.PIXELS_PER_UNIT;
    private static final double CORNER_RADIUS = 3;
    private static final double TITLE_HEIGHT = 2 * UNIT;
    private static final double BORDER_WIDTH = 1.5;
    private static final double CAPTURE_BORDER_WIDTH = 2;
    private static final double HALO_WIDTH = 6;
    private static final int HALO_ALPHA = 64;
    private static final int DIVIDER_ALPHA = 89;
    private static final int KEY_BORDER_ALPHA = 115;
    private static final int HEAD_TINT_ALPHA = 41;
    private static final int FILL_BAR_ALPHA = 115;
    private static final int MORE_ALPHA = 153;
    private static final int PIN_NAME_ALPHA = 191;
    private static final int EMPTY_OUTLINE_ALPHA = 102;
    private static final int EMPTY_TEXT_ALPHA = 128;
    private static final int CHIP_ALPHA = 20;
    private static final int FULL_CHIP_ALPHA = 41;

    private static final double TITLE_TEXT_LEFT = 19;
    private static final float LABEL_SIZE = 8f;
    private static final float CAPTURE_TEXT_SIZE = 7f;
    private static final float CHIP_SIZE = 7f;
    private static final float PIN_NAME_SIZE = 5f;
    private static final double PIN_NAME_INSET = 3.5;
    private static final double PIN_NAME_RISE = 4;

    private static final int VISIBLE_KEYS = 8;
    private static final double KEY_LEFT = 5;
    private static final double KEY_TOP = TITLE_HEIGHT + 4;
    private static final double KEY_WIDTH = 11;
    private static final double KEY_HEIGHT = 13;
    private static final double KEY_GAP = 2;
    private static final double KEY_RADIUS = 2.5;
    private static final double LIP = 2;
    private static final float KEY_TEXT_SIZE = 8f;
    private static final float KEY_WORD_SIZE = 6f;
    private static final float HEAD_MARK_SIZE = 5f;
    private static final double HEAD_MARK_DROP = 3.5;
    private static final double CARET_WIDTH = 1.5;

    private static final double BAR_TOP = KEY_TOP + KEY_HEIGHT + 7;
    private static final double BAR_LEFT = 26;
    private static final double BAR_RIGHT_MARGIN = 18;
    private static final double BAR_HEIGHT = 1.6;
    private static final double WARNING_RATIO = 0.75;

    private static final float[] DASH = {2f, 2f};
    private static final Font EMPTY_FONT = new Font(Font.SANS_SERIF, Font.ITALIC, 1);
    private static final String ELLIPSIS = "…";
    private static final int MULTI_BYTE_LEAD_MINIMUM = 0xC2;
    private static final int THREE_BYTE_LEAD = 0xE0;
    private static final int FOUR_BYTE_LEAD = 0xF0;
    private static final int CONTINUATION_MASK = 0xC0;
    private static final int CONTINUATION_MARK = 0x80;
    private static final int DELETE_CODE = 127;
    private static final int FIRST_PRINTABLE_CODE = 32;
    private static final int CONTROL_LETTER_OFFSET = 64;

    /** «Color al escribir»: a tone for the light schemes and one for the dark ones. */
    private record Tone(Color light, Color dark) {
    }

    private static final Map<String, Tone> TONES = Map.of(
            "blue", new Tone(new Color(0x1E88E5), new Color(0x4C9AFF)),
            "green", new Tone(new Color(0x2E9E44), new Color(0x5FD38A)),
            "purple", new Tone(new Color(0x8347AD), new Color(0xB98AE0)),
            "orange", new Tone(new Color(0xE8710A), new Color(0xFFA94D)),
            "pink", new Tone(new Color(0xD81B60), new Color(0xFF7AAC)),
            "teal", new Tone(new Color(0x00897B), new Color(0x3CB4E5)),
            "yellow", new Tone(new Color(0xC79100), new Color(0xFFD54F)));

    @Override
    public void paint(Graphics2D graphics, ComponentType type, Map<String, Object> parameters, VisibleState state,
                      ColorScheme scheme) {
        paintWithCapture(graphics, type, parameters, state, scheme, null, false);
    }

    /**
     * Draws the Teclado with {@code label} in its title bar ({@code null}: the part's name); {@code capturing} when the
     * window's keyboard is captured for it. The palette icon and the ghost use {@link #paint}: the idle look.
     */
    public void paintWithCapture(Graphics2D graphics, ComponentType type, Map<String, Object> parameters,
                                 VisibleState state, ColorScheme scheme, String label, boolean capturing) {
        Map<String, Object> resolved = type.resolveParameters(parameters);
        VisibleState.KeyboardState keyboard = state instanceof VisibleState.KeyboardState known ? known
                : new VisibleState.KeyboardState(0, List.of(), 0, 0);
        int capacity = (Integer) resolved.get(KeyboardTypes.CAPACITY);
        KeyEncoding encoding = KeyEncoding.forParameters(resolved);
        boolean utf8 = encoding.dataWidth() == KeyEncoding.EIGHT_BITS && encoding.encoding() == TerminalEncoding.UTF8;
        Color accent = capturing
                ? captureColor(scheme, String.valueOf(resolved.get(KeyboardTypes.CAPTURE_COLOR))) : scheme.selection();
        double width = type.size().width() * UNIT;
        double height = type.size().height() * UNIT;
        Shape outline = new RoundRectangle2D.Double(0, 0, width, height, 2 * CORNER_RADIUS, 2 * CORNER_RADIUS);

        if (capturing) {
            graphics.setColor(PainterSupport.withAlpha(accent, HALO_ALPHA));
            graphics.setStroke(new BasicStroke((float) HALO_WIDTH));
            graphics.draw(outline);
        }
        paintBody(graphics, outline, width, scheme, accent, capturing);
        paintTitle(graphics, type, resolved, keyboard, capacity, width, scheme, accent, label, capturing);
        paintKeys(graphics, keyboard, utf8, scheme, accent, capturing);
        paintFillBar(graphics, keyboard.size(), capacity, width, scheme);
        paintPins(graphics, type, keyboard.size() > 0, scheme);
    }

    /** The colour of the capture look for the «Color al escribir» choice; «automatic» is the scheme's selection. */
    public static Color captureColor(ColorScheme scheme, String choice) {
        Tone tone = TONES.get(choice);
        if (tone == null) {
            return scheme.selection();
        }
        return scheme.isDark() ? tone.dark() : tone.light();
    }

    /** White, or near-black when {@code background} is light (luminance above 0.62), whichever reads better on it. */
    public static Color textOn(Color background) {
        double luminance = (0.299 * background.getRed() + 0.587 * background.getGreen()
                + 0.114 * background.getBlue()) / 255;
        return luminance > LIGHT_LUMINANCE ? NEAR_BLACK : Color.WHITE;
    }

    /**
     * What each key of the queue shows: ↵ ⏎ ⇥ ⌫ ⌦ ⎋ ␣ for the usual control codes, «^C» for the other ones, the
     * character for the rest; with {@code utf8} the bytes of a multi-byte character make one key.
     */
    static List<String> keyTexts(List<Integer> bytes, boolean utf8) {
        List<String> texts = new ArrayList<>();
        int index = 0;
        while (index < bytes.size()) {
            int length = utf8 ? sequenceLength(bytes, index) : 1;
            int codePoint = bytes.get(index);
            if (length > 1) {
                byte[] sequence = new byte[length];
                for (int offset = 0; offset < length; offset++) {
                    sequence[offset] = (byte) (int) bytes.get(index + offset);
                }
                codePoint = new String(sequence, StandardCharsets.UTF_8).codePointAt(0);
            }
            texts.add(keyText(codePoint));
            index += length;
        }
        return texts;
    }

    /** How many bytes the UTF‑8 character starting at {@code index} has, 1 when it is not a whole valid one. */
    private static int sequenceLength(List<Integer> bytes, int index) {
        int lead = bytes.get(index);
        if (lead < MULTI_BYTE_LEAD_MINIMUM) {
            return 1;
        }
        int length = lead >= FOUR_BYTE_LEAD ? 4 : lead >= THREE_BYTE_LEAD ? 3 : 2;
        if (index + length > bytes.size()) {
            return 1;
        }
        for (int offset = 1; offset < length; offset++) {
            if ((bytes.get(index + offset) & CONTINUATION_MASK) != CONTINUATION_MARK) {
                return 1;
            }
        }
        return length;
    }

    private static String keyText(int codePoint) {
        return switch (codePoint) {
            case '\n' -> "↵";
            case '\r' -> "⏎";
            case '\t' -> "⇥";
            case '\b' -> "⌫";
            case DELETE_CODE -> "⌦";
            case 27 -> "⎋";
            case ' ' -> "␣";
            default -> codePoint < FIRST_PRINTABLE_CODE ? "^" + (char) (codePoint + CONTROL_LETTER_OFFSET)
                    : new String(Character.toChars(codePoint));
        };
    }

    private static void paintBody(Graphics2D graphics, Shape outline, double width, ColorScheme scheme, Color accent,
                                  boolean capturing) {
        graphics.setColor(scheme.componentFill());
        graphics.fill(outline);
        Graphics2D clipped = (Graphics2D) graphics.create();
        try {
            clipped.clip(outline);
            clipped.setColor(capturing ? accent : scheme.componentShade());
            clipped.fill(new Rectangle2D.Double(0, 0, width, TITLE_HEIGHT));
        } finally {
            clipped.dispose();
        }
        graphics.setColor(capturing ? accent : scheme.componentStroke());
        graphics.setStroke(new BasicStroke((float) (capturing ? CAPTURE_BORDER_WIDTH : BORDER_WIDTH)));
        graphics.draw(outline);
        graphics.setColor(PainterSupport.withAlpha(capturing ? accent : scheme.componentStroke(), DIVIDER_ALPHA));
        graphics.setStroke(new BasicStroke(1f));
        graphics.draw(new Line2D.Double(0, TITLE_HEIGHT, width, TITLE_HEIGHT));
    }

    private static void paintTitle(Graphics2D graphics, ComponentType type, Map<String, Object> resolved,
                                   VisibleState.KeyboardState keyboard, int capacity, double width,
                                   ColorScheme scheme, Color accent, String label, boolean capturing) {
        Color onTitle = capturing ? textOn(accent) : scheme.text();
        paintIcon(graphics, onTitle);
        if (PainterSupport.turnedSideways(graphics)) {
            // upright text cannot fit the title bar turned into a narrow vertical strip
            return;
        }
        double middle = TITLE_HEIGHT / 2 + 0.5;
        if (capturing) {
            PainterSupport.uprightText(graphics, Messages.text("keyboard.painter.capturing"), TITLE_TEXT_LEFT, middle,
                    CAPTURE_TEXT_SIZE, onTitle, along(graphics, PainterSupport.ALIGN_LEFT));
            return;
        }
        boolean full = keyboard.size() >= capacity;
        String chip = full ? Messages.text("keyboard.painter.full") : keyboard.size() + "/" + capacity;
        Font chipFont = fontFor(chip, false);
        FontMetrics chipMetrics = graphics.getFontMetrics(chipFont.deriveFont(CHIP_SIZE));
        double chipWidth = chipMetrics.stringWidth(chip) + 6;
        double chipLeft = width - chipWidth - 4;
        Color chipColor = full ? scheme.wireConflict() : scheme.text();
        graphics.setColor(PainterSupport.withAlpha(chipColor, full ? FULL_CHIP_ALPHA : CHIP_ALPHA));
        graphics.fill(new RoundRectangle2D.Double(chipLeft, 3.5, chipWidth, 9, 9, 9));
        PainterSupport.uprightText(graphics, chip, chipLeft + chipWidth / 2, middle, CHIP_SIZE, chipColor,
                PainterSupport.ALIGN_CENTER, chipFont);
        String name = label == null ? Messages.componentName(type.id()) : label;
        FontMetrics labelMetrics = graphics.getFontMetrics(PainterSupport.BASE_FONT.deriveFont(LABEL_SIZE));
        PainterSupport.uprightText(graphics, ellipsized(name, labelMetrics, chipLeft - TITLE_TEXT_LEFT - 3),
                TITLE_TEXT_LEFT, middle, LABEL_SIZE, scheme.text(), along(graphics, PainterSupport.ALIGN_LEFT));
    }

    /**
     * The alignment of a text anchored on a local point: turned upside down the part's left is the screen's right, so
     * a text that grows to the part's right must grow to the screen's left to stay inside it.
     */
    private static double along(Graphics2D graphics, double alignment) {
        boolean upsideDown = Math.abs(PainterSupport.quarterTurns(graphics)) % 4 == 2;
        return upsideDown ? 1 - alignment : alignment;
    }

    /** {@code text}, cut with «…» when it is wider than {@code available} pixels. */
    private static String ellipsized(String text, FontMetrics metrics, double available) {
        if (metrics.stringWidth(text) <= available) {
            return text;
        }
        String cut = text;
        while (!cut.isEmpty() && metrics.stringWidth(cut + ELLIPSIS) > available) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + ELLIPSIS;
    }

    /** A little board with two rows of keys and a space bar, 12 × 8 px. */
    private static void paintIcon(Graphics2D graphics, Color color) {
        graphics.setColor(color);
        graphics.setStroke(new BasicStroke(0.8f));
        graphics.draw(new RoundRectangle2D.Double(4, 4, 12, 8, 2.4, 2.4));
        for (int row = 0; row < 2; row++) {
            for (int column = 0; column < 4; column++) {
                graphics.fill(new Rectangle2D.Double(5.6 + column * 2.4, 5.5 + row * 2, 1.3, 1.1));
            }
        }
        graphics.fill(new Rectangle2D.Double(7, 9.6, 6, 1));
    }

    private static void paintKeys(Graphics2D graphics, VisibleState.KeyboardState keyboard, boolean utf8,
                                  ColorScheme scheme, Color accent, boolean capturing) {
        List<String> keys = keyTexts(keyboard.queue(), utf8);
        boolean beyondWindow = keyboard.size() > keyboard.queue().size();
        boolean crowded = keys.size() > VISIBLE_KEYS || beyondWindow;
        int shown = crowded ? VISIBLE_KEYS - 1 : keys.size();
        if (keys.isEmpty()) {
            paintEmptyKey(graphics, scheme);
        }
        for (int index = 0; index < shown; index++) {
            paintKey(graphics, keys.get(index), KEY_LEFT + index * (KEY_WIDTH + KEY_GAP), index == 0, scheme);
        }
        double moreLeft = KEY_LEFT + shown * (KEY_WIDTH + KEY_GAP) + 1;
        if (crowded) {
            int more = keys.size() - shown + keyboard.size() - keyboard.queue().size();
            PainterSupport.uprightText(graphics, "+" + more, moreLeft, KEY_TOP + (KEY_HEIGHT - LIP) / 2 + 0.5,
                    CHIP_SIZE, PainterSupport.withAlpha(scheme.text(), MORE_ALPHA), along(graphics, PainterSupport.ALIGN_LEFT),
                    fontFor("+0", false));
        }
        if (capturing && !crowded) {
            double caretLeft = KEY_LEFT + (keys.isEmpty() ? KEY_WIDTH + 2 : keys.size() * (KEY_WIDTH + KEY_GAP));
            graphics.setColor(accent);
            graphics.fill(new Rectangle2D.Double(caretLeft, KEY_TOP + 1, CARET_WIDTH, KEY_HEIGHT - LIP - 2));
        }
    }

    private static void paintEmptyKey(Graphics2D graphics, ColorScheme scheme) {
        graphics.setColor(PainterSupport.withAlpha(scheme.text(), EMPTY_OUTLINE_ALPHA));
        graphics.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, DASH, 0f));
        graphics.draw(keyShape(KEY_LEFT + 0.5, KEY_TOP + 0.5, KEY_WIDTH, KEY_HEIGHT));
        PainterSupport.uprightText(graphics, Messages.text("keyboard.painter.empty"), KEY_LEFT + KEY_WIDTH + 5,
                KEY_TOP + KEY_HEIGHT / 2, 7.5f, PainterSupport.withAlpha(scheme.text(), EMPTY_TEXT_ALPHA),
                along(graphics, PainterSupport.ALIGN_LEFT), EMPTY_FONT);
    }

    private static void paintKey(Graphics2D graphics, String text, double left, boolean head, ColorScheme scheme) {
        double faceHeight = KEY_HEIGHT - LIP;
        graphics.setColor(scheme.componentShade());
        graphics.fill(keyShape(left, KEY_TOP, KEY_WIDTH, KEY_HEIGHT));
        graphics.setColor(scheme.componentFill());
        graphics.fill(keyShape(left, KEY_TOP, KEY_WIDTH, faceHeight));
        if (head) {
            graphics.setColor(PainterSupport.withAlpha(scheme.memoryRead(), HEAD_TINT_ALPHA));
            graphics.fill(keyShape(left, KEY_TOP, KEY_WIDTH, faceHeight));
        }
        graphics.setColor(head ? scheme.memoryRead()
                : PainterSupport.withAlpha(scheme.componentStroke(), KEY_BORDER_ALPHA));
        graphics.setStroke(new BasicStroke(head ? 1.1f : 0.7f));
        graphics.draw(keyShape(left + 0.35, KEY_TOP + 0.35, KEY_WIDTH - 0.7, KEY_HEIGHT - 0.7));
        Color textColor = head ? scheme.memoryRead() : scheme.text();
        float size = text.codePointCount(0, text.length()) > 1 ? KEY_WORD_SIZE : KEY_TEXT_SIZE;
        PainterSupport.uprightText(graphics, text, left + KEY_WIDTH / 2, KEY_TOP + faceHeight / 2 + 0.5, size,
                textColor, PainterSupport.ALIGN_CENTER, fontFor(text, head));
        if (head) {
            PainterSupport.uprightText(graphics, "D", left + KEY_WIDTH / 2, KEY_TOP + KEY_HEIGHT + HEAD_MARK_DROP,
                    HEAD_MARK_SIZE, textColor, PainterSupport.ALIGN_CENTER, fontFor("D", true));
        }
    }

    private static RoundRectangle2D keyShape(double left, double top, double width, double height) {
        return new RoundRectangle2D.Double(left, top, width, height, 2 * KEY_RADIUS, 2 * KEY_RADIUS);
    }

    private static void paintFillBar(Graphics2D graphics, int size, int capacity, double width, ColorScheme scheme) {
        double barWidth = width - BAR_LEFT - BAR_RIGHT_MARGIN;
        double ratio = Math.min(1.0, (double) size / capacity);
        graphics.setColor(scheme.componentShade());
        graphics.fill(new RoundRectangle2D.Double(BAR_LEFT, BAR_TOP, barWidth, BAR_HEIGHT, BAR_HEIGHT, BAR_HEIGHT));
        if (ratio <= 0) {
            return;
        }
        graphics.setColor(ratio >= 1 ? scheme.wireConflict() : ratio >= WARNING_RATIO ? scheme.memoryWrite()
                : PainterSupport.withAlpha(scheme.text(), FILL_BAR_ALPHA));
        graphics.fill(new RoundRectangle2D.Double(BAR_LEFT, BAR_TOP, Math.max(BAR_HEIGHT, barWidth * ratio),
                BAR_HEIGHT, BAR_HEIGHT, BAR_HEIGHT));
    }

    /** Each pin's dot (AV lit while the queue has data) and its name inside the part, next to its stub. */
    private static void paintPins(Graphics2D graphics, ComponentType type, boolean hasData, ColorScheme scheme) {
        double width = type.size().width() * UNIT;
        for (PinSpec pin : type.pins()) {
            double pinX = pin.position().x() * UNIT;
            double pinY = pin.position().y() * UNIT;
            boolean available = pin.name().equals("AV");
            graphics.setColor(available ? (hasData ? scheme.wireOne() : scheme.wireZero()) : scheme.componentStroke());
            graphics.fill(new Ellipse2D.Double(pinX - PainterSupport.PIN_DOT_RADIUS, pinY - PainterSupport.PIN_DOT_RADIUS,
                    2 * PainterSupport.PIN_DOT_RADIUS, 2 * PainterSupport.PIN_DOT_RADIUS));
            Color nameColor = PainterSupport.withAlpha(scheme.text(), PIN_NAME_ALPHA);
            if (pinX >= width) {
                PainterSupport.uprightText(graphics, pin.name(), pinX - PIN_NAME_INSET, pinY + 0.5, PIN_NAME_SIZE,
                        nameColor, along(graphics, PainterSupport.ALIGN_RIGHT), fontFor(pin.name(), false));
            } else {
                PainterSupport.uprightText(graphics, pin.name(), pinX, pinY - PIN_NAME_RISE, PIN_NAME_SIZE,
                        nameColor, PainterSupport.ALIGN_CENTER, fontFor(pin.name(), false));
            }
        }
    }

    /** Plex Mono for {@code text}; a logical font for the symbols it has no glyph for (⏎ ⌦ ␣...). */
    private static Font fontFor(String text, boolean bold) {
        Font mono = TerminalFonts.font(TerminalFontFamily.PLEX_MONO, bold);
        if (mono.canDisplayUpTo(text) == -1) {
            return mono;
        }
        return new Font(Font.DIALOG, bold ? Font.BOLD : Font.PLAIN, 1);
    }
}
