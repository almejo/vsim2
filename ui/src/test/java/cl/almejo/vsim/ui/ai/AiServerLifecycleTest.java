package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static cl.almejo.vsim.ui.UiTestSupport.freePort;
import static org.assertj.core.api.Assertions.assertThat;

/** «Encender» off the EDT, «Apagar» during a start, «Reiniciar» and what the panel's header says (IA.3). */
class AiServerLifecycleTest {

    private static final Instant STARTED = Instant.parse("2026-10-22T14:03:00Z");

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final FakeAiTasks tasks = new FakeAiTasks();
    /** The server thread, run by hand: the test is the EDT. */
    private final Deque<Runnable> serverWork = new ArrayDeque<>();
    private final List<String> heard = new ArrayList<>();
    private AiServerService service;
    private int port;

    @BeforeEach
    void create() throws IOException {
        Messages.initialize(Messages.SPANISH);
        port = freePort();
        preferences.setAiPort(port);
        service = serviceOn(serverWork::add);
    }

    private AiServerService serviceOn(java.util.concurrent.Executor serverThread) {
        AiServerService created = new AiServerService(preferences, new AutomationApi(new NoDocuments()), tasks,
                Runnable::run, Runnable::run, AiServerService.QueuedWork.NONE, SessionPort.none(), serverThread,
                Clock.fixed(STARTED, ZoneOffset.UTC));
        created.addListener(new AiServerService.Listener() {
            @Override
            public void stateChanged() {
                heard.add(created.state().title());
            }
        });
        return created;
    }

    @AfterEach
    void stop() {
        runServerWork();
        service.stop();
        runServerWork();
    }

    private void runServerWork() {
        while (!serverWork.isEmpty()) {
            serverWork.removeFirst().run();
        }
    }

    @Test
    void encenderBindsOffTheEditorThreadAndSaysEncendiendoMeanwhile() {
        service.activate();

        assertThat(service.state()).isEqualTo(new AiServerState.Starting(port));
        assertThat(service.state().title()).isEqualTo("Encendiendo…");
        assertThat(service.isRunning()).isFalse();
        assertThat(serverWork).as("the bind waits for the server thread").hasSize(1);

        runServerWork();

        assertThat(service.state()).isEqualTo(new AiServerState.Listening(port));
        assertThat(heard).containsExactly("Encendiendo…", "Escuchando");
    }

    @Test
    void aSlowProbeOfATakenPortNeverHoldsTheCaller() throws Exception {
        ExecutorService realServerThread = Executors.newSingleThreadExecutor();
        // takes connections and never answers: the probe of «another VSim?» waits for its whole timeout
        try (ServerSocket silent = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            Thread acceptor = Thread.ofVirtual().start(() -> {
                List<Socket> held = new ArrayList<>();
                try {
                    while (!silent.isClosed()) {
                        held.add(silent.accept()); // read nothing, answer nothing
                    }
                } catch (IOException closed) {
                    held.forEach(socket -> {
                        try {
                            socket.close();
                        } catch (IOException exception) {
                            // closing anyway
                        }
                    });
                }
            });
            preferences.setAiPort(silent.getLocalPort());
            List<Runnable> editorWork = new java.util.concurrent.CopyOnWriteArrayList<>();
            AiServerService slow = new AiServerService(preferences, new AutomationApi(new NoDocuments()), tasks,
                    editorWork::add, Runnable::run, AiServerService.QueuedWork.NONE, SessionPort.none(),
                    realServerThread, Clock.systemUTC());

            long before = System.nanoTime();
            slow.activate();
            long tookMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before);

            assertThat(tookMillis).as("«Encender» returns at once").isLessThan(300);
            assertThat(slow.state()).isEqualTo(new AiServerState.Starting(silent.getLocalPort()));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (editorWork.isEmpty() && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            editorWork.forEach(Runnable::run);
            assertThat(slow.state()).isEqualTo(new AiServerState.PortInUse(silent.getLocalPort()));
            silent.close();
            acceptor.join(2000);
        } finally {
            realServerThread.shutdownNow();
        }
    }

    @Test
    void apagarWhileStartingDropsTheStartAndFreesThePort() throws Exception {
        service.activate();

        service.deactivate();
        assertThat(service.state()).isEqualTo(new AiServerState.Off());
        runServerWork(); // the bind finishes, sees it was overtaken and stops what it started

        assertThat(service.state()).isEqualTo(new AiServerState.Off());
        assertThat(service.isRunning()).isFalse();
        try (ServerSocket free = new ServerSocket(port, 50, InetAddress.getLoopbackAddress())) {
            assertThat(free.getLocalPort()).isEqualTo(port);
        }
    }

    @Test
    void reiniciarStopsTheRunningTasksAndListensAgainOnTheSamePort() {
        service.activate();
        runServerWork();
        tasks.start(FakeAiTasks.task("task-1", new DocumentId("circuit-1"), "Sumador"));
        heard.clear();

        assertThat(service.restart()).isTrue();
        runServerWork();

        assertThat(tasks.stopped).as("as by «Detener»").containsExactly("task-1");
        assertThat(service.runningTasks()).isEmpty();
        assertThat(service.state()).isEqualTo(new AiServerState.Listening(port));
        assertThat(heard).as("off between, so every wait for «Siguiente» ends")
                .containsExactly("Apagada", "Encendiendo…", "Escuchando");
    }

