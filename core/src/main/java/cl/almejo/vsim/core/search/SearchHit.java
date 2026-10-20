package cl.almejo.vsim.core.search;

import cl.almejo.vsim.core.document.ElementId;

import java.util.List;
import java.util.Objects;

/**
 * One match of a find. {@code elementId} is always a top-level element of the document (what the view centres and
 * selects). {@code partIds} and {@code nestedPath} are empty for a match on that element itself; for a match inside a
 * user component they go from the first level inside the instance down to the matching part: {@code partIds} by the
 * parts' element ids inside their definitions (what tells two unlabeled parts of one type apart, as the component
 * tree's {@code c5/c2} paths do), {@code nestedPath} by what the user reads, each part's label, or its type's display
 * name when it has none (e.g. {@code ["H1", "X1"]}). {@code matchedText} is the whole text that matched (the label,
 * the type name, the free text or the frame title).
 */
public record SearchHit(ElementId elementId, List<ElementId> partIds, List<String> nestedPath, Field field,
                        String matchedText) {

    /**
     * Where the match is. An element matching on several fields gives one hit, with the first field in this order:
     * its label ({@link #LABEL}, or {@link #PIN_NAME} for an input or output pin), then a tunnel's name
     * ({@link #TUNNEL_NAME}), then its type ({@link #TYPE}).
     * Free texts and frames have one field each.
     */
    public enum Field { LABEL, TYPE, TEXT, FRAME_TITLE, PIN_NAME, TUNNEL_NAME }

    public SearchHit {
        Objects.requireNonNull(elementId, "elementId");
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(matchedText, "matchedText");
        partIds = List.copyOf(partIds);
        nestedPath = List.copyOf(nestedPath);
        if (partIds.size() != nestedPath.size()) {
            throw new IllegalArgumentException("one name per part: " + partIds + " " + nestedPath);
        }
    }

    /** True for a match inside a user component (reached through the instance {@link #elementId}). */
    public boolean nested() {
        return !nestedPath.isEmpty();
    }
}
