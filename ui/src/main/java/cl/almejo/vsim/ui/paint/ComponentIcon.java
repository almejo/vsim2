package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;

import javax.swing.Icon;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Map;

import static cl.almejo.vsim.ui.paint.PainterSupport.UNIT;

/** Palette icon drawn by the same painter as the canvas, so both always match. */
public final class ComponentIcon implements Icon {

    private static final double PADDING = 3;

    private final ComponentType type;
    private final ComponentPainter painter;
    private final ColorScheme scheme;
    private final int size;

    public ComponentIcon(ComponentType type, ComponentPainter painter, ColorScheme scheme, int size) {
        this.type = type;
        this.painter = painter;
        this.scheme = scheme;
        this.size = size;
    }

    public ColorScheme scheme() {
        return scheme;
    }

    @Override
    public void paintIcon(java.awt.Component owner, Graphics graphics, int x, int y) {
        Graphics2D local = (Graphics2D) graphics.create();
        try {
            local.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            local.clipRect(x, y, size, size);
            double contentWidth = type.size().width() * UNIT + 2 * PADDING;
            double contentHeight = type.size().height() * UNIT + 2 * PADDING;
            double scale = Math.min(size / contentWidth, size / contentHeight);
            local.translate(x + (size - contentWidth * scale) / 2, y + (size - contentHeight * scale) / 2);
            local.scale(scale, scale);
            local.translate(PADDING, PADDING);
            painter.paint(local, type, type.resolveParameters(Map.of()), VisibleState.NONE, scheme);
        } finally {
            local.dispose();
        }
    }

    @Override
    public int getIconWidth() {
        return size;
    }

    @Override
    public int getIconHeight() {
        return size;
    }
}
