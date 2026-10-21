package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.automation.AutomationListener;
import cl.almejo.vsim.automation.TaskInfo;
import cl.almejo.vsim.automation.UndoOutcome;
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
 * The AI server of this VSim (spec §9, §9.1): one per process, an application service, off until «Activar». It listens
 * on the remembered port with the remembered secret, made the first time, and never changes the port by itself: a
 * taken port is a state the panel shows, and only «Usar el siguiente libre» moves it. A new secret or a port changed
 * in Preferencias restarts a running server at once, so the old configuration stops working. It also follows the AI's
 * tasks for the panels and the status lines. EDT only, but for {@link #requestServed()}.
 */
public final class AiServerService {

    /** The hand-test switch: {@code -Dvsim.ai=on} or {@code VSIM_AI=on} turns «Activar» on at launch. */
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

        default void taskStarted(TaskInfo task) {
        }

        default void taskEnded(TaskInfo task, Set<String> changedElementIds) {
        }

        default void taskStopped(TaskInfo task) {
        }
    }

    private final AppPreferences preferences;
    private final AutomationApi api;
    private final AiTasks tasks;
    private final Executor editorThread;
    private final Executor background;
    private final QueuedWork queuedWork;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    /** The running tasks, in the order they started. */
    private final Map<String, TaskInfo> runningTasks = new LinkedHashMap<>();
    private RunningServer running;
    private String runningSecret;
    private AiServerState failure;
    private boolean connected;
    private boolean portChanged;

    public AiServerService(AppPreferences preferences, AutomationApi api) {
        this(preferences, api, QueuedWork.NONE);
    }

    /** @param queuedWork the editor's queue of the AI's work, refused while the server is off */
    public AiServerService(AppPreferences preferences, AutomationApi api, QueuedWork queuedWork) {
        this(preferences, api, AiTasks.of(api), SwingUtilities::invokeLater, backgroundExecutor(), queuedWork);
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
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        this.api = Objects.requireNonNull(api, "api");
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.editorThread = Objects.requireNonNull(editorThread, "editorThread");
        this.background = Objects.requireNonNull(background, "background");
        this.queuedWork = Objects.requireNonNull(queuedWork, "queuedWork");
        // Both live as long as the application, like this service.
        preferences.addListener(this::followPreferences);
        tasks.addListener(new TaskEvents());
    }

    private static Executor backgroundExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "vsim-ai-tasks");
            thread.setDaemon(true);
            return thread;
        });
    }

    // --- The server ---

    /** Starts listening; a no-op when already running. A taken port is reported, never replaced by another. */
    public void start() throws PortInUseException {
        if (running != null) {
            return;
        }
        String secret = ensureSecret();
        queuedWork.accept();
        running = VsimMcpServer.start(api, new McpServerSettings(preferences.aiPort(), secret));
        runningSecret = secret;
        failure = null;
        connected = false;
    }

    /**
     * Stops serving and frees the port; a no-op when not running. The AI's work still waiting for the EDT is refused
     * first, so the requests in flight answer at once and stopping never waits for the EDT it runs on.
     */
    public void stop() {
        if (running != null) {
            queuedWork.refuse();
            running.stop();
            running = null;
            runningSecret = null;
            connected = false;
        }
    }

    /** «Activar»: listens, or shows why not ({@link AiServerState.PortInUse}, {@link AiServerState.OtherVsim}). */
    public void activate() {
        try {
            start();
        } catch (PortInUseException exception) {
            failure = exception.otherVsim() ? new AiServerState.OtherVsim(exception.port())
                    : new AiServerState.PortInUse(exception.port());
        }
        notifyStateChanged();
    }

    /** «Desactivar»: stops listening, which revokes everything; the running tasks are stopped. */
    public void deactivate() {
        stop();
        failure = null;
        portChanged = false;
        List<String> stopping = List.copyOf(runningTasks.keySet());
        runningTasks.clear();
        stopping.forEach(this::stopTask);
        notifyStateChanged();
    }

    public boolean isRunning() {
        return running != null;
    }

    public OptionalInt port() {
        return running == null ? OptionalInt.empty() : OptionalInt.of(running.port());
    }

    public AiServerState state() {
        if (failure != null) {
            return failure;
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
            if (running != null && !connected) {
                connected = true;
                notifyStateChanged();
            }
        });
    }

    /**
     * «Usar el siguiente libre» after {@link AiServerState.PortInUse}: remembers the first free port above the taken
     * one and listens there; the panel then says the configuration must be copied again. False (and nothing changes)
     * when there is no free port in the next {@value #NEXT_FREE_PORT_RANGE}.
     */
    public boolean useNextFreePort() {
        OptionalInt free = nextFreePort(preferences.aiPort());
        if (free.isEmpty()) {
            return false;
        }
        stop();
        failure = null;
        portChanged = true;
        preferences.setAiPort(free.getAsInt());
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
        if (running != null) {
            boolean secretChanged = !preferences.aiSecret().map(secret -> secret.equals(runningSecret)).orElse(false);
            if (preferences.aiPort() != running.port() || secretChanged) {
                stop();
                activate();
            }
        } else if (failedPort().isPresent() && failedPort().getAsInt() != preferences.aiPort()) {
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
     * remembered port and secret (made now if there is none yet).
     */
    public String claudeCodeCommand() {
        return claudeCodeCommand(preferences);
    }

    /** {@link #claudeCodeCommand()} from {@code preferences} alone (Preferencias ▸ «IA»). */
    public static String claudeCodeCommand(AppPreferences preferences) {
        return "claude mcp add --transport http --scope user " + CLIENT_SERVER_NAME + " http://127.0.0.1:"
                + preferences.aiPort() + "/mcp --header \"Authorization: Bearer " + ensureSecret(preferences) + "\"";
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
            editorThread.execute(() -> {
                runningTasks.remove(task.taskId());
                listeners.forEach(listener -> listener.taskStopped(task));
                notifyStateChanged();
            });
        }
    }

    // --- Launch ---

    /**
     * At VSim's start, on the EDT: turns «Activar» on when «Iniciar con VSim» is on or when VSim was launched with
     * the hand-test switch. With the switch it also writes one line to {@code terminal} with the command to register
     * it in Claude Code (the only place the secret is printed), or why it could not start.
     */
    public void startAtLaunch(PrintStream terminal) {
        boolean launchSwitch = requestedAtLaunch(System.getProperty(LAUNCH_PROPERTY),
                System.getenv(LAUNCH_ENVIRONMENT));
        if (!launchSwitch && !preferences.aiStartWithVsim()) {
            return;
        }
        activate();
        if (!launchSwitch) {
            return;
        }
        switch (state()) {
            case AiServerState.PortInUse taken ->
                    terminal.println(Messages.format("ai.launch.portInUse", Integer.toString(taken.port())));
            case AiServerState.OtherVsim other ->
                    terminal.println(Messages.format("ai.launch.otherVsim", Integer.toString(other.port())));
            default -> terminal.println(Messages.format("ai.launch.command", claudeCodeCommand()));
        }
    }

    /** The property wins over the environment when both are given. */
    static boolean requestedAtLaunch(String property, String environment) {
        String switchValue = property != null ? property : environment;
        return ON.equalsIgnoreCase(switchValue);
    }
}
