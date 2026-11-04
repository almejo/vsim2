package cl.almejo.vsim.ui.groups;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.BitGroups;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ComponentPainter;

import javax.swing.JComponent;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import java.awt.Rectangle;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.IntStream;

/**
 * The «Vista previa de la pieza» of the «Grupos» window: the splitter or joiner drawn by its own painter with the
 * groups being edited, on the scheme's board colour, so the user sees the pins and their labels before Aceptar. While
 * the groups have a problem it keeps showing the last valid ones.
 */
@SuppressWarnings("serial")
final class GroupsPreview extends JComponent implements Scrollable {

    private static final double UNIT = GridPoint.PIXELS_PER_UNIT;
    /** Room around the part, in grid units, so the ends of its pins are not cut at the edge. */
    private static final double MARGIN_UNITS = 3;
    private static final double LARGEST_SCALE = 2;
    private static final double SMALLEST_SCALE = 1;
    /**
     * The height a preview grows to before its scale shrinks; a part still taller at the smallest scale scrolls (the
     * viewport never asks for more), so the preview never makes the window tall.
     */
    static final int TALLEST = 150;

    private final ComponentType partType;
    private final ComponentPainter painter;
    private final Supplier<ColorScheme> scheme;
    private BitGroups shown;

    GroupsPreview(ComponentType partType, ComponentPainter painter, Supplier<ColorScheme> scheme, BitGroups groups) {
        this.partType = partType;
        this.painter = painter;
        this.scheme = scheme;
        this.shown = groups;
        setName("groups.preview");
    }

    /** Shows {@code groups} from now on. */
    void show(BitGroups groups) {
        shown = groups;
        revalidate();
        repaint();
    }

    /** The names of the pins drawn now, the bus first. */
    List<String> shownPins() {
        ComponentType type = type();
        return IntStream.range(0, type.pinCount()).mapToObj(index -> type.pin(index).name()).toList();
    }

    private Map<String, Object> parameters() {
        return Map.of(StandardComponentTypes.WIDTH, shown.width(), StandardComponentTypes.GROUPS, shown.canonical());
    }

    private ComponentType type() {
        return partType.specializedFor(parameters());
    }

    private double contentWidth() {
        return (type().size().width() + 2 * MARGIN_UNITS) * UNIT;
    }

    private double contentHeight() {
        return (type().size().height() + 2) * UNIT;
    }

    private double scale() {
        return Math.clamp(TALLEST / contentHeight(), SMALLEST_SCALE, LARGEST_SCALE);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension((int) Math.ceil(contentWidth() * scale()), (int) Math.ceil(contentHeight() * scale()));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D local = (Graphics2D) graphics.create();
        try {
            ColorScheme colors = scheme.get();
            local.setColor(colors.background());
            local.fillRect(0, 0, getWidth(), getHeight());
            local.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            local.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            double scale = scale();
            local.translate((getWidth() - contentWidth() * scale) / 2, (getHeight() - contentHeight() * scale) / 2);
            local.scale(scale, scale);
            local.translate(MARGIN_UNITS * UNIT, UNIT);
            ComponentType type = type();
            painter.paint(local, type, type.resolveParameters(parameters()), VisibleState.NONE, colors);
        } finally {
            local.dispose();
        }
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        Dimension size = getPreferredSize();
        return new Dimension(size.width, Math.min(size.height, TALLEST));
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
        return 16;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
        return 64;
    }

    /** A viewport wider than the part gets the part centred in it instead of at its left. */
    @Override
    public boolean getScrollableTracksViewportWidth() {
        return getParent() instanceof JViewport viewport && viewport.getWidth() > getPreferredSize().width;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return getParent() instanceof JViewport viewport && viewport.getHeight() > getPreferredSize().height;
    }
}
