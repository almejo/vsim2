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
    void contentHoldsTheLogoTheVersionTheLabelledGridAndTheCredits() {
        AboutContent content = content(NO_BROWSER);

        assertThat(everyComponent(content)).anyMatch(component -> AboutContent.LOGO_NAME.equals(component.getName()));
        assertThat(allText(content)).contains("VSim 2").contains("Versión 9.9").contains("Autor")
                .contains("Alejandro Vera").contains("Licencia").contains("GPL 3.0").contains("Código")
                .contains("github.com/almejo/vsim2").contains("github.com/almejo/vsim2/releases")
                .contains("Versiones").contains("Historia").contains("JCSim (2001), VSim (2013), VSim 2 (2026)")
                .contains("Usa FlatLaf, MigLayout");
    }

    @Test
    void theLogoIs72PixelsOnTheLeftOfTheHeading() {
        AboutContent content = content(NO_BROWSER);
        JLabel logo = (JLabel) everyComponent(content).stream()
                .filter(component -> AboutContent.LOGO_NAME.equals(component.getName())).findFirst().orElseThrow();

        assertThat(logo.getIcon().getIconWidth()).isEqualTo(72);
        assertThat(logo.getIcon().getIconHeight()).isEqualTo(72);
    }

    @Test
    void theBodyIsNotAnOptionPaneAndTheFooterHasCopyAndClose() {
        AboutContent content = content(NO_BROWSER);

        assertThat(everyComponent(content)).noneMatch(component -> component instanceof javax.swing.JOptionPane);
        List<String> buttons = everyComponent(content).stream().filter(JButton.class::isInstance)
                .map(component -> ((JButton) component).getName()).toList();
        assertThat(buttons).containsExactly(AboutContent.COPY_BUTTON_NAME, AboutContent.CLOSE_BUTTON_NAME);
        assertThat(content.closeButton().getText()).isEqualTo("Cerrar");
    }

    @Test
    void withoutABrowserTheAddressesAreSelectableTextWithoutACaretAndThereAreNoLinks() {
        AboutContent content = content(NO_BROWSER);

        List<JTextField> fields = everyComponent(content).stream().filter(JTextField.class::isInstance)
                .map(JTextField.class::cast).toList();
        assertThat(fields).extracting(JTextField::getText)
                .containsExactly("github.com/almejo/vsim2", "github.com/almejo/vsim2/releases");
        assertThat(fields).allMatch(field -> !field.isEditable() && !field.getCaret().isVisible());
        assertThat(everyComponent(content)).noneMatch(AboutContent.LinkLabel.class::isInstance);
    }

    @Test
    void withABrowserEachAddressIsALinkThatOpensItsUrl() {
        List<String> opened = new ArrayList<>();
        AboutContent content = content(new LinkOpener() {
            @Override public boolean available() { return true; }
            @Override public boolean open(String link) { return opened.add(link); }
        });

        List<AboutContent.LinkLabel> links = everyComponent(content).stream()
                .filter(AboutContent.LinkLabel.class::isInstance).map(AboutContent.LinkLabel.class::cast).toList();
        links.forEach(AboutContent.LinkLabel::activate);

        assertThat(links).hasSize(2);
        assertThat(links.get(0).getCursor().getType()).isEqualTo(java.awt.Cursor.HAND_CURSOR);
        assertThat(opened).containsExactly(AboutContent.REPOSITORY, AboutContent.RELEASES);
    }

    @Test
    void theCardAndTheFooterHaveDifferentBackgroundsReadFromTheLookAndFeelAtPaintTime() {
        AboutContent content = content(NO_BROWSER);
        java.awt.Color before = content.card().getBackground();

        javax.swing.UIManager.put("List.background", new java.awt.Color(1, 2, 3));
        javax.swing.UIManager.put("Panel.background", new java.awt.Color(9, 8, 7));
        try {
            assertThat(content.card().getBackground()).isEqualTo(new java.awt.Color(1, 2, 3));
            assertThat(content.footer().getBackground()).isEqualTo(new java.awt.Color(9, 8, 7));
        } finally {
            javax.swing.UIManager.put("List.background", null);
            javax.swing.UIManager.put("Panel.background", null);
        }
        assertThat(content.card().getBackground()).isEqualTo(before);
    }

    @Test
    void theMutedTextFollowsTheDisabledForegroundAndTheLinksTheLinkColour() {
        AboutContent content = content(new LinkOpener() {
            @Override public boolean available() { return true; }
            @Override public boolean open(String link) { return true; }
        });
        javax.swing.UIManager.put("Label.disabledForeground", new java.awt.Color(11, 12, 13));
        javax.swing.UIManager.put("Component.linkColor", new java.awt.Color(21, 22, 23));
        try {
            JLabel version = (JLabel) everyComponent(content).stream()
                    .filter(component -> AboutContent.VERSION_NAME.equals(component.getName())).findFirst()
                    .orElseThrow();
            AboutContent.LinkLabel link = (AboutContent.LinkLabel) everyComponent(content).stream()
                    .filter(AboutContent.LinkLabel.class::isInstance).findFirst().orElseThrow();
            assertThat(version.getForeground()).isEqualTo(new java.awt.Color(11, 12, 13));
            assertThat(link.getForeground()).isEqualTo(new java.awt.Color(21, 22, 23));
        } finally {
            javax.swing.UIManager.put("Label.disabledForeground", null);
            javax.swing.UIManager.put("Component.linkColor", null);
        }
    }

    @Test
    void bindingItToARootPaneMakesCloseTheDefaultButtonAndEscapeCloseIt() {
        AboutContent content = content(NO_BROWSER);
        javax.swing.JRootPane rootPane = new javax.swing.JRootPane();

        content.bindTo(rootPane);

        assertThat(rootPane.getDefaultButton()).isSameAs(content.closeButton());
        assertThat(rootPane.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW)
                .get(javax.swing.KeyStroke.getKeyStroke("ESCAPE"))).isNotNull();
        assertThat(content.getPreferredSize().width).isGreaterThanOrEqualTo(460);
    }

    @Test
    void asTextHasTheVersionJavaAndTheSystem() {
        assertThat(INFORMATION.asText()).contains("9.9").contains("21.0.1").contains("Linux 7")
                .contains("FlatLaf Light");
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
