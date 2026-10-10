package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Probe;

import java.util.List;

/** Replaces the waveform channels: adding, removing, renaming and reordering are all this one command. */
public final class SetProbesCommand implements Command {

    private final List<Probe> before;
    private final List<Probe> after;
    private final String description;

    public SetProbesCommand(List<Probe> before, List<Probe> after, String description) {
        this.before = List.copyOf(before);
        this.after = List.copyOf(after);
        this.description = description;
    }

    @Override
    public void apply(CircuitDocument document) {
        document.setProbes(after);
    }

    @Override
    public void revert(CircuitDocument document) {
        document.setProbes(before);
    }

    @Override
    public String description() {
        return description;
    }
}
