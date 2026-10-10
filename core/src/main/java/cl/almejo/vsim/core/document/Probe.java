package cl.almejo.vsim.core.document;

import java.util.Objects;

/**
 * A waveform channel: what it watches and the name its row shows. Probes are top-level only, are not document
 * elements (never copied in fragments, never part of definitions) and change only through {@code SetProbesCommand}.
 */
public record Probe(String id, ProbeTarget target, String name) {

    public Probe {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(target, "target");
        if (id.isBlank()) {
            throw new IllegalArgumentException("probe id must not be blank");
        }
        name = name == null ? "" : name;
    }

    public Probe withName(String newName) {
        return new Probe(id, target, newName);
    }
}
