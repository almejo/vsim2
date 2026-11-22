package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Removes elements; undo puts each one back at its original paint and file position. */
public final class RemoveElementsCommand implements Command {

    private record RemovedElement(DocumentElement element, long order) {
    }

    private final List<ElementId> ids;
    private final List<RemovedElement> removed = new ArrayList<>();

    public RemoveElementsCommand(Collection<ElementId> ids) {
        this.ids = List.copyOf(ids);
    }

    @Override
    public void apply(CircuitDocument document) {
        removed.clear();
        document.batch(() -> {
            for (ElementId id : ids) {
                if (document.element(id).isPresent()) {
                    long order = document.orderOf(id);
                    document.remove(id).ifPresent(element -> removed.add(new RemovedElement(element, order)));
                }
            }
        });
    }

    @Override
    public void revert(CircuitDocument document) {
        document.batch(() -> removed.forEach(entry -> document.restore(entry.element(), entry.order())));
    }

    @Override
    public String description() {
        return "command.remove";
    }
}
