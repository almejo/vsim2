package cl.almejo.vsim.ui.shortcuts.sheet;

import cl.almejo.vsim.ui.i18n.Messages;
import net.miginfocom.swing.MigLayout;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.font.TextAttribute;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * What the sheet of shortcuts shows (the mockup of 2026-11-09): one column per group with its keys and names, the
 * strip with the phrase of the gesture under way (hidden without one), and at the foot the checkbox «Mostrar los
 * atajos que se usan» and the link «Ver y cambiar todos los atajos…». It never takes the keyboard (the canvas keeps it,
 * so the phrase of a gesture stays and «?» or Esc reach the sheet's key hook), and a click on it goes nowhere else.
 * Colours come from the theme at paint time; the whole sheet is drawn at {@link #opacity()} while it fades in.
 */
final class ShortcutSheetPanel extends JPanel {

    private static final int CORNER = 14;
    private static final String ROW_SEPARATOR = " · ";
    private static final float HEADING_SCALE = 0.85f;

    private final JPanel columnsPanel = new JPanel(new MigLayout("insets 0, fillx, gapx 28", "", "[top]"));
    private final JTextArea gesture = new GestureStrip();
    private final JCheckBox showUsedShortcuts = new JCheckBox(Messages.text("action.showUsedShortcuts"));
    private final JButton allShortcuts = new LinkButton(Messages.text("shortcutSheet.allShortcuts"));
    private List<SheetSelection.Column> columns = List.of();
    private String gesturePhrase = "";
    private float opacity = 1f;

    /**
     * {@code showUsedShortcutsChosen} hears the checkbox (the preference behind Ver ▸ «Mostrar los atajos que se
     * usan»), {@code allShortcutsChosen} the link.
     */
    ShortcutSheetPanel(Consumer<Boolean> showUsedShortcutsChosen, Runnable allShortcutsChosen) {
        super(new MigLayout("insets 14 18 12 18, wrap 1, fillx", "[grow, fill]", "[][][]"));
        setOpaque(false);
        setFocusable(false);
        getAccessibleContext().setAccessibleName(Messages.text("shortcutSheet.title"));
        addMouseListener(new MouseAdapter() { }); // a click on the sheet is the sheet's, not the circuit's under it
        columnsPanel.setOpaque(false);
        add(columnsPanel, "growx");
        gesture.setVisible(false);
        add(gesture, "hidemode 3, growx, wmin 0, gaptop 8");
        showUsedShortcuts.setOpaque(false);
        showUsedShortcuts.setFocusable(false);
        showUsedShortcuts.addActionListener(event -> showUsedShortcutsChosen.accept(showUsedShortcuts.isSelected()));
        allShortcuts.addActionListener(event -> allShortcutsChosen.run());
        JPanel foot = new JPanel(new MigLayout("insets 6 0 0 0, fillx", "[][grow, center][]"));
        foot.setOpaque(false);
        foot.add(showUsedShortcuts);
        foot.add(new ThemedLabel("Label.disabledForeground", Messages.text("shortcutSheet.closeHint")));
        foot.add(allShortcuts, "hidemode 3");
        add(foot, "growx, wmin 0, gaptop 8");
    }

    /** Shows {@code shown} as the sheet's columns. */
    void setColumns(List<SheetSelection.Column> shown) {
        columns = List.copyOf(shown);
        columnsPanel.removeAll();
        for (SheetSelection.Column column : columns) {
            columnsPanel.add(columnPanel(column), "growx, wmin 0");
        }
        revalidate();
        repaint();
    }

    private static JPanel columnPanel(SheetSelection.Column column) {
        JPanel panel = new JPanel(new MigLayout("insets 0, wrap 2, gapy 3", "[right][left]"));
        panel.setOpaque(false);
        JLabel heading = new ThemedLabel("Label.disabledForeground",
                column.title().toUpperCase(Locale.ROOT));
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, heading.getFont().getSize2D() * HEADING_SCALE));
        panel.add(heading, "span 2, gapbottom 4, align left");
        for (SheetSelection.Row row : column.rows()) {
            JLabel keys = new ThemedLabel("Label.foreground", row.keys());
            keys.setFont(new Font(Font.MONOSPACED, Font.BOLD, keys.getFont().getSize()));
            panel.add(keys);
            panel.add(new ThemedLabel("Label.foreground", row.name()));
        }
        return panel;
    }

    /** Shows {@code phrase} in the strip under the columns; an empty phrase hides the strip. */
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

    /** The titles of the columns shown, in order. */
    List<String> groupTitles() {
        return columns.stream().map(SheetSelection.Column::title).toList();
    }

    /** Every row shown as «keys · name», column after column. */
    List<String> rows() {
        List<String> rows = new ArrayList<>();
        for (SheetSelection.Column column : columns) {
            column.rows().forEach(row -> rows.add(row.keys() + ROW_SEPARATOR + row.name()));
        }
        return rows;
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

    /** Twice: the wrapped strip of the gesture knows its height only once the first pass has given it its width. */
    @Override
    public void doLayout() {
        super.doLayout();
        super.doLayout();
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

    /** A rounded card in the theme's panel colour with its border, under a soft line above the strip and the foot. */
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

    private static Color themeColor(String key, Color fallback) {
        Color color = UIManager.getColor(key);
        return color == null ? fallback : color;
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
     * The phrase of the gesture: a long one wraps instead of widening the sheet. It only shows text: no keyboard, no
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

    /** A text button drawn as a link, in the theme's link colour read at paint time; never takes the keyboard. */
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
            setFont(getFont().deriveFont(Map.of(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON)));
        }

        @Override
        public Color getForeground() {
            Color link = UIManager.getColor("Component.linkColor");
            return link != null ? link : themeColor("Label.foreground", Color.BLUE);
        }
    }
}
