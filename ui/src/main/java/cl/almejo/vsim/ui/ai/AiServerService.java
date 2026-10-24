package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.automation.AutomationListener;
import cl.almejo.vsim.automation.TaskInfo;
import cl.almejo.vsim.automation.TaskStopReason;
import cl.almejo.vsim.automation.UndoOutcome;
import cl.almejo.vsim.automation.edit.TaskStage;
import cl.almejo.vsim.automation.present.Narration;
import cl.almejo.vsim.automation.present.PresentationListener;
import cl.almejo.vsim.automation.present.Question;
import cl.almejo.vsim.automation.present.QuestionAnswer;
import cl.almejo.vsim.automation.present.StepPreview;
import cl.almejo.vsim.automation.present.StepStatus;
import cl.almejo.vsim.mcp.McpServerSettings;
import cl.almejo.vsim.mcp.PortInUseException;
import cl.almejo.vsim.mcp.RunningServer;
import cl.almejo.vsim.mcp.VsimMcpServer;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * The AI server of this VSim (spec §9, §9.1): one per process, an application service, off until «Encender». It listens
 * on the remembered port with the remembered secret, made the first time, and never changes the port by itself: a
 * taken port is a state the panel shows, and only «Usar el siguiente libre» moves it. A new secret or a port changed
 * in Preferencias restarts a running server at once, so the old configuration stops working. A port given on the
 * command line ({@link SessionPort}) takes the remembered port's place for this run only and is never written to the
 * preferences. «Encender» never blocks the EDT: the port is bound (and a taken one probed) on a thread of its own while
 * the state says «Encendiendo…». It also follows the AI's tasks for the panels and the status lines. EDT only, but for
 * {@link #requestServed()}.
 */
public final class AiServerService {

    /** The hand-test switch: {@code -Dvsim.ai=on} or {@code VSIM_AI=on} turns «Encender» on at launch. */
    public static final String LAUNCH_PROPERTY = "vsim.ai";
    public static final String LAUNCH_ENVIRONMENT = "VSIM_AI";
    private static final String ON = "on";
    /** The name Claude Code knows this server by. */
    private static final String CLIENT_SERVER_NAME = "vsim";
    /** 32 random bytes: 256 bits, more than enough against guessing over loopback. */
    private static final int SECRET_BYTES = 32;
    /** «Usar el siguiente libre» looks this many ports above the taken one. */
    static final int NEXT_FREE_PORT_RANGE = 100;
    private static final int MAXIMUM_PORT = 65_535;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Logger LOGGER = LoggerFactory.getLogger(AiServerService.class);

    /** The AI's work waiting for the EDT: refused while the server is off (review M1). */
    public interface QueuedWork {
        QueuedWork NONE = new QueuedWork() {
            @Override
            public void refuse() {
            }

            @Override
            public void accept() {
            }
        };

        void refuse();

        void accept();
    }

    /** Hears the server's state and the AI's tasks, on the EDT. */
    public interface Listener {
        default void stateChanged() {
        }

        /** One more request served while the state stays the same (the panel's count). */
        default void requestServed() {
        }

        default void taskStarted(TaskInfo task) {
        }

        default void taskEnded(TaskInfo task, Set<String> changedElementIds) {
        }

        default void taskStopped(TaskInfo task) {
        }

        /** The task stopped, and why (IA.3: VSim stops it when the AI stopped answering); by default the above. */
        default void taskStopped(TaskInfo task, TaskStopReason reason) {
            taskStopped(task);
        }

        /** The AI said a line about what comes (IA.3, {@code vsim_say}), in any mode. */
        default void narrated(Narration narration) {
        }

        /** The AI asked the user {@code question} in VSim (IA.3). */
        default void questionAsked(Question question) {
        }

        /** The question is gone: answered in VSim or withdrawn (IA.3). */
        default void questionClosed(String taskId, String questionId, boolean answered) {
        }

        /**
         * The AI asked nothing about its task for {@code silentFor} while the question waits; {@code ZERO}: it asked
         * again (IA.3 review I2). The task goes on.
         */
        default void questionUnattended(String taskId, String questionId, Duration silentFor) {
        }
    }

    private final AppPreferences preferences;
    private final AutomationApi api;
    private final AiTasks tasks;
    private final Executor editorThread;
    private final Executor background;
    /** Binds the port (and probes a taken one) off the EDT; stops a server whose start was cancelled meanwhile. */
    private final Executor serverThread;
    private final Clock clock;
    private final QueuedWork queuedWork;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    /** The running tasks, in the order they started. */
    private final Map<String, TaskInfo> runningTasks = new LinkedHashMap<>();
    private RunningServer running;
    private String runningSecret;
    private AiServerState failure;
    private boolean connected;
    private boolean portChanged;
    /** The port being bound while «Encendiendo…»; empty otherwise. */
    private OptionalInt startingPort = OptionalInt.empty();
    /** Counts the starts, so a start that «Apagar» or another start overtook is dropped when it finishes. */
    private int startGeneration;
    /** When the running server started and how many requests it served since. */
    private Instant startedAt;
    private long requestsServed;
    /** The port given on the command line for this run, moved by «Usar el siguiente libre»; never remembered. */
    private OptionalInt sessionPort;
    private final Optional<String> invalidSessionPort;

    public AiServerService(AppPreferences preferences, AutomationApi api) {
        this(preferences, api, QueuedWork.NONE);
    }

    /** @param queuedWork the editor's queue of the AI's work, refused while the server is off */
    public AiServerService(AppPreferences preferences, AutomationApi api, QueuedWork queuedWork) {
        this(preferences, api, queuedWork, SessionPort.none());
    }

    /**
     * @param queuedWork  the editor's queue of the AI's work, refused while the server is off
     * @param sessionPort the port given on the command line for this run ({@link SessionPort#fromLaunch})
     */
    public AiServerService(AppPreferences preferences, AutomationApi api, QueuedWork queuedWork,
                           SessionPort sessionPort) {
        this(preferences, api, AiTasks.of(api), SwingUtilities::invokeLater, daemonThread("vsim-ai-tasks"),
                queuedWork, sessionPort, daemonThread("vsim-ai-server"), Clock.systemDefaultZone());
    }

    AiServerService(AppPreferences preferences, AutomationApi api, AiTasks tasks, Executor editorThread,
                    Executor background) {
        this(preferences, api, tasks, editorThread, background, QueuedWork.NONE);
    }

    /**
     * @param editorThread runs the task events and the outcomes on the EDT ({@code SwingUtilities::invokeLater})
     * @param background   runs «Deshacer lo que hizo la IA», which waits for the EDT, off it
     * @param queuedWork   the editor's queue of the AI's work, refused while the server is off
     */
    AiServerService(AppPreferences preferences, AutomationApi api, AiTasks tasks, Executor editorThread,
                    Executor background, QueuedWork queuedWork) {
        this(preferences, api, tasks, editorThread, background, queuedWork, SessionPort.none());
    }

    /**
     * The server is started on {@code background} (the same thread as the undo in tests that run it at once).
     *
     * @param sessionPort the port given on the command line for this run, never remembered
     */
    AiServerService(AppPreferences preferences, AutomationApi api, AiTasks tasks, Executor editorThread,
                    Executor background, QueuedWork queuedWork, SessionPort sessionPort) {
        this(preferences, api, tasks, editorThread, background, queuedWork, sessionPort, background,
                Clock.systemDefaultZone());
    }

    /**
     * @param serverThread binds the port (and probes a taken one) off the EDT
     * @param clock        says since when the server runs
     */
    AiServerService(AppPreferences preferences, AutomationApi api, AiTasks tasks, Executor editorThread,
                    Executor background, QueuedWork queuedWork, SessionPort sessionPort, Executor serverThread,
                    Clock clock) {
        Objects.requireNonNull(sessionPort, "sessionPort");
        this.serverThread = Objects.requireNonNull(serverThread, "serverThread");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.sessionPort = sessionPort.port();
        this.invalidSessionPort = sessionPort.invalidValue();
        invalidSessionPort.ifPresent(value -> LOGGER.warn(
                "Ignoring the AI port {} given on the command line: it is not a port from 1024 to 65535", value));
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        this.api = Objects.requireNonNull(api, "api");
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.editorThread = Objects.requireNonNull(editorThread, "editorThread");
        this.background = Objects.requireNonNull(background, "background");
        this.queuedWork = Objects.requireNonNull(queuedWork, "queuedWork");
        // Both live as long as the application, like this service.
        preferences.addListener(this::followPreferences);
        tasks.addListener(new TaskEvents());
        tasks.addPresentationListener(new WhatTheAiSays());
    }

    private static Executor daemonThread(String name) {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        });
    }

    // --- The server ---

    /**
     * Starts listening on the calling thread; a no-op when already running or starting. A taken port is reported,
     * never replaced by another. The panels use {@link #activate()}, which never blocks the EDT.
     */
    public void start() throws PortInUseException {
        if (running != null || startingPort.isPresent()) {
            return;
        }
        String secret = ensureSecret();
        queuedWork.accept();
        RunningServer server = VsimMcpServer.start(api, new McpServerSettings(effectivePort(), secret));
        tasks.acceptWaits(); // the AI's waits for «Siguiente», ended when it was last turned off, wait again
        listening(server, secret);
    }

    /** The server listens. */
    private void listening(RunningServer server, String secret) {
        running = server;
        runningSecret = secret;
        failure = null;
        connected = false;
        startedAt = clock.instant();
        requestsServed = 0;
    }

    /**
     * Stops serving and frees the port on the calling thread (tests; VSim quitting uses {@link #quit}, which stops it
     * off the EDT); a no-op when not running. The AI's waits end first (its long polls answer STOPPED or WITHDRAWN)
     * and its work still waiting for the EDT is refused, so the requests in flight answer at once. The panels use
     * {@link #deactivate()}, which never blocks the EDT.
     */
    public void stop() {
        RunningServer stopping = letGo();
        if (stopping != null) {
            stopping.stop();
        }
    }

    /**
     * VSim quits (its last window closed): as «Apagar» for the AI, so its waits end at once and its proposals go with
     * the state change, but the server stops on the server thread and {@code exit} runs there right after (review
     * M1). Jetty gives the requests in flight up to two seconds, and some of them need the EDT to finish: the EDT must
     * never be the one waiting.
     */
    public void quit(Runnable exit) {
        Objects.requireNonNull(exit, "exit");
        dropStart();
        RunningServer stopping = letGo();
        failure = null;
        portChanged = false;
        notifyStateChanged();
        serverThread.execute(() -> {
            try {
                if (stopping != null) {
                    stopping.stop();
                }
            } finally {
                exit.run();
            }
        });
    }

    /**
     * As {@link #stop()}, but the server stops on the server thread (IA.3 review I1): Jetty gives the requests in
     * flight up to two seconds, which the EDT must not wait. A start that follows binds after it, on the same thread,
     * so the port is free by then.
     */
    private void stopOffTheEditor() {
        RunningServer stopping = letGo();
        if (stopping != null) {
            serverThread.execute(stopping::stop);
        }
    }

    /** Forgets the running server after ending the AI's waits and refusing its queued work; null when none ran. */
    private RunningServer letGo() {
        if (running == null) {
            return null;
        }
        queuedWork.refuse();
        tasks.endEveryWait();
        RunningServer stopping = running;
        running = null;
        runningSecret = null;
        connected = false;
        return stopping;
    }

    /**
     * «Encender»: listens, or shows why not ({@link AiServerState.PortInUse}, {@link AiServerState.OtherVsim}). The
     * port is bound off the EDT (a taken one is probed there too, up to a second) while the state says
     * «Encendiendo…»; a no-op while running or starting.
     */
    public void activate() {
        activate(() -> {
        });
    }

    /** {@link #activate()}; {@code settled} runs on the EDT once the server listens or could not start. */
    private void activate(Runnable settled) {
        if (running != null || startingPort.isPresent()) {
            settled.run();
            return;
        }
        int port = effectivePort();
        String secret = ensureSecret();
        int generation = ++startGeneration;
        startingPort = OptionalInt.of(port);
        failure = null;
        queuedWork.accept();
        notifyStateChanged();
        // the AI's waits for «Siguiente», ended when it was last turned off, wait again before the port answers: the
        // AI may call as soon as it does, before this EDT hears that the server listens (review M2)
        tasks.acceptWaits();
        serverThread.execute(() -> {
            RunningServer started = null;
            AiServerState refused = null;
            try {
                started = VsimMcpServer.start(api, new McpServerSettings(port, secret));
            } catch (PortInUseException exception) {
                refused = exception.otherVsim() ? new AiServerState.OtherVsim(exception.port())
                        : new AiServerState.PortInUse(exception.port());
            } catch (RuntimeException exception) {
                LOGGER.warn("Could not start the AI server on port {}", port, exception);
                refused = new AiServerState.Failed(port);
            }
            RunningServer server = started;
            AiServerState failed = refused;
            editorThread.execute(() -> finishStart(generation, server, failed, secret, settled));
        });
    }

    /** On the EDT: the start of {@code generation} ended; a start overtaken meanwhile frees its port at once. */
    private void finishStart(int generation, RunningServer server, AiServerState failed, String secret,
                             Runnable settled) {
        if (generation != startGeneration) {
            if (server != null) {
                serverThread.execute(server::stop);
            }
            return;
        }
        startingPort = OptionalInt.empty();
        if (server != null) {
            listening(server, secret);
        } else {
            failure = failed;
            tasks.endEveryWait();
        }
        notifyStateChanged();
        settled.run();
        if (server != null) {
            followPreferences(); // the port or the secret may have changed while it started
        }
    }

    /** Drops a start in progress (its server stops when its bind ends), refusing its work and ending its waits. */
    private void dropStart() {
        startGeneration++;
        if (startingPort.isPresent()) {
            startingPort = OptionalInt.empty();
            queuedWork.refuse();
            tasks.endEveryWait();
        }
    }

    /** True while the port is being bound («Encendiendo…»). */
    public boolean isStarting() {
        return startingPort.isPresent();
    }

    /**
     * «Apagar»: stops listening, which revokes everything; the running tasks are stopped (as «Detener») and a start
     * in progress is dropped. The tasks stop and the AI's waits end first, so its long polls answer STOPPED; then the
     * server stops off the EDT (IA.3 review I1).
     */
    public void deactivate() {
        dropStart();
        List<String> stopping = List.copyOf(runningTasks.keySet());
        runningTasks.clear();
        stopping.forEach(this::stopTask);
        stopOffTheEditor();
        failure = null;
        portChanged = false;
        notifyStateChanged();
    }

    /**
     * «Reiniciar»: «Apagar» then «Encender» on the same effective port (after a port or secret change, or when
     * something got stuck); the running tasks are stopped as by «Detener». Refused (false, nothing changes) while the
     * server is starting.
     */
    public boolean restart() {
        if (startingPort.isPresent()) {
            return false;
        }
        deactivate();
        activate();
        return true;
    }

    public boolean isRunning() {
        return running != null;
    }

    /** When the running server started; empty while it does not run. */
    public Optional<Instant> startedAt() {
        return running == null ? Optional.empty() : Optional.ofNullable(startedAt);
    }

    /** The requests the running server served since it started. */
    public long requestsServed() {
        return running == null ? 0 : requestsServed;
    }

    /** The port the server listens on (or would): the session port when one was given, else the remembered one. */
    public int effectivePort() {
        return sessionPort.orElseGet(preferences::aiPort);
    }

    /** True when this run listens on a port given on the command line, not the remembered one. */
    public boolean isSessionPort() {
        return sessionPort.isPresent();
    }

    /** The value given on the command line that is not a usable port (the remembered one is used then). */
    public Optional<String> invalidSessionPort() {
        return invalidSessionPort;
    }

    /**
     * What the panels say about the session port: «Puerto <n> (solo esta sesión…)», or that the value given is not a
     * port and the remembered one is used; empty without a session port.
     */
    public Optional<String> sessionPortNote() {
        if (isSessionPort()) {
            return Optional.of(Messages.format("ai.sessionPort", Integer.toString(sessionPort.getAsInt())));
        }
        return invalidSessionPort.map(value -> Messages.format("ai.sessionPort.invalid", value,
                Integer.toString(preferences.aiPort())));
    }

    public OptionalInt port() {
        return running == null ? OptionalInt.empty() : OptionalInt.of(running.port());
    }

    public AiServerState state() {
        if (failure != null) {
            return failure;
        }
        if (startingPort.isPresent()) {
            return new AiServerState.Starting(startingPort.getAsInt());
        }
        if (running == null) {
            return new AiServerState.Off();
        }
        if (!runningTasks.isEmpty()) {
            List<TaskInfo> started = new ArrayList<>(runningTasks.values());
            return new AiServerState.Working(running.port(), started.get(started.size() - 1).title());
        }
        return connected ? new AiServerState.Connected(running.port()) : new AiServerState.Listening(running.port());
    }

    /** Any thread: the server answered a request, so Claude Code is connected («Claude Code conectado»). */
    public void requestServed() {
        editorThread.execute(() -> {
            if (running != null) {
                requestsServed++;
                boolean firstRequest = !connected;
                connected = true;
                if (firstRequest) {
                    notifyStateChanged();
                } else {
                    listeners.forEach(Listener::requestServed);
                }
            }
        });
    }

    /**
     * «Usar el siguiente libre» after {@link AiServerState.PortInUse}: remembers the first free port above the taken
     * one and listens there; the panel then says the configuration must be copied again. On a session port only the
     * session port moves: the remembered one stays. False (and nothing changes) when there is no free port in the
     * next {@value #NEXT_FREE_PORT_RANGE}.
     */
    public boolean useNextFreePort() {
        if (startingPort.isPresent()) {
            return false;
        }
        OptionalInt free = nextFreePort(effectivePort());
        if (free.isEmpty()) {
            return false;
        }
        stopOffTheEditor();
        failure = null;
        portChanged = true;
        if (isSessionPort()) {
            sessionPort = free;
        } else {
            preferences.setAiPort(free.getAsInt());
        }
        activate();
        return true;
    }

    /** True after «Usar el siguiente libre» until the configuration is copied again. */
    public boolean portChanged() {
        return portChanged;
    }

    /** The configuration was copied: the port-change notice goes. */
    public void configurationCopied() {
        if (portChanged) {
            portChanged = false;
            notifyStateChanged();
        }
    }

    /** The first port above {@code taken} that nothing listens on, within {@value #NEXT_FREE_PORT_RANGE}. */
    static OptionalInt nextFreePort(int taken) {
        int last = Math.min(MAXIMUM_PORT, taken + NEXT_FREE_PORT_RANGE);
        for (int candidate = taken + 1; candidate <= last; candidate++) {
            if (isFree(candidate)) {
                return OptionalInt.of(candidate);
            }
        }
        return OptionalInt.empty();
    }

    private static boolean isFree(int port) {
        try (ServerSocket probe = new ServerSocket()) {
            probe.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    /** A new secret or another port while running: listen again, so the old configuration stops working. */
    private void followPreferences() {
        if (startingPort.isPresent()) {
            return; // checked again when the start ends
        }
        if (running != null) {
            boolean secretChanged = !preferences.aiSecret().map(secret -> secret.equals(runningSecret)).orElse(false);
            if (effectivePort() != running.port() || secretChanged) {
                stopOffTheEditor();
                activate();
            }
        } else if (failedPort().isPresent() && failedPort().getAsInt() != effectivePort()) {
            // the user chose another port: the error was about the old one
            failure = null;
            notifyStateChanged();
        }
    }

    private OptionalInt failedPort() {
        return switch (failure) {
            case AiServerState.PortInUse taken -> OptionalInt.of(taken.port());
            case AiServerState.OtherVsim other -> OptionalInt.of(other.port());
            case null, default -> OptionalInt.empty();
        };
    }

    // --- Secret and configuration ---

    /** The remembered secret, made and remembered the first time it is needed. */
    public String ensureSecret() {
        return ensureSecret(preferences);
    }

    /** The secret remembered in {@code preferences}, made and remembered there the first time. */
    public static String ensureSecret(AppPreferences preferences) {
        Optional<String> remembered = preferences.aiSecret();
        if (remembered.isPresent()) {
            return remembered.get();
        }
        String secret = newSecret();
        preferences.setAiSecret(secret);
        return secret;
    }

    /** «Generar un secreto nuevo»: a running server restarts with it, so the old one stops working at once. */
    public static void regenerateSecret(AppPreferences preferences) {
        preferences.setAiSecret(newSecret());
    }

    /** Base64url without padding: safe inside the double quotes of the command line. */
    static String newSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * The line that registers this VSim in Claude Code once, for every project ({@code --scope user}), with the
     * remembered port and secret (made now if there is none yet). On a session port it names that port and a server
     * of its own, {@code vsim-<port>}, so it never replaces the usual {@code vsim} in Claude Code.
     */
    public String claudeCodeCommand() {
        if (isSessionPort()) {
            int port = sessionPort.getAsInt();
            return claudeCodeCommand(CLIENT_SERVER_NAME + "-" + port, port, ensureSecret());
        }
        return claudeCodeCommand(preferences);
    }

    /** {@link #claudeCodeCommand()} from {@code preferences} alone, without a session port. */
    public static String claudeCodeCommand(AppPreferences preferences) {
        return claudeCodeCommand(CLIENT_SERVER_NAME, preferences.aiPort(), ensureSecret(preferences));
    }

    private static String claudeCodeCommand(String serverName, int port, String secret) {
        return "claude mcp add --transport http --scope user " + serverName + " http://127.0.0.1:" + port
                + "/mcp --header \"Authorization: Bearer " + secret + "\"";
    }

    // --- Tasks ---

    /** The running tasks, in the order they started. */
    public List<TaskInfo> runningTasks() {
        return List.copyOf(runningTasks.values());
    }

    /**
     * «Detener»: stopped at once, on the caller's thread (it never waits for the EDT), so an edit of the task queued
     * on the EDT behind this click is refused; the task's applied edits stay.
     */
    public void stopTask(String taskId) {
        Objects.requireNonNull(taskId, "taskId");
        try {
            tasks.stopTask(taskId);
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not stop the AI task {}", taskId, exception);
        }
    }

    /** The user answered the AI's question in the status line (IA.3): sent off the EDT; a refusal is logged. */
    public void answerQuestion(String questionId, QuestionAnswer answer) {
        Objects.requireNonNull(questionId, "questionId");
        Objects.requireNonNull(answer, "answer");
        background.execute(() -> {
            try {
                if (!tasks.answerQuestion(questionId, answer)) {
                    LOGGER.info("The AI's question {} was closed already or does not take that answer", questionId);
                }
            } catch (RuntimeException exception) {
                LOGGER.warn("Could not answer the AI's question {}", questionId, exception);
            }
        });
    }

    /** «Deshacer lo que hizo la IA»: undone off the EDT; {@code done} hears the outcome on the EDT. */
    public void undoTask(String taskId, Consumer<UndoOutcome> done) {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(done, "done");
        background.execute(() -> {
            UndoOutcome outcome;
            try {
                outcome = tasks.undoTask(taskId);
            } catch (RuntimeException exception) {
                LOGGER.warn("Could not undo the AI task {}", taskId, exception);
                outcome = new UndoOutcome(0, false);
            }
            UndoOutcome finished = outcome;
            editorThread.execute(() -> done.accept(finished));
        });
    }

    public Runnable addListener(Listener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return () -> listeners.remove(listener);
    }

    int listenerCount() {
        return listeners.size();
    }

    private void notifyStateChanged() {
        listeners.forEach(Listener::stateChanged);
    }

    /** The automation layer's events, off the EDT: moved to the EDT. */
    private final class TaskEvents implements AutomationListener {

        @Override
        public void taskStarted(TaskInfo task) {
            editorThread.execute(() -> {
                runningTasks.put(task.taskId(), task);
                listeners.forEach(listener -> listener.taskStarted(task));
                notifyStateChanged();
            });
        }

        @Override
        public void taskEnded(TaskInfo task, Set<String> changedElementIds) {
            Set<String> changed = Set.copyOf(changedElementIds);
            editorThread.execute(() -> {
                runningTasks.remove(task.taskId());
                listeners.forEach(listener -> listener.taskEnded(task, changed));
                notifyStateChanged();
            });
        }

        @Override
        public void taskStopped(TaskInfo task) {
            taskStopped(task, TaskStopReason.USER);
        }

        @Override
        public void taskStopped(TaskInfo task, TaskStopReason reason) {
            editorThread.execute(() -> {
                runningTasks.remove(task.taskId());
                listeners.forEach(listener -> listener.taskStopped(task, reason));
                notifyStateChanged();
            });
        }
    }

    /**
     * What the AI says and asks (IA.3), moved to the EDT for the status lines; the rest of «Presentar» is the
     * panel's.
     */
    private final class WhatTheAiSays implements PresentationListener {

        @Override
        public void planChanged(TaskInfo task, List<TaskStage> stages, int currentStage, int currentStep) {
        }

        @Override
        public void stepProposed(StepPreview preview) {
        }

        @Override
        public void stepApplied(StepPreview preview) {
        }

        @Override
        public void stepDiscarded(String taskId, StepStatus why) {
        }

        @Override
        public void stageChecked(String taskId, int stageIndex, boolean passed, String summary) {
        }

        @Override
        public void narrated(Narration narration) {
            editorThread.execute(() -> listeners.forEach(listener -> listener.narrated(narration)));
        }

        @Override
        public void questionAsked(Question question) {
            editorThread.execute(() -> listeners.forEach(listener -> listener.questionAsked(question)));
        }

        @Override
        public void questionClosed(String taskId, String questionId, boolean answered) {
            editorThread.execute(() -> listeners.forEach(listener -> listener.questionClosed(taskId, questionId,
                    answered)));
        }

        @Override
        public void questionUnattended(String taskId, String questionId, Duration silentFor) {
            editorThread.execute(() -> listeners.forEach(listener -> listener.questionUnattended(taskId, questionId,
                    silentFor)));
        }
    }

    // --- Launch ---

    /**
     * At VSim's start, on the EDT: turns «Encender» on when «Iniciar con VSim» is on or when VSim was launched with
     * the hand-test switch. With the switch it also writes one line to {@code terminal}, once the server listens or
     * could not start, with the command to register it in Claude Code (the only place the secret is printed), or why
     * it could not start. It never waits for the port.
     */
    public void startAtLaunch(PrintStream terminal) {
        boolean launchSwitch = requestedAtLaunch(System.getProperty(LAUNCH_PROPERTY),
                System.getenv(LAUNCH_ENVIRONMENT));
        if (!launchSwitch && !preferences.aiStartWithVsim()) {
            return;
        }
        if (!launchSwitch) {
            activate();
            return;
        }
        activate(() -> {
            switch (state()) {
                case AiServerState.PortInUse taken ->
                        terminal.println(Messages.format("ai.launch.portInUse", Integer.toString(taken.port())));
                case AiServerState.OtherVsim other ->
                        terminal.println(Messages.format("ai.launch.otherVsim", Integer.toString(other.port())));
                case AiServerState.Failed failed ->
                        terminal.println(Messages.format("ai.launch.failed", Integer.toString(failed.port())));
                default -> terminal.println(Messages.format("ai.launch.command", claudeCodeCommand()));
            }
        });
    }

    /** The property wins over the environment when both are given. */
    static boolean requestedAtLaunch(String property, String environment) {
        String switchValue = property != null ? property : environment;
        return ON.equalsIgnoreCase(switchValue);
    }
}
