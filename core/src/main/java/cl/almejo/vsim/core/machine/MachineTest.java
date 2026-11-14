package cl.almejo.vsim.core.machine;

import java.util.Objects;

/**
 * A saved test of a machine (hito 4, «Modo prueba»). Placeholder until then: only its name, so files and the model
 * can already carry the list.
 */
public record MachineTest(String name) {

    public MachineTest {
        Objects.requireNonNull(name, "name");
    }
}
