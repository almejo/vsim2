package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.machine.MachineLimits;
import cl.almejo.vsim.core.machine.OutputAssignment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The outputs of a state as the right panel writes them (spec 2026-11-14 §1 «Conditions and outputs»):
 * {@code Z=1, Y=0}, separated by commas or spaces, spaces allowed around {@code =}. An output not written is 0.
 * A wrong text gives no outputs and a problem (a bundle key and its arguments) saying what is wrong.
 */
final class MachineOutputsText {

    /** An output's name, as a pin name of «Circuito desde expresiones»: a letter, then letters, digits or _. */
    private static final Pattern NAME = Pattern.compile("\\p{L}[\\p{L}\\p{Nd}_]*");
    private static final Pattern AROUND_EQUALS = Pattern.compile("\\s*=\\s*");
    private static final Pattern SEPARATORS = Pattern.compile("[,\\s]+");
    private static final String SEPARATOR = ", ";

    /** What is wrong with a text: a bundle key and its arguments. */
    record Problem(String key, List<Object> arguments) {
        Problem {
            arguments = List.copyOf(arguments);
        }
    }

    /** The outputs a text gives (none when it has a problem), and its problem. */
    record Reading(List<OutputAssignment> outputs, Optional<Problem> problem) {
        Reading {
            outputs = List.copyOf(outputs);
        }

        private static Reading wrong(String key, Object argument) {
            return new Reading(List.of(), Optional.of(new Problem(key, List.of(argument))));
        }
    }

    private MachineOutputsText() {
    }

    /** Reads {@code text}; blank gives no outputs. */
    static Reading read(String text) {
        String compact = AROUND_EQUALS.matcher(text.strip()).replaceAll("=");
        if (compact.isEmpty()) {
            return new Reading(List.of(), Optional.empty());
        }
        List<OutputAssignment> outputs = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String part : SEPARATORS.split(compact)) {
            if (part.isEmpty()) {
                continue;
            }
            int equals = part.indexOf('=');
            if (equals < 0 || part.indexOf('=', equals + 1) >= 0) {
                return Reading.wrong("machine.outputs.notAssignment", part);
            }
            String name = part.substring(0, equals);
            String value = part.substring(equals + 1);
            if (!NAME.matcher(name).matches()) {
                return Reading.wrong("machine.outputs.badName", name);
            }
            if (MachineLimits.isReserved(name)) {
                return Reading.wrong("machine.outputs.reserved", name);
            }
            if (!value.equals("0") && !value.equals("1")) {
                return Reading.wrong("machine.outputs.badValue", value);
            }
            if (!seen.add(name)) {
                return Reading.wrong("machine.outputs.repeated", name);
            }
            outputs.add(new OutputAssignment(name, value.equals("1")));
        }
        return new Reading(outputs, Optional.empty());
    }

    /** {@code outputs} as the panel shows them: {@code Z=1, Y=0}. */
    static String write(List<OutputAssignment> outputs) {
        return outputs.stream().map(output -> output.output() + "=" + (output.value() ? 1 : 0))
                .collect(Collectors.joining(SEPARATOR));
    }
}
