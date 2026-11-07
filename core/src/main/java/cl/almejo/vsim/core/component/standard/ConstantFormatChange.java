package cl.almejo.vsim.core.component.standard;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * A Constante reads its «Valor» in its «Mostrar como» ({@code 1.5} is other bits as a float than as a fixed-point
 * number), so changing the format alone would change what it drives while the box looks the same. An edit that changes
 * the format or the «Bits de fracción» and leaves the value text as it was writes the value again in the new format,
 * in the same edit: the bits stay, only the way they are written changes ({@code 10} becomes {@code 0.625} in fixed
 * point with 4 fraction bits). Bits the new format cannot write back exactly (a float at 8 bits, a NaN's payload) are
 * written in hex. A value typed in the same edit is read in the new format, as typed. Files are read as before.
 */
public final class ConstantFormatChange {

    private ConstantFormatChange() {
    }

    /**
     * {@code requested} with the Constante's value written again in its new format when the edit only changes how it
     * is written; {@code requested} itself for any other edit or type. {@code current} are the part's resolved
     * parameters now; {@code requested} the resolved parameters the edit asks for (both with every parameter).
     */
    public static Map<String, Object> keepingTheBits(String typeId, Map<String, Object> current,
                                                     Map<String, Object> requested) {
        if (!StandardComponentTypes.CONSTANT.equals(typeId) || !onlyTheWayOfWritingChanges(current, requested)) {
            return requested;
        }
        int width = (Integer) current.get(StandardComponentTypes.WIDTH);
        OptionalLong bits = ConstantValue.bitsOf(valueOf(current), width, formatOf(current), fractionOf(current));
        if (bits.isEmpty()) {
            return requested;
        }
        Map<String, Object> rewritten = new HashMap<>(requested);
        rewritten.put(StandardComponentTypes.VALUE, textFor(bits.getAsLong(), width, formatOf(requested),
                fractionOf(requested)));
        return rewritten;
    }

    private static boolean onlyTheWayOfWritingChanges(Map<String, Object> current, Map<String, Object> requested) {
        boolean newWay = !Objects.equals(formatOf(current), formatOf(requested))
                || fractionOf(current) != fractionOf(requested);
        return newWay && Objects.equals(valueOf(current), valueOf(requested))
                && Objects.equals(current.get(StandardComponentTypes.WIDTH),
                requested.get(StandardComponentTypes.WIDTH));
    }

    /** {@code bits} as a value text in {@code format} that reads back as the same bits; hex when none does. */
    private static String textFor(long bits, int width, String format, int fraction) {
        String text = ConstantValue.normalize(ConstantValue.editableText(bits, width, format, fraction));
        OptionalLong readBack = ConstantValue.bitsOf(text, width, format, fraction);
        return readBack.isPresent() && readBack.getAsLong() == bits ? text
                : ConstantValue.editableText(bits, width, ConstantValue.HEX);
    }

    private static String valueOf(Map<String, Object> parameters) {
        return (String) parameters.get(StandardComponentTypes.VALUE);
    }

    private static String formatOf(Map<String, Object> parameters) {
        return (String) parameters.get(ConstantValue.FORMAT);
    }

    private static int fractionOf(Map<String, Object> parameters) {
        return (Integer) parameters.get(ConstantValue.FRACTION);
    }
}
