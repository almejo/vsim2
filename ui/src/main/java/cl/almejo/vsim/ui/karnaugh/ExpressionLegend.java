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
final class ExpressionLegend extends JPanel {

    /** A row of the notation; {@code insertion} is what its button types, empty for a row that only explains. */
    enum Row {
        NOT("'", "expressions.legend.not", "expressions.legend.not.detail"),
        AND("·", "expressions.legend.and", "expressions.legend.and.detail"),
        OR("+", "expressions.legend.or", "expressions.legend.or.detail"),
        XOR("^", "expressions.legend.xor", "expressions.legend.xor.detail"),
        PARENTHESES("()", "expressions.legend.parentheses", "expressions.legend.parentheses.detail"),
        ZERO("0", "expressions.legend.zero", "expressions.legend.zero.detail"),
        ONE("1", "expressions.legend.one", "expressions.legend.one.detail"),
        PRECEDENCE("", "expressions.legend.precedence", "expressions.legend.precedence.detail"),
        NAMES("", "expressions.legend.names", "expressions.legend.names.detail");

        final String insertion;
        final String nameKey;
        final String detailKey;

        Row(String insertion, String nameKey, String detailKey) {
            this.insertion = insertion;
            this.nameKey = nameKey;
            this.detailKey = detailKey;
        }
    }

    private static final int BUTTON_WIDTH = 40;

    private final JButton toggle = new JButton();
    private final JPanel content = new JPanel(new MigLayout("insets 0 0 0 0, wrap 3, fillx",
            "[]8[]8[grow]", ""));
    private final Map<Row, JButton> buttons = new EnumMap<>(Row.class);
    private boolean expanded = true;
    private Runnable onToggle = () -> { };

    ExpressionLegend(Consumer<String> insert) {
        super(new BorderLayout(0, 8));
        Paragraph instructions = new Paragraph(Messages.text("expressions.legend.instructions"));
        content.add(instructions, "span 3, growx, wmin 100, gapbottom 8");
        for (Row row : Row.values()) {
            JLabel name = new JLabel(Messages.text(row.nameKey));
            name.setFont(name.getFont().deriveFont(Font.BOLD));
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
            content.add(new Paragraph(Messages.text(row.detailKey)),
                    "growx, wmin 80");
        }
        toggle.setFocusable(false);
        toggle.addActionListener(event -> setExpanded(!expanded));
        JPanel header = new JPanel(new BorderLayout());
        header.add(toggle, BorderLayout.WEST);
        add(header, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
        setExpanded(true);
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
}
