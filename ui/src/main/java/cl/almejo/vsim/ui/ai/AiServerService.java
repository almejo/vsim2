package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.mcp.McpServerSettings;
import cl.almejo.vsim.mcp.PortInUseException;
import cl.almejo.vsim.mcp.RunningServer;
import cl.almejo.vsim.mcp.VsimMcpServer;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;

import java.io.PrintStream;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The AI server of this VSim (spec §9, §9.1): one per process, an application service, off until started. It listens
 * on the remembered port with the remembered secret, made the first time; it never changes the port by itself. The
 * EDT starts and stops it (it reads and remembers preferences); binding on loopback takes a few milliseconds.
 */
public final class AiServerService {

    /** Temporary until the «IA» panel (IA.1 Task 5): {@code -Dvsim.ai=on} or {@code VSIM_AI=on} starts it at launch. */
    public static final String LAUNCH_PROPERTY = "vsim.ai";
    public static final String LAUNCH_ENVIRONMENT = "VSIM_AI";
    private static final String ON = "on";
    /** The name Claude Code knows this server by. */
    private static final String CLIENT_SERVER_NAME = "vsim";
    /** 32 random bytes: 256 bits, more than enough against guessing over loopback. */
    private static final int SECRET_BYTES = 32;

    private final AppPreferences preferences;
    private final AutomationApi api;
    private final SecureRandom random = new SecureRandom();
    private RunningServer running;

    public AiServerService(AppPreferences preferences, AutomationApi api) {
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        this.api = Objects.requireNonNull(api, "api");
    }

    /** Starts listening; a no-op when already running. A taken port is reported, never replaced by another. */
    public synchronized void start() throws PortInUseException {
        if (running != null) {
            return;
        }
        running = VsimMcpServer.start(api, new McpServerSettings(preferences.aiPort(), ensureSecret()));
    }

    /** Stops serving and frees the port (requests in flight get a moment to finish); a no-op when not running. */
    public synchronized void stop() {
        if (running != null) {
            running.stop();
            running = null;
        }
    }

    public synchronized boolean isRunning() {
        return running != null;
    }

    public synchronized OptionalInt port() {
        return running == null ? OptionalInt.empty() : OptionalInt.of(running.port());
    }

    /** The remembered secret, made and remembered the first time it is needed. */
    public String ensureSecret() {
        Optional<String> remembered = preferences.aiSecret();
        if (remembered.isPresent()) {
            return remembered.get();
        }
        byte[] bytes = new byte[SECRET_BYTES];
        random.nextBytes(bytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        preferences.setAiSecret(secret);
        return secret;
    }

    /**
     * The line that registers this VSim in Claude Code once, for every project ({@code --scope user}), with the
     * remembered port and secret (made now if there is none yet).
     */
    public String claudeCodeCommand() {
        return "claude mcp add --transport http --scope user " + CLIENT_SERVER_NAME + " " + url()
                + " --header \"Authorization: Bearer " + ensureSecret() + "\"";
    }

    private String url() {
        return "http://127.0.0.1:" + preferences.aiPort() + "/mcp";
    }

    /**
     * At VSim's start, on the EDT: starts the server when «Iniciar con VSim» is on or, temporarily until the «IA»
     * panel, when VSim was launched with the switch on. With the switch it also writes one line to {@code terminal}
     * with the command to register it in Claude Code (the only place the secret is printed); a taken port is told
     * there either way.
     */
    public void startAtLaunch(PrintStream terminal) {
        boolean launchSwitch = requestedAtLaunch(System.getProperty(LAUNCH_PROPERTY),
                System.getenv(LAUNCH_ENVIRONMENT));
        if (!launchSwitch && !preferences.aiStartWithVsim()) {
            return;
        }
        try {
            start();
            if (launchSwitch) {
                terminal.println(Messages.format("ai.launch.command", claudeCodeCommand()));
            }
        } catch (PortInUseException exception) {
            terminal.println(Messages.format(exception.otherVsim() ? "ai.launch.otherVsim" : "ai.launch.portInUse",
                    Integer.toString(exception.port())));
        }
    }

    /** The property wins over the environment when both are given. */
    static boolean requestedAtLaunch(String property, String environment) {
        String switchValue = property != null ? property : environment;
        return ON.equalsIgnoreCase(switchValue);
    }
}
