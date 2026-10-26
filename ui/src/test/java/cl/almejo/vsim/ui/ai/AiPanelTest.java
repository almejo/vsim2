package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.automation.EditorSetting;
import cl.almejo.vsim.automation.ProposalAnswer;
import cl.almejo.vsim.automation.ProposedPreference;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.theme.AppThemes;
import cl.almejo.vsim.ui.theme.ReadableColors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;

import static cl.almejo.vsim.ui.UiTestSupport.freePort;
import static org.assertj.core.api.Assertions.assertThat;

/** Herramientas ▸ «IA (MCP)…»: the live state in words and colour, and what the user does there (spec §2, §7). */
class AiPanelTest {

    private final AppPreferences saved = AppPreferences.inMemory();
    private final AppPreferences windowPreferences = saved.layered();
    private final FakeAiTasks tasks = new FakeAiTasks();
    private final AiServerService service = new AiServerService(saved, new AutomationApi(new NoDocuments()), tasks,
            Runnable::run, Runnable::run);
    private final PreferenceProposals proposals = new PreferenceProposals(saved);
    private final List<String> copied = new ArrayList<>();
    private AiPanel panel;

    @BeforeEach
    void create() throws IOException {
        saved.setAiPort(freePort());
        panel = new AiPanel(service, windowPreferences, proposals, copied::add);
    }

    @AfterEach
    void close() {
        panel.dispose();
        service.stop();
    }

    @Test
    void offByDefaultWithActivarAndTheDataNote() {
        assertThat(panel.stateLabel().getText()).isEqualTo("Apagada");
        assertThat(panel.toggleButton().getText()).isEqualTo("Encender");
        assertThat(panel.nextFreePortButton().isVisible()).isFalse();
        assertThat(panel.dataNote().getText()).isEqualTo("Lo que la IA lee del circuito se envía a su proveedor");
        assertThat(panel.settingsSection().isVisible()).isFalse();
        assertThat(panel.proposalsSection().isVisible()).isFalse();
    }

    @Test
    void aTakenPortOffersTheNextFreeOneAndThenAsksToCopyAgain() throws Exception {
        try (ServerSocket other = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            saved.setAiPort(other.getLocalPort());

            panel.toggleButton().doClick();

            assertThat(panel.stateLabel().getText())
                    .isEqualTo("El puerto " + other.getLocalPort() + " está ocupado por otro programa");
            assertThat(panel.nextFreePortButton().isVisible()).isTrue();
            assertThat(panel.nextFreePortButton().getText()).isEqualTo("Usar el siguiente libre");
            assertThat(panel.toggleButton().getText()).isEqualTo("Encender");

            panel.nextFreePortButton().doClick();

            assertThat(panel.stateLabel().getText()).isEqualTo("Escuchando en 127.0.0.1:" + saved.aiPort());
            assertThat(panel.nextFreePortButton().isVisible()).isFalse();
            assertThat(panel.notice().getText())
                    .isEqualTo("Cambió el puerto: vuelve a copiar la configuración en Claude Code");

            panel.copyButton().doClick();

            assertThat(copied).hasSize(1);
            assertThat(copied.get(0)).contains("http://127.0.0.1:" + saved.aiPort() + "/mcp");
            assertThat(panel.notice().getText()).isNotEqualTo(
                    "Cambió el puerto: vuelve a copiar la configuración en Claude Code");
        }
    }

    @Test
    void theSettingsTheAiTouchedAreListedAndCanBeKeptOrDropped() {
        SettingsOverride override = SettingsOverride.of(windowPreferences).orElseThrow();

        override.set(EditorSetting.GRID_STYLE, "LINES");
        override.set(EditorSetting.SHOW_GUIDES, "false");

        assertThat(panel.settingsSection().isVisible()).isTrue();
        assertThat(panel.settingsHeader().getText()).isEqualTo("Ajustes que tocó la IA (2)");
        assertThat(panel.settingTexts()).containsExactly("Cuadrícula: líneas", "Mostrar guías: no");

        panel.keepSettingsButton().doClick();

        assertThat(saved.gridStyle()).isEqualTo(AppPreferences.GridStyle.LINES);
        assertThat(saved.showGuides()).isFalse();
        assertThat(panel.settingsSection().isVisible()).isFalse();

        override.set(EditorSetting.WIRES_FOLLOW, "false");
        panel.dropSettingsButton().doClick();

        assertThat(windowPreferences.wiresFollow()).isTrue();
        assertThat(saved.wiresFollow()).isTrue();
    }

