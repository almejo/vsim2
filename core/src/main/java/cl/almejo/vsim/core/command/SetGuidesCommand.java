package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Guide;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Replaces the alignment guides: adding, moving, removing and clearing are all this one command (one undo step).
 * The helpers build {@code before} and {@code after} from the document's guides as they are when called, and give
 * no command when the edit would change nothing.
 */
public final class SetGuidesCommand implements Command {

    private static final String ADD_GUIDE = "command.addGuide";
    private static final String MOVE_GUIDE = "command.moveGuide";
    private static final String REMOVE_GUIDE = "command.removeGuide";
    private static final String CLEAR_GUIDES = "command.clearGuides";

    private final List<Guide> before;
    private final List<Guide> after;
    private final String descriptionKey;

    public SetGuidesCommand(List<Guide> before, List<Guide> after, String descriptionKey) {
        this.before = List.copyOf(before);
        this.after = List.copyOf(after);
        this.descriptionKey = Objects.requireNonNull(descriptionKey, "descriptionKey");
    }

    /** Adds {@code guide} at the end; empty when the document already has it. */
    public static Optional<SetGuidesCommand> adding(CircuitDocument document, Guide guide) {
        List<Guide> current = document.guides();
        if (current.contains(guide)) {
            return Optional.empty();
        }
        List<Guide> after = new ArrayList<>(current);
        after.add(guide);
        return Optional.of(new SetGuidesCommand(current, after, ADD_GUIDE));
    }

    /**
     * Moves {@code from} to {@code to}, keeping its place in the list. When {@code to} already exists the two merge
     * (the moved one disappears). Empty when {@code from} is gone or {@code to} equals it.
     */
    public static Optional<SetGuidesCommand> moving(CircuitDocument document, Guide from, Guide to) {
        List<Guide> current = document.guides();
        if (!current.contains(from) || from.equals(to)) {
            return Optional.empty();
        }
        List<Guide> after = new ArrayList<>(current);
        if (current.contains(to)) {
            after.remove(from);
        } else {
            after.set(current.indexOf(from), to);
        }
        return Optional.of(new SetGuidesCommand(current, after, MOVE_GUIDE));
    }

    /** Removes {@code guide}; empty when the document does not have it. */
    public static Optional<SetGuidesCommand> removing(CircuitDocument document, Guide guide) {
        List<Guide> current = document.guides();
        if (!current.contains(guide)) {
            return Optional.empty();
        }
        List<Guide> after = new ArrayList<>(current);
        after.remove(guide);
        return Optional.of(new SetGuidesCommand(current, after, REMOVE_GUIDE));
    }

    /** Removes every guide; empty when there are none. */
    public static Optional<SetGuidesCommand> clearing(CircuitDocument document) {
        List<Guide> current = document.guides();
        if (current.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new SetGuidesCommand(current, List.of(), CLEAR_GUIDES));
    }

    @Override
    public void apply(CircuitDocument document) {
        document.setGuides(after);
    }

    @Override
    public void revert(CircuitDocument document) {
        document.setGuides(before);
    }

    @Override
    public String description() {
        return descriptionKey;
    }
}
