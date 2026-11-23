package cl.almejo.vsim.ui.inline;

import javax.swing.JLabel;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import java.awt.Color;

/**
 * The quiet badge an inline edit shows a hint in (how its text is written), in the tooltip's colours: read from the
 * theme every time, since the theme may change while it shows. The {@link ProblemBadge} takes its place when the text
 * has a problem.
 */
final class HintBadge extends JLabel {

    private static final long serialVersionUID = 1L;

    HintBadge() {
        setOpaque(true);
        setBorder(new EmptyBorder(1, 4, 1, 4));
    }

    @Override
    public Color getForeground() {
        Color colour = UIManager.getColor("ToolTip.foreground");
        return colour != null ? colour : super.getForeground();
    }

    @Override
    public Color getBackground() {
        Color colour = UIManager.getColor("ToolTip.background");
        return colour != null ? colour : super.getBackground();
    }
}
