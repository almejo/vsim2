package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.EditorSetting;
import cl.almejo.vsim.automation.UndoOutcome;
import cl.almejo.vsim.mcp.McpServerSettings;
import cl.almejo.vsim.mcp.RunningServer;
import cl.almejo.vsim.mcp.VsimMcpServer;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.theme.AppThemes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.Icon;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.freePort;
import static org.assertj.core.api.Assertions.assertThat;

/** The rebuilt panel (IA.3): the state header, Encender / Apagar / Reiniciar, what to do on errors, tasks, icons. */
class AiPanelStatesTest {

    private static final Instant STARTED = Instant.parse("2026-10-22T14:03:00Z");

    private final AppPreferences saved = AppPreferences.inMemory();
    private final AppPreferences windowPreferences = saved.layered();
    private final FakeAiTasks tasks = new FakeAiTasks();
    private final AiServerService service = new AiServerService(saved, new AutomationApi(new NoDocuments()), tasks,
            Runnable::run, Runnable::run, AiServerService.QueuedWork.NONE, SessionPort.none(), Runnable::run,
            Clock.fixed(STARTED, ZoneOffset.UTC));
    private final PreferenceProposals proposals = new PreferenceProposals(saved);
    private final List<String> copied = new ArrayList<>();
    private AiPanel panel;
    private int port;

    @BeforeEach
    void create() throws IOException {
        Messages.initialize(Messages.SPANISH);
        port = freePort();
        saved.setAiPort(port);
        panel = new AiPanel(service, windowPreferences, proposals, copied::add);
    }

    @AfterEach
    void close() {
        panel.dispose();
        service.stop();
    }

    @Test
    void offSaysApagadaWithEncenderAndNothingToRestart() {
        assertThat(panel.titleLabel().getText()).isEqualTo("Apagada");
        assertThat(panel.stateLabel().isVisible()).as("the word is said once").isFalse();
        assertThat(panel.detailsLabel().isVisible()).isFalse();
        assertThat(panel.toggleButton().getText()).isEqualTo("Encender");
        assertThat(panel.restartButton().getText()).isEqualTo("Reiniciar");
        assertThat(panel.restartButton().isEnabled()).isFalse();
        assertThat(panel.helpLabel().isVisible()).isFalse();
        assertThat(panel.tasksSection().isVisible()).isFalse();
        assertThat(panel.stateDot().color()).isEqualTo(AiColors.muted());
    }

    @Test
    void theStateHeaderIsTheOneComponentPreferenciasShowsToo() {
        assertThat(panel.getComponent(0)).isInstanceOf(AiStateHeader.class);
        assertThat(panel.titleLabel()).isSameAs(((AiStateHeader) panel.getComponent(0)).titleLabel());
    }