    @Test
    void reiniciarIsRefusedWhileStarting() {
        service.activate();

        assertThat(service.restart()).isFalse();
        assertThat(serverWork).hasSize(1);
        runServerWork();
        assertThat(service.state()).isEqualTo(new AiServerState.Listening(port));
    }

    @Test
    void sinceWhenAndTheRequestsServedAreCountedWhileItRuns() {
        assertThat(service.startedAt()).isEmpty();
        service.activate();
        runServerWork();

        service.requestServed();
        service.requestServed();
        service.requestServed();

        assertThat(service.startedAt()).contains(STARTED);
        assertThat(service.requestsServed()).isEqualTo(3);
        assertThat(service.state()).isEqualTo(new AiServerState.Connected(port));
        service.restart();
        runServerWork();
        assertThat(service.requestsServed()).as("a new start counts again").isZero();
    }

    @Test
    void everyStateHasItsTitleWord() {
        assertThat(new AiServerState.Off().title()).isEqualTo("Apagada");
        assertThat(new AiServerState.Starting(1).title()).isEqualTo("Encendiendo…");
        assertThat(new AiServerState.Listening(1).title()).isEqualTo("Escuchando");
        assertThat(new AiServerState.Connected(1).title()).isEqualTo("Conectado");
        assertThat(new AiServerState.Working(1, "Sumador").title()).isEqualTo("Trabajando");
        assertThat(new AiServerState.PortInUse(1).title()).isEqualTo("Error");
        assertThat(new AiServerState.OtherVsim(1).title()).isEqualTo("Error");
        assertThat(new AiServerState.Failed(1).title()).isEqualTo("Error");
        assertThat(new AiServerState.Failed(47100).text()).isEqualTo("No pude encender la IA en el puerto 47100");
        assertThat(new AiServerState.Failed(1).isError()).isTrue();
        assertThat(new AiServerState.Starting(1).isListening()).isFalse();
    }

    /** IA.3 review I1: the tasks stop and the waits end on the EDT; the server stops on its own thread. */
    @Test
    void apagarEndsTheWaitsFirstAndLeavesTheServersStopToItsThread() throws Exception {
        service.activate();
        runServerWork();
        tasks.start(FakeAiTasks.task("task-1", new cl.almejo.vsim.automation.DocumentId("circuit-1"), "Luces"));

        service.deactivate();

        assertThat(tasks.stopped).containsExactly("task-1");
        assertThat(tasks.waitsEnded).containsExactly("after [task-1]");
        assertThat(service.state()).isEqualTo(new AiServerState.Off());
        assertThat(serverWork).as("the server's stop waits for the server thread").hasSize(1);
        assertThat(portIsFree()).isFalse();

        runServerWork();
        assertThat(portIsFree()).isTrue();
    }

    /**
     * Pulido T1: «Apagar» ended the waits for «Siguiente» for good; «Encender» lets them wait again before the port
     * answers (review M2: the AI may call as soon as it does, before the EDT hears that the server listens).
     */
    @Test
    void theWaitsForSiguienteWaitAgainFromEncenderBeforeThePortAnswers() {
        service.activate();
        assertThat(tasks.waitsAccepted).as("before the bind").isEqualTo(1);
        runServerWork();
        assertThat(tasks.waitsAccepted).isEqualTo(1);
        assertThat(tasks.waitsEnded).isEmpty();

        service.deactivate();
        runServerWork();
        assertThat(tasks.waitsAccepted).as("Apagar").isEqualTo(1);
        assertThat(tasks.waitsEnded).hasSize(1);

        assertThat(service.restart()).isTrue();
        runServerWork();
        assertThat(tasks.waitsAccepted).as("Reiniciar listens again").isEqualTo(2);
    }

    @Test
    void apagarWhileStartingEndsTheWaitsAgain() {
        service.activate();

        service.deactivate();
        runServerWork();

        assertThat(tasks.waitsEnded).hasSize(1);
    }

    @Test
    void aStartThatFailsEndsTheWaitsAgain() throws Exception {
        try (ServerSocket other = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            preferences.setAiPort(other.getLocalPort());
            service.activate();

            runServerWork();

            assertThat(service.state()).isEqualTo(new AiServerState.PortInUse(other.getLocalPort()));
            assertThat(tasks.waitsEnded).hasSize(1);
        }
    }

    /** Review M1: quitting never holds the EDT for the server's stop; VSim ends once the port is free. */
    @Test
    void quittingStopsTheServerOnItsThreadAndEndsAfterwards() {
        service.activate();
        runServerWork();
        heard.clear();
        List<String> ended = new ArrayList<>();

        service.quit(() -> ended.add("exit"));

        assertThat(service.isRunning()).isFalse();
        assertThat(tasks.waitsEnded).as("the AI's waits end at once").hasSize(1);
        assertThat(heard).as("off: the application withdraws the proposals").containsExactly("Apagada");
        assertThat(ended).isEmpty();
        assertThat(portIsFree()).as("the stop waits for the server thread").isFalse();

        runServerWork();
        assertThat(portIsFree()).isTrue();
        assertThat(ended).containsExactly("exit");
    }

    @Test
    void quittingWithTheServerOffEndsAfterTheServerThreadsWork() {
        List<String> ended = new ArrayList<>();

        service.quit(() -> ended.add("exit"));
        runServerWork();

        assertThat(ended).containsExactly("exit");
    }

    private boolean portIsFree() {
        try (ServerSocket probe = new ServerSocket(port, 50, InetAddress.getLoopbackAddress())) {
            return probe.getLocalPort() == port;
        } catch (IOException exception) {
            return false;
        }
    }
}
