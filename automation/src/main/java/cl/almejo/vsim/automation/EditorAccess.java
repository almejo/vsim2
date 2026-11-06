package cl.almejo.vsim.automation;

import java.util.List;

/**
 * What the AI's work sees of the editor while it runs on the editor thread ({@link AutomationHost#onEditorThread}).
 * Only valid during that call: never keep it. Later tasks add what they need to read and change.
 */
public interface EditorAccess {

    /** The open circuits, one entry per circuit however many windows show it, in the order they were opened. */
    List<DocumentSummary> openDocuments();

    /**
     * The places of circuit {@code document}: the main circuit first, then every definition with where it is used
     * (spec §12). Refuses by default (an editor without definitions).
     */
    default List<PlaceSummary> openPlaces(DocumentId document) {
        throw new AutomationException(AutomationError.NOT_ALLOWED, "This editor has no places");
    }

    /**
     * Shows {@code place} of {@code document} in the window the AI is attached to, opening the circuit's group there
     * first when the window does not hold it: the one thing that changes the tab the user sees (spec §12).
     */
    default void openPlace(DocumentId document, PlaceRef place) {
        throw new AutomationException(AutomationError.NOT_ALLOWED, "This editor has no places");
    }
}
