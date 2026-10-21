package cl.almejo.vsim.automation;

/**
 * What «Deshacer lo que hizo la IA» did: how many AI steps it undid, and whether it stopped early because the user's
 * own edit was on top of the history (spec §4.1).
 */
public record UndoOutcome(int undone, boolean stoppedAtUserEdit) {

    public UndoOutcome {
        if (undone < 0) {
            throw new IllegalArgumentException("undone is never negative: " + undone);
        }
    }
}
