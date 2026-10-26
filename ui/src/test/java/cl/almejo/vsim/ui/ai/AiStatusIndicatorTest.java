package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.theme.FlatLafInstaller;
import cl.almejo.vsim.ui.theme.ReadableColors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import java.awt.Cursor;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static cl.almejo.vsim.ui.UiTestSupport.freePort;
import static org.assertj.core.api.Assertions.assertThat;

/** The AI's state in every window's status bar (Pulido T3): «IA: escuchando», a coloured dot, a click to the panel. */
class AiStatusIndicatorTest {

    private final AppPreferences saved = AppPreferences.inMemory();
    private final FakeAiTasks tasks = new FakeAiTasks();
    private final AiServerService service = new AiServerService(saved, new AutomationApi(new NoDocuments()), tasks,
            Runnable::run, Runnable::run, AiServerService.QueuedWork.NONE, SessionPort.none(), Runnable::run,
            Clock.systemUTC());
    private final AtomicInteger panelsOpened = new AtomicInteger();
    private AiStatusIndicator indicator;
    private int port;

    @BeforeEach
    void create() throws IOException {
        Messages.initialize(Messages.SPANISH);
        port = freePort();
        saved.setAiPort(port);
        indicator = new AiStatusIndicator(service, panelsOpened::incrementAndGet);
    }

    @AfterEach
    void close() {
        indicator.dispose();
        service.stop();
    }

    @Test
    void eachStateIsSaidInOneWordAfterIa() {
        assertThat(AiStatusIndicator.text(new AiServerState.Off())).isEqualTo("IA: apagada");
        assertThat(AiStatusIndicator.text(new AiServerState.Starting(port))).isEqualTo("IA: encendiendo…");
        assertThat(AiStatusIndicator.text(new AiServerState.Listening(port))).isEqualTo("IA: escuchando");
        assertThat(AiStatusIndicator.text(new AiServerState.Connected(port))).isEqualTo("IA: conectada");
        assertThat(AiStatusIndicator.text(new AiServerState.Working(port, "Flip-flop D")))
                .isEqualTo("IA: trabajando");
        assertThat(AiStatusIndicator.text(new AiServerState.PortInUse(port))).isEqualTo("IA: error");
        assertThat(AiStatusIndicator.text(new AiServerState.OtherVsim(port))).isEqualTo("IA: error");
        assertThat(AiStatusIndicator.text(new AiServerState.Failed(port))).isEqualTo("IA: error");
    }

    @Test
    void eachStateHasTheColourRoleOfThePanel() {
        assertThat(AiColors.role(new AiServerState.Off())).isEqualTo(AiColors.Role.MUTED);
        assertThat(AiColors.role(new AiServerState.Starting(port))).isEqualTo(AiColors.Role.MUTED);
        assertThat(AiColors.role(new AiServerState.Listening(port))).isEqualTo(AiColors.Role.TEXT);
        assertThat(AiColors.role(new AiServerState.Connected(port))).isEqualTo(AiColors.Role.SUCCESS);
        assertThat(AiColors.role(new AiServerState.Working(port, "Flip-flop D"))).isEqualTo(AiColors.Role.SUCCESS);
        assertThat(AiColors.role(new AiServerState.PortInUse(port))).isEqualTo(AiColors.Role.ERROR);
        assertThat(AiColors.role(new AiServerState.OtherVsim(port))).isEqualTo(AiColors.Role.ERROR);
        assertThat(AiColors.role(new AiServerState.Failed(port))).isEqualTo(AiColors.Role.ERROR);
    }

    @Test
    void itFollowsTheServerLiveWithItsAddressInTheTooltip() {
        assertThat(indicator.getText()).isEqualTo("IA: apagada");
        assertThat(indicator.getToolTipText()).contains("127.0.0.1:" + port).endsWith("Clic: abrir IA (MCP)…");
        assertThat(indicator.getForeground()).isEqualTo(AiColors.muted());

        service.activate();
        assertThat(indicator.getText()).isEqualTo("IA: escuchando");
        assertThat(indicator.getToolTipText()).startsWith("Escuchando en 127.0.0.1:" + port);
        assertThat(indicator.getForeground()).isEqualTo(AiColors.foreground());

        service.requestServed();
        assertThat(indicator.getText()).isEqualTo("IA: conectada");
        assertThat(indicator.getToolTipText()).startsWith("Claude Code conectado · 127.0.0.1:" + port);
        assertThat(indicator.getForeground()).isEqualTo(AiColors.success());

        tasks.start(FakeAiTasks.task("task-1", new DocumentId("circuit-1"), "Flip-flop D"));
        assertThat(indicator.getText()).isEqualTo("IA: trabajando");
        assertThat(indicator.getToolTipText()).startsWith("IA trabajando: Flip-flop D · 127.0.0.1:" + port);

        service.deactivate();
        assertThat(indicator.getText()).isEqualTo("IA: apagada");
    }

    @Test
    void anErrorIsSaidInTheReadableRed() throws Exception {
        try (ServerSocket other = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            saved.setAiPort(other.getLocalPort());

            service.activate();

            assertThat(indicator.getText()).isEqualTo("IA: error");
            assertThat(indicator.getToolTipText())
                    .startsWith("El puerto " + other.getLocalPort() + " está ocupado por otro programa");
            assertThat(indicator.getForeground()).isEqualTo(ReadableColors.errorText());
        }
    }

    @Test
    void aClickOpensTheAiPanelOnlyWithThePrimaryButton() {
        assertThat(indicator.getCursor()).isEqualTo(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        click(MouseEvent.BUTTON3);
        assertThat(panelsOpened).hasValue(0);

        click(MouseEvent.BUTTON1);
        assertThat(panelsOpened).hasValue(1);
    }

    @Test
    void disposingStopsFollowingTheServer() {
        int before = service.listenerCount();
        AiStatusIndicator another = new AiStatusIndicator(service, () -> { });
        assertThat(service.listenerCount()).isEqualTo(before + 1);

        another.dispose();

        assertThat(service.listenerCount()).isEqualTo(before);
    }

    @Test
    void everyStateReadsOnTheStatusBarInLightAndDark() throws Exception {
        LookAndFeel previous = UIManager.getLookAndFeel();
        try {
            for (boolean dark : List.of(false, true)) {
                UIManager.setLookAndFeel(FlatLafInstaller.lookAndFeel("linux", dark));
                for (AiServerState state : List.of(new AiServerState.Off(), new AiServerState.Listening(port),
                        new AiServerState.Connected(port), new AiServerState.Failed(port))) {
                    assertThat(ReadableColors.contrast(AiColors.of(state), ReadableColors.panelBackground()))
                            .as("%s on the %s theme", state, dark ? "dark" : "light")
                            .isGreaterThanOrEqualTo(ReadableColors.MINIMUM_TEXT_CONTRAST);
                }
            }
        } finally {
            UIManager.setLookAndFeel(previous);
        }
    }

    private void click(int button) {
        MouseEvent click = new MouseEvent(indicator, MouseEvent.MOUSE_CLICKED, 0, 0, 2, 2, 1, false, button);
        for (MouseListener listener : indicator.getMouseListeners()) {
            listener.mouseClicked(click);
        }
    }
}
