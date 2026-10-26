package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Probe;

import java.util.List;

/** Replaces the waveform channels: adding, removing, renaming and reordering are all this one command. */
public final class SetProbesCommand extends BeforeAfterCommand<List<Probe>> {

    private final String description;

    public SetProbesCommand(List<Probe> before, List<Probe> after, String description) {
        super(List.copyOf(before), List.copyOf(after));
        this.description = description;
    }

    @Override
    protected void set(CircuitDocument document, List<Probe> probes) {
        document.setProbes(probes);
    }

    @Override
    public String description() {
        return description;
    }
}
