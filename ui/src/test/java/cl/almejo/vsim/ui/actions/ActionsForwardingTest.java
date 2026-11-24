package cl.almejo.vsim.ui.actions;

import org.junit.jupiter.api.Test;

import javax.swing.Action;
import java.awt.event.ActionEvent;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Test
    void aConditionalUnnamedActionIsNotFiredThroughTheInputMapWhileTheConditionIsFalse() {
        boolean[] enabled = {false};
        int[] runs = {0};
        javax.swing.JPanel panel = new javax.swing.JPanel();
        panel.getInputMap(javax.swing.JComponent.WHEN_FOCUSED).put(javax.swing.KeyStroke.getKeyStroke("ESCAPE"), "key");
        panel.getActionMap().put("key", Actions.unnamed(() -> enabled[0], () -> runs[0]++));
        java.awt.event.KeyEvent escape = new java.awt.event.KeyEvent(panel, java.awt.event.KeyEvent.KEY_PRESSED, 0, 0,
                java.awt.event.KeyEvent.VK_ESCAPE, java.awt.event.KeyEvent.CHAR_UNDEFINED);

        boolean consumedWhileOff = javax.swing.SwingUtilities.processKeyBindings(escape);
        enabled[0] = true;
        boolean consumedWhileOn = javax.swing.SwingUtilities.processKeyBindings(escape);

        assertThat(consumedWhileOff).isFalse();
        assertThat(consumedWhileOn).isTrue();
        assertThat(runs[0]).isEqualTo(1);
    }
}
