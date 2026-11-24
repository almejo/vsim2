package cl.almejo.vsim.ui.actions;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.event.ActionEvent;
import javax.swing.Action;
import org.junit.jupiter.api.Test;

/** The actions that only forward to a {@link Runnable} (the ones the code builds instead of anonymous classes). */
class ActionsForwardingTest {

    private static ActionEvent event() {
        return new ActionEvent(new Object(), ActionEvent.ACTION_PERFORMED, "");
    }

    @Test
    void ofIsNamedAndRunsTheBehaviour() {
        int[] runs = {0};
        Action action = Actions.of("Name", () -> runs[0]++);

        assertThat(action.getValue(Action.NAME)).isEqualTo("Name");
        action.actionPerformed(event());
        assertThat(runs[0]).isEqualTo(1);
    }

    @Test
    void unnamedHasNoNameAndAsksTheConditionEachTime() {
        boolean[] enabled = {false};
        int[] runs = {0};
        Action action = Actions.unnamed(() -> enabled[0], () -> runs[0]++);

        assertThat(action.getValue(Action.NAME)).isNull();
        assertThat(action.isEnabled()).isFalse();
        enabled[0] = true;
        assertThat(action.isEnabled()).isTrue();
        action.actionPerformed(event());
        assertThat(runs[0]).isEqualTo(1);
    }

    @Test
    void doNothingIsEnabledUnnamedAndHarmless() {
        Action action = Actions.doNothing();

        assertThat(action.getValue(Action.NAME)).isNull();
        assertThat(action.isEnabled()).isTrue();
        action.actionPerformed(event());
    }
}
