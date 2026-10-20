package cl.almejo.vsim.ui.tunnel;

import cl.almejo.vsim.core.tunnel.Tunnels;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import com.formdev.flatlaf.FlatLaf;
import net.miginfocom.swing.MigLayout;

import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The colour of a tunnel entrada in the inspector (spec §5): one swatch per colour of the tunnel palette, the chosen
 * one pressed and circled by a ring in the theme's focus colour, with a gap so the ring reads on any hue. Each swatch
 * is painted with the variant of the current theme (read at paint time) and says its colour's name as a tooltip, so
 * the colour is never the only cue.
 */
public final class TunnelColorSwatches extends JPanel {

    private static final int SWATCH_SIZE = 14;
    private static final int SWATCH_CORNER = 4;
    /** Room around the swatch for the selection ring: a gap, then the ring itself. */
    private static final int RING_GAP = 2;
    private static final float RING_WIDTH = 2f;
    private static final int RING_MARGIN = RING_GAP + (int) RING_WIDTH;
    private static final int ICON_SIZE = SWATCH_SIZE + 2 * RING_MARGIN;
    /** The name of each palette colour, in palette order (one per {@link Tunnels#COLOR_COUNT}). */
    private static final List<String> COLOR_NAME_KEYS = List.of("tunnel.color.red", "tunnel.color.cyan",
            "tunnel.color.amber", "tunnel.color.purple", "tunnel.color.green", "tunnel.color.blue",
            "tunnel.color.pink", "tunnel.color.brown");

    private final List<JToggleButton> swatches = new ArrayList<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private int selectedColor;

    public TunnelColorSwatches(int selectedColor) {
        super(new MigLayout("insets 0, gap 2"));
        this.selectedColor = selectedColor;
        ButtonGroup group = new ButtonGroup();
        for (int color = 0; color < Tunnels.COLOR_COUNT; color++) {
            JToggleButton swatch = newSwatch(color);
            group.add(swatch);
            swatches.add(swatch);
            add(swatch);
        }
    }

    private JToggleButton newSwatch(int color) {
        JToggleButton swatch = new JToggleButton(new SwatchIcon(color));
        String name = Messages.text(COLOR_NAME_KEYS.get(color));
        swatch.setToolTipText(name);
        swatch.getAccessibleContext().setAccessibleName(name);
        swatch.putClientProperty("JButton.buttonType", "toolBarButton");
        swatch.setSelected(color == selectedColor);
        swatch.addActionListener(event -> choose(color));
        return swatch;
    }

    private void choose(int color) {
        if (color == selectedColor) {
            return;
        }
        selectedColor = color;
        listeners.forEach(Runnable::run);
    }

    /** The chosen palette colour, 0 .. {@link Tunnels#COLOR_COUNT} - 1. */
    public int selectedColor() {
        return selectedColor;
    }

    /** The swatch of palette colour {@code color}. */
    public JToggleButton swatch(int color) {
        return swatches.get(color);
    }

    /** Calls {@code listener} each time the user picks another colour. */
    public void addChoiceListener(Runnable listener) {
        listeners.add(listener);
    }

    /** The colour of the ring around the chosen swatch: the theme's focus colour, read when painting. */
    static Color selectionRingColor() {
        Color focus = UIManager.getColor("Component.focusColor");
        if (focus != null) {
            return focus;
        }
        Color foreground = UIManager.getColor("Label.foreground");
        return foreground != null ? foreground : Color.BLACK;
    }

    /**
     * A filled rounded square in the tunnel colour, in the variant of the theme shown now; when its button is the
     * chosen one, a ring in {@link #selectionRingColor()} circles it.
     */
    private record SwatchIcon(int color) implements Icon {

        @Override
        public void paintIcon(Component owner, Graphics graphics, int x, int y) {
            Graphics2D canvas = (Graphics2D) graphics.create();
            try {
                canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color fill = ColorSchemes.forTheme(FlatLaf.isLafDark()).tunnelColor(color);
                canvas.setColor(fill);
                canvas.fillRoundRect(x + RING_MARGIN, y + RING_MARGIN, SWATCH_SIZE, SWATCH_SIZE, SWATCH_CORNER,
                        SWATCH_CORNER);
                if (owner instanceof AbstractButton button && button.isSelected()) {
                    paintRing(canvas, x, y);
                }
            } finally {
                canvas.dispose();
            }
        }

        private static void paintRing(Graphics2D canvas, int x, int y) {
            float inset = RING_WIDTH / 2;
            float size = ICON_SIZE - RING_WIDTH;
            float corner = SWATCH_CORNER + 2 * RING_MARGIN;
            canvas.setColor(selectionRingColor());
            canvas.setStroke(new BasicStroke(RING_WIDTH));
            canvas.draw(new RoundRectangle2D.Float(x + inset, y + inset, size, size, corner, corner));
        }

        @Override
        public int getIconWidth() {
            return ICON_SIZE;
        }

        @Override
        public int getIconHeight() {
            return ICON_SIZE;
        }
    }
}
