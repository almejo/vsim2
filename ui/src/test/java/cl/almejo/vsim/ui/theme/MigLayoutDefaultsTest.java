package cl.almejo.vsim.ui.theme;

import net.miginfocom.layout.PlatformDefaults;
import net.miginfocom.swing.MigLayout;
import net.miginfocom.swing.SwingComponentWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import java.util.List;

import static cl.almejo.vsim.ui.UiTestSupport.layOut;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * MigLayout moves a combo box, a spinner or a tabbed pane by the padding of the native macOS (Aqua) or Windows
 * components; VSim draws FlatLaf's on every system, so on a Mac the fields of a form did not line up (CI, macOS).
 */
class MigLayoutDefaultsTest {

    private final int platform = PlatformDefaults.getPlatform();

    @AfterEach
    void restore() {
        PlatformDefaults.setPlatform(platform);
        MigLayoutDefaults.install();
    }

    @Test
    void onAMacTheFieldsOfAFormLineUpAndATabbedPaneStartsAtTheInsets() {
        PlatformDefaults.setPlatform(PlatformDefaults.MAC_OSX);
        MigLayoutDefaults.install();
        JPanel form = new JPanel(new MigLayout("wrap 1, insets 12", "[grow, fill]"));
        JTextField text = new JTextField("texto");
        JComboBox<String> choice = new JComboBox<>(new String[]{"uno", "dos"});
        JSpinner number = new JSpinner();
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("F", new JPanel());
        for (java.awt.Component field : List.of(tabs, text, choice, number)) {
            form.add(field);
        }
        form.setSize(300, 400);
        layOut(form);

        assertThat(tabs.getY()).isEqualTo(12);
        assertThat(List.of(tabs.getX(), text.getX(), choice.getX(), number.getX())).containsOnly(12);
        assertThat(SwingComponentWrapper.isVisualPaddingEnabled()).isFalse();
    }
}
