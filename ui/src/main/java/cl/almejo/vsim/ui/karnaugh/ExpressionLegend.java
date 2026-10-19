package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.ui.i18n.Messages;
import net.miginfocom.swing.MigLayout;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Font;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The side panel of «Circuito desde expresiones» (spec §3): short instructions and the notation, one row per
 * operation with its symbols and an example. Clicking the button of a row inserts its operator into the line the
 * user was typing in ({@code insert}). Open by default; «Notación» collapses it.
 */
final class ExpressionLegend extends WidthTrackingPanel {

    /** A row of the notation; {@code insertion} is what its button types, empty for a row that only explains. */
    enum Row {
        NOT("'", "not", true),
        AND("·", "and", true),
        OR("+", "or", true),
        XOR("^", "xor", true),
        PARENTHESES("()", "parentheses", true),
        ZERO("0", "zero", true),
        ONE("1", "one", true),
        PRECEDENCE("", "precedence", false),
        NAMES("", "names", false);

        final String insertion;
        final String nameKey;
        final String detailKey;
        /** Its example, on a line of its own that never wraps (an expression broken in two reads wrong); or null. */
        final String exampleKey;

        Row(String insertion, String key, boolean hasExample) {
            this.insertion = insertion;
            this.nameKey = "expressions.legend." + key;
            this.detailKey = nameKey + ".detail";
            this.exampleKey = hasExample ? nameKey + ".example" : null;
        }
    }

    private static final int BUTTON_WIDTH = 40;
    private static final int COLUMN_GAP = 8;
    private static final int EXAMPLE_GAP = 16;

    private final JButton toggle = new JButton();
    private final JPanel content = new JPanel(new MigLayout("insets 0 0 0 0, wrap 4, fillx",
            "[]" + COLUMN_GAP + "[]" + COLUMN_GAP + "[grow]" + EXAMPLE_GAP + "[]", ""));
    private final Map<Row, JLabel> names = new EnumMap<>(Row.class);
    private final Map<Row, Paragraph> details = new EnumMap<>(Row.class);
    private final Map<Row, JButton> buttons = new EnumMap<>(Row.class);
    private final Map<Row, JLabel> examples = new EnumMap<>(Row.class);
    private boolean expanded = true;
    private Runnable onToggle = () -> { };

    ExpressionLegend(Consumer<String> insert) {
        super(new BorderLayout(0, 8));
        Paragraph instructions = new Paragraph(Messages.text("expressions.legend.instructions"));
        content.add(instructions, "span 4, growx, wmin 100, gapbottom 8");
        for (Row row : Row.values()) {
            JLabel name = new JLabel(Messages.text(row.nameKey));
            name.setFont(name.getFont().deriveFont(Font.BOLD));
            names.put(row, name);
            content.add(name, "top");
            if (row.insertion.isEmpty()) {
                content.add(new JLabel(""), "top");
            } else {
                JButton button = new JButton(row.insertion);
                button.setToolTipText(Messages.text("expressions.legend.insert"));
                button.setFocusable(false); // the caret stays in the line being typed
                button.putClientProperty("JComponent.minimumWidth", BUTTON_WIDTH); // FlatLaf's default is wider
                button.addActionListener(event -> insert.accept(row.insertion));
                buttons.put(row, button);
                content.add(button, "top, sg insert");
            }
            // The detail wraps only when the dialog is too narrow for the row; the example never does.
            Paragraph detail = new Paragraph(Messages.text(row.detailKey));
            details.put(row, detail);
            if (row.exampleKey == null) {
                content.add(detail, "span 2, growx, wmin 80");
            } else {
                content.add(detail, "growx, wmin 80");
                JLabel example = new JLabel(Messages.text(row.exampleKey));
                examples.put(row, example);
                content.add(example, "top");
            }
        }
        toggle.setFocusable(false);
        toggle.addActionListener(event -> setExpanded(!expanded));
        JPanel header = new JPanel(new BorderLayout());
        header.add(toggle, BorderLayout.WEST);
        add(header, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
        setExpanded(true);
    }

    /**
     * The width that puts every notation row on one line with the current fonts: the widest name, the button, the
     * widest detail and the widest example (a row without example spreads over both). «Nombres» is prose and wraps
     * like the instructions, so it does not count. Measured each time (fonts follow the theme).
     */
    int rowsWidth() {
        int nameWidth = 0;
        int detailWidth = 0;
        int exampleWidth = 0;
        int spreadWidth = 0;
        for (Row row : Row.values()) {
            nameWidth = Math.max(nameWidth, names.get(row).getPreferredSize().width);
            if (row == Row.NAMES) {
                continue;
            }
            Paragraph detail = details.get(row);
            int textWidth = detail.getFontMetrics(detail.getFont()).stringWidth(detail.getText()) + 2;
            JLabel example = examples.get(row);
            if (example == null) {
                spreadWidth = Math.max(spreadWidth, textWidth);
            } else {
                detailWidth = Math.max(detailWidth, textWidth);
                exampleWidth = Math.max(exampleWidth, example.getPreferredSize().width);
            }
        }
        int buttonWidth = BUTTON_WIDTH;
        for (JButton button : buttons.values()) {
            buttonWidth = Math.max(buttonWidth, button.getPreferredSize().width);
        }
        int textColumns = Math.max(detailWidth + EXAMPLE_GAP + exampleWidth, spreadWidth);
        return nameWidth + COLUMN_GAP + buttonWidth + COLUMN_GAP + textColumns;
    }

    boolean isExpanded() {
        return expanded;
    }

    void setExpanded(boolean expand) {
        expanded = expand;
        content.setVisible(expand);
        toggle.setText(Messages.text(expand ? "expressions.legend.hide" : "expressions.legend.show"));
        revalidate();
        repaint();
        onToggle.run();
    }

    /** Told after the legend opens or collapses (the owner gives it its width back). */
    void setOnToggle(Runnable action) {
        onToggle = action;
    }

    AbstractButton toggleButton() {
        return toggle;
    }

    AbstractButton rowButton(Row row) {
        return buttons.get(row);
    }

    Paragraph detail(Row row) {
        return details.get(row);
    }

    /** The example of a row (after its detail), or null for a row without one. */
    JLabel example(Row row) {
        return examples.get(row);
    }
}
