package cl.almejo.vsim.core.document;

import cl.almejo.vsim.core.component.standard.ConstantValue;

import java.util.Objects;
import java.util.Set;

/**
 * A waveform channel: what it watches, the name its row shows and how a bus value is written: {@link #FORMAT_HEX}, or
 * one of the other «Mostrar como» formats ({@link #FORMAT_ASCII} for bytes as characters, binary, octal, decimal,
 * signed, float and {@link #fixedFormat fixed point}). Probes are top-level only, are not document elements (never copied
 * in fragments, never part of definitions) and change only through {@code SetProbesCommand}.
 */
public record Probe(String id, ProbeTarget target, String name, String format) {

    /** Bus values as {@code 0x…}: what a probe without a format shows. */
    public static final String FORMAT_HEX = "hex";
    /** Bus values as characters (printable ASCII quoted, control bytes by name, the rest hex). */
    public static final String FORMAT_ASCII = "ascii";

    public static final String FORMAT_BINARY = ConstantValue.BINARY;
    public static final String FORMAT_OCTAL = ConstantValue.OCTAL;
    public static final String FORMAT_DECIMAL = ConstantValue.DECIMAL;
    public static final String FORMAT_SIGNED = ConstantValue.SIGNED;
    /** IEEE 754 of the channel's width; a width with no float shows hex. */
    public static final String FORMAT_FLOAT = ConstantValue.FLOAT;
    /** Fixed point is written {@code fixed:<fraction bits>}: the bits after the point travel with the format. */
    private static final String FIXED_PREFIX = ConstantValue.FIXED + ":";
    private static final Set<String> PLAIN_FORMATS = Set.of(FORMAT_HEX, FORMAT_ASCII, FORMAT_BINARY, FORMAT_OCTAL,
            FORMAT_DECIMAL, FORMAT_SIGNED, FORMAT_FLOAT);

    public Probe {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(target, "target");
        if (id.isBlank()) {
            throw new IllegalArgumentException("probe id must not be blank");
        }
        name = name == null ? "" : name;
        format = normalizedFormat(format);
    }

    /** The format for fixed point with {@code fractionBits} bits after the point. */
    public static String fixedFormat(int fractionBits) {
        return FIXED_PREFIX + fractionBits;
    }

    /** The {@link ConstantValue} format a probe format shows ({@code fixed:4} is {@code fixed}). */
    public static String valueFormatOf(String format) {
        return format.startsWith(FIXED_PREFIX) ? ConstantValue.FIXED : format;
    }

    /** The bits after the point of a fixed-point format; 0 for every other. */
    public static int fractionBitsOf(String format) {
        return format.startsWith(FIXED_PREFIX) ? Integer.parseInt(format.substring(FIXED_PREFIX.length())) : 0;
    }

    /** A known format as it is; anything else (an unknown name, a fixed one with no valid bits) is hex. */
    private static String normalizedFormat(String format) {
        if (format == null) {
            return FORMAT_HEX;
        }
        if (PLAIN_FORMATS.contains(format)) {
            return format;
        }
        if (format.startsWith(FIXED_PREFIX) && format.substring(FIXED_PREFIX.length()).matches("[0-9]{1,2}")
                && Integer.parseInt(format.substring(FIXED_PREFIX.length())) <= 64) {
            return format;
        }
        return FORMAT_HEX;
    }

    public Probe(String id, ProbeTarget target, String name) {
        this(id, target, name, FORMAT_HEX);
    }

    public Probe withName(String newName) {
        return new Probe(id, target, newName, format);
    }

    public Probe withFormat(String newFormat) {
        return new Probe(id, target, name, newFormat);
    }

    public Probe withTarget(ProbeTarget newTarget) {
        return new Probe(id, newTarget, name, format);
    }
}
