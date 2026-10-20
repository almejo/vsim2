package cl.almejo.vsim.core.simulation;

import java.util.List;
import java.util.Objects;

/**
 * Tunnels of name {@code name} are set up wrong. {@code netId} is the net of the name (the one all of them join);
 * {@code tunnels} are the ids of the tunnels the problem is about, hierarchical ({@code c5/c2}) inside a component, as
 * {@link FlatComponent} ids are. Found from the wiring alone, so they come and go as the circuit is edited.
 */
public record TunnelProblem(String netId, Kind kind, String name, List<String> tunnels) implements Problem {

    public enum Kind {
        /** Two or more entradas with one name (every one is listed). */
        DUPLICATE_ENTRANCE,
        /** Salidas whose name has no entrada (all of them are listed). */
        EXIT_WITHOUT_ENTRANCE,
        /** An entrada whose name has no salida. */
        ENTRANCE_WITHOUT_EXITS,
        /** A salida whose own wire (as drawn, before joining by name) has a driver: maybe it should be an entrada. */
        EXIT_DRIVEN
    }

    public TunnelProblem {
        Objects.requireNonNull(netId, "netId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(name, "name");
        tunnels = List.copyOf(tunnels);
    }

    /** True for the softer kinds, which may be on purpose while the circuit is being built. */
    public boolean isWarning() {
        return kind == Kind.ENTRANCE_WITHOUT_EXITS || kind == Kind.EXIT_DRIVEN;
    }

    /** This problem found inside the instance whose ids start with {@code prefix} ({@code "c5/"}). */
    TunnelProblem nestedIn(String prefix) {
        return new TunnelProblem(prefix + netId, kind, name, tunnels.stream().map(id -> prefix + id).toList());
    }
}
