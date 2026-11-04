package cl.almejo.vsim.ui.about;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.UiTestSupport;
import cl.almejo.vsim.ui.actions.EditorActions;
import cl.almejo.vsim.ui.actions.ToolChoice;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.Selection;
import cl.almejo.vsim.ui.tools.Tool;
import org.junit.jupiter.api.Test;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemReportLinkTest {

    private static final AboutInformation INFORMATION =
            new AboutInformation("0.6", "21.0.1 (Test)", "Linux 7 amd64", "FlatLaf Light", "deb");

    @Test
    void spanishUsesTheSpanishFormWithTheThreeFieldsEncoded() {
        assertThat(ProblemReportLink.url(INFORMATION, Locale.forLanguageTag("es"))).isEqualTo(
                "https://github.com/almejo/vsim2/issues/new?template=problema.yml&version=0.6"
                        + "&sistema=Linux%207%20amd64&formato=deb");
    }

    @Test
    void anyOtherLanguageUsesTheEnglishForm() {
        assertThat(ProblemReportLink.url(INFORMATION, Locale.ENGLISH))
                .startsWith("https://github.com/almejo/vsim2/issues/new?template=problem.yml&version=0.6");
    }

    @Test
    void accentsAndReservedCharactersAreEncodedAndNothingElseIsSent() {
        AboutInformation odd = new AboutInformation("0.6", "secret java", "Sistema ñ&=#", "secret laf", "jar");
        String url = ProblemReportLink.url(odd, Locale.forLanguageTag("es"));
        assertThat(url).contains("sistema=Sistema%20%C3%B1%26%3D%23&formato=jar")
                .doesNotContain("secret").doesNotContain(" ");
    }

    @Test
    void packageFormatDefaultsToJarAndComesFromTheSystemProperty() {
        String previous = System.getProperty("vsim.packageFormat");
        try {
            System.clearProperty("vsim.packageFormat");
            assertThat(AboutInformation.current().packageFormat()).isEqualTo("jar");
            System.setProperty("vsim.packageFormat", "msi");
            assertThat(AboutInformation.current().packageFormat()).isEqualTo("msi");
        } finally {
            if (previous == null) {
                System.clearProperty("vsim.packageFormat");
            } else {
                System.setProperty("vsim.packageFormat", previous);
            }
        }
    }

    @Test
    void theCopiedTextShowsThePackageFormat() {
        assertThat(INFORMATION.asText()).contains("deb");
    }

    @Test
    void theReportActionOpensTheUrlThroughTheInjectedOpener() {
        List<String> opened = new ArrayList<>();
        LinkOpener opener = new LinkOpener() {
            @Override public boolean available() { return true; }
            @Override public boolean open(String link) { opened.add(link); return true; }
        };
        Supplier<CircuitCanvas> noPane = () -> null;
        EditorActions actions = new EditorActions(OpenDocument.createEmpty(StandardComponentTypes.registry()), noPane,
                new Selection(), new ToolChoice() {
                    @Override public void choose(Supplier<? extends Tool> newTool) { }
                    @Override public void useDefault() { }
                }, UiTestSupport.NO_WINDOW, owner -> { }, opener, () -> INFORMATION);

        actions.reportProblem().actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "report"));

        assertThat(opened).hasSize(1);
        assertThat(opened.get(0)).startsWith("https://github.com/almejo/vsim2/issues/new?template=")
                .contains("version=0.6&sistema=Linux%207%20amd64&formato=deb");
    }
}
