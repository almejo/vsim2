package cl.almejo.vsim.core.component.appearance;

import cl.almejo.vsim.core.geometry.GridSize;

import java.util.List;
import java.util.Objects;

/** A monochrome symbol whose mixed path/text list preserves front-to-back ordering. */
public record SymbolDrawing(GridSize size, List<SymbolElement> elements, boolean showPinNames) {
    public static final int MAXIMUM_PATHS = 64;
    public static final int MAXIMUM_TEXTS = 32;

    public SymbolDrawing {
        Objects.requireNonNull(size, "size");
        elements = List.copyOf(elements);
        long pathCount = elements.stream().filter(SymbolPath.class::isInstance).count();
        if (pathCount > MAXIMUM_PATHS || elements.size() - pathCount > MAXIMUM_TEXTS) {
            throw new IllegalArgumentException("too many paths or texts in symbol");
        }
        for (SymbolElement element : elements) {
            switch (element) {
                case SymbolPath path -> path.nodes().forEach(node -> {
                    requireInside(node.point(), size);
                    node.curve().ifPresent(curve -> curve.controls().forEach(control -> requireInside(control, size)));
                });
                case SymbolText text -> requireInside(text.anchor(), size);
            }
        }
    }

    /** An intentionally invisible symbol (its pins can still be displayed). */
    public static SymbolDrawing empty(GridSize size) {
        return new SymbolDrawing(size, List.of(), true);
    }

    public List<SymbolPath> paths() {
        return elements.stream().filter(SymbolPath.class::isInstance).map(SymbolPath.class::cast).toList();
    }

    public List<SymbolText> texts() {
        return elements.stream().filter(SymbolText.class::isInstance).map(SymbolText.class::cast).toList();
    }

    private static void requireInside(SymbolPoint point, GridSize size) {
        if (point.x() < 0 || point.y() < 0 || (long) point.x() > (long) size.width() * SymbolPoint.EIGHTHS
                || (long) point.y() > (long) size.height() * SymbolPoint.EIGHTHS) {
            throw new IllegalArgumentException("symbol coordinate outside its box: " + point);
        }
    }
}
