package cl.almejo.vsim.automation.read;

import cl.almejo.vsim.core.geometry.Bounds;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * What {@code readCircuit} returns. {@link Detail#SUMMARY} (the default) lists parts, frames and notes without pins
 * and counts the wires and nets; {@link Detail#FULL} adds every part's pins, the wires and the nets of the page's
 * elements. {@code kinds} (empty: those of the detail) and {@code elementIds} (empty: all) narrow the elements;
 * {@code region} keeps those whose bounds touch it. A page holds at most {@code pageSize} elements, never more than
 * {@link #MAXIMUM_PAGE_SIZE}.
 */
public record ReadFilter(Detail detail, Set<ElementKind> kinds, Set<String> elementIds, Optional<Bounds> region,
                         int pageSize) {

    public static final int MAXIMUM_PAGE_SIZE = 200;

    public static final ReadFilter SUMMARY = new ReadFilter(Detail.SUMMARY, Set.of(), Set.of(), Optional.empty(),
            MAXIMUM_PAGE_SIZE);

    public static final ReadFilter FULL = new ReadFilter(Detail.FULL, Set.of(), Set.of(), Optional.empty(),
            MAXIMUM_PAGE_SIZE);

    public enum Detail { SUMMARY, FULL }

    public enum ElementKind { PART, WIRE, FRAME, NOTE, STICKY_NOTE }

    public ReadFilter {
        Objects.requireNonNull(detail, "detail");
        kinds = Set.copyOf(kinds);
        elementIds = Set.copyOf(elementIds);
        Objects.requireNonNull(region, "region");
        if (pageSize < 1) {
            throw new IllegalArgumentException("a page holds at least one element: " + pageSize);
        }
        pageSize = Math.min(pageSize, MAXIMUM_PAGE_SIZE);
    }

    /** The kinds this filter lists: the asked ones, or those of the detail. */
    Set<ElementKind> listedKinds() {
        if (!kinds.isEmpty()) {
            return kinds;
        }
        return detail == Detail.FULL ? Set.of(ElementKind.values())
                : Set.of(ElementKind.PART, ElementKind.FRAME, ElementKind.NOTE, ElementKind.STICKY_NOTE);
    }
}
