package cl.almejo.vsim.ui.print;

import cl.almejo.vsim.core.document.ElementId;

import java.awt.geom.Rectangle2D;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * What one {@link PrintScope} puts on paper, in grid units: the area the pages cover ({@code gridArea}), the elements
 * that are painted, in document order, and, for a frame, the rectangle the drawing is cut to (wires that cross the
 * frame's border are painted and cut at it). The rectangles are copied in and out, so an area never changes.
 */
public record PrintArea(Rectangle2D gridArea, Set<ElementId> elements, Optional<Rectangle2D> clip) {

    public PrintArea {
        gridArea = copyOf(Objects.requireNonNull(gridArea, "gridArea"));
        elements = Collections.unmodifiableSet(new LinkedHashSet<>(elements));
        clip = clip.map(PrintArea::copyOf);
    }

    @Override
    public Rectangle2D gridArea() {
        return copyOf(gridArea);
    }

    @Override
    public Optional<Rectangle2D> clip() {
        return clip.map(PrintArea::copyOf);
    }

    private static Rectangle2D copyOf(Rectangle2D rectangle) {
        return new Rectangle2D.Double(rectangle.getX(), rectangle.getY(), rectangle.getWidth(),
                rectangle.getHeight());
    }
}
