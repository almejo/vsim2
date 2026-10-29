package cl.almejo.vsim.automation;

/**
 * What «Deshacer lo que hizo la IA» did: how many AI steps it undid, whether it stopped early because the user's own
 * edit was on top of the history (spec §4.1), and how many of the user's switch-click steps it undid on the way
 * because they lay on top of the AI's steps (a click only changes an input, so it never stops the undo).
 */
public record UndoOutcome(int undone, boolean stoppedAtUserEdit, int switchClicksUndone) {

    public UndoOutcome {
        if (undone < 0 || switchClicksUndone < 0) {
            throw new IllegalArgumentException("the counts are never negative: " + undone + ", " + switchClicksUndone);
        }
    }

    /** An outcome that undid no switch click. */
    public UndoOutcome(int undone, boolean stoppedAtUserEdit) {
        this(undone, stoppedAtUserEdit, 0);
    }
}
