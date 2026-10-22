package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.automation.AutomationHost;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.EditorAccess;
import cl.almejo.vsim.automation.TaskInfo;
import cl.almejo.vsim.automation.UndoOutcome;
import cl.almejo.vsim.mcp.McpServerSettings;
import cl.almejo.vsim.mcp.RunningServer;
import cl.almejo.vsim.mcp.VsimMcpServer;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/** The server's states as the panel says them (spec §2, §9.1) and the tasks it follows, with a fake automation. */
class AiServerStatesTest {

    private static final DocumentId CIRCUIT = new DocumentId("circuit-1");

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final FakeAiTasks tasks = new FakeAiTasks();
    private final AiServerService service = new AiServerService(preferences, new AutomationApi(new NoDocuments()),
            tasks, Runnable::run, Runnable::run);
    private final List<String> heard = new ArrayList<>();

    @BeforeEach
    void spanish() throws IOException {
        Messages.initialize(Messages.SPANISH);
        preferences.setAiPort(freePort());
        service.addListener(new AiServerService.Listener() {
            @Override
            public void stateChanged() {
                heard.add(service.state().text());
            }
        });
    }

    @AfterEach
    void stop() {
        service.stop();
    }

    @Test
    void offListeningConnectedWorkingAndOffAgainInWords() {
        int port = preferences.aiPort();
        assertThat(service.state()).isEqualTo(new AiServerState.Off());
        assertThat(service.state().text()).isEqualTo("Apagada");

        service.activate();
        assertThat(service.state()).isEqualTo(new AiServerState.Listening(port));
        assertThat(service.state().text()).isEqualTo("Escuchando en 127.0.0.1:" + port);

        service.requestServed();
        assertThat(service.state().text()).isEqualTo("Claude Code conectado");

        TaskInfo task = FakeAiTasks.task("task-1", CIRCUIT, "Flip-flop D");
        tasks.start(task);
        assertThat(service.state()).isEqualTo(new AiServerState.Working(port, "Flip-flop D"));
        assertThat(service.state().text()).isEqualTo("IA trabajando: Flip-flop D");
        assertThat(service.runningTasks()).containsExactly(task);

        tasks.end(task, Set.of());
        assertThat(service.state()).isEqualTo(new AiServerState.Connected(port));

        service.deactivate();
        assertThat(service.state().text()).isEqualTo("Apagada");
        assertThat(service.isRunning()).isFalse();
        assertThat(heard).containsExactly("Encendiendo en 127.0.0.1:" + port + "…",
                "Escuchando en 127.0.0.1:" + port, "Claude Code conectado",
                "IA trabajando: Flip-flop D", "Claude Code conectado", "Apagada");
    }

    @Test
    void aPortHeldByAnotherProgramIsSaidAndOnlyTheUsersChoiceMovesIt() throws Exception {
        try (ServerSocket other = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            int taken = other.getLocalPort();
            preferences.setAiPort(taken);

            service.activate();

            assertThat(service.state()).isEqualTo(new AiServerState.PortInUse(taken));
            assertThat(service.state().text()).isEqualTo("El puerto " + taken + " está ocupado por otro programa");
            assertThat(service.state().isError()).isTrue();
            assertThat(preferences.aiPort()).as("VSim never changes the port by itself").isEqualTo(taken);
            assertThat(service.portChanged()).isFalse();

            assertThat(service.useNextFreePort()).isTrue();

            int chosen = preferences.aiPort();
            assertThat(chosen).isGreaterThan(taken).isLessThanOrEqualTo(taken + AiServerService.NEXT_FREE_PORT_RANGE);
            assertThat(service.state()).isEqualTo(new AiServerState.Listening(chosen));
            assertThat(service.portChanged()).isTrue();

            service.configurationCopied();
            assertThat(service.portChanged()).isFalse();
        }
    }

    @Test
    void aPortHeldByAnotherVsimSaysSo() throws Exception {
        RunningServer otherVsim = VsimMcpServer.start(new AutomationApi(new NoDocuments()),
                new McpServerSettings(0, "another-vsim-secret-value"));
        try {
            preferences.setAiPort(otherVsim.port());

            service.activate();

            assertThat(service.state()).isEqualTo(new AiServerState.OtherVsim(otherVsim.port()));
            assertThat(service.state().text()).isEqualTo("Otro VSim ya tiene la IA conectada; ciérralo o usa ese");
        } finally {
            otherVsim.stop();
        }
    }

    @Test
    void choosingAnotherPortClearsTheErrorAboutTheOldOne() throws Exception {
        try (ServerSocket other = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            preferences.setAiPort(other.getLocalPort());
            service.activate();

            preferences.setAiPort(freePort());

            assertThat(service.state()).isEqualTo(new AiServerState.Off());
        }
    }

    @Test
    void aNewSecretRestartsTheServerSoTheOldOneNoLongerWorks() throws Exception {
        service.activate();
        String oldSecret = preferences.aiSecret().orElseThrow();

        AiServerService.regenerateSecret(preferences);

        String newSecret = preferences.aiSecret().orElseThrow();
        assertThat(newSecret).isNotEqualTo(oldSecret);
        assertThat(service.isRunning()).isTrue();
        assertThat(statusWith(oldSecret)).isEqualTo(401);
        assertThat(statusWith(newSecret)).isNotEqualTo(401);
    }

    @Test
    void aPortChangedInThePreferencesWhileListeningMovesTheServerThere() throws Exception {
        service.activate();
        int next = freePort();

        preferences.setAiPort(next);

        assertThat(service.state()).isEqualTo(new AiServerState.Listening(next));
    }

    @Test
    void stopAndUndoGoToTheAutomationOffTheEditorThreadAndTheOutcomeComesBack() {
        tasks.nextOutcome = new UndoOutcome(2, true);
        List<UndoOutcome> outcomes = new ArrayList<>();

        service.stopTask("task-7");
        service.undoTask("task-7", outcomes::add);

        assertThat(tasks.stopped).containsExactly("task-7");
        assertThat(tasks.undone).containsExactly("task-7");
        assertThat(outcomes).containsExactly(new UndoOutcome(2, true));
    }

    @Test
    void turningItOffStopsTheRunningTasks() {
        service.activate();
        tasks.start(FakeAiTasks.task("task-1", CIRCUIT, "Sumador"));

        service.deactivate();

        assertThat(tasks.stopped).containsExactly("task-1");
        assertThat(service.runningTasks()).isEmpty();
    }

    @Test
    void theHandTestSwitchTurnsActivarOnAndPrintsTheCommand() throws Exception {
        assertThat(AiServerService.requestedAtLaunch("on", null)).isTrue();
        preferences.setAiStartWithVsim(true);
        java.io.ByteArrayOutputStream terminal = new java.io.ByteArrayOutputStream();

        service.startAtLaunch(new java.io.PrintStream(terminal, true, java.nio.charset.StandardCharsets.UTF_8));

        assertThat(service.state()).isInstanceOf(AiServerState.Listening.class);
    }

    private int statusWith(String secret) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + preferences.aiPort() + "/mcp"))
                .header("Authorization", "Bearer " + secret)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket probe = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            return probe.getLocalPort();
        }
    }

    private static final class NoDocuments implements AutomationHost {
        @Override
        public <T> CompletableFuture<T> onEditorThread(Function<EditorAccess, T> work) {
            return CompletableFuture.completedFuture(work.apply(List::of));
        }
    }
}
