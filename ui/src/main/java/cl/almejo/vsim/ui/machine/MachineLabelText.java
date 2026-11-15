package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.machine.MachineTransition;
import cl.almejo.vsim.core.machine.OutputAssignment;

import java.util.List;
import java.util.Optional;

/**
 * An arrow's whole label as the diagram draws it and the inline editor writes it (spec 2026-11-14 §1 «Conditions and
 * outputs»): {@code x / Z=1}, {@code / Z=1} for an «otherwise» arrow with outputs, or just {@code x}. The text is
 * split at the first {@code /}, which the condition notation never uses; the outputs part is read as the state's
 * outputs are ({@link MachineOutputsText}).
 */
final class MachineLabelText {

    private static final String SLASH = "/";

    /**
     * What a label says: the condition as typed (kept even when it cannot be read), the outputs, and the problem of
     * the outputs part, if any.
     */
    record Reading(String condition, List<OutputAssignment> outputs, Optional<MachineOutputsText.Problem> problem) {
        Reading {
            outputs = List.copyOf(outputs);
        }
    }

    private MachineLabelText() {
    }

    /** {@code transition}'s label: {@code x / Z=1}, {@code / Z=1}, {@code x} or empty. */
    static String of(MachineTransition transition) {
        if (transition.outputs().isEmpty()) {
            return transition.condition();
        }
        String outputs = SLASH + " " + MachineOutputsText.write(transition.outputs());
        return transition.isOtherwise() ? outputs : transition.condition() + " " + outputs;
    }

    /** Reads {@code text}; a bad outputs part gives no outputs and its problem, the condition is returned as typed. */
    static Reading read(String text) {
        int slash = text.indexOf(SLASH);
        if (slash < 0) {
            return new Reading(text.strip(), List.of(), Optional.empty());
        }
        MachineOutputsText.Reading outputs = MachineOutputsText.read(text.substring(slash + 1));
        return new Reading(text.substring(0, slash).strip(), outputs.outputs(), outputs.problem());
    }
}
