package cl.almejo.vsim.ui.search;

import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.inline.ProblemBadge;
import net.miginfocom.swing.MigLayout;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.border.AbstractBorder;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Insets;
import java.util.List;

/**
 * The slim bar «Buscar en el circuito» shows between the toolbar and the canvas, like a browser's find bar: the field,
 * the counter «3 de 7», ↑ / ↓, the toggles «Aa», «.*» and «Viaje suave», a message (where a nested match is, or why a
 * regular expression is not valid) and ✕. Only the field takes the focus, so the keys always reach it. The counter
 * has a fixed width and the message takes the free space, so nothing moves while the user types. What the controls do
 * is wired by {@link FindFeatures}.
 */
public final class FindBar extends JPanel {

    private static final int FIELD_WIDTH = 260;
    /** In a narrow window the field gives way first, down to this width, so ✕ stays on screen. */
    private static final int MINIMUM_FIELD_WIDTH = 100;

    private final SearchField field = new SearchField(Messages.text("find.placeholder"));
    private final CounterLabel counter = new CounterLabel();
    private final MessageLabel message = new MessageLabel();
    private final JButton previousButton = iconButton(new JButton(), FindIcons.PREVIOUS, "find.previous");
    private final JButton nextButton = iconButton(new JButton(), FindIcons.NEXT, "find.next");
    private final JToggleButton matchCaseToggle = iconButton(new JToggleButton(), FindIcons.MATCH_CASE,
            "find.matchCase");
    private final JToggleButton regexToggle = iconButton(new JToggleButton(), FindIcons.REGEX, "find.regex");
    private final JToggleButton animateToggle = iconButton(new JToggleButton(), FindIcons.ANIMATE, "find.animate");
    private final JButton closeButton = iconButton(new JButton(), FindIcons.CLOSE, "find.close");

    FindBar() {
        super(new MigLayout("insets 3 6 3 6, gap 2, novisualpadding, hidemode 3",
                "[]6[]4[][]8[][][]8[grow, fill]8[]", "[center]"));
        setBorder(new BottomLine());
        counter.setBorder(new EmptyBorder(1, 4, 1, 4)); // room for the badge around the text
        message.setBorder(new EmptyBorder(1, 4, 1, 4));
        add(field, "w " + MINIMUM_FIELD_WIDTH + ":" + FIELD_WIDTH + ":" + FIELD_WIDTH);
        add(counter);
        add(previousButton);
        add(nextButton);
        add(matchCaseToggle);
        add(regexToggle);
        add(animateToggle);
        add(message, "wmin 0");
        add(closeButton);
    }

    public SearchField field() {
        return field;
    }

    JButton previousButton() {
        return previousButton;
    }

    JButton nextButton() {
        return nextButton;
    }

    JToggleButton matchCaseToggle() {
        return matchCaseToggle;
    }

    JToggleButton regexToggle() {
        return regexToggle;
    }

    JToggleButton animateToggle() {
        return animateToggle;
    }

    JButton closeButton() {
        return closeButton;
    }

    /**
     * «3 de 7», «Sin resultados» or nothing, and the message. With {@code error} the field gets the error outline and
     * tint and the texts show as the {@link ProblemBadge}.
     */
    void showState(String counterText, String messageText, boolean error) {
        counter.show(counterText, error);
        message.show(messageText, error);
        message.setToolTipText(messageText.isEmpty() ? null : messageText);
        field.showError(error);
    }

    JLabel counterLabel() {
        return counter;
    }

    JLabel messageLabel() {
        return message;
    }

    String counterText() {
        return counter.getText();
    }

    String messageText() {
        return message.getText();
    }

    boolean showsError() {
        return field.showsError();
    }

    private static <T extends AbstractButton> T iconButton(T button, String icon, String toolTipKey) {
        button.setIcon(Icons.get(icon, Icons.MENU_SIZE));
        button.setToolTipText(Messages.text(toolTipKey));
        button.getAccessibleContext().setAccessibleName(Messages.text(toolTipKey));
        button.setFocusable(false); // the field keeps the focus, and its keys
        button.putClientProperty("JButton.buttonType", "toolBarButton");
        button.setMargin(new Insets(2, 2, 2, 2));
        return button;
    }

    /**
     * A label that shows its text as the {@link ProblemBadge} (white on a solid red, readable in light and dark themes)
     * while the bar shows an error, and as plain text otherwise. The badge is only as wide as the text.
     */
    private static class StateLabel extends JLabel {

        private static final int ARC = 6;

        private boolean error;

        void show(String text, boolean inError) {
            setText(text);
            error = inError;
            repaint();
        }

        boolean showsBadge() {
            return error && !getText().isEmpty();
        }

        @Override
        public Color getForeground() {
            return error ? ProblemBadge.foreground() : super.getForeground();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            if (showsBadge()) {
                Insets insets = getInsets();
                int width = Math.min(getWidth(),
                        getFontMetrics(getFont()).stringWidth(getText()) + insets.left + insets.right);
                Graphics2D badge = (Graphics2D) graphics.create();
                try {
                    badge.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    badge.setColor(ProblemBadge.background());
                    badge.fillRoundRect(0, 0, width, getHeight(), ARC, ARC);
                } finally {
                    badge.dispose();
                }
            }
            super.paintComponent(graphics);
        }
    }

    /** As wide as its widest text, whatever it shows now, so the arrows after it never move. */
    private static final class CounterLabel extends StateLabel {

        @Override
        public Dimension getPreferredSize() {
            Dimension size = super.getPreferredSize();
            FontMetrics metrics = getFontMetrics(getFont());
            int widest = 0;
            for (String sample : List.of(Messages.text("find.noResults"), Messages.format("find.counter", 9999, 9999))) {
                widest = Math.max(widest, metrics.stringWidth(sample));
            }
            Insets insets = getInsets();
            return new Dimension(widest + insets.left + insets.right + 2, size.height);
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }
    }

    private static final class MessageLabel extends StateLabel {
    }

    /** A one-pixel line under the bar in the theme's separator colour, read at paint time. */
    private static final class BottomLine extends AbstractBorder {

        @Override
        public void paintBorder(Component component, Graphics graphics, int x, int y, int width, int height) {
            Color color = UIManager.getColor("Separator.foreground");
            graphics.setColor(color != null ? color : Color.GRAY);
            graphics.fillRect(x, y + height - 1, width, 1);
        }

        @Override
        public Insets getBorderInsets(Component component, Insets insets) {
            insets.set(0, 0, 1, 0);
            return insets;
        }
    }
}
