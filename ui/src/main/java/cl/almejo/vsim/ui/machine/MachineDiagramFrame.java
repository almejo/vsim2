package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.ui.paint.ColorScheme;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.AbstractBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Insets;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * What a machine's tab shows where the circuit panes go: its diagram, and in test mode (hito 4) a 3 px border around it
 * and the test bar over it. The frame is always there, so entering and leaving the test mode never re-installs the
 * diagram in the view; the border's colour is the scheme's, read each time it is painted.
 */
final class MachineDiagramFrame extends JPanel {

    private static final long serialVersionUID = 1L;
    /** The test mode's border, in pixels. */
    static final int BORDER_WIDTH = 3;

    private final transient Supplier<ColorScheme> scheme;
    private transient Optional<JComponent> bar = Optional.empty();

    MachineDiagramFrame(MachineCanvas canvas, Supplier<ColorScheme> scheme) {
        super(new BorderLayout());
        this.scheme = Objects.requireNonNull(scheme, "scheme");
        add(canvas, BorderLayout.CENTER);
    }

    /** Shows {@code testBar} over the diagram and the border around both, until the returned runnable runs. */
    Runnable showTestMode(JComponent testBar) {
        Objects.requireNonNull(testBar, "testBar");
        bar.ifPresent(this::remove);
        bar = Optional.of(testBar);
        add(testBar, BorderLayout.NORTH);
        setBorder(new TestModeBorder());
        revalidate();
        repaint();
        return () -> {
            if (bar.isPresent() && bar.get() == testBar) {
                remove(testBar);
                bar = Optional.empty();
                setBorder(null);
                revalidate();
                repaint();
            }
        };
    }

    /** Whether the test mode's bar and border show. */
    boolean showsTestMode() {
        return bar.isPresent();
    }

    /** The border of the test mode: its colour asked of the scheme each time it is painted. */
    private final class TestModeBorder extends AbstractBorder {

        private static final long serialVersionUID = 1L;

        @Override
        public void paintBorder(Component component, Graphics graphics, int x, int y, int width, int height) {
            graphics.setColor(MachineColors.testMode(scheme.get()));
            graphics.fillRect(x, y, width, BORDER_WIDTH);
            graphics.fillRect(x, y + height - BORDER_WIDTH, width, BORDER_WIDTH);
            graphics.fillRect(x, y, BORDER_WIDTH, height);
            graphics.fillRect(x + width - BORDER_WIDTH, y, BORDER_WIDTH, height);
        }

        @Override
        public Insets getBorderInsets(Component component) {
            return new Insets(BORDER_WIDTH, BORDER_WIDTH, BORDER_WIDTH, BORDER_WIDTH);
        }

        @Override
        public Insets getBorderInsets(Component component, Insets insets) {
            insets.set(BORDER_WIDTH, BORDER_WIDTH, BORDER_WIDTH, BORDER_WIDTH);
            return insets;
        }
    }
}