    @Test
    void aProposalIsAcceptedOrDeclinedFromThePanel() {
        String accepted = proposals.propose(ProposedPreference.THEME, "DARK", "¿Paso al tema oscuro para el video?")
                .id();
        String declined = proposals.propose(ProposedPreference.LANGUAGE, "en", "<html><b>Click</b>").id();

        assertThat(panel.proposalsSection().isVisible()).isTrue();
        assertThat(panel.proposalTexts()).containsExactly("Cambiar el tema a «Oscuro»",
                "¿Paso al tema oscuro para el video?",
                "Cambiar el idioma a «English» (al volver a abrir VSim)", "<html><b>Click</b>");
        assertThat(panel.proposalLabels()).allSatisfy(label ->
                assertThat(label.getClientProperty("html.disable")).as("the AI's text is data").isEqualTo(true));

        panel.acceptButton(accepted).doClick();
        panel.declineButton(declined).doClick();

        assertThat(saved.theme()).isEqualTo(AppPreferences.Theme.DARK);
        assertThat(saved.language()).isEmpty();
        assertThat(proposals.answerOf(accepted)).isCompletedWithValue(ProposalAnswer.ACCEPTED);
        assertThat(proposals.answerOf(declined)).isCompletedWithValue(ProposalAnswer.DECLINED);
        assertThat(panel.proposalsSection().isVisible()).isFalse();
    }

    @Test
    void theStateColourIsReadAtPaintTimeAndTheErrorReadsOnBothThemes() throws Exception {
        LookAndFeel previous = UIManager.getLookAndFeel();
        try (ServerSocket other = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            saved.setAiPort(other.getLocalPort());
            panel.toggleButton().doClick();
            for (LookAndFeel theme : AppThemes.all()) {
                UIManager.setLookAndFeel(theme);
                assertThat(panel.stateLabel().getForeground()).as(theme.getName())
                        .isEqualTo(ReadableColors.errorText());
                assertThat(ReadableColors.contrast(panel.stateLabel().getForeground(),
                        ReadableColors.panelBackground())).as("error on " + theme.getName())
                        .isGreaterThanOrEqualTo(ReadableColors.MINIMUM_TEXT_CONTRAST);
                for (AiServerState state : List.of(new AiServerState.Off(), new AiServerState.Connected(1),
                        new AiServerState.Listening(1))) {
                    assertThat(ReadableColors.contrast(AiColors.of(state), ReadableColors.panelBackground()))
                            .as(state + " on " + theme.getName())
                            .isGreaterThanOrEqualTo(ReadableColors.MINIMUM_TEXT_CONTRAST);
                }
            }
        } finally {
            UIManager.setLookAndFeel(previous);
        }
    }

    @Test
    void disposingTakesBackEveryListener() {
        int serviceListeners = service.listenerCount();
        int preferenceListeners = saved.listenerCount();
        int proposalListeners = proposals.listenerCount();
        AiPanel another = new AiPanel(service, windowPreferences, proposals, copied::add);

        another.dispose();

        assertThat(service.listenerCount()).isEqualTo(serviceListeners);
        assertThat(saved.listenerCount()).isEqualTo(preferenceListeners);
        assertThat(proposals.listenerCount()).isEqualTo(proposalListeners);
    }

    @Test
    void aSessionPortIsSaidInThePanelAndCopyingRegistersAServerOfItsOwn() throws Exception {
        int sessionPort = freePort();
        saved.setAiSecret("s3cret-s3cret-s3cret-s3cret");
        AiServerService session = new AiServerService(saved, new AutomationApi(new NoDocuments()), tasks,
                Runnable::run, Runnable::run, AiServerService.QueuedWork.NONE, SessionPort.of(sessionPort));
        AiPanel sessionPanel = new AiPanel(session, windowPreferences, proposals, copied::add);
        try {
            assertThat(sessionPanel.portNote().isVisible()).isTrue();
            assertThat(sessionPanel.portNote().getText())
                    .isEqualTo("Puerto " + sessionPort + " (solo esta sesión, desde la línea de comandos)");
            assertThat(panel.portNote().isVisible()).as("no session port, no note").isFalse();

            sessionPanel.copyButton().doClick();

            assertThat(copied).containsExactly("claude mcp add --transport http --scope user vsim-" + sessionPort
                    + " http://127.0.0.1:" + sessionPort
                    + "/mcp --header \"Authorization: Bearer s3cret-s3cret-s3cret-s3cret\"");
        } finally {
            sessionPanel.dispose();
            session.stop();
        }
    }
}
