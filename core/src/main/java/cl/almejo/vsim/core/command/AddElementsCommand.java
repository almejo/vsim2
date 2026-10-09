package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.DocumentElement;

import java.util.ArrayList;
import java.util.List;

public final class AddElementsCommand implements Command {

    private final List<DocumentElement> elements;

    public AddElementsCommand(List<? extends DocumentElement> elements) {
        this.elements = List.copyOf(elements);
    }

    @Override
    public void apply(CircuitDocument document) {
        document.batch(() -> {
            List<DocumentElement> added = new ArrayList<>();
            try {
                for (DocumentElement element : elements) {
                    document.add(element);
                    added.add(element);
                }
            } catch (RuntimeException exception) {
                added.forEach(element -> document.remove(element.id()));
                throw exception;
            }
        });
    }

    @Override
    public void revert(CircuitDocument document) {
        document.batch(() -> elements.forEach(element -> document.remove(element.id())));
    }

    @Override
    public String description() {
        return "command.add";
    }
}
