package cl.almejo.vsim.core.component;

import java.util.Map;
import java.util.Objects;

/**
 * A named starting set of parameter values («Logisim (LRV32IM)», «VT100‑xterm», «Teletipo»): choosing it sets every
 * value it lists. {@code name} is one of the choices of the type's profile parameter
 * ({@link ComponentType#profileParameter()}); parameters it does not list (rows, columns) are the user's and never
 * count as differences.
 */
public record ParameterProfile(String name, Map<String, Object> values) {

    public ParameterProfile {
        Objects.requireNonNull(name, "name");
        values = Map.copyOf(values);
    }
}
