package cl.almejo.vsim.ui.panels;

import org.junit.jupiter.api.Test;

import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

import static org.assertj.core.api.Assertions.assertThat;

class ModalDialogsTest {

    private int cancels;

    @Test
    void escapeAnywhereInTheWindowRunsTheCancel() {
        JRootPane root = new JRootPane();

        ModalDialogs.closeOnEscape(root, () -> cancels++);

        ActionListener escape = root.getActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
        assertThat(escape).isNotNull();
        escape.actionPerformed(new ActionEvent(root, ActionEvent.ACTION_PERFORMED, ""));
        assertThat(cancels).isEqualTo(1);
    }
}
