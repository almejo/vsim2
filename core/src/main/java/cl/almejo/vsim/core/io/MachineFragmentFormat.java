package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.machine.StateMachine;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Optional;

/**
 * The clipboard text of states and arrows copied from a machine's diagram (hand test of hito 2: Ctrl+C / Ctrl+X /
 * Ctrl+V in a machine's tab): a VSim file header with its own {@code kind}, so a circuit never reads it as one of its
 * fragments ({@link FragmentFormat}) and a machine never reads a circuit's, and the piece as a machine
 * ({@link MachineEntries}): the copied states with the arrows between them, without an initial state or pins.
 */
public final class MachineFragmentFormat {

    static final String KIND_MACHINE_FRAGMENT = "machine-fragment";
    private static final String KIND = "kind";
    private static final String PIECE_ID = "clipboard";

    record MachineFragmentFile(String format, String savedWith, int formatVersion, String kind,
                               MachineEntries.MachineEntry machine) {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private MachineFragmentFormat() {
    }

    /** {@code piece} as clipboard text. */
    public static String write(StateMachine piece) {
        MachineFragmentFile file = new MachineFragmentFile(FileHeader.FORMAT, FileHeader.savedWith(),
                CircuitFormat.FORMAT_VERSION, KIND_MACHINE_FRAGMENT, MachineEntries.entry(piece));
        try {
            return MAPPER.writeValueAsString(file);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("cannot serialize machine piece", exception);
        }
    }

    /** The piece {@code json} carries; empty for any other text (a circuit's fragment, plain text, a broken piece). */
    public static Optional<StateMachine> read(String json) {
        if (!isMachineFragment(json)) {
            return Optional.empty();
        }
        try {
            JsonNode root = CircuitFormat.parse(MAPPER, json);
            MachineFragmentFile file = CircuitFormat.readFile(MAPPER, root, MachineFragmentFile.class,
                    KIND_MACHINE_FRAGMENT, MachineFragmentFile::kind);
            if (file.machine() == null) {
                return Optional.empty();
            }
            return Optional.of(MachineEntries.machine(file.machine(), PIECE_ID));
        } catch (CircuitFormatException exception) {
            return Optional.empty();
        }
    }

    /** Whether {@code json} is VSim's text of copied states and arrows. */
    public static boolean isMachineFragment(String json) {
        return kindOf(json).filter(KIND_MACHINE_FRAGMENT::equals).isPresent();
    }

    /** Whether {@code json} is VSim's text of a circuit's copied parts and wires. */
    public static boolean isCircuitFragment(String json) {
        return kindOf(json).filter(FragmentFormat.KIND_FRAGMENT::equals).isPresent();
    }

    /** The {@code kind} of a VSim text; empty for any other text. */
    private static Optional<String> kindOf(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            if (root == null || !FileHeader.FORMAT.equals(root.path("format").asText())) {
                return Optional.empty();
            }
            JsonNode kind = root.path(KIND);
            return kind.isTextual() ? Optional.of(kind.asText()) : Optional.empty();
        } catch (JsonProcessingException exception) {
            return Optional.empty();
        }
    }
}
