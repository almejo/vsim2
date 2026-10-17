package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

public final class MoveElementsCommand implements Command {

    private final List<ElementId> ids;
    private final int deltaX;
    private final int deltaY;
    private final boolean nudge;

    public MoveElementsCommand(Collection<ElementId> ids, int deltaX, int deltaY) {
        this(ids, deltaX, deltaY, false);
    }

    private MoveElementsCommand(Collection<ElementId> ids, int deltaX, int deltaY, boolean nudge) {
        this.ids = List.copyOf(ids);
        this.deltaX = deltaX;
        this.deltaY = deltaY;
        this.nudge = nudge;
    }

    /**
     * A small move by keyboard: a run of nudges of the same elements, one right after the other, merges into one undo
     * step. A move made by dragging never merges.
     */
    public static MoveElementsCommand nudge(Collection<ElementId> ids, int deltaX, int deltaY) {
        return new MoveElementsCommand(ids, deltaX, deltaY, true);
    }

    @Override
    public Optional<Command> mergedWith(Command next) {
        if (nudge && next instanceof MoveElementsCommand following && following.nudge
                && new HashSet<>(ids).equals(new HashSet<>(following.ids))) {
            return Optional.of(new MoveElementsCommand(ids, deltaX + following.deltaX, deltaY + following.deltaY,
                    true));
        }
        return Optional.empty();
    }

    @Override
    public boolean changesNothing() {
        return deltaX == 0 && deltaY == 0;
    }

    @Override
    public void apply(CircuitDocument document) {
        translate(document, deltaX, deltaY);
    }

    @Override
    public void revert(CircuitDocument document) {
        translate(document, -deltaX, -deltaY);
    }

    private void translate(CircuitDocument document, int byX, int byY) {
        document.batch(() -> ids.forEach(id -> document.element(id)
                .ifPresent(element -> document.replace(element.translate(byX, byY)))));
    }

    @Override
    public String description() {
        return "command.move";
    }
}
