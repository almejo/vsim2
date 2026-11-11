package cl.almejo.vsim.ui.whatsnew;

import cl.almejo.vsim.ui.export.CircuitImage;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.panels.Keycaps;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.shortcuts.KeyNames;
import cl.almejo.vsim.ui.theme.ThemeColors;
import com.formdev.flatlaf.util.UIScale;

import javax.swing.JComponent;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

/**
 * The picture of a tip of the day (mockup v11): its circuit drawn with the user's scheme, two pictures side by side
 * with the big key between them («antes y después»), or an image; the big key is the user's own binding, drawn here by
 * VSim (never part of an image), on a plate so a circuit under it does not show through. Everything is read at paint
 * time: the scheme, the theme's accent and fonts.
 */
final class TipPicture extends JComponent {

    /** One picture: a tip circuit, or an image (a capture). */
    sealed interface Slot permits CircuitSlot, ImageSlot {
    }

    record CircuitSlot(TipCircuit circuit) implements Slot {

        CircuitSlot {
            Objects.requireNonNull(circuit, "circuit");
        }
    }

    record ImageSlot(BufferedImage image) implements Slot {

        ImageSlot {
            Objects.requireNonNull(image, "image");
        }
    }

    /** What the picture shows: its kind, its pictures (a slot that could not be read is left blank) and its key. */
    record Content(TipKind kind, List<Optional<Slot>> slots, Optional<List<String>> key) {

        Content {
            Objects.requireNonNull(kind, "kind");
            slots = List.copyOf(slots);
            Objects.requireNonNull(key, "key");
        }

        static final Content EMPTY = new Content(TipKind.IMAGE, List.of(), Optional.empty());
    }

    /** The mockup's picture: 180 × 100. */
    private static final double ASPECT = 100.0 / 180.0;
    private static final int CORNER = 6;
    private static final int PADDING = 6;
    /**
     * A tip circuit is never drawn bigger than this, so the parts look about the same size in every tip (a pair of
     * «antes y después» halves comes out near it too).
     */
    private static final double MAXIMUM_SCALE = 0.7;
    /**
     * A circuit with a frame is about the frame (its title has to read at the card's real size), so it may draw
     * bigger than the others.
     */
    private static final double FRAMED_MAXIMUM_SCALE = 1.4;
    /** The big key: the UI font times this. */
    private static final float BIG_KEY_SCALE = 1.4f;
    private static final int KEY_PLATE_PADDING = 4;
    private static final int KEY_GAP = 10;
    /** The room between the two pictures of «antes y después» and the two panes of a split view. */
    private static final int SLOT_GAP = 8;
    /** How much closer the right pane of a split view shows the circuit than the left one. */
    private static final double ZOOM_IN = 2.2;
    private static final int ARROW_LENGTH = 22;
    private static final int ARROW_HEAD = 4;
    private static final int CAPTION_GAP = 2;
    private static final int PLATE_ALPHA = 235;

    private final PainterRegistry painters;
    private final Supplier<ColorScheme> scheme;
    private Content content = Content.EMPTY;

    TipPicture(PainterRegistry painters, Supplier<ColorScheme> scheme) {
        this.painters = Objects.requireNonNull(painters, "painters");
        this.scheme = Objects.requireNonNull(scheme, "scheme");
        setOpaque(false);
    }

    /** The height of the picture at {@code width}, the mockup's proportions. */
    static int heightFor(int width) {
        return (int) Math.round(width * ASPECT);
    }

