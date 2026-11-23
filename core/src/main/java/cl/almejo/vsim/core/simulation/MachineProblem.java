package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.machine.MachineFinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A problem of a «Máquina de estados» in the Problemas list ({@link cl.almejo.vsim.core.machine.MachineChecks}): of
 * the machine {@code definitionId}'s model, or, with {@code instancePath}, of one running copy (an input or the clock
 * in Z). {@code netId} is no net: a stable key made of the machine, the kind and the ids it is about, so the list keeps
 * its place across refreshes.
 */
public record MachineProblem(String netId, String definitionId, Optional<String> instancePath,
                             MachineFinding finding) implements Problem {

    /** The start of every machine problem's key. */
    public static final String PREFIX = "machine:";

    public MachineProblem {
        Objects.requireNonNull(netId, "netId");
        Objects.requireNonNull(definitionId, "definitionId");
        Objects.requireNonNull(instancePath, "instancePath");
        Objects.requireNonNull(finding, "finding");
    }

    /** The problem {@code finding} of machine {@code definitionId}, of the copy at {@code instancePath} if any. */
    public static MachineProblem of(String definitionId, Optional<String> instancePath, MachineFinding finding) {
        List<String> parts = new ArrayList<>();
        parts.add(definitionId);
        parts.add(finding.kind().name());
        instancePath.ifPresent(parts::add);
        parts.add(String.join(",", finding.stateIds()));
        parts.add(String.join(",", finding.transitionIds()));
        parts.add(String.join(",", finding.names()));
        return new MachineProblem(PREFIX + String.join(":", parts), definitionId, instancePath, finding);
    }

    /** Warnings are only listed; errors count on the red badge. */
    public boolean isWarning() {
        return finding.isWarning();
    }
}
