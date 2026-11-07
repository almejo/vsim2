package cl.almejo.vsim.automation.edit;

import cl.almejo.vsim.core.component.ParameterProblem;
import cl.almejo.vsim.core.component.ParameterProblemException;
import cl.almejo.vsim.core.component.standard.ConstantValue;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.value.Value;

import java.util.Map;

/**
 * What a part's own parameter check found wrong, told to the AI in English (core gives a bundle key and its
 * arguments, which are for the editor's bundle): «value 'A' does not fit in 1 bit (needs 7); widen the part with
 * width».
 */
final class ParameterProblemWords {

    private ParameterProblemWords() {
    }

    /** The sentence for {@code invalid}: its problem in words, or its own message for any other refusal. */
    static String of(IllegalArgumentException invalid) {
        return invalid instanceof ParameterProblemException problem
                ? describe(problem.problem(), problem.rawParameters()) : invalid.getMessage();
    }

    static String describe(ParameterProblem problem, Map<String, Object> raw) {
        String name = problem.parameterName();
        Object given = raw.get(name);
        return switch (problem.messageKey()) {
            case "constant.doesNotFit" -> doesNotFit(name, String.valueOf(given), (Integer) problem.arguments().get(0),
                    raw);
            case "constant.invalidValue" -> name + " \"" + given + "\" is not a number: write it as 0b…, 0x…, a "
                    + "decimal, or a character like 'A'";
            case "switch.noZ" -> name + " Z (high impedance) only exists on a 1-bit part; set width to 1 or "
                    + "another value";
            default -> name.equals(StandardComponentTypes.GROUPS)
                    ? "the groups \"" + given + "\" are not valid: "
                    + BusGroupsProblems.describe(problem.messageKey(), problem.arguments())
                    : "parameter " + name + ": " + problem.messageKey() + " " + problem.arguments();
        };
    }

    private static String doesNotFit(String name, String text, int width, Map<String, Object> raw) {
        String format = raw.get(ConstantValue.FORMAT) == null ? ConstantValue.AUTO
                : raw.get(ConstantValue.FORMAT).toString();
        int fraction = raw.get(ConstantValue.FRACTION) instanceof Number number ? number.intValue() : 0;
        String needs = "";
        for (int bits = width + 1; bits <= Value.MAXIMUM_WIDTH; bits++) {
            if (ConstantValue.fitsText(text, bits, format, fraction)) {
                needs = " (needs " + bits + ")";
                break;
            }
        }
        return name + " " + (text.startsWith("'") ? text : "\"" + text + "\"") + " does not fit in " + width + " bit"
                + (width == 1 ? "" : "s") + needs + "; widen the part with width or write a smaller value";
    }
}
