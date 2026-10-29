package cl.almejo.vsim.ui.about;

import cl.almejo.vsim.core.VsimVersion;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.UiTestSupport;
import cl.almejo.vsim.ui.actions.EditorActions;
import cl.almejo.vsim.ui.actions.ToolChoice;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.Selection;
import cl.almejo.vsim.ui.tools.Tool;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class AboutDialogTest {

    private static final AboutInformation INFORMATION =
            new AboutInformation("9.9", "21.0.1 (Test)", "Linux 7 amd64", "FlatLaf Light");

    private final List<String> copied = new ArrayList<>();

    private AboutContent content(LinkOpener links) {
        return new AboutContent(INFORMATION, copied::add, links);
    }

    private static List<Component> everyComponent(Container root) {
        List<Component> found = new ArrayList<>();
        for (Component child : root.getComponents()) {
            found.add(child);
            if (child instanceof Container container) {
                found.addAll(everyComponent(container));
            }
        }
        return found;
    }

    private static String allText(Container root) {
        StringBuilder text = new StringBuilder();
        for (Component component : everyComponent(root)) {
            if (component instanceof JLabel label) {
                text.append(label.getText()).append('\n');
            } else if (component instanceof JTextField field) {
                text.append(field.getText()).append('\n');
            }
        }
        return text.toString();
    }

    private static final LinkOpener NO_BROWSER = new LinkOpener() {
        @Override public boolean available() { return false; }
        @Override public boolean open(String link) { return false; }
    };

    @Test
    void contentHoldsTheLogoTheVersionTheRepositoryAndTheLicence() {
        AboutContent content = content(NO_BROWSER);

        assertThat(everyComponent(content)).anyMatch(component -> AboutContent.LOGO_NAME.equals(component.getName()));
        assertThat(allText(content)).contains("9.9").contains("https://github.com/almejo/vsim2")
                .contains("https://github.com/almejo/vsim2/releases").contains("GPL").contains("Alejandro Vera")
                .contains("FlatLaf").contains("2001").contains("2013").contains("2026");
    }

    @Test
    void theLogoHasASizeSoItIsVisible() {
        JLabel logo = (JLabel) everyComponent(content(NO_BROWSER)).stream()
                .filter(component -> AboutContent.LOGO_NAME.equals(component.getName())).findFirst().orElseThrow();

        assertThat(logo.getIcon().getIconWidth()).isPositive();
    }

    @Test
    void withoutABrowserThereAreNoOpenButtonsButTheAddressesStillShow() {
        AboutContent content = content(NO_BROWSER);

        List<String> buttons = everyComponent(content).stream().filter(JButton.class::isInstance)
                .map(component -> ((JButton) component).getName()).toList();
        assertThat(buttons).containsExactly(AboutContent.COPY_BUTTON_NAME);
    }

    @Test
    void openButtonsSendEachAddressToTheBrowser() {
        List<String> opened = new ArrayList<>();
        AboutContent content = content(new LinkOpener() {
            @Override public boolean available() { return true; }
            @Override public boolean open(String link) { return opened.add(link); }
        });

        everyComponent(content).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                .filter(button -> !AboutContent.COPY_BUTTON_NAME.equals(button.getName())).forEach(JButton::doClick);

        assertThat(opened).containsExactly(AboutContent.REPOSITORY, AboutContent.RELEASES);
    }

    @Test
    void asTextHasTheVersionJavaAndTheSystem() {
        assertThat(INFORMATION.asText()).contains("9.9").contains("21.0.1").contains("Linux 7").contains("FlatLaf Light");
    }

    @Test
    void currentReadsTheRunningVersion() {
        assertThat(AboutInformation.current().version()).isEqualTo(VsimVersion.current());
        assertThat(AboutInformation.current().javaVersion()).isNotBlank();
    }

    @Test
    void copyInformationWritesTheTextToTheInjectedClipboard() {
        JButton copy = (JButton) everyComponent(content(NO_BROWSER)).stream()
                .filter(component -> AboutContent.COPY_BUTTON_NAME.equals(component.getName())).findFirst()
                .orElseThrow();

        copy.doClick();

        assertThat(copied).containsExactly(INFORMATION.asText());
    }

    @Test
    void theMenuItemOpensThePresenter() {
        List<Component> shownOver = new ArrayList<>();
        Component owner = new JLabel();
        Supplier<cl.almejo.vsim.ui.canvas.CircuitCanvas> noPane = () -> null;
        EditorActions actions = new EditorActions(OpenDocument.createEmpty(StandardComponentTypes.registry()), noPane,
                new Selection(), new ToolChoice() {
                    @Override public void choose(Supplier<? extends Tool> newTool) { }
                    @Override public void useDefault() { }
                }, UiTestSupport.NO_WINDOW, shownOver::add);

        actions.about().actionPerformed(new ActionEvent(owner, ActionEvent.ACTION_PERFORMED, "about"));

        assertThat(shownOver).hasSize(1);
    }
}
