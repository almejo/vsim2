package cl.almejo.vsim.ui.window;

import java.util.Optional;

/**
 * Work a tab keeps in memory only, outside the circuit (a machine's test-mode draft, plan 2026-11-14 hito 4), that has
 * to be settled before the tab is saved, closed or left: the window asks each guard of a view ({@link DraftGuards})
 * at those moments, and the guard asks the user what to do with it, or settles it quietly.
 */
public interface DraftGuard {

    /** Why the window asks. */
    enum Reason {
        /** Guardar or Guardar como of the tab's circuit: the tab stays as it is after. */
        SAVE,
        /** The tab, its group, its window or VSim closes. */
        CLOSE,
        /** Another tab comes to the front. */
        SWITCH
    }

    /** What the window does next. */
    enum Answer {
        /** Go on with the save, the close or the switch. */
        GO_ON,
        /** The user wants to keep working: the save, the close or the switch does not happen. */
        CANCEL
    }

    /** Settles the draft before {@code reason} happens (asking the user when it has to); EDT only. */
    Answer settle(Reason reason);

    /** What the tab's title adds while this guard is on (« · prueba»); empty for nothing. */
    default Optional<String> titleNote() {
        return Optional.empty();
    }

    /**
     * What the status line says when the tab goes without being asked (its definition left the circuit), so the user
     * knows the draft went with it; empty to say nothing.
     */
    default Optional<String> discardNotice() {
        return Optional.empty();
    }
}
