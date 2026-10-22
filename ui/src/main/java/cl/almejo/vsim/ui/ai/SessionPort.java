package cl.almejo.vsim.ui.ai;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;

/**
 * The AI's port for this run only, from the command line: {@code -Dvsim.ai.port=<n>} or {@code VSIM_AI_PORT=<n>}
 * (the property wins when both are given). It overrides the remembered port and is never written to the preferences,
 * so two VSims can listen side by side. A value that is not a port from 1024 to 65535 is ignored and kept in
 * {@link #invalidValue()} so the panels can say so.
 *
 * @param port         the port to listen on this session, empty to use the remembered one
 * @param invalidValue the value given that is not a usable port, empty when none was or it was valid
 */
public record SessionPort(OptionalInt port, Optional<String> invalidValue) {

    public static final String LAUNCH_PROPERTY = "vsim.ai.port";
    public static final String LAUNCH_ENVIRONMENT = "VSIM_AI_PORT";
    /** The same range Preferencias ▸ «IA» accepts: below are the system's ports. */
    static final int MINIMUM_PORT = 1024;
    static final int MAXIMUM_PORT = 65_535;

    public SessionPort {
        Objects.requireNonNull(port, "port");
        Objects.requireNonNull(invalidValue, "invalidValue");
        if (port.isPresent() && !isUsable(port.getAsInt())) {
            throw new IllegalArgumentException("port " + port.getAsInt() + " is not between 1024 and 65535");
        }
    }

    /** No session port: the remembered one, as always. */
    public static SessionPort none() {
        return new SessionPort(OptionalInt.empty(), Optional.empty());
    }

    public static SessionPort of(int port) {
        return new SessionPort(OptionalInt.of(port), Optional.empty());
    }

    /**
     * Reads the launch property, then the environment; a blank value counts as not given.
     *
     * @param properties  {@code System::getProperty} in the editor
     * @param environment {@code System::getenv} in the editor
     */
    public static SessionPort fromLaunch(Function<String, String> properties, Function<String, String> environment) {
        String value = given(properties.apply(LAUNCH_PROPERTY));
        if (value == null) {
            value = given(environment.apply(LAUNCH_ENVIRONMENT));
        }
        if (value == null) {
            return none();
        }
        try {
            int port = Integer.parseInt(value);
            if (isUsable(port)) {
                return of(port);
            }
        } catch (NumberFormatException exception) {
            // said below, as any other unusable value
        }
        return new SessionPort(OptionalInt.empty(), Optional.of(value));
    }

    /** True when a usable port was given for this session. */
    public boolean isGiven() {
        return port.isPresent();
    }

    private static String given(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static boolean isUsable(int port) {
        return port >= MINIMUM_PORT && port <= MAXIMUM_PORT;
    }
}
