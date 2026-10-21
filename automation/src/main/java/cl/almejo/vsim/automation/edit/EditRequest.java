package cl.almejo.vsim.automation.edit;

import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.DocumentVersion;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One AI edit call (spec §10, A§6): the circuit, the version it was prepared against, the AI's own key for the call
 * (a retry with the same key and arguments gets the same receipt, A§6.3), the step of the task it carries out, and
 * the operations, applied all or nothing. With {@code validateOnly} VSim checks everything and changes nothing.
 */
public record EditRequest(DocumentId document, DocumentVersion expected, String operationId, Optional<TaskStep> step,
                          List<EditOperation> operations, boolean validateOnly) {

    public EditRequest {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(step, "step");
        operations = List.copyOf(operations);
    }
}
