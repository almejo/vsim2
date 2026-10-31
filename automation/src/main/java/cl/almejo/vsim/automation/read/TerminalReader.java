package cl.almejo.vsim.automation.read;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.component.standard.TerminalTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.simulation.TerminalReading;
import cl.almejo.vsim.core.terminal.ByteLog;
import cl.almejo.vsim.core.terminal.ExpectedOutput;
import cl.almejo.vsim.core.terminal.KeyEncoding;
import cl.almejo.vsim.core.terminal.TerminalFrame;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads what the AI may know of a Terminal (the screen the simulation shows, its text and bytes) and finds the Teclado
 * it may type into. Only parts placed on the circuit itself are reachable, found by id or, failing that, by their
 * only label.
 */
public final class TerminalReader {

    /** The most history rows one read returns. */
    public static final int MAXIMUM_HISTORY = 1000;
    /** The most last bytes one read returns. */
    public static final int MAXIMUM_BYTES = 256;

    static final String EXPECTED_MATCHES = "matches";
    static final String EXPECTED_DIFFERS = "differs";
    static final String EXPECTED_FROM_FILE = "file";

    private static final int FIRST_PRINTABLE = 0x20;
    private static final int DELETE = 0x7F;
    private static final int ESCAPE = 0x1B;

    /** A Terminal of the circuit: how it was set, and the expected text if the circuit holds one. */
    public record Described(String component, String profile, String mode, Optional<String> expectedText,
                            boolean expectedFromFile, ReadSource source) {
    }

    /** A Teclado of the circuit and the way it encodes what is typed. */
    public record Keyboard(String component, KeyEncoding encoding) {
    }

    private TerminalReader() {
    }

    /** Checks the sizes of a read before it is asked of the simulation. */
    public static void checkCounts(int historyLines, int byteCount) {
        if (historyLines < 0 || historyLines > MAXIMUM_HISTORY) {
            throw new AutomationException(AutomationError.INVALID_PARAMETER, "history is between 0 and "
                    + MAXIMUM_HISTORY, Map.of("maximum", MAXIMUM_HISTORY));
        }
        if (byteCount < 0 || byteCount > MAXIMUM_BYTES) {
            throw new AutomationException(AutomationError.INVALID_PARAMETER, "bytes is between 0 and "
                    + MAXIMUM_BYTES, Map.of("maximum", MAXIMUM_BYTES));
        }
    }

    /** The Terminal {@code reference} (id or label) of the circuit; INVALID_REFERENCE when it is not one. */
    public static Described describe(ReadSource source, String reference) {
        Component component = find(source, reference, StandardComponentTypes.TERMINAL, "terminal");
        ComponentType type = source.registry().typeOf(component);
        Map<String, Object> resolved = type.resolveParameters(component.parameters());
        String expectedKind = String.valueOf(resolved.get(TerminalTypes.EXPECTED));
        Optional<String> expectedText = TerminalTypes.EXPECTED_WRITTEN.equals(expectedKind)
                ? Optional.of(String.valueOf(resolved.get(TerminalTypes.EXPECTED_TEXT))) : Optional.empty();
        return new Described(component.id().value(), String.valueOf(resolved.get(TerminalTypes.PROFILE)),
                TerminalTypes.settings(resolved).mode().name(), expectedText,
                TerminalTypes.EXPECTED_FROM_FILE.equals(expectedKind), source);
    }

    /** The Teclado {@code reference} (id or label) of the circuit; INVALID_REFERENCE when it is not one. */
    public static Keyboard keyboard(ReadSource source, String reference) {
        Component component = find(source, reference, StandardComponentTypes.KEYBOARD, "keyboard (Teclado)");
        ComponentType type = source.registry().typeOf(component);
        return new Keyboard(component.id().value(),
                KeyEncoding.forParameters(type.resolveParameters(component.parameters())));
    }

    /** {@code reading} of {@code terminal} as the AI sees it. */
    public static TerminalView view(Described terminal, TerminalReading reading) {
        TerminalFrame frame = reading.frame();
        List<String> screen = new ArrayList<>(frame.rows());
        for (int row = 0; row < frame.rows(); row++) {
            screen.add(frame.rowText(row));
        }
        List<TerminalView.ByteView> bytes = new ArrayList<>();
        for (ByteLog.Entry entry : reading.bytes()) {
            bytes.add(byteView(entry));
        }
        return new TerminalView(terminal.source().id(), terminal.source().version(), terminal.component(),
                terminal.mode(), terminal.profile(), frame.rows(), frame.columns(), frame.cursorRow(),
                frame.cursorColumn(), frame.cursorVisible(), screen, reading.history(), bytes,
                expected(terminal, reading), reading.time());
    }

    private static Optional<String> expected(Described terminal, TerminalReading reading) {
        if (terminal.expectedFromFile()) {
            return Optional.of(EXPECTED_FROM_FILE);
        }
        // the window's «Salida esperada» tab compares the same way
        return terminal.expectedText().map(text -> ExpectedOutput.matches(text, reading.transcript())
                ? EXPECTED_MATCHES : EXPECTED_DIFFERS);
    }

    private static TerminalView.ByteView byteView(ByteLog.Entry entry) {
        if (entry.undefined()) {
            return new TerminalView.ByteView(entry.time(), "??", "?");
        }
        return new TerminalView.ByteView(entry.time(), String.format("%02X", entry.value()), shown(entry.value()));
    }

    /** A byte as text: printable ASCII as it is, the usual controls escaped, the rest as {@code \xNN}. */
    private static String shown(int value) {
        if (value >= FIRST_PRINTABLE && value < DELETE) {
            return String.valueOf((char) value);
        }
        return switch (value) {
            case '\n' -> "\\n";
            case '\r' -> "\\r";
            case '\t' -> "\\t";
            case ESCAPE -> "\\e";
            default -> String.format("\\x%02X", value);
        };
    }

    private static Component find(ReadSource source, String reference, String typeId, String kind) {
        Optional<Component> byId = idOf(reference).flatMap(id -> source.document().component(id));
        Optional<Component> found = byId.isPresent() ? byId : byLabel(source, reference);
        Component component = found.orElseThrow(() -> new AutomationException(AutomationError.INVALID_REFERENCE,
                "There is no part " + reference + " on the circuit itself; read the circuit again"));
        if (!component.typeId().equals(typeId)) {
            throw new AutomationException(AutomationError.INVALID_REFERENCE, reference + " is a "
                    + component.typeId() + ", not a " + kind);
        }
        return component;
    }

    private static Optional<Component> byLabel(ReadSource source, String label) {
        List<Component> matches = source.document().components().stream()
                .filter(component -> component.label().equals(label)).toList();
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    private static Optional<ElementId> idOf(String value) {
        try {
            return Optional.of(new ElementId(value));
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }
}
