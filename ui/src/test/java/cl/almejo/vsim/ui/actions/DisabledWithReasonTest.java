package cl.almejo.vsim.ui.actions;

import org.junit.jupiter.api.Test;

import javax.swing.Action;
import javax.swing.JButton;

import static org.assertj.core.api.Assertions.assertThat;

/** An action held disabled with the reason as its tooltip, whatever its owner sets meanwhile, and given back after. */
class DisabledWithReasonTest {

    private final Action action = Actions.of("Ordenar circuito", () -> { });

    @Test
    void theActionIsDisabledWithTheReasonAsItsTooltip() {
        action.putValue(Action.SHORT_DESCRIPTION, "Ordenar circuito (Ctrl+Alt+L)");
        JButton button = new JButton(action);

        DisabledWithReason.disable(action, "No se usa en una máquina de estados: no hay cables que ordenar");

        assertThat(action.isEnabled()).isFalse();
        assertThat(button.isEnabled()).isFalse();
        assertThat(button.getToolTipText()).isEqualTo("No se usa en una máquina de estados: no hay cables que ordenar");
    }

    @Test
    void whatTheOwnerSetsMeanwhileIsHeldBackAndGivenBackAfter() {
        action.putValue(Action.SHORT_DESCRIPTION, "before");
        Runnable release = DisabledWithReason.disable(action, "reason");

        action.setEnabled(true);
        action.setEnabled(false);
        action.setEnabled(true);
        action.putValue(Action.SHORT_DESCRIPTION, "Ordenar circuito (Ctrl+Alt+Mayús+L)");

        assertThat(action.isEnabled()).isFalse();
        assertThat(action.getValue(Action.SHORT_DESCRIPTION)).isEqualTo("reason");
        release.run();
        assertThat(action.isEnabled()).isTrue();
        assertThat(action.getValue(Action.SHORT_DESCRIPTION)).isEqualTo("Ordenar circuito (Ctrl+Alt+Mayús+L)");
    }

    /**
     * An action with its own reason (main's {@link Actions#disabledBecause}) says the hold's while held, so «Acciones»
     * and its tooltip say why a machine's tab disables it, and its own once released.
     */
    @Test
    void anActionsOwnReasonGivesWayToTheHoldsAndComesBackAfter() {
        Actions.disabledBecause(action, () -> "Selecciona algo primero");
        action.setEnabled(false);

        Runnable release = DisabledWithReason.disable(action, "No se usa en una máquina de estados");
        action.putValue(Action.SHORT_DESCRIPTION, "Rotar ↻ (R)"); // the owner rewrites its tooltip meanwhile

        assertThat(Actions.disabledReason(action)).contains("No se usa en una máquina de estados");
        assertThat(action.getValue(Action.SHORT_DESCRIPTION)).isEqualTo("No se usa en una máquina de estados");
        release.run();
        assertThat(Actions.disabledReason(action)).contains("Selecciona algo primero");
        assertThat(action.getValue(Action.SHORT_DESCRIPTION)).isEqualTo("Selecciona algo primero");
    }

    @Test
    void releasingGivesBackTheStateItHadWhenNothingChanged() {
        action.setEnabled(false);
        Runnable release = DisabledWithReason.disable(action, "reason");

        release.run();
        release.run(); // twice does nothing more

        assertThat(action.isEnabled()).isFalse();
        assertThat(action.getValue(Action.SHORT_DESCRIPTION)).isNull();
    }
}
