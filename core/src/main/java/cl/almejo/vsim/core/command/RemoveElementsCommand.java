package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class RemoveElementsCommand implements Command {

    private final List<ElementId> ids;
    private final List<DocumentElement> removed = new ArrayList<>();

    public RemoveElementsCommand(Collection<ElementId> ids) {
        this.ids = List.copyOf(ids);
    }

    @Override
    public void apply(CircuitDocument document) {
        removed.clear();
        document.batch(() -> ids.forEach(id -> document.remove(id).ifPresent(removed::add)));
    }

    @Override
    public void revert(CircuitDocument document) {
        document.batch(() -> removed.forEach(document::add));
    }

    @Override
    public String description() {
        return "command.remove";
    }
}
