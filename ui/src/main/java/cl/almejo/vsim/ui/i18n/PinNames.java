package cl.almejo.vsim.ui.i18n;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How a pin is named in the texts the user reads. A gate's inputs are {@code in0}, {@code in1}… inside (file format,
 * AI tools), counted from zero; the user reads «entrada 1», «entrada 2»… (English «input 1»). Any other pin name
 * (A, CLK, Q, out…) is shown as it is.
 */
public final class PinNames {

    private static final Pattern NUMBERED_INPUT = Pattern.compile("in(\\d+)");

    private PinNames() {
    }

    /** The name of the pin whose internal id is {@code pinName}, as the user reads it. */
    public static String display(String pinName) {
        Matcher matcher = NUMBERED_INPUT.matcher(pinName);
        return matcher.matches() ? Messages.format("pin.input", Integer.parseInt(matcher.group(1)) + 1) : pinName;
    }

    /** True when {@code pinName} is a numbered input ({@code in0}, {@code in1}…), which {@link #display} renames. */
    public static boolean isNumberedInput(String pinName) {
        return NUMBERED_INPUT.matcher(pinName).matches();
    }
}
