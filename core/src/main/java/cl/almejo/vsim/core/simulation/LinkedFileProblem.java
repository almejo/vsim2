package cl.almejo.vsim.core.simulation;

import java.util.Objects;

/**
 * A memory (RAM or ROM) linked to a contents file that cannot be used now: the file is missing, or it exists but cannot
 * be read. The circuit keeps simulating with the copy saved in it. Found by the editor (it watches the file) and by the
 * AI's reads ({@code LinkedFileChecks}), not by the simulation.
 *
 * @param elementId the memory's id in the circuit
 * @param link      the link as the user reads it (relative to the circuit when it is saved)
 * @param kind      why the file cannot be used
 * @param detail    for {@link Kind#UNREADABLE} what is wrong with the file; empty when it is missing
 */
public record LinkedFileProblem(String elementId, String link, Kind kind, String detail) implements Problem {

    /** The prefix of {@link #netId()}: a memory problem belongs to no net. */
    public static final String NET_PREFIX = "memory-file:";

    public enum Kind {
        MISSING,
        UNREADABLE
    }

    public LinkedFileProblem {
        Objects.requireNonNull(elementId, "elementId");
        Objects.requireNonNull(link, "link");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(detail, "detail");
    }

    @Override
    public String netId() {
        return NET_PREFIX + elementId;
    }
}
