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
