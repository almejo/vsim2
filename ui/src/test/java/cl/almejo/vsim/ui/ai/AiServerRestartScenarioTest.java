package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.automation.TaskInfo;
import cl.almejo.vsim.automation.edit.TaskStage;
import cl.almejo.vsim.automation.present.PresentationListener;
import cl.almejo.vsim.automation.present.PresentationSettings;
import cl.almejo.vsim.automation.present.StepPreview;
import cl.almejo.vsim.automation.present.StepStatus;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static cl.almejo.vsim.ui.UiTestSupport.freePort;
import static cl.almejo.vsim.ui.UiTestSupport.onEditor;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Review focus 3: «Reiniciar» and «Apagar» while the AI's task runs and its step waits for «Siguiente» as a ghost.
 * The real server of the panel ({@link AiServerService}) over the automation API and the editor's host; an MCP client
 * over HTTP plays the AI. The task ends as by «Detener», the ghost goes, and the server is off or listening again.
 * Also the port for one session (IA.3, review focus 2): the same story on a port from the command line.
 */
class AiServerRestartScenarioTest {

    private static final String REMEMBERED_SECRET = "the-remembered-secret-for-this-test";

    private final List<OpenDocument> open = new CopyOnWriteArrayList<>();
    private final AppPreferences preferences = AppPreferences.inMemory();
    private final EditorAutomationHost host = new EditorAutomationHost(() -> List.copyOf(open),
            document -> List.of(), new PresentingWindows(), new PreferenceProposals(preferences));
    private final AutomationApi api = new AutomationApi(host);
    private final ExecutorService serverThread = Executors.newSingleThreadExecutor();
    private final BlockingQueue<String> heard = new LinkedBlockingQueue<>();
    private final List<String> runningWhenChanged = new CopyOnWriteArrayList<>();
    private AiServerService service;
    private Runnable stopListening;
    private OpenDocument circuit;
    private McpSyncClient client;
    private String document;
    private int port;

    @BeforeEach
    void open() {
        Messages.initialize(Messages.SPANISH);
        CircuitDocument empty = new CircuitDocument();
        empty.setName("Luces");
        circuit = new OpenDocument(empty, StandardComponentTypes.registry(), null, false);
        open.add(circuit);
        stopListening = api.addPresentationListener(new Heard());
    }

    /** The panel's server on {@code sessionPort}, or on the remembered port when none was given, switched on. */
    private void turnOn(SessionPort sessionPort) throws Exception {
        service = new AiServerService(preferences, api, AiTasks.of(api), SwingUtilities::invokeLater, serverThread,
                new AiServerService.QueuedWork() {
                    @Override
                    public void refuse() {
                        host.refuseQueuedWork();
                    }

                    @Override
                    public void accept() {
                        host.acceptQueuedWork();
                    }
                }, sessionPort);
        // what Application does when the server is not running: every wait for «Siguiente» ends
        service.addListener(new AiServerService.Listener() {
            @Override
            public void stateChanged() {
                runningWhenChanged.add(service.state().getClass().getSimpleName());
                if (!service.isRunning()) {
                    api.stopPresenting();
                }
            }
        });
        onEditor(() -> {
            service.activate();
            return null;
        });
        awaitState(AiServerState.Listening.class);
        client = newClient();
        document = (String) list(call("vsim_list_documents", Map.of()), "documents").getFirst().get("id");
    }

    /** The remembered port: free, as when VSim starts as usual. */
    private void turnOnOnTheRememberedPort() throws Exception {
        port = freePort();
        preferences.setAiPort(port);
        turnOn(SessionPort.none());
    }

    @AfterEach
    void disconnect() throws Exception {
        if (client != null) {
            client.close();
        }
        if (service != null) {
            onEditor(() -> {
                service.stop();
                return null;
            });
        }
        stopListening.run();
        serverThread.shutdownNow();
        open.forEach(OpenDocument::close);
    }

    @Test
    void reiniciarWithAGhostWaitingStopsTheTaskDropsTheGhostAndListensAgainOnTheSamePort() throws Exception {
        turnOnOnTheRememberedPort();
        String task = aStepWaitsForSiguiente();

        boolean restarted = onEditor(service::restart);

        assertThat(restarted).isTrue();
        awaitHeard("stepDiscarded STOPPED");
        awaitState(AiServerState.Listening.class);
        assertThat(onEditor(service::runningTasks)).as("the task ended as by «Detener»").isEmpty();
        assertThat(onEditor(() -> circuit.document().components())).as("the ghost was never applied").isEmpty();
        assertThat(onEditor(service::port)).hasValue(port);
        assertThat(runningWhenChanged).contains("Starting");
        client.close();
        client = newClient(); // the old session went with the old server
        assertThat(call("vsim_await_step", Map.of("taskId", task))).containsEntry("status", "STOPPED");
    }

