package cl.almejo.vsim.ui.panels;

import net.miginfocom.swing.MigLayout;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A row of colour swatches to pick one value of a fixed list: a tunnel entrada's palette colour, an LED's colour, a
 * frame's tint. The caller gives the values, the visible name of each (tooltip and accessible name, so the colour is
 * never the only cue) and its fill, which is asked again at every paint so a theme or scheme change shows at once;
 * an empty fill is a «no colour» swatch, drawn as an outline crossed by a diagonal.
 *
 * <p>The chosen swatch is pressed and circled by a ring in {@link #selectionRingColor()}, with a gap so the ring reads
 * on any hue. With the keyboard the arrows move between swatches and Space or Enter picks the focused one; only a
 * pick by the user calls the choice listeners, so each pick is one edit (one undo step for the callers).
 *
 * @param <T> the kind of value chosen
 */
public final class ColorSwatches<T> extends JPanel {

    private static final int SWATCH_SIZE = 14;
    private static final int SWATCH_CORNER = 4;
    /** Room around the swatch for the selection ring: a gap, then the ring itself. */
    private static final int RING_GAP = 2;
    private static final float RING_WIDTH = 2f;
    private static final int RING_MARGIN = RING_GAP + (int) RING_WIDTH;
    private static final int ICON_SIZE = SWATCH_SIZE + 2 * RING_MARGIN;
    /** WCAG's minimum contrast for graphical objects that carry meaning (1.4.11): the ring must reach it. */
    static final double MINIMUM_RING_CONTRAST = 3.0;
    /** The theme colours tried for the ring, in order of preference: the theme's accent first. */
    private static final List<String> RING_COLOR_KEYS = List.of("Component.accentColor", "Component.linkColor",
            "Label.foreground");
    private static final String CHOOSE_ACTION = "colorSwatches.choose";
    private static final String PREVIOUS_ACTION = "colorSwatches.previous";
    private static final String NEXT_ACTION = "colorSwatches.next";

    private final List<T> values;
    private final List<JToggleButton> swatches = new ArrayList<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private T selectedValue;

    /**
     * @param values   the values offered, in the order shown
     * @param selected the value chosen now; must be one of {@code values}
     * @param nameOf   the visible name of a value
     * @param fillOf   the fill of a value's swatch, read at paint time; empty for «no colour»
     */
    public ColorSwatches(List<T> values, T selected, Function<? super T, String> nameOf,
                         Function<? super T, Optional<Color>> fillOf) {
        super(new MigLayout("insets 0, gap 2"));
        this.values = List.copyOf(values);
        if (!this.values.contains(selected)) {
            throw new IllegalArgumentException(selected + " is not one of " + values);
        }
        Objects.requireNonNull(nameOf, "nameOf");
        Objects.requireNonNull(fillOf, "fillOf");
        this.selectedValue = selected;
        ButtonGroup group = new ButtonGroup();
        for (int index = 0; index < this.values.size(); index++) {
            JToggleButton swatch = newSwatch(index, nameOf, fillOf);
            group.add(swatch);
            swatches.add(swatch);
            add(swatch);
        }
    }

    private JToggleButton newSwatch(int index, Function<? super T, String> nameOf,
                                    Function<? super T, Optional<Color>> fillOf) {
        T value = values.get(index);
        JToggleButton swatch = new JToggleButton(new SwatchIcon(() -> fillOf.apply(value)));
        String name = nameOf.apply(value);
        swatch.setToolTipText(name);
        swatch.getAccessibleContext().setAccessibleName(name);
        swatch.putClientProperty("JButton.buttonType", "toolBarButton");
        swatch.setSelected(value.equals(selectedValue));
        swatch.addActionListener(event -> choose(value));
        bindKeys(swatch, index);
        return swatch;
    }

    private void bindKeys(JToggleButton swatch, int index) {
        InputMap keys = swatch.getInputMap(JComponent.WHEN_FOCUSED);
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), CHOOSE_ACTION);
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), PREVIOUS_ACTION);
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), PREVIOUS_ACTION);
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), NEXT_ACTION);
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), NEXT_ACTION);
        swatch.getActionMap().put(CHOOSE_ACTION, action(swatch::doClick));
        swatch.getActionMap().put(PREVIOUS_ACTION, action(() -> focusSwatch(index - 1)));
        swatch.getActionMap().put(NEXT_ACTION, action(() -> focusSwatch(index + 1)));
    }

    private static AbstractAction action(Runnable body) {
        return new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                body.run();
            }
        };
    }

    /** Moves the keyboard focus to the swatch at {@code index}, wrapping around both ends. */
    private void focusSwatch(int index) {
        swatches.get(Math.floorMod(index, swatches.size())).requestFocusInWindow();
    }

    private void choose(T value) {
        if (value.equals(selectedValue)) {
            return;
        }
        selectedValue = value;
        listeners.forEach(Runnable::run);
    }

    /** The value chosen now. */
    public T selectedValue() {
        return selectedValue;
    }

    /** Shows {@code value} as the chosen one without calling the listeners (the document changed it). */
    public void select(T value) {
        int index = values.indexOf(value);
        if (index < 0) {
            throw new IllegalArgumentException(value + " is not one of " + values);
        }
        selectedValue = value;
        swatches.get(index).setSelected(true);
    }

    /** The values offered, in the order shown. */
    public List<T> values() {
        return values;
    }

    /** The swatch of the value at {@code index} of {@link #values()}. */
    public JToggleButton swatch(int index) {
        return swatches.get(index);
    }

    /** Calls {@code listener} each time the user picks another value. */
    public void addChoiceListener(Runnable listener) {
        listeners.add(listener);
    }

    /**
     * The colour of the ring around the chosen swatch, read when painting: the first of the theme's accent, link and
     * text colours that stands out at least {@link #MINIMUM_RING_CONTRAST}:1 against both the panel and the pressed
     * swatch's background (FlatLaf's dark accent is too dim on the dark panel, so there the brighter link blue wins).
     */
    static Color selectionRingColor() {
        List<Color> backgrounds = new ArrayList<>();
        Optional.ofNullable(UIManager.getColor("Panel.background")).ifPresent(backgrounds::add);
        Optional.ofNullable(UIManager.getColor("ToggleButton.toolbar.selectedBackground")).ifPresent(backgrounds::add);
        Color fallback = null;
        for (String key : RING_COLOR_KEYS) {
            Color candidate = UIManager.getColor(key);
            if (candidate == null) {
                continue;
            }
            fallback = candidate;
            if (backgrounds.stream().allMatch(background -> contrast(candidate, background)
                    >= MINIMUM_RING_CONTRAST)) {
                return candidate;
            }
        }
        return fallback != null ? fallback : Color.BLACK;
    }

    /** The WCAG contrast ratio between two colours (1 to 21). */
    static double contrast(Color first, Color second) {
        double lighter = Math.max(luminance(first), luminance(second));
        double darker = Math.min(luminance(first), luminance(second));
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double luminance(Color color) {
        return 0.2126 * linear(color.getRed()) + 0.7152 * linear(color.getGreen()) + 0.0722 * linear(color.getBlue());
    }

    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= 0.03928 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    /** The theme's border colour, which outlines every swatch so a white or «no colour» one still shows its edge. */
    private static Color outlineColor() {
        Color border = UIManager.getColor("Component.borderColor");
        return border != null ? border : Color.GRAY;
    }

    /**
     * A filled rounded square in the value's colour, as the theme shows it now (or an outline crossed by a diagonal
     * for «no colour»); when its button is the chosen one, a ring in {@link #selectionRingColor()} circles it.
     */
    private record SwatchIcon(Supplier<Optional<Color>> fill) implements Icon {

        @Override
        public void paintIcon(Component owner, Graphics graphics, int x, int y) {
            Graphics2D canvas = (Graphics2D) graphics.create();
            try {
                canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                RoundRectangle2D square = new RoundRectangle2D.Float(x + RING_MARGIN + 0.5f, y + RING_MARGIN + 0.5f,
                        SWATCH_SIZE - 1, SWATCH_SIZE - 1, SWATCH_CORNER, SWATCH_CORNER);
                Optional<Color> color = fill.get();
                color.ifPresent(opaque -> {
                    canvas.setColor(opaque);
                    canvas.fill(square);
                });
                canvas.setColor(outlineColor());
                canvas.setStroke(new BasicStroke(1f));
                canvas.draw(square);
                if (color.isEmpty()) {
                    canvas.draw(new Line2D.Double(square.getMaxX() - 2, square.getMinY() + 2,
                            square.getMinX() + 2, square.getMaxY() - 2));
                }
                if (owner instanceof AbstractButton button && button.isSelected()) {
                    paintRing(canvas, x, y);
                }
            } finally {
                canvas.dispose();
            }
        }

        private static void paintRing(Graphics2D canvas, int x, int y) {
            float inset = RING_WIDTH / 2;
            float size = ICON_SIZE - RING_WIDTH;
            float corner = SWATCH_CORNER + 2 * RING_MARGIN;
            canvas.setColor(selectionRingColor());
            canvas.setStroke(new BasicStroke(RING_WIDTH));
            canvas.draw(new RoundRectangle2D.Float(x + inset, y + inset, size, size, corner, corner));
        }

        @Override
        public int getIconWidth() {
            return ICON_SIZE;
        }

        @Override
        public int getIconHeight() {
            return ICON_SIZE;
        }
    }
}
