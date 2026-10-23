package cl.almejo.vsim.ui.ai;

import javax.swing.Icon;
import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * The AI state's coloured indicator, never alone (the word goes next to it): hollow when off, filled otherwise, its
 * colour read from {@link AiColors} when painted. The panel's header shows it as a component, the status bar's
 * indicator as the icon of its label ({@link #icon}).
 */
final class AiStateDot extends JComponent {

    /** The header's dot, in pixels. */
    static final int HEADER_SIZE = 10;

    private final Supplier<AiServerState> state;

    AiStateDot(Supplier<AiServerState> state) {
        this.state = Objects.requireNonNull(state, "state");
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(HEADER_SIZE + 2, HEADER_SIZE + 2);
    }

    Color color() {
        return AiColors.of(state.get());
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        paint(graphics, state.get(), (getWidth() - HEADER_SIZE) / 2, (getHeight() - HEADER_SIZE) / 2, HEADER_SIZE);
    }

    /** The dot as an icon of {@code size} pixels, following {@code state} when painted. */
    static Icon icon(Supplier<AiServerState> state, int size) {
        Objects.requireNonNull(state, "state");
        return new Icon() {
            @Override
            public void paintIcon(Component component, Graphics graphics, int x, int y) {
                paint(graphics, state.get(), x, y, size);
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }

    private static void paint(Graphics graphics, AiServerState state, int x, int y, int size) {
        Graphics2D painter = (Graphics2D) graphics.create();
        try {
            painter.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            painter.setColor(AiColors.of(state));
            if (state instanceof AiServerState.Off) {
                painter.drawOval(x, y, size - 1, size - 1); // hollow when off
            } else {
                painter.fillOval(x, y, size, size);
            }
        } finally {
            painter.dispose();
        }
    }
}
