package cl.almejo.vsim.ui.tree;

import cl.almejo.vsim.core.machine.StateColor;
import cl.almejo.vsim.ui.machine.MachineColors;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import com.formdev.flatlaf.util.UIScale;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * The dot a state's row of the circuit tree shows: a circle in the state's fill (the diagram's, so a green state is
 * a green dot), the initial state's with a second ring as on the diagram. The colours are read from the scheme when
 * it is painted, never kept.
 */
final class StateDot implements Icon {

    private static final int SIZE = 16;
    private static final int MARGIN = 3;
    private static final float RING_WIDTH = 1.2f;

    private final java.util.Optional<StateColor> color;
    private final boolean initial;
    private final Appearance appearance;

    StateDot(java.util.Optional<StateColor> color, boolean initial, Appearance appearance) {
        this.color = color;
        this.initial = initial;
        this.appearance = appearance;
    }

    @Override
    public void paintIcon(Component owner, Graphics graphics, int x, int y) {
        ColorScheme scheme = appearance.scheme();
        Graphics2D painter = (Graphics2D) graphics.create();
        try {
            painter.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int margin = UIScale.scale(MARGIN);
            int diameter = getIconWidth() - 2 * margin;
            painter.setColor(color.map(scheme::stateFill).orElseGet(() -> MachineColors.stateFill(scheme)));
            painter.fillOval(x + margin, y + margin, diameter, diameter);
            painter.setColor(MachineColors.stateOutline(scheme));
            painter.setStroke(new BasicStroke(UIScale.scale(RING_WIDTH)));
            painter.drawOval(x + margin, y + margin, diameter, diameter);
            if (initial) {
                int inner = UIScale.scale(2);
                painter.drawOval(x + margin + inner, y + margin + inner, diameter - 2 * inner, diameter - 2 * inner);
            }
        } finally {
            painter.dispose();
        }
    }

    @Override
    public int getIconWidth() {
        return UIScale.scale(SIZE);
    }

    @Override
    public int getIconHeight() {
        return UIScale.scale(SIZE);
    }
}
