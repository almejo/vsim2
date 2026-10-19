package cl.almejo.vsim.ui.search;

import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.Icons;
import net.miginfocom.swing.MigLayout;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
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

    /** «3 de 7», «Sin resultados» or nothing; red with the field when {@code error}. */
    void showState(String counterText, String messageText, boolean error) {
        counter.setText(counterText);
        counter.error = error;
        counter.repaint();
        message.setText(messageText);
        message.setToolTipText(messageText.isEmpty() ? null : messageText);
        message.error = error;
        message.repaint();
        field.showError(error);
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

    /** The theme's error red, read every time (the theme may change while the bar shows). */
    private static Color errorColor() {
        for (String key : List.of("Actions.Red", "Component.error.focusedBorderColor")) {
            Color color = UIManager.getColor(key);
            if (color != null) {
                return color;
            }
        }
        return new Color(0xDB5860);
    }

    /** As wide as its widest text, whatever it shows now, so the arrows after it never move. */
    private static final class CounterLabel extends JLabel {

        private boolean error;

        @Override
        public Color getForeground() {
            return error ? errorColor() : super.getForeground();
        }

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

    private static final class MessageLabel extends JLabel {

        private boolean error;

        @Override
        public Color getForeground() {
            return error ? errorColor() : super.getForeground();
        }
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
