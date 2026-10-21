package cl.almejo.vsim.automation;

import java.util.Objects;

/** One AI task (spec §3) as the editor's status line and panel show it. */
public record TaskInfo(String taskId, DocumentId document, String title) {

    public TaskInfo {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(title, "title");
    }
}
