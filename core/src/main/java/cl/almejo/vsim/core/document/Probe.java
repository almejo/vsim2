package cl.almejo.vsim.core.document;

import java.util.Objects;

/**
 * A waveform channel: what it watches, the name its row shows and how a bus value is written ({@link #FORMAT_HEX}, or
 * {@link #FORMAT_ASCII} for bytes as characters). Probes are top-level only, are not document elements (never copied
 * in fragments, never part of definitions) and change only through {@code SetProbesCommand}.
 */
public record Probe(String id, ProbeTarget target, String name, String format) {

    /** Bus values as {@code 0x…}: what a probe without a format shows. */
    public static final String FORMAT_HEX = "hex";
    /** Bus values as characters (printable ASCII quoted, control bytes by name, the rest hex). */
    public static final String FORMAT_ASCII = "ascii";

    public Probe {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(target, "target");
        if (id.isBlank()) {
            throw new IllegalArgumentException("probe id must not be blank");
        }
        name = name == null ? "" : name;
        format = FORMAT_ASCII.equals(format) ? FORMAT_ASCII : FORMAT_HEX;
    }

    public Probe(String id, ProbeTarget target, String name) {
        this(id, target, name, FORMAT_HEX);
    }

    public Probe withName(String newName) {
        return new Probe(id, target, newName, format);
    }

    public Probe withFormat(String newFormat) {
        return new Probe(id, target, name, newFormat);
    }

    public Probe withTarget(ProbeTarget newTarget) {
        return new Probe(id, newTarget, name, format);
    }
}
