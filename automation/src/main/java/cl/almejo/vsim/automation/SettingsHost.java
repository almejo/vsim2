package cl.almejo.vsim.automation;

import java.util.concurrent.CompletableFuture;

/**
 * The editor-side services for new circuits, the editor settings the AI may change and preference proposals (IA.1
 * Task 5, spec §7). Every method runs on the editor thread: call them inside {@link AutomationHost#onEditorThread}.
 * The defaults refuse, so a host that offers none of it (a test's fake) needs to write nothing.
 */
public interface SettingsHost {

    /** Opens a new, unsaved circuit window named {@code name} (as Archivo ▸ Nuevo); it is never saved by the AI. */
    default DocumentId newCircuit(String name) {
        throw new AutomationException(AutomationError.NOT_ALLOWED, "This editor cannot open new circuits");
    }

    /**
     * Changes {@code setting} to {@code value} (one of {@link EditorSetting#allowedValues()}) in the temporary layer of
     * the window that shows {@code document}: never the saved preferences nor the other windows. Dropped when the
     * task ends unless the user keeps it. {@link AutomationError#INVALID_PARAMETER} for a value not allowed,
     * {@link AutomationError#DOCUMENT_CLOSED} for a document no longer open.
     */
    default void setEditorSetting(DocumentId document, EditorSetting setting, String value) {
        throw new AutomationException(AutomationError.NOT_ALLOWED, "This editor has no settings the AI may change");
    }

    /**
     * Shows the user a proposal to change {@code preference} to {@code value}, with {@code reason} («¿Paso al tema
     * oscuro para el video?»); returns its id for {@link #proposalAnswer}. Nothing changes until the user accepts.
     */
    default String proposePreference(ProposedPreference preference, String value, String reason) {
        throw new AutomationException(AutomationError.NOT_ALLOWED, "This editor takes no preference proposals");
    }

    /**
     * Completes with the user's answer to the proposal {@code proposalId} (at once when it was already answered);
     * wait for it off the editor thread. {@link AutomationError#INVALID_REFERENCE} for an unknown id.
     */
    default CompletableFuture<ProposalAnswer> proposalAnswer(String proposalId) {
        throw new AutomationException(AutomationError.NOT_ALLOWED, "This editor takes no preference proposals");
    }
}
