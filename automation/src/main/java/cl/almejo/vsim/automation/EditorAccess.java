package cl.almejo.vsim.automation;

import java.util.List;

/**
 * What the AI's work sees of the editor while it runs on the editor thread ({@link AutomationHost#onEditorThread}).
 * Only valid during that call: never keep it. Later tasks add what they need to read and change.
 */
public interface EditorAccess {

    /** The open circuits, one entry per circuit however many windows show it, in the order they were opened. */
    List<DocumentSummary> openDocuments();
}
