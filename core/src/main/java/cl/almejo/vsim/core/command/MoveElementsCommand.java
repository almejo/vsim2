package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;

import java.util.Collection;
import java.util.List;

public final class MoveElementsCommand implements Command {

    private final List<ElementId> ids;
    private final int deltaX;
    private final int deltaY;

    public MoveElementsCommand(Collection<ElementId> ids, int deltaX, int deltaY) {
        this.ids = List.copyOf(ids);
        this.deltaX = deltaX;
        this.deltaY = deltaY;
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
