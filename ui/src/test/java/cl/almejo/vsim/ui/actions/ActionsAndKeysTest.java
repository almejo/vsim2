package cl.almejo.vsim.ui.actions;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;

class ActionsAndKeysTest {

    private static final ActionEvent EVENT = new ActionEvent(new Object(), ActionEvent.ACTION_PERFORMED, "");

    @Test
    void aNamedActionCarriesItsNameAndAcceleratorAndRunsTheBehavior() {
        int[] runs = {0};
        KeyStroke accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0);

        Action action = Actions.of("Run", accelerator, () -> runs[0]++);
        action.actionPerformed(EVENT);

        assertThat(action.getValue(Action.NAME)).isEqualTo("Run");
        assertThat(action.getValue(Action.ACCELERATOR_KEY)).isEqualTo(accelerator);
        assertThat(runs[0]).isEqualTo(1);
        assertThat(Actions.of("Plain", () -> { }).getValue(Action.ACCELERATOR_KEY)).isNull();
    }

    @Test
    void anUnnamedActionHasNoName() {
        assertThat(Actions.unnamed(() -> { }).getValue(Action.NAME)).isNull();
    }

    @Test
    void theEnabledStateFollowsTheCondition() {
        boolean[] allowed = {false};
        Action action = Actions.unnamed(() -> allowed[0], () -> { });

        assertThat(action.isEnabled()).isFalse();
        allowed[0] = true;
        assertThat(action.isEnabled()).isTrue();
    }

    @Test
    void bindPutsTheKeyAndTheActionUnderTheSameName() {
        JPanel panel = new JPanel();
        KeyStroke keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
        int[] runs = {0};

        Keys.bind(panel, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, keyStroke, "close", () -> runs[0]++);
        panel.getActionMap().get("close").actionPerformed(EVENT);

        assertThat(panel.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(keyStroke)).isEqualTo("close");
        assertThat(panel.getActionMap().get("close").getValue(Action.NAME)).isNull();
        assertThat(runs[0]).isEqualTo(1);
    }

    @Test
    void bindWhenFocusedUsesTheFocusedMapAndTheEnabledOverloadAsksTheCondition() {
        JPanel panel = new JPanel();
        KeyStroke keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);
        boolean[] allowed = {false};

        Keys.bindWhenFocused(panel, keyStroke, "go", () -> { });
        Keys.bind(panel, JComponent.WHEN_FOCUSED, keyStroke, "guarded", () -> allowed[0], () -> { });

        assertThat(panel.getInputMap(JComponent.WHEN_FOCUSED).get(keyStroke)).isEqualTo("guarded");
        assertThat(panel.getActionMap().get("go")).isNotNull();
        assertThat(panel.getActionMap().get("guarded").isEnabled()).isFalse();
        allowed[0] = true;
        assertThat(panel.getActionMap().get("guarded").isEnabled()).isTrue();
    }
}
