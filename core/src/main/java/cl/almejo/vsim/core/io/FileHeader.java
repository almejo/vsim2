package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.VsimVersion;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigInteger;

/**
 * The first keys of every VSim 2 file (circuit, library component, clipboard fragment): {@code "format": "vsim2"},
 * {@code "savedWith"} (the app version that wrote it), {@code "formatVersion"} and {@code "kind"}. The one place that
 * decides whether a JSON tree is a VSim 2 file this build can read.
 */
final class FileHeader {

    static final String FORMAT = "vsim2";
    static final String FORMAT_KEY = "format";
    static final String FORMAT_VERSION_KEY = "formatVersion";

    private FileHeader() {
    }

    /** The {@code "savedWith"} value written in new files. */
    static String savedWith() {
        return VsimVersion.current();
    }

    /**
     * Whether {@code root} says it is a VSim 2 file: an object with the marker or, for files written before it, with
     * a {@code formatVersion}. It may still be refused by {@link #check}.
     */
    static boolean claimsVsim2(JsonNode root) {
        return root != null && root.isObject() && (root.has(FORMAT_KEY) || root.has(FORMAT_VERSION_KEY));
    }

    /**
     * Refuses what this build cannot read: not a VSim file ({@code NOT_VSIM_FILE}: no object, another marker, or
     * neither marker nor version), a newer {@code formatVersion} ({@code NEWER_FORMAT}, detail = that version), or any
     * other version ({@code UNSUPPORTED_VERSION}).
     */
    static void check(JsonNode root) throws CircuitFormatException {
        if (!claimsVsim2(root)) {
            throw new CircuitFormatException(CircuitFormatException.Reason.NOT_VSIM_FILE,
                    root != null && root.isObject() ? "no format marker" : "root must be a JSON object");
        }
        JsonNode marker = root.path(FORMAT_KEY);
        if (!marker.isMissingNode() && !FORMAT.equals(marker.asText(null))) {
            throw new CircuitFormatException(CircuitFormatException.Reason.NOT_VSIM_FILE, "format " + marker);
        }
        JsonNode version = root.path(FORMAT_VERSION_KEY);
        if (version.isIntegralNumber() && version.canConvertToInt()
                && version.intValue() == CircuitFormat.FORMAT_VERSION) {
            return;
        }
        if (version.isIntegralNumber()
                && version.bigIntegerValue().compareTo(BigInteger.valueOf(CircuitFormat.FORMAT_VERSION)) > 0) {
            throw new CircuitFormatException(CircuitFormatException.Reason.NEWER_FORMAT, version.asText());
        }
        throw new CircuitFormatException(CircuitFormatException.Reason.UNSUPPORTED_VERSION,
                "formatVersion " + (version.isMissingNode() ? "missing" : version.toString()));
    }
}
