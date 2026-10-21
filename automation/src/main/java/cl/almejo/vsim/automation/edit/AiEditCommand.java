package cl.almejo.vsim.automation.edit;

import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.command.ReplaceContentCommand;
import cl.almejo.vsim.core.document.CircuitDocument;

import java.util.Objects;
import java.util.Optional;

/**
 * One AI edit call in the undo history (spec §4.1): the whole call as one step, tagged with its task so «Deshacer lo
 * que hizo la IA» finds it. It never merges with the commands around it (the default of {@link Command#mergedWith}),
 * and no user command merges into it: a user's nudge only merges with another nudge.
 */
final class AiEditCommand implements Command {

    /** The undo menu says «Deshacer cambio de la IA». */
    static final String DESCRIPTION = "command.aiEdit";

    private final ReplaceContentCommand content;
    private final Optional<String> taskId;
    /** Whether the document has this step now: true after apply (and redo), false after undo. EDT only. */
    private boolean applied;

    AiEditCommand(ReplaceContentCommand content, Optional<String> taskId) {
        this.content = Objects.requireNonNull(content, "content");
        this.taskId = Objects.requireNonNull(taskId, "taskId");
    }

    /** True when this step belongs to {@code task}. */
    boolean belongsTo(String task) {
        return taskId.filter(task::equals).isPresent();
    }

    boolean isApplied() {
        return applied;
    }

    @Override
    public void apply(CircuitDocument document) {
        content.apply(document);
        applied = true;
    }

    @Override
    public void revert(CircuitDocument document) {
        content.revert(document);
        applied = false;
    }

    @Override
    public String description() {
        return DESCRIPTION;
    }
}