    @Test
    void apagarWithAGhostWaitingStopsTheTaskAndDropsTheGhost() throws Exception {
        turnOnOnTheRememberedPort();
        aStepWaitsForSiguiente();

        onEditor(() -> {
            service.deactivate();
            return null;
        });

        awaitHeard("stepDiscarded STOPPED");
        assertThat(onEditor(service::state)).isEqualTo(new AiServerState.Off());
        assertThat(onEditor(service::runningTasks)).isEmpty();
        assertThat(onEditor(() -> circuit.document().components())).isEmpty();
        // the server stops on its own thread (IA.3 review I1): the port is free once it did
        serverThread.submit(() -> null).get(10, TimeUnit.SECONDS);
        try (ServerSocket free = new ServerSocket(port, 50, InetAddress.getLoopbackAddress())) {
            assertThat(free.getLocalPort()).as("the port is free again").isEqualTo(port);
        }
    }

    /**
     * IA.3 review I1: «Apagar» while the AI long-polls for «Siguiente»: the wait answers STOPPED (not a broken
     * connection) and the editor thread never waits for the server to stop (it took 2.5 s before).
     */
    @Test
    void apagarWhileTheAiWaitsForSiguienteAnswersStoppedAndNeverHoldsTheEditor() throws Exception {
        turnOnOnTheRememberedPort();
        String task = aStepWaitsForSiguiente();
        java.util.concurrent.CompletableFuture<Map<String, Object>> waiting = java.util.concurrent.CompletableFuture
                .supplyAsync(() -> call("vsim_await_step", Map.of("taskId", task)));
        awaitStepWaits(1);
        assertThat(waiting).as("the AI waits for «Siguiente»").isNotDone();

        long tookMillis = onEditor(() -> {
            long before = System.nanoTime();
            service.deactivate();
            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before);
        });

