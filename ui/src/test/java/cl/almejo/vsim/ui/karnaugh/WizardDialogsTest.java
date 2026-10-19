package cl.almejo.vsim.ui.karnaugh;

import org.junit.jupiter.api.Test;

import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

import static org.assertj.core.api.Assertions.assertThat;

class WizardDialogsTest {

    private int requests;

    @Test
    void escapeAndTheCloseBoxBothAskThePanelToClose() {
        JRootPane root = new JRootPane();
        WizardDialogs.bindEscape(root, () -> requests++);
        ActionListener escape = root.getActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
        assertThat(escape).isNotNull();
        escape.actionPerformed(new ActionEvent(root, ActionEvent.ACTION_PERFORMED, ""));
        assertThat(requests).isEqualTo(1);

        WizardDialogs.closeBox(() -> requests++).windowClosing(null);
        assertThat(requests).isEqualTo(2);
    }
}
