package cl.almejo.vsim.automation.edit;

import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.command.ReplaceContentCommand;
import cl.almejo.vsim.core.document.CircuitDocument;

import java.util.Objects;
import java.util.Optional;

/**
 * One AI edit call in the undo history (spec §4.1): the whole call as one step, tagged with its task so «Deshacer lo
 * que hizo la IA» finds it, and with the stage it belongs to so «Deshacer etapa» does (IA.2). It never merges with the
 * commands around it (the default of {@link Command#mergedWith}), and no user command merges into it: a user's nudge
 * only merges with another nudge.
 */
final class AiEditCommand implements Command {

    /** The undo menu says «Deshacer cambio de la IA». */
    static final String DESCRIPTION = "command.aiEdit";
    /** The stage of a step that belongs to no stage of its task («Dejar solo el resultado», a step with no task). */
    static final int NO_STAGE = -1;

    private final ReplaceContentCommand content;
    private final Optional<String> taskId;
    private final int stageIndex;
    private final String description;
    /** Whether the document has this step now: true after apply (and redo), false after undo. EDT only. */
    private boolean applied;

    AiEditCommand(ReplaceContentCommand content, Optional<String> taskId) {
        this(content, taskId, NO_STAGE, DESCRIPTION);
    }

    AiEditCommand(ReplaceContentCommand content, Optional<String> taskId, int stageIndex, String description) {
        this.content = Objects.requireNonNull(content, "content");
        this.taskId = Objects.requireNonNull(taskId, "taskId");
        this.stageIndex = stageIndex;
        this.description = Objects.requireNonNull(description, "description");
    }

    /** True when this step belongs to {@code task}. */
    boolean belongsTo(String task) {
        return taskId.filter(task::equals).isPresent();
    }

    /** True when this step belongs to stage {@code stage} of {@code task}. */
    boolean belongsTo(String task, int stage) {
        return belongsTo(task) && stageIndex == stage;
    }

    /** The stage of its task it belongs to; {@link #NO_STAGE} for none. */
    int stageIndex() {
        return stageIndex;
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
        return description;
    }
}