        assertThat(tookMillis).as("«Apagar» on the editor thread").isLessThan(500);
        assertThat(waiting.get(10, TimeUnit.SECONDS)).containsEntry("status", "STOPPED");
        assertThat(onEditor(service::state)).isEqualTo(new AiServerState.Off());
    }

    @Test
    void reiniciarNeverHoldsTheEditorAndListensAgainOnceTheOldServerFreedThePort() throws Exception {
        turnOnOnTheRememberedPort();
        String task = aStepWaitsForSiguiente();
        java.util.concurrent.CompletableFuture<Map<String, Object>> waiting = java.util.concurrent.CompletableFuture
                .supplyAsync(() -> call("vsim_await_step", Map.of("taskId", task)));
        awaitStepWaits(1);
        assertThat(waiting).isNotDone();

        long tookMillis = onEditor(() -> {
            long before = System.nanoTime();
            service.restart();
            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before);
        });

        assertThat(tookMillis).as("«Reiniciar» on the editor thread").isLessThan(500);
        assertThat(waiting.get(10, TimeUnit.SECONDS)).containsEntry("status", "STOPPED");
        awaitState(AiServerState.Listening.class);
        assertThat(onEditor(service::port)).hasValue(port);
    }

    // --- IA.3: the port for one session, from the command line ---

    @Test
    void aSessionPortServesTheAiThroughReiniciarAndApagarAndThePreferencesNeverChange() throws Exception {
        int remembered = freePort();
        preferences.setAiPort(remembered);
        preferences.setAiSecret(REMEMBERED_SECRET);
        port = freePort();
        assertThat(port).isNotEqualTo(remembered);
        // what VSim reads at launch: -Dvsim.ai.port=<n> (it wins over VSIM_AI_PORT)
        SessionPort fromTheCommandLine = SessionPort.fromLaunch(
                name -> "vsim.ai.port".equals(name) ? Integer.toString(port) : null,
                name -> "VSIM_AI_PORT".equals(name) ? Integer.toString(remembered) : null);
        List<String> rememberedWhileRunning = new CopyOnWriteArrayList<>();
        preferences.addListener(() -> rememberedWhileRunning.add(preferences.aiPort() + " "
                + preferences.aiSecret().orElse("")));

        turnOn(fromTheCommandLine);

        assertThat(onEditor(service::isSessionPort)).isTrue();
        assertThat(onEditor(service::port)).hasValue(port);
        String command = onEditor(service::claudeCodeCommand);
        assertThat(command).contains(" vsim-" + port + " ",
                "http://127.0.0.1:" + port + "/mcp", "Bearer " + REMEMBERED_SECRET);
        String task = aStepWaitsForSiguiente();
        assertThat(onEditor(service::restart)).isTrue();
        awaitHeard("stepDiscarded STOPPED");
        awaitState(AiServerState.Listening.class);
        assertThat(onEditor(service::port)).as("Reiniciar keeps the session port").hasValue(port);
        client.close();
        client = newClient();
        assertThat(call("vsim_await_step", Map.of("taskId", task))).containsEntry("status", "STOPPED");
        onEditor(() -> {
            service.deactivate();
            return null;
        });
        assertThat(onEditor(service::state)).isEqualTo(new AiServerState.Off());

        assertThat(preferences.aiPort()).as("the remembered port").isEqualTo(remembered);
        assertThat(preferences.aiSecret()).contains(REMEMBERED_SECRET);
        assertThat(rememberedWhileRunning).as("whatever else changed («Presentar»), never the port or the secret")
                .allMatch(seen -> seen.equals(remembered + " " + REMEMBERED_SECRET));
    }

    /** «Presentar», «Paso a paso»: the AI's first step is a ghost waiting for «Siguiente». */
    private String aStepWaitsForSiguiente() throws Exception {
        call("vsim_set_presentation", Map.of("document", document, "presenting", true));
        String task = (String) call("vsim_begin_task", Map.of("document", document, "title", "Dos luces",
                "stages", List.of(Map.of("title", "Luces")))).get("taskId");
        awaitRunningTasks(1);
        long version = ((Number) call("vsim_read_circuit", Map.of("document", document)).get("version"))
                .longValue();
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("document", document);
        arguments.put("version", version);
        arguments.put("operationId", "operation-1");
        arguments.put("taskId", task);
        arguments.put("stage", "Luces");
        arguments.put("step", "Pongo la primera");
        arguments.put("text", "Pongo la primera");
        arguments.put("operations", List.of(Map.of("op", "PlacePart", "alias", "one", "typeId", "led", "x", 0,
                "y", 0, "label", "")));
        assertThat(call("vsim_edit", arguments)).containsEntry("status", "PROPOSED");
        awaitHeard("stepProposed Pongo la primera");
        return task;
    }

    private McpSyncClient newClient() {
        String secret = preferences.aiSecret().orElseThrow();
        McpSyncClient created = McpClient.sync(HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + port)
                        .endpoint("/mcp")
                        .requestBuilder(HttpRequest.newBuilder().header("Authorization", "Bearer " + secret))
                        .build())
                .requestTimeout(Duration.ofSeconds(60))
                .build();
        created.initialize();
        return created;
    }

    private void awaitState(Class<? extends AiServerState> wanted) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (!wanted.isInstance(onEditor(service::state))) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("never " + wanted.getSimpleName() + ": " + onEditor(service::state));
            }
            Thread.onSpinWait();
        }
    }

    private void awaitRunningTasks(int count) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (onEditor(service::runningTasks).size() != count) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("never " + count + " running tasks");
            }
            Thread.onSpinWait();
        }
    }

    /** Until VSim has {@code count} {@code vsim_await_step} calls waiting (the request reached it, Pulido T1). */
    private void awaitStepWaits(int count) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (api.stepWaits() != count) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("never " + count + " waits for «Siguiente»");
            }
            Thread.onSpinWait();
        }
    }

    private void awaitHeard(String wanted) throws InterruptedException {
        List<String> seen = new ArrayList<>();
        while (true) {
            String event = heard.poll(20, TimeUnit.SECONDS);
            if (event == null) {
                throw new AssertionError("never heard " + wanted + ", only " + seen);
            }
            seen.add(event);
            if (event.equals(wanted)) {
                return;
            }
        }
    }

    private Map<String, Object> call(String name, Map<String, Object> arguments) {
        McpSchema.CallToolResult result = client.callTool(McpSchema.CallToolRequest.builder(name)
                .arguments(arguments).build());
        assertThat(result.isError()).as("%s answered %s", name, result.structuredContent()).isNotEqualTo(true);
        return structured(result);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> structured(McpSchema.CallToolResult result) {
        return (Map<String, Object>) result.structuredContent();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Map<String, Object> object, String key) {
        return (List<Map<String, Object>>) object.get(key);
    }

    private final class Heard implements PresentationListener {

        @Override
        public void planChanged(TaskInfo task, List<TaskStage> stages, int currentStage, int currentStep) {
        }

        @Override
        public void stepProposed(StepPreview preview) {
            heard.add("stepProposed " + preview.text());
        }

        @Override
        public void stepApplied(StepPreview preview) {
            heard.add("stepApplied " + preview.text());
        }

        @Override
        public void stepDiscarded(String taskId, StepStatus why) {
            heard.add("stepDiscarded " + why);
        }

        @Override
        public void stageChecked(String taskId, int stageIndex, boolean passed, String summary) {
        }
    }

    /** «Presentar» lives in the test's preferences, as in a window's. */
    private final class PresentingWindows implements EditorWindows {

        @Override
        public OpenDocument openNewCircuit(String name) {
            throw new UnsupportedOperationException("no new windows in this test");
        }

        @Override
        public Optional<SettingsOverride> settingsOverride(OpenDocument openDocument) {
            return Optional.empty();
        }

        @Override
        public PresentationSettings presentationSettings(OpenDocument openDocument) {
            return PresentationPreferences.read(preferences);
        }

        @Override
        public void setPresenting(OpenDocument openDocument, boolean presenting) {
            preferences.setAiPresenting(presenting);
        }
    }
}
