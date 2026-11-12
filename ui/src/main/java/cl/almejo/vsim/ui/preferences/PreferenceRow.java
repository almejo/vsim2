package cl.almejo.vsim.ui.preferences;

import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.theme.ReadableColors;
import cl.almejo.vsim.ui.theme.ThemeColors;

import javax.swing.AbstractButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * One option of a {@link PreferencesPage}: the components it occupies (label, control, extra lines such as notes and
 * buttons), the words the search looks at, and how it is shown, hidden and highlighted.
 */
final class PreferenceRow {

    /** A component of a row and its MigLayout constraints. */
    record Placed(JComponent component, String constraints) {
    }

    private static final String KEYWORDS_KEY_PREFIX = "preferences.keywords.";

    private final String id;
    private final List<Placed> placed;
    /** The label or the check box: the component whose text the search marks (the other one is null). */
    private final JLabel label;
    private final AbstractButton checkBox;
    /** Its text without marks, kept to restore it. */
    private final String plainText;
    private final JComponent control;
    private boolean ignoringValues;

    private PreferenceRow(String id, List<Placed> placed, JLabel label, AbstractButton checkBox, String plainText,
                          JComponent control) {
        this.id = id;
        this.placed = List.copyOf(placed);
        this.label = label;
        this.checkBox = checkBox;
        this.plainText = plainText;
        this.control = control;
    }

    /** A label (the text of {@code labelKey}) and its {@code control}, then the {@code extras} on lines below. */
    static PreferenceRow labeled(String id, String labelKey, JComponent control, String controlConstraints,
                                 Placed... extras) {
        JLabel label = new JLabel(Messages.text(labelKey));
        label.setLabelFor(control);
        List<Placed> placed = new ArrayList<>();
        placed.add(new Placed(label, ""));
        placed.add(new Placed(control, controlConstraints));
        placed.addAll(List.of(extras));
        return new PreferenceRow(id, placed, label, null, label.getText(), control);
    }

    /** A check box on its own line. */
    static PreferenceRow checkBox(String id, JCheckBox checkBox, String constraints) {
        return new PreferenceRow(id, List.of(new Placed(checkBox, constraints)), null, checkBox, checkBox.getText(),
                null);
    }

    /**
     * The search then ignores the values of the control (the entries of a combo, the text of a switch), keeping the
     * label, the other components and the keywords: for a control whose values are not words to look for (the
     * installed fonts).
     */
    PreferenceRow ignoringValues() {
        ignoringValues = true;
        return this;
    }

    /** The width its label needs (0 for a check box): the page keeps its label column that wide while filtering. */
    int labelWidth() {
        if (label == null) {
            return 0;
        }
        java.awt.Insets insets = label.getInsets();
        return label.getFontMetrics(label.getFont()).stringWidth(plainText) + insets.left + insets.right;
    }

    String id() {
        return id;
    }

    List<Placed> placed() {
        return placed;
    }

    /** Label or check box text, the texts of the values and extra components, and the keywords of the row. */
    String words() {
        List<String> words = new ArrayList<>();
        words.add(plainText);
        for (Placed each : placed) {
            if (!(each.component() == control && ignoringValues)) {
                addTextOf(each.component(), words);
            }
        }
        Messages.find(KEYWORDS_KEY_PREFIX + id).ifPresent(words::add);
        return String.join(" ", words);
    }

    private void addTextOf(JComponent component, List<String> words) {
        if (component == label || component == checkBox) {
            return;
        }
        if (component instanceof JComboBox<?> combo) {
            for (int index = 0; index < combo.getItemCount(); index++) {
                words.add(String.valueOf(combo.getItemAt(index)));
            }
        } else if (component instanceof JLabel other) {
            words.add(other.getText());
        } else if (component instanceof AbstractButton button) {
            words.add(button.getText());
        }
    }

    void setShown(boolean shown) {
        placed.forEach(each -> each.component().setVisible(shown));
    }

    boolean isShown() {
        return placed.get(0).component().isVisible();
    }

    /** Marks where {@code query} occurs in the label or check box text; the plain text for a blank query. */
    void highlight(String query) {
        String text = marked(query);
        if (label != null) {
            label.setText(text);
        } else {
            checkBox.setText(text);
        }
    }

    private String marked(String query) {
        List<PreferencesSearch.Range> ranges = PreferencesSearch.ranges(plainText, query);
        if (ranges.isEmpty()) {
            return plainText;
        }
        String style = "background:" + hex(markBackground());
        StringBuilder html = new StringBuilder("<html>");
        int position = 0;
        for (PreferencesSearch.Range range : ranges) {
            html.append(escaped(plainText.substring(position, range.start())));
            html.append("<span style=\"").append(style).append("\">")
                    .append(escaped(plainText.substring(range.start(), range.end()))).append("</span>");
            position = range.end();
        }
        return html.append(escaped(plainText.substring(position))).toString();
    }

    /**
     * A chip of the theme's warning amber over the panel background, faded until the label's text stays readable:
     * worked out from the theme each time (never cached), so every look and feel gets its own.
     */
    private static Color markBackground() {
        Color tint = ThemeColors.ui("Component.warning.focusedBorderColor", new Color(0xE2A53A));
        return ReadableColors.readableTint(tint, ReadableColors.dark() ? 0.6 : 0.5);
    }

    private static String hex(Color color) {
        return String.format("#%06X", color.getRGB() & 0xFFFFFF);
    }

    private static String escaped(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
