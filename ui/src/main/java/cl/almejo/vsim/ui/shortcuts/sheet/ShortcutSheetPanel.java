package cl.almejo.vsim.ui.shortcuts.sheet;

import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.panels.Keycaps;
import cl.almejo.vsim.ui.shortcuts.KeyNames;
import cl.almejo.vsim.ui.theme.SwitchBox;
import com.formdev.flatlaf.FlatLaf;
import net.miginfocom.swing.MigLayout;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * What the sheet of shortcuts shows: a dark translucent veil over the circuit area (the circuit still shows, dimmed)
 * and on it a centred card, at most {@value #MAXIMUM_CARD_WIDTH} px wide, with the groups of shortcuts (each key a
 * keycap, as in the chips of «Mostrar los atajos al usarlos»), the strip with the phrase of the gesture under way
 * (only while there is one) and, last, the foot: the switch «Mostrar los atajos al usarlos», the hint «? o Esc» and
 * the link «Ver y cambiar todos los atajos…». It never takes the keyboard (the canvas keeps it, so the phrase of a
 * gesture stays and «?» or Esc reach the sheet's key hook); a click on the veil closes the sheet, one on the card stays
 * there. Colours come from the theme at paint time; everything is drawn at {@link #opacity()} while it fades in.
 */
final class ShortcutSheetPanel extends JPanel {

    /** The widest the card gets: the mockup's 900 px, less in a narrower circuit area. */
    static final int MAXIMUM_CARD_WIDTH = 900;
    /** The veil left visible around the card. */
    private static final int VEIL_MARGIN = 24;
    private static final int CORNER = 18;
    private static final int SHADOW_SIZE = 14;
    private static final int SHADOW_ALPHA = 7;
    private static final int LIGHT_VEIL_ALPHA = 110;
    private static final int DARK_VEIL_ALPHA = 150;
    private static final float HEADING_SCALE = 0.8f;
    private static final float HEADING_TRACKING = 0.08f;
    private static final String ROW_SEPARATOR = " · ";

    private final JPanel card = new Card();
    private final JPanel columnsPanel = new JPanel(new MigLayout("insets 0, fillx, gapx 40",
            "[grow, fill][grow, fill]", "[top]"));
    private final JTextArea gesture = new GestureStrip();
    private final JLabel showUsedShortcutsLabel = new ThemedLabel("Label.foreground",
            Messages.text("action.showUsedShortcuts"));
    private final SwitchBox showUsedShortcuts = new SwitchBox(false);
    private final JButton allShortcuts = new LinkButton(Messages.text("shortcutSheet.allShortcuts"));
    private List<SheetSelection.Column> columns = List.of();
    private String gesturePhrase = "";
    private float opacity = 1f;

    /**
     * {@code showUsedShortcutsChosen} hears the switch (the preference behind Ver ▸ «Mostrar los atajos que se
     * usan»), {@code allShortcutsChosen} the link and {@code dismissed} a click on the veil.
     */
    ShortcutSheetPanel(Consumer<Boolean> showUsedShortcutsChosen, Runnable allShortcutsChosen, Runnable dismissed) {
        super(null);
        setOpaque(false);
        setFocusable(false);
        getAccessibleContext().setAccessibleName(Messages.text("shortcutSheet.title"));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (!card.getBounds().contains(event.getPoint())) {
                    dismissed.run();
                }
            }
        });
        card.addMouseListener(new MouseAdapter() { }); // a click on the card is the card's
        add(card);
        columnsPanel.setOpaque(false);
        card.add(columnsPanel, "growx, wmin 0");
        gesture.setVisible(false);
        card.add(gesture, "hidemode 3, growx, wmin 0, gaptop 14");
        card.add(foot(showUsedShortcutsChosen, allShortcutsChosen), "growx, wmin 0, gaptop 14");
    }

    /** The switch with its label on the left, the closing hint in the middle, the link on the right. */
    private JPanel foot(Consumer<Boolean> showUsedShortcutsChosen, Runnable allShortcutsChosen) {
        showUsedShortcuts.setFocusable(false);
        showUsedShortcuts.addActionListener(event -> showUsedShortcutsChosen.accept(showUsedShortcuts.isSelected()));
        showUsedShortcutsLabel.setLabelFor(showUsedShortcuts);
        showUsedShortcutsLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                showUsedShortcuts.doClick(0);
            }
        });
        allShortcuts.addActionListener(event -> allShortcutsChosen.run());
        JPanel foot = new JPanel(new MigLayout("insets 0, fillx", "[][][grow, center][]"));
        foot.setOpaque(false);
        foot.add(showUsedShortcutsLabel);
        foot.add(showUsedShortcuts);
        foot.add(new ThemedLabel("Label.disabledForeground", Messages.text("shortcutSheet.closeHint")));
        foot.add(allShortcuts, "hidemode 3");
        return foot;
    }

    /**
     * Shows {@code shown}: the first half of the groups stacked on the left, the rest (one more when they are odd, the
     * short ones: Ver, Simular, Ayuda) on the right.
     */
    void setColumns(List<SheetSelection.Column> shown) {
        columns = List.copyOf(shown);
        columnsPanel.removeAll();
        int leftCount = columns.size() / 2;
        columnsPanel.add(stack(columns.subList(0, leftCount)), "wmin 0");
        columnsPanel.add(stack(columns.subList(leftCount, columns.size())), "wmin 0");
        revalidate();
        repaint();
    }

    private static JPanel stack(List<SheetSelection.Column> groups) {
        JPanel stack = new JPanel(new MigLayout("insets 0, wrap 1, gapy 24", "[left]"));
        stack.setOpaque(false);
        groups.forEach(group -> stack.add(groupPanel(group)));
        return stack;
    }

    private static JPanel groupPanel(SheetSelection.Column group) {
        JPanel panel = new JPanel(new MigLayout("insets 0, wrap 2, gapy 10, gapx 12", "[right][left]"));
        panel.setOpaque(false);
        JLabel heading = new ThemedLabel("Label.disabledForeground", group.title().toUpperCase(Locale.ROOT));
        Font font = heading.getFont();
        heading.setFont(font.deriveFont(Map.of(TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD,
                TextAttribute.SIZE, font.getSize2D() * HEADING_SCALE, TextAttribute.TRACKING, HEADING_TRACKING)));
        panel.add(heading, "span 2, gapbottom 6, align left");
        for (SheetSelection.Row row : group.rows()) {
            panel.add(new KeycapsLabel(row.keys()));
            panel.add(new ThemedLabel("Label.foreground", row.name()));
        }
        return panel;
    }

    /** Shows {@code phrase} in the strip at the card's bottom; an empty phrase hides the strip. */
    void setGesturePhrase(String phrase) {
        gesturePhrase = phrase;
        gesture.setText(phrase.isEmpty() ? "" : Messages.format("shortcutSheet.gesture", phrase));
        gesture.setVisible(!phrase.isEmpty());
        revalidate();
        repaint();
    }

    void setShowUsedShortcuts(boolean shown) {
        showUsedShortcuts.setSelected(shown);
    }

    /** Whether the link to Preferencias ▸ Atajos de teclado shows (it needs the preferences to be there). */
    void setAllShortcutsAvailable(boolean available) {
        allShortcuts.setVisible(available);
    }

    /** How opaque the whole sheet is drawn, 0 to 1. */
    float opacity() {
        return opacity;
    }

    void setOpacity(float shown) {
        opacity = Math.max(0f, Math.min(1f, shown));
        repaint();
    }

    /** The titles of the groups shown, in order. */
    List<String> groupTitles() {
        return columns.stream().map(SheetSelection.Column::title).toList();
    }

    /** Every row shown as «keys · name», group after group. */
    List<String> rows() {
        List<String> rows = new ArrayList<>();
        for (SheetSelection.Column column : columns) {
            column.rows().forEach(row -> rows.add(row.text() + ROW_SEPARATOR + row.name()));
        }
        return rows;
    }

    JPanel card() {
        return card;
    }

    JLabel showUsedShortcutsLabel() {
        return showUsedShortcutsLabel;
    }

    JCheckBox showUsedShortcuts() {
        return showUsedShortcuts;
    }

    AbstractButton allShortcutsLink() {
        return allShortcuts;
    }

    boolean gestureShown() {
        return gesture.isVisible();
    }

    String gestureText() {
        return gesturePhrase.isEmpty() ? "" : gesture.getText();
    }

    /**
     * The card centred on the veil: as wide as the area allows up to {@value #MAXIMUM_CARD_WIDTH} px, as tall as its
     * content at that width (the wrapped gesture strip knows its height once it has its width) but never taller than
     * the area.
     */
    @Override
    public void doLayout() {
        int width = Math.max(0, Math.min(MAXIMUM_CARD_WIDTH, getWidth() - 2 * VEIL_MARGIN));
        card.setSize(width, Math.max(1, card.getHeight()));
        layOutTree(card);
        int height = Math.max(0, Math.min(card.getPreferredSize().height, getHeight() - 2 * VEIL_MARGIN));
        card.setBounds((getWidth() - width) / 2, (getHeight() - height) / 2, width, height);
        layOutTree(card);
    }

    /** Lays out {@code container} and everything in it now (validate() waits for a window on screen). */
    private static void layOutTree(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container inner) {
                layOutTree(inner);
            }
        }
    }

    @Override
    public void paint(Graphics graphics) {
        Graphics2D painter = (Graphics2D) graphics.create();
        try {
            painter.setComposite(AlphaComposite.SrcOver.derive(opacity));
            super.paint(painter);
        } finally {
            painter.dispose();
        }
    }

    /** The veil, darker in both themes, and the card's soft shadow. */
    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D painter = (Graphics2D) graphics.create();
        try {
            painter.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            painter.setColor(new Color(0, 0, 0, FlatLaf.isLafDark() ? DARK_VEIL_ALPHA : LIGHT_VEIL_ALPHA));
            painter.fillRect(0, 0, getWidth(), getHeight());
            Rectangle bounds = card.getBounds();
            painter.setColor(new Color(0, 0, 0, SHADOW_ALPHA));
            for (int spread = SHADOW_SIZE; spread > 0; spread -= 2) {
                painter.fillRoundRect(bounds.x - spread, bounds.y - spread + SHADOW_SIZE / 3,
                        bounds.width + 2 * spread, bounds.height + 2 * spread, CORNER + spread, CORNER + spread);
            }
        } finally {
            painter.dispose();
        }
    }

    private static Color themeColor(String key, Color fallback) {
        Color color = UIManager.getColor(key);
        return color == null ? fallback : color;
    }

    /** The card: rounded, in the theme's panel colour with its border, with generous padding. */
    private static final class Card extends JPanel {

        Card() {
            super(new MigLayout("insets 24 30 18 30, wrap 1, fillx", "[grow, fill]"));
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D painter = (Graphics2D) graphics.create();
            try {
                painter.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                painter.setColor(themeColor("Panel.background", Color.WHITE));
                painter.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, CORNER, CORNER);
                painter.setColor(themeColor("Component.borderColor", Color.GRAY));
                painter.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, CORNER, CORNER);
            } finally {
                painter.dispose();
            }
        }
    }

    /** The keys of a shortcut as keycaps ({@link Keycaps}), in the theme's text colour read at paint time. */
    private static final class KeycapsLabel extends JComponent {

        private static final float SCALE = 1f;
        private final List<String> keys;

        KeycapsLabel(List<String> keys) {
            this.keys = List.copyOf(keys);
            setFont(UIManager.getFont("Label.font"));
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(Keycaps.width(this, getFont(), SCALE, keys, !KeyNames.MAC_NOTATION) + 1,
                    Keycaps.height(this, getFont(), SCALE));
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D painter = (Graphics2D) graphics.create();
            try {
                painter.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                painter.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                int left = getWidth() - getPreferredSize().width; // right-aligned in its cell
                Keycaps.paint(painter, getFont(), SCALE, keys, !KeyNames.MAC_NOTATION, left, 0,
                        themeColor("Label.foreground", Color.BLACK));
            } finally {
                painter.dispose();
            }
        }
    }

    /** A label whose colour is the theme's {@code colorKey}, read at each paint (themes change live). */
    private static final class ThemedLabel extends JLabel {

        private final String colorKey;

        ThemedLabel(String colorKey, String text) {
            super(text);
            this.colorKey = colorKey;
            setHorizontalAlignment(SwingConstants.LEADING);
        }

        @Override
        public Color getForeground() {
            return colorKey == null ? super.getForeground() : themeColor(colorKey, super.getForeground());
        }
    }

    /**
     * The phrase of the gesture: a long one wraps instead of widening the card. It only shows text: no keyboard, no
     * selection, the theme's colours at paint time.
     */
    private static final class GestureStrip extends JTextArea {

        GestureStrip() {
            setLineWrap(true);
            setWrapStyleWord(true);
            setEditable(false);
            setFocusable(false);
            setOpaque(false);
            setBorder(null);
            setHighlighter(null);
        }

        @Override
        public Color getForeground() {
            return themeColor("Label.foreground", Color.BLACK);
        }

        @Override
        public void updateUI() {
            super.updateUI();
            setFont(UIManager.getFont("Label.font"));
        }
    }

    /** A text button drawn as a link, without underline, in the theme's link colour read at paint time; no keyboard. */
    private static final class LinkButton extends JButton {

        LinkButton(String text) {
            super(text);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setFocusable(false);
            setOpaque(false);
            setMargin(new Insets(0, 0, 0, 0));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override
        public Color getForeground() {
            Color link = UIManager.getColor("Component.linkColor");
            return link != null ? link : themeColor("Label.foreground", Color.BLUE);
        }
    }
}
