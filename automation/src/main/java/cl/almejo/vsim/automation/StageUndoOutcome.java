package cl.almejo.vsim.automation;

import java.util.Objects;

/**
 * What «Deshacer etapa» did (IA.2, spec §4.1): the stage it undid ({@code -1} when there was none to undo), how many
 * of its steps went away, what it stopped at when some of the stage's steps stay, and whether some stage of the task
 * still has steps to undo (the panel's button stays on); and how many of the user's switch-click steps it undid on
 * the way because they lay on top of the stage's steps (a click only changes an input, so it never stops the undo).
 */
public record StageUndoOutcome(int stageIndex, int undone, StoppedAt stoppedAt, boolean moreToUndo,
                               int switchClicksUndone) {

    /** No stage of the task has a step in the circuit. */
    public static final StageUndoOutcome NOTHING_TO_UNDO = new StageUndoOutcome(-1, 0, StoppedAt.NOTHING, false);

    /** Why some of the stage's steps stay. */
    public enum StoppedAt {
        /** None stays: the whole stage was undone (or it had nothing). */
        NOTHING,
        /** The user's own edit (or anything that is not a step of this task) lies on top of them. */
        USER_EDIT,
        /** A step of another stage of the same task lies on top of them. */
        ANOTHER_STAGE
    }

    public StageUndoOutcome {
        Objects.requireNonNull(stoppedAt, "stoppedAt");
        if (undone < 0 || switchClicksUndone < 0) {
            throw new IllegalArgumentException("the counts are never negative: " + undone + ", " + switchClicksUndone);
        }
    }

    /** An outcome that undid no switch click. */
    public StageUndoOutcome(int stageIndex, int undone, StoppedAt stoppedAt, boolean moreToUndo) {
        this(stageIndex, undone, stoppedAt, moreToUndo, 0);
    }
}
