package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.app.Listeners;
import cl.almejo.vsim.ui.theme.ReadableColors;

import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A row of colour swatches to pick one value of a fixed list: a tunnel entrada's palette colour, an LED's colour, a
 * frame's tint, a note's background, text and border. The caller gives the values, the visible name of each (tooltip
 * and accessible name, so the colour is never the only cue) and its fill, which is asked again at every paint so a
 * theme or scheme change shows at once; an empty fill is a «no colour» swatch, drawn as an outline crossed by a
 * diagonal.
 *
 * <p>The chosen swatch is pressed and circled by a ring in {@link #selectionRingColor()}, with a gap so the ring reads
 * on any hue. With the keyboard the row is one Tab stop (the chosen swatch, or the one the arrows last moved to), the
 * arrows move between swatches, Space picks the focused one and Enter picks it and then presses the dialog's default
 * button (in «Configurar…», OK), as Enter on the old combo box did. Only a pick by the user calls the choice
 * listeners, so each pick is one edit (one undo step for the callers). Screen readers announce the row as a group
 * named by the caption whose {@code setLabelFor} points at it, and each swatch by its colour's name.
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
    /**
     * The ring's blue on a light and on a dark theme. The themes' own accent and link blues miss 3:1 against the
     * pressed swatch on FlatLaf Dark and on macOS light, so the ring has its own pair (checked on all four themes).
     */
    private static final Color LIGHT_RING = new Color(0x1F5FAD);
    private static final Color DARK_RING = new Color(0x9EC2FF);
    private static final String CHOOSE_ACTION = "colorSwatches.choose";
    private static final String PREVIOUS_ACTION = "colorSwatches.previous";
    private static final String NEXT_ACTION = "colorSwatches.next";

    private final List<T> values;
    private final List<JToggleButton> swatches = new ArrayList<>();
    private final Listeners<Runnable> listeners = new Listeners<>();
    private final ButtonGroup group = new ButtonGroup();
    private T selectedValue;
    private boolean showsNoChoice;

    /**
     * @param values   the values offered, in the order shown
     * @param selected the value chosen now; must be one of {@code values}
     * @param nameOf   the visible name of a value
     * @param fillOf   the fill of a value's swatch, read at paint time; empty for «no colour»
     */
    public ColorSwatches(List<T> values, T selected, Function<? super T, String> nameOf,
                         Function<? super T, Optional<Color>> fillOf) {
        this(values, selected, nameOf, fillOf, Integer.MAX_VALUE);
    }

    /**
     * As the four-argument constructor, in rows of at most {@code perRow} swatches (a long list in a narrow panel);
     * the arrows still go through them in order, from the end of a row to the start of the next.
     */
    public ColorSwatches(List<T> values, T selected, Function<? super T, String> nameOf,
                         Function<? super T, Optional<Color>> fillOf, int perRow) {
        super(null);
        if (perRow < 1) {
            throw new IllegalArgumentException("perRow must be at least 1, was " + perRow);
        }
        setLayout(new WrappingRows(perRow));
        this.values = List.copyOf(values);
        if (!this.values.contains(selected)) {
            throw new IllegalArgumentException(selected + " is not one of " + values);
        }
        Objects.requireNonNull(nameOf, "nameOf");
        Objects.requireNonNull(fillOf, "fillOf");
        this.selectedValue = selected;
        for (int index = 0; index < this.values.size(); index++) {
            JToggleButton swatch = newSwatch(index, nameOf, fillOf);
            group.add(swatch);
            swatches.add(swatch);
            add(swatch);
        }
        makeTabStop(this.values.indexOf(selected));
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
        swatch.addFocusListener(new RovingTabStop(index));
        bindKeys(swatch, index);
        return swatch;
    }

    /**
     * Keeps the row a single Tab stop: the swatch that gains the focus becomes the stop, and when the focus leaves the
     * row the stop goes back to the chosen swatch.
     */
    private final class RovingTabStop extends FocusAdapter {

        private final int index;

        RovingTabStop(int index) {
            this.index = index;
        }

        @Override
        public void focusGained(FocusEvent event) {
            makeTabStop(index);
        }

        @Override
        public void focusLost(FocusEvent event) {
            if (!swatches.contains(event.getOppositeComponent())) {
                makeTabStop(values.indexOf(selectedValue));
            }
        }
    }

    /**
     * Lays the swatches out left to right and wraps them onto as many lines as the width allows (and no more than
     * {@code perRow} to a line). A layout manager cannot be told its width by the grid that holds it, so the preferred
     * height is the one for the width the row has now; when the grid gives the row another width and the height it
     * needs changes, the row asks to be laid out again (one extra pass, then it is stable). The minimum width is one
     * swatch, so the grid can shrink the row to the column instead of widening the panel.
     */
    private static final class WrappingRows implements LayoutManager {

        private static final int GAP = 2;
        private final int perRow;

        WrappingRows(int perRow) {
            this.perRow = perRow;
        }

        @Override
        public void addLayoutComponent(String name, Component component) {
        }

        @Override
        public void removeLayoutComponent(Component component) {
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            Dimension swatch = swatchSize(parent);
            int count = parent.getComponentCount();
            int widest = Math.min(count, perRow);
            int fullWidth = widest * swatch.width + Math.max(0, widest - 1) * GAP;
            int width = parent.getWidth();
            int lines = linesFor(count, swatch.width, width);
            return new Dimension(fullWidth, heightOf(lines, swatch.height));
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            Dimension swatch = swatchSize(parent);
            int lines = parent.getWidth() <= 0 ? 1 : linesFor(parent.getComponentCount(), swatch.width, parent.getWidth());
            return new Dimension(swatch.width, heightOf(Math.max(lines, 1), swatch.height));
        }

        @Override
        public void layoutContainer(Container parent) {
            Dimension swatch = swatchSize(parent);
            int count = parent.getComponentCount();
            int inLine = perLine(swatch.width, parent.getWidth(), count);
            for (int index = 0; index < count; index++) {
                int line = index / inLine;
                int column = index % inLine;
                parent.getComponent(index).setBounds(column * (swatch.width + GAP), line * (swatch.height + GAP),
                        swatch.width, swatch.height);
            }
            int lines = (count + inLine - 1) / inLine;
            if (parent.getPreferredSize().height != heightOf(lines, swatch.height)) {
                parent.invalidate();
                SwingUtilities.invokeLater(parent::revalidate);
            }
        }

        /**
         * How many swatches go on a line {@code width} pixels wide; with no width yet (0, before the grid gives it one)
         * as many as a line may hold, the one line {@link #preferredLayoutSize} counts then: laying it out one per line
         * would differ from that and ask for a new layout forever (review M-1).
         */
        private int perLine(int swatchWidth, int width, int count) {
            if (width <= 0) {
                return Math.max(1, Math.min(perRow, count));
            }
            int fitting = Math.max(1, (width + GAP) / (swatchWidth + GAP));
            return Math.min(perRow, fitting);
        }

        private int linesFor(int count, int swatchWidth, int width) {
            int inLine = perLine(swatchWidth, width, count);
            return (count + inLine - 1) / inLine;
        }

        private static int heightOf(int lines, int swatchHeight) {
            return lines * swatchHeight + Math.max(0, lines - 1) * GAP;
        }

        private static Dimension swatchSize(Container parent) {
            return parent.getComponentCount() == 0 ? new Dimension(ICON_SIZE, ICON_SIZE)
                    : parent.getComponent(0).getPreferredSize();
        }
    }

    /** Only the swatch at {@code index} takes the focus with Tab; the arrows reach the others. */
    private void makeTabStop(int index) {
        for (int swatchIndex = 0; swatchIndex < swatches.size(); swatchIndex++) {
            swatches.get(swatchIndex).setFocusable(swatchIndex == index);
        }
    }

    /** Moves the Tab stop to the chosen swatch unless the user is in the row now (it follows the focus there). */
    private void moveTabStopToTheChosenSwatch() {
        if (swatches.stream().noneMatch(JToggleButton::isFocusOwner)) {
            makeTabStop(values.indexOf(selectedValue));
        }
    }

    private void bindKeys(JToggleButton swatch, int index) {
        InputMap keys = swatch.getInputMap(JComponent.WHEN_FOCUSED);
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), CHOOSE_ACTION);
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), PREVIOUS_ACTION);
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), PREVIOUS_ACTION);
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), NEXT_ACTION);
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), NEXT_ACTION);
        swatch.getActionMap().put(CHOOSE_ACTION, Actions.unnamed(() -> {
            swatch.doClick();
            pressDefaultButton(swatch);
        }));
        swatch.getActionMap().put(PREVIOUS_ACTION, Actions.unnamed(() -> focusSwatch(index - 1)));
        swatch.getActionMap().put(NEXT_ACTION, Actions.unnamed(() -> focusSwatch(index + 1)));
    }

    /** The default button of the window holding {@code swatch} (a dialog's OK), pressed if there is one. */
    private static void pressDefaultButton(JToggleButton swatch) {
        JRootPane rootPane = SwingUtilities.getRootPane(swatch);
        JButton defaultButton = rootPane == null ? null : rootPane.getDefaultButton();
        if (defaultButton != null && defaultButton.isEnabled()) {
            defaultButton.doClick();
        }
    }

    /**
     * Moves the keyboard focus to the swatch at {@code index}, wrapping around both ends. It becomes focusable first;
     * the others stop being focusable once it has the focus ({@link RovingTabStop}), not before, or Swing would push
     * the focus out of the row.
     */
    private void focusSwatch(int index) {
        JToggleButton target = swatches.get(Math.floorMod(index, swatches.size()));
        target.setFocusable(true);
        target.requestFocusInWindow();
    }

    private void choose(T value) {
        if (!showsNoChoice && value.equals(selectedValue)) {
            return;
        }
        selectedValue = value;
        showsNoChoice = false;
        moveTabStopToTheChosenSwatch();
        listeners.fire(Runnable::run);
    }

    /** The value chosen now; while {@link #showsNoChoice()}, the last one chosen. */
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
        showsNoChoice = false;
        swatches.get(index).setSelected(true);
        moveTabStopToTheChosenSwatch();
    }

    /**
     * Shows no swatch as chosen, without calling the listeners: the row stands for several things whose values
     * differ (notes of different colours). Any pick then calls the listeners, even of the value chosen last.
     */
    public void showNoChoice() {
        showsNoChoice = true;
        group.clearSelection();
    }

    /** True while no swatch is shown as chosen ({@link #showNoChoice}). */
    public boolean showsNoChoice() {
        return showsNoChoice;
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
     * The colour of the ring around the chosen swatch, read when painting: a blue that stands out at least
     * {@link #MINIMUM_RING_CONTRAST}:1 against both the panel and the pressed swatch's background, dark on a light
     * theme and bright on a dark one.
     */
    static Color selectionRingColor() {
        return ReadableColors.dark() ? DARK_RING : LIGHT_RING;
    }

    /** The row is a group for screen readers (its name comes from the caption's {@code setLabelFor}). */
    @Override
    public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJPanel() {
                @Override
                public AccessibleRole getAccessibleRole() {
                    return AccessibleRole.GROUP_BOX;
                }
            };
        }
        return accessibleContext;
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