    @Test
    void listeningConnectedAndWorkingSayTheirWordTheAddressSinceWhenAndTheRequests() {
        String time = LocalTime.ofInstant(STARTED, ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"));

        panel.toggleButton().doClick();
        assertThat(panel.titleLabel().getText()).isEqualTo("Escuchando");
        assertThat(panel.stateLabel().getText()).isEqualTo("Escuchando en 127.0.0.1:" + port);
        assertThat(panel.detailsLabel().getText())
                .isEqualTo("127.0.0.1:" + port + " · desde las " + time + " · ningún pedido");
        assertThat(panel.toggleButton().getText()).isEqualTo("Apagar");
        assertThat(panel.restartButton().isEnabled()).isTrue();

        service.requestServed();
        assertThat(panel.titleLabel().getText()).isEqualTo("Conectado");
        assertThat(panel.detailsLabel().getText()).endsWith("· 1 pedido");
        service.requestServed();
        assertThat(panel.detailsLabel().getText()).as("counted while the state stays").endsWith("· 2 pedidos");
        assertThat(panel.stateDot().color()).isEqualTo(AiColors.success());

        tasks.start(FakeAiTasks.task("task-1", new DocumentId("circuit-1"), "Flip-flop D"));
        assertThat(panel.titleLabel().getText()).isEqualTo("Trabajando");
        assertThat(panel.stateLabel().getText()).isEqualTo("IA trabajando: Flip-flop D");
    }

    @Test
    void theRunningTaskCanBeStoppedOrUndoneFromThePanel() {
        panel.toggleButton().doClick();
        tasks.start(FakeAiTasks.task("task-1", new DocumentId("circuit-1"), "Flip-flop D"));
        tasks.nextOutcome = new UndoOutcome(3, false);

        assertThat(panel.tasksSection().isVisible()).isTrue();
        assertThat(panel.taskTexts()).containsExactly("Flip-flop D");
        panel.stopButton("task-1").doClick();
        panel.undoButton("task-1").doClick();

        assertThat(tasks.stopped).containsExactly("task-1");
        assertThat(tasks.undone).containsExactly("task-1");
        assertThat(panel.notice().getText()).isEqualTo("Pasos de la IA deshechos: 3");

        tasks.stop(FakeAiTasks.task("task-1", new DocumentId("circuit-1"), "Flip-flop D"));
        assertThat(panel.tasksSection().isVisible()).isFalse();
    }

    @Test
    void reiniciarStopsTheTaskAndListensAgainOnTheSamePort() {
        panel.toggleButton().doClick();
        tasks.start(FakeAiTasks.task("task-1", new DocumentId("circuit-1"), "Flip-flop D"));

        panel.restartButton().doClick();

        assertThat(tasks.stopped).containsExactly("task-1");
        assertThat(panel.titleLabel().getText()).isEqualTo("Escuchando");
        assertThat(service.port()).hasValue(port);
        assertThat(panel.tasksSection().isVisible()).isFalse();
    }

    @Test
    void reiniciarWaitsWhileItStarts() {
        List<Runnable> serverWork = new ArrayList<>();
        AiServerService starting = new AiServerService(saved, new AutomationApi(new NoDocuments()), tasks,
                Runnable::run, Runnable::run, AiServerService.QueuedWork.NONE, SessionPort.none(), serverWork::add,
                Clock.systemUTC());
        AiPanel startingPanel = new AiPanel(starting, windowPreferences, proposals, copied::add);
        try {
            startingPanel.toggleButton().doClick();
            assertThat(startingPanel.titleLabel().getText()).isEqualTo("Encendiendo…");
            assertThat(startingPanel.toggleButton().getText()).as("a start can be cancelled").isEqualTo("Apagar");

            assertThat(startingPanel.restartButton().isEnabled()).as("nothing to restart yet").isFalse();
            assertThat(starting.restart()).isFalse();
            assertThat(serverWork).as("still the one start").hasSize(1);

            serverWork.removeFirst().run();
            assertThat(startingPanel.titleLabel().getText()).isEqualTo("Escuchando");
            assertThat(startingPanel.restartButton().isEnabled()).isTrue();
        } finally {
            serverWork.forEach(Runnable::run);
            startingPanel.dispose();
            starting.stop();
        }
    }

    @Test
    void aTakenPortSaysWhatToDoWithTheNextFreeOne() throws Exception {
        try (ServerSocket other = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            saved.setAiPort(other.getLocalPort());

            panel.toggleButton().doClick();

            assertThat(panel.titleLabel().getText()).isEqualTo("Error");
            assertThat(panel.helpLabel().isVisible()).isTrue();
            assertThat(panel.helpLabel().getText()).startsWith("Otro programa usa el puerto " + other.getLocalPort()
                    + ": usa el siguiente libre");
            assertThat(panel.nextFreePortButton().isVisible()).isTrue();
            assertThat(panel.restartButton().isEnabled()).as("try again").isTrue();
        }
    }

    @Test
    void anotherVsimSaysCloseItOrUseIt() throws Exception {
        RunningServer otherVsim = VsimMcpServer.start(new AutomationApi(new NoDocuments()),
                new McpServerSettings(0, "another-vsim-secret-value"));
        try {
            saved.setAiPort(otherVsim.port());

            panel.toggleButton().doClick();

            assertThat(panel.helpLabel().getText()).isEqualTo("Otro VSim escucha en el puerto " + otherVsim.port()
                    + ": ciérralo y vuelve a encender esta, o trabaja con ese VSim");
            assertThat(panel.nextFreePortButton().isVisible()).isFalse();
        } finally {
            otherVsim.stop();
        }
    }

    @Test
    void theConfigurationIsFoldedBelowAndUnfoldsWhenThePortMoved() throws Exception {
        assertThat(panel.configurationHeader().getText()).isEqualTo("Configuración");
        assertThat(panel.configurationSection().isVisible()).isFalse();
        assertThat(panel.portLabel().getText()).isEqualTo("Puerto " + port);

        panel.configurationHeader().doClick();
        assertThat(panel.configurationSection().isVisible()).isTrue();
        panel.configurationHeader().doClick();

        try (ServerSocket other = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            saved.setAiPort(other.getLocalPort());
            panel.toggleButton().doClick();
            panel.nextFreePortButton().doClick();

            assertThat(panel.configurationSection().isVisible()).as("the line to copy again").isTrue();
            assertThat(panel.portLabel().getText()).isEqualTo("Puerto " + saved.aiPort());
        }
    }

    @Test
    void theButtonsCarryTheirIconNextToTheirText() {
        panel.toggleButton().doClick();
        tasks.start(FakeAiTasks.task("task-1", new DocumentId("circuit-1"), "Flip-flop D"));

        assertThat(panel.toggleButton().getIcon()).isSameAs(AiIcons.icon(AiIcons.POWER));
        assertThat(panel.restartButton().getIcon()).isSameAs(AiIcons.icon(AiIcons.RESTART));
        assertThat(panel.copyButton().getIcon()).isSameAs(AiIcons.icon(AiIcons.CLIPBOARD));
        assertThat(panel.stopButton("task-1").getIcon()).isSameAs(AiIcons.icon(AiIcons.STOP));
        assertThat(panel.undoButton("task-1").getIcon()).isSameAs(AiIcons.icon(AiIcons.UNDO));
        assertThat(panel.stopButton("task-1").getText()).isEqualTo("Detener");
    }

    @Test
    void everyAiIconDrawsInLightAndDark() throws Exception {
        LookAndFeel previous = UIManager.getLookAndFeel();
        try {
            for (LookAndFeel theme : AppThemes.all()) {
                UIManager.setLookAndFeel(theme);
                for (String name : AiIcons.names()) {
                    Icon icon = Icons.get(name, Icons.MENU_SIZE);
                    BufferedImage image = new BufferedImage(Icons.MENU_SIZE, Icons.MENU_SIZE,
                            BufferedImage.TYPE_INT_ARGB);
                    Graphics2D graphics = image.createGraphics();
                    icon.paintIcon(null, graphics, 0, 0);
                    graphics.dispose();
                    assertThat(drawnPixels(image)).as("%s on %s", name, theme.getName()).isPositive();
                }
            }
        } finally {
            UIManager.setLookAndFeel(previous);
        }
    }

    @Test
    void aSecondWindowShowsTheSettingsTheAiTouchedInTheCircuitsWindow() {
        AppPreferences firstWindow = saved.layered();
        SettingsOverride circuits = SettingsOverride.of(firstWindow).orElseThrow();
        int listenersBefore = firstWindow.listenerCount();
        AiPanel secondWindowPanel = new AiPanel(service, windowPreferences, () -> Optional.of(circuits), proposals,
                copied::add);
        try {
            assertThat(secondWindowPanel.settingsSection().isVisible()).isFalse();

            circuits.set(EditorSetting.GRID_STYLE, "LINES");

            assertThat(secondWindowPanel.settingsSection().isVisible()).isTrue();
            assertThat(secondWindowPanel.settingTexts()).containsExactly("Cuadrícula: líneas");
            secondWindowPanel.dropSettingsButton().doClick();
            assertThat(circuits.changes()).isEmpty();
            assertThat(secondWindowPanel.settingsSection().isVisible()).isFalse();
        } finally {
            secondWindowPanel.dispose();
        }
        assertThat(firstWindow.listenerCount()).as("disposing stops hearing the other window")
                .isEqualTo(listenersBefore);
    }

    @Test
    void disposingTakesBackEveryListener() {
        int serviceListeners = service.listenerCount();
        int preferenceListeners = windowPreferences.listenerCount();
        AiPanel another = new AiPanel(service, windowPreferences, proposals, copied::add);

        another.dispose();

        assertThat(service.listenerCount()).isEqualTo(serviceListeners);
        assertThat(windowPreferences.listenerCount()).isEqualTo(preferenceListeners);
    }

    private static int drawnPixels(BufferedImage image) {
        int drawn = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    drawn++;
                }
            }
        }
        return drawn;
    }
}
