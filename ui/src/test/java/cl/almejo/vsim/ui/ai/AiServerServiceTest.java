package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.mcp.PortInUseException;
import cl.almejo.vsim.ui.app.AppPreferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static cl.almejo.vsim.ui.UiTestSupport.freePort;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiServerServiceTest {

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final AiServerService service = new AiServerService(preferences, new AutomationApi(new NoDocuments()));

    @AfterEach
    void stop() {
        service.stop();
    }

    @Test
    void stoppingRefusesTheQueuedWorkFirstAndStartingTakesWorkAgain() throws Exception {
        List<String> gate = new java.util.ArrayList<>();
        AiServerService gated = new AiServerService(preferences, new AutomationApi(new NoDocuments()),
                new FakeAiTasks(), Runnable::run, Runnable::run, new AiServerService.QueuedWork() {
                    @Override
                    public void refuse() {
                        gate.add("refuse");
                    }

                    @Override
                    public void accept() {
                        gate.add("accept");
                    }
                });
        preferences.setAiPort(freePort());

        gated.start();
        gated.stop();

        assertThat(gate).containsExactly("accept", "refuse");
    }

    @Test
    void detenerStopsTheTaskAtOnceOnTheEditorThreadWithoutWaitingForAnotherThread() {
        FakeAiTasks tasks = new FakeAiTasks();
        AiServerService neverInTheBackground = new AiServerService(preferences, new AutomationApi(new NoDocuments()),
                tasks, Runnable::run, runnable -> { });

        neverInTheBackground.stopTask("task-1");

        assertThat(tasks.stopped).containsExactly("task-1");
    }

    @Test
    void theFirstStartMakesASecretAndRemembersItForTheNext() throws Exception {
        preferences.setAiPort(freePort());

        service.start();
        String secret = preferences.aiSecret().orElseThrow();
        service.stop();
        service.start();

        assertThat(secret).hasSizeGreaterThanOrEqualTo(32);
        assertThat(preferences.aiSecret()).contains(secret);
    }

    @Test
    void itListensOnTheRememberedPortAndStoppingFreesIt() throws Exception {
        int port = freePort();
        preferences.setAiPort(port);

        service.start();
        assertThat(service.isRunning()).isTrue();
        assertThat(service.port()).hasValue(port);
        service.stop();

        assertThat(service.isRunning()).isFalse();
        assertThat(AiTestSupport.portIsFree(port)).isTrue();
    }

    @Test
    void aTakenPortIsReportedAndThePortIsNotChanged() throws Exception {
        try (ServerSocket other = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            preferences.setAiPort(other.getLocalPort());

            assertThatThrownBy(service::start).isInstanceOf(PortInUseException.class);

            assertThat(service.isRunning()).isFalse();
            assertThat(preferences.aiPort()).isEqualTo(other.getLocalPort());
        }
    }

    @Test
    void theClaudeCodeCommandNamesTheUrlAndTheSecretHeader() throws Exception {
        preferences.setAiPort(48123);
        preferences.setAiSecret("s3cret-s3cret-s3cret-s3cret");

        assertThat(service.claudeCodeCommand()).isEqualTo("claude mcp add --transport http --scope user vsim "
                + "http://127.0.0.1:48123/mcp --header \"Authorization: Bearer s3cret-s3cret-s3cret-s3cret\"");
    }

    @Test
    void theTemporarySwitchAcceptsOnFromThePropertyOrTheEnvironment() {
        assertThat(AiServerService.requestedAtLaunch("on", null)).isTrue();
        assertThat(AiServerService.requestedAtLaunch(null, "on")).isTrue();
        assertThat(AiServerService.requestedAtLaunch(null, null)).isFalse();
        assertThat(AiServerService.requestedAtLaunch("off", "on")).isFalse();
    }

    @Test
    void atLaunchItStaysOffUnlessStartingWithVsimIsOnAndThenPrintsNoSecret() throws Exception {
        preferences.setAiPort(freePort());
        ByteArrayOutputStream terminal = new ByteArrayOutputStream();
        // the port is bound off the EDT (IA.3); here that thread and the EDT run at once
        AiServerService atOnce = new AiServerService(preferences, new AutomationApi(new NoDocuments()),
                new FakeAiTasks(), Runnable::run, Runnable::run);
        try {
            atOnce.startAtLaunch(new PrintStream(terminal, true, StandardCharsets.UTF_8));
            assertThat(atOnce.isRunning()).isFalse();
            preferences.setAiStartWithVsim(true);
            atOnce.startAtLaunch(new PrintStream(terminal, true, StandardCharsets.UTF_8));

            assertThat(atOnce.isRunning()).isTrue();
            assertThat(atOnce.state()).isInstanceOf(AiServerState.Listening.class);
            assertThat(terminal.toString(StandardCharsets.UTF_8)).isEmpty();
        } finally {
            atOnce.stop();
        }
    }
}
