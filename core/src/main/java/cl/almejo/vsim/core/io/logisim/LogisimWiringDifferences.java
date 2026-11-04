package cl.almejo.vsim.core.io.logisim;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The parts of a laid-out circuit that, wired as the file wires them, do not do in VSim what they do in Logisim (the
 * «Diferencias reales»): a loose end that Logisim reads otherwise than VSim, or a connected end whose meaning differs.
 * Each is one {@link ImportNote.Kind#DIFFERENT} note at the part, never hidden and never «fixed» by connecting
 * something the file does not have. A loose end that both read the same (a register's EN = 1, CLR = 0, an adder's
 * Cin = 0, a memory's CS = 1) is no note: inside a component it is no problem either, as on the canvas.
 */
final class LogisimWiringDifferences {

    /** One end of a Logisim part that matters when it is loose ({@code whenConnected} false) or when connected. */
    private record Difference(String end, boolean whenConnected, String messageKey) {
    }

    /**
     * Logisim part (by library and name) → its ends that differ. RAM: with separate data it writes
     * when WE ≠ 0, so a loose WE writes on every rising edge; VSim's RAM reads a loose WE as 0.
     * Controlled buffer: a loose control gives an error; VSim's buffer is off (Z). Counter:
     * LD = 1 counts down unless CT = 0 and loads only with CT = 0; VSim's loads whenever LD = 1.
     */
    private static final Map<String, List<Difference>> DIFFERENCES = Map.of(
            LogisimPartRules.key(LogisimLibraries.MEMORY, "RAM"),
            List.of(new Difference("WE", false, LogisimNotes.LOOSE_WRITE_ENABLE)),
            LogisimPartRules.key(LogisimLibraries.GATES, "Controlled Buffer"),
            List.of(new Difference("enable", false, LogisimNotes.LOOSE_BUFFER_CONTROL)),
            LogisimPartRules.key(LogisimLibraries.GATES, "Controlled Inverter"),
            List.of(new Difference("enable", false, LogisimNotes.LOOSE_BUFFER_CONTROL)),
            LogisimPartRules.key(LogisimLibraries.MEMORY, "Counter"),
            List.of(new Difference("LD", true, LogisimNotes.COUNTER_LOAD)));

    private LogisimWiringDifferences() {
    }

    /** One note per placed part of {@code laidOut} and end that differs, in the circuit's order; the part's name. */
    static List<ImportNote> notesFor(LogisimNetAnalysis analysis, LaidOutCircuit laidOut) {
        List<ImportNote> notes = new ArrayList<>();
        for (LogisimComponent component : analysis.circuit().components()) {
            List<Difference> differences = DIFFERENCES.get(LogisimPartRules.key(component.library(),
                    component.name()));
            if (differences == null || !laidOut.parts().containsKey(component)) {
                continue;
            }
            for (Difference difference : differences) {
                analysis.ends(component).stream()
                        .filter(end -> end.name().equals(difference.end()))
                        .filter(end -> analysis.isConnected(end.point()) == difference.whenConnected())
                        .findFirst()
                        .ifPresent(end -> notes.add(ImportNote.at(ImportNote.Kind.DIFFERENT,
                                analysis.circuit().name(), component.location(), difference.messageKey(),
                                component.name())));
            }
        }
        return notes;
    }
}
