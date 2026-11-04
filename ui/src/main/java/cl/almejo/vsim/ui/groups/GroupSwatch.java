package cl.almejo.vsim.ui.groups;

import cl.almejo.vsim.ui.paint.ColorScheme;

import javax.swing.Icon;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Supplier;

/**
 * The small square of a pin's colour next to its line in the Propiedades summary and its row in the «Grupos» window,
 * so the pin is found again in the bit strip. The colour is read from the scheme at every paint.
 */
final class GroupSwatch implements Icon {

    private static final int SIZE = 12;
    private static final double CORNER = 3;

    private final int pin;
    private final Supplier<ColorScheme> scheme;

    GroupSwatch(int pin, Supplier<ColorScheme> scheme) {
        this.pin = pin;
        this.scheme = scheme;
    }

    @Override
    public void paintIcon(Component owner, Graphics graphics, int x, int y) {
        Graphics2D local = (Graphics2D) graphics.create();
        try {
            local.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            RoundRectangle2D square = new RoundRectangle2D.Double(x + 0.5, y + 0.5, SIZE - 1, SIZE - 1, CORNER,
                    CORNER);
            local.setColor(scheme.get().bitGroupColor(pin));
            local.fill(square);
            local.setColor(scheme.get().bitGroupUnusedText());
            local.draw(square);
        } finally {
            local.dispose();
        }
    }

    @Override
    public int getIconWidth() {
        return SIZE;
    }

    @Override
    public int getIconHeight() {
        return SIZE;
    }
}
