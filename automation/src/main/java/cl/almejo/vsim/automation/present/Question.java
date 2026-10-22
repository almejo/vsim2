package cl.almejo.vsim.automation.present;

import cl.almejo.vsim.automation.DocumentId;

import java.util.List;
import java.util.Objects;

/**
 * A question the AI asks the user in VSim (IA.3, {@code vsim_ask_user}): its text, up to four short choices (a button
 * each) and whether the user may also write an answer. The texts are the AI's, shown as plain text.
 */
public record Question(String questionId, String taskId, DocumentId document, String text, List<String> choices,
                       boolean allowText) {

    public Question {
        Objects.requireNonNull(questionId, "questionId");
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(text, "text");
        choices = List.copyOf(choices);
    }
}