    void show(Content shown) {
        this.content = Objects.requireNonNull(shown, "shown");
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D painter = (Graphics2D) graphics.create();
        try {
            painter.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            painter.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            RoundRectangle2D frame = new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1,
                    UIScale.scale(CORNER), UIScale.scale(CORNER));
            painter.setColor(scheme.get().background());
            painter.fill(frame);
            Graphics2D inside = (Graphics2D) painter.create();
            inside.clip(frame);
            paintContent(inside);
            inside.dispose();
            painter.setColor(borderColour());
            painter.draw(frame);
        } finally {
            painter.dispose();
        }
    }

    private void paintContent(Graphics2D painter) {
        int padding = UIScale.scale(PADDING);
        Rectangle area = new Rectangle(padding, padding, getWidth() - 2 * padding, getHeight() - 2 * padding);
        switch (content.kind()) {
            case CIRCUIT, IMAGE -> paintAlone(painter, area);
            case SPLIT -> paintSplit(painter, area);
            case BEFORE_AFTER -> paintBeforeAndAfter(painter, area);
        }
    }

    /** One picture over the area, its key (if any) in the bottom-right corner, in a row of its own. */
    private void paintAlone(Graphics2D painter, Rectangle area) {
        int keyRow = content.key().isPresent()
                ? Keycaps.height(this, keyFont(), BIG_KEY_SCALE) + UIScale.scale(KEY_GAP) / 2 : 0;
        Rectangle picture = new Rectangle(area.x, area.y, area.width, area.height - keyRow);
        slot(0).ifPresent(slot -> paintSlot(painter, slot, picture, OptionalDouble.empty()));
        content.key().ifPresent(keys -> {
            int width = Keycaps.width(this, keyFont(), BIG_KEY_SCALE, keys, !KeyNames.MAC_NOTATION);
            int height = Keycaps.height(this, keyFont(), BIG_KEY_SCALE);
            paintKey(painter, keys, area.x + area.width - width, area.y + area.height - height);
        });
    }

    /**
     * Two pictures side by side at one scale, «Antes» → «Después» under them and the key across the bottom, so the
     * pictures keep the whole width (the mockup's key between them left them too little room).
     */
    private void paintBeforeAndAfter(Graphics2D painter, Rectangle area) {
        Font caption = captionFont();
        FontMetrics captionMetrics = getFontMetrics(caption);
        int captionHeight = captionMetrics.getHeight() + UIScale.scale(CAPTION_GAP);
        int keyWidth = content.key().map(keys -> Keycaps.width(this, keyFont(), BIG_KEY_SCALE, keys,
                !KeyNames.MAC_NOTATION)).orElse(0);
        int keyHeight = Keycaps.height(this, keyFont(), BIG_KEY_SCALE);
        int keyRow = content.key().isPresent() ? keyHeight + UIScale.scale(KEY_GAP) : 0;
        int slotWidth = (area.width - UIScale.scale(SLOT_GAP)) / 2;
        Rectangle before = new Rectangle(area.x, area.y, slotWidth, area.height - captionHeight - keyRow);
        Rectangle after = new Rectangle(area.x + area.width - slotWidth, area.y, slotWidth, before.height);
        OptionalDouble common = commonScale(before);
        slot(0).ifPresent(slot -> paintSlot(painter, slot, before, common));
        slot(1).ifPresent(slot -> paintSlot(painter, slot, after, common));
        painter.setFont(caption);
        painter.setColor(mutedColour());
        int captionBaseline = before.y + before.height + captionMetrics.getAscent();
        paintCentred(painter, Messages.text("tip.before"), before, captionBaseline);
        paintCentred(painter, Messages.text("tip.after"), after, captionBaseline);
        int centreX = area.x + area.width / 2;
        paintArrow(painter, centreX, captionBaseline - captionMetrics.getAscent() / 2 + UIScale.scale(1));
        content.key().ifPresent(keys -> paintKey(painter, keys, centreX - keyWidth / 2,
                area.y + area.height - keyHeight));
    }

    /**
     * The circuit in two panes with a divider between them, as a split view shows it: the whole circuit on the left,
     * and on the right its middle {@value #ZOOM_IN} times closer (the tip's circuit puts a gate there). Its key (if
     * any) in the bottom-right corner.
     */
    private void paintSplit(Graphics2D painter, Rectangle area) {
        int divider = UIScale.scale(SLOT_GAP);
        int paneWidth = (area.width - divider) / 2;
        Rectangle whole = new Rectangle(area.x, area.y, paneWidth, area.height);
        Rectangle closer = new Rectangle(area.x + area.width - paneWidth, area.y, paneWidth, area.height);
        slot(0).filter(CircuitSlot.class::isInstance).map(slot -> ((CircuitSlot) slot).circuit())
                .flatMap(circuit -> circuit.image(painters, scheme.get())).ifPresent(image -> {
                    double scale = fittingScale(image, whole, MAXIMUM_SCALE);
                    paintCircuit(painter, image, whole, scale);
                    paintCircuit(painter, image, closer, scale * ZOOM_IN);
                });
        int dividerX = area.x + paneWidth + divider / 2;
        painter.setColor(borderColour());
        painter.setStroke(new BasicStroke(UIScale.scale(2f)));
        painter.drawLine(dividerX, area.y - UIScale.scale(PADDING), dividerX,
                area.y + area.height + UIScale.scale(PADDING));
        content.key().ifPresent(keys -> {
            int width = Keycaps.width(this, keyFont(), BIG_KEY_SCALE, keys, !KeyNames.MAC_NOTATION);
            int height = Keycaps.height(this, keyFont(), BIG_KEY_SCALE);
            paintKey(painter, keys, area.x + area.width - width, area.y + area.height - height);
        });
    }

    private Optional<Slot> slot(int index) {
        return index < content.slots().size() ? content.slots().get(index) : Optional.empty();
    }

    private void paintCentred(Graphics2D painter, String text, Rectangle under, int baseline) {
        painter.drawString(text, under.x + (under.width - painter.getFontMetrics().stringWidth(text)) / 2, baseline);
    }

    /** {@code slot} as big as it fits in {@code area} (a circuit never past 100 %), centred. */
    private void paintSlot(Graphics2D painter, Slot slot, Rectangle area, OptionalDouble scale) {
        switch (slot) {
            case CircuitSlot circuitSlot -> circuitSlot.circuit().image(painters, scheme.get())
                    .ifPresent(image -> paintCircuit(painter, image, area,
                            scale.orElseGet(() -> fittingScale(image, area,
                                    circuitSlot.circuit().hasFrame() ? FRAMED_MAXIMUM_SCALE : MAXIMUM_SCALE))));
            case ImageSlot imageSlot -> paintImage(painter, imageSlot.image(), area);
        }
    }

    /**
     * One scale for both circuits of «antes y después», the one at which both fit in a slot of {@code slot}'s size:
     * the same parts look the same size on both sides. Empty when a side is not a circuit.
     */
    private OptionalDouble commonScale(Rectangle slot) {
        double scale = MAXIMUM_SCALE;
        for (int index = 0; index < 2; index++) {
            Optional<CircuitImage> image = slot(index).filter(CircuitSlot.class::isInstance)
                    .flatMap(found -> ((CircuitSlot) found).circuit().image(painters, scheme.get()));
            if (image.isEmpty()) {
                return OptionalDouble.empty();
            }
            scale = Math.min(scale, fittingScale(image.get(), slot, MAXIMUM_SCALE));
        }
        return OptionalDouble.of(scale);
    }

    /** As big as {@code image} fits in {@code area}, never past 100 %. */
    private static double fittingScale(CircuitImage image, Rectangle area, double maximum) {
        return Math.min(maximum, Math.min((double) area.width / image.width(1.0),
                (double) area.height / image.height(1.0)));
    }

    /** {@code image} at {@code scale}, its centre on the centre of {@code area}, cut to {@code area}. */
    private static void paintCircuit(Graphics2D painter, CircuitImage image, Rectangle area, double scale) {
        Graphics2D placed = (Graphics2D) painter.create();
        try {
            placed.clip(area);
            placed.translate(area.x + (area.width - image.width(scale)) / 2,
                    area.y + (area.height - image.height(scale)) / 2);
            image.paint(placed, scale);
        } finally {
            placed.dispose();
        }
    }

    private static void paintImage(Graphics2D painter, BufferedImage image, Rectangle area) {
        double scale = Math.min((double) area.width / image.getWidth(), (double) area.height / image.getHeight());
        int width = (int) Math.round(image.getWidth() * scale);
        int height = (int) Math.round(image.getHeight() * scale);
        painter.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        painter.drawImage(image, area.x + (area.width - width) / 2, area.y + (area.height - height) / 2, width,
                height, null);
    }

    /** The big key on a plate of the circuit's background, so what is under it does not show through. */
    private void paintKey(Graphics2D painter, List<String> keys, int x, int y) {
        int width = Keycaps.width(this, keyFont(), BIG_KEY_SCALE, keys, !KeyNames.MAC_NOTATION);
        int height = Keycaps.height(this, keyFont(), BIG_KEY_SCALE);
        int plate = UIScale.scale(KEY_PLATE_PADDING);
        Color background = scheme.get().background();
        painter.setColor(new Color(background.getRed(), background.getGreen(), background.getBlue(), PLATE_ALPHA));
        painter.fillRoundRect(x - plate, y - plate, width + 2 * plate, height + 2 * plate, 2 * plate, 2 * plate);
        Keycaps.paintOutlined(painter, keyFont(), BIG_KEY_SCALE, keys, !KeyNames.MAC_NOTATION, x, y,
                ThemeColors.accent(), inkColour());
    }

    private void paintArrow(Graphics2D painter, int centreX, int y) {
        int half = UIScale.scale(ARROW_LENGTH) / 2;
        int head = UIScale.scale(ARROW_HEAD);
        Path2D arrow = new Path2D.Float();
        arrow.moveTo(centreX - half, y);
        arrow.lineTo(centreX + half, y);
        arrow.moveTo(centreX + half - head, y - head);
        arrow.lineTo(centreX + half, y);
        arrow.lineTo(centreX + half - head, y + head);
        painter.setColor(mutedColour());
        painter.setStroke(new BasicStroke(UIScale.scale(1.5f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        painter.draw(arrow);
    }

    private static Font keyFont() {
        Font font = UIManager.getFont("Label.font");
        return font == null ? new Font(Font.SANS_SERIF, Font.PLAIN, 13) : font;
    }

    private static Font captionFont() {
        Font font = keyFont();
        return font.deriveFont(font.getSize2D() - 2);
    }

    /** The text's colour of the theme: the names on the keycaps, readable in light and dark. */
    static Color inkColour() {
        Color ink = UIManager.getColor("Label.foreground");
        return ink == null ? Color.BLACK : ink;
    }

    static Color mutedColour() {
        Color muted = UIManager.getColor("Label.disabledForeground");
        return muted == null ? Color.GRAY : muted;
    }

    static Color borderColour() {
        Color border = UIManager.getColor("Component.borderColor");
        return border == null ? Color.LIGHT_GRAY : border;
    }
}
