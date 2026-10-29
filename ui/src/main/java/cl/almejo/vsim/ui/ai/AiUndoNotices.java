package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.UndoOutcome;
import cl.almejo.vsim.ui.i18n.Messages;

/**
 * The words of «Deshacer lo que hizo la IA» and «Deshacer etapa»: the AI's undo passes over the user's switch clicks
 * that lie on top of its steps (a click only changes an input) and undoes them too, so the notice says so.
 */
public final class AiUndoNotices {

    private AiUndoNotices() {
    }

    /** What «Deshacer lo que hizo la IA» did: «Pasos de la IA deshechos: 3», or where it stopped, and the clicks. */
    static String of(UndoOutcome outcome) {
        String notice = Messages.format(outcome.stoppedAtUserEdit() ? "ai.status.undoStoppedAtUser"
                : "ai.status.undone", Integer.toString(outcome.undone()));
        return withSwitchClicks(notice, outcome.switchClicksUndone());
    }

    /** {@code notice}, plus a sentence for the {@code switchClicksUndone} switch-click steps undone (if any). */
    public static String withSwitchClicks(String notice, int switchClicksUndone) {
        return switchClicksUndone == 0 ? notice
                : Messages.format("ai.status.alsoUndidSwitchClicks", notice, switchClicksUndone);
    }
}
