package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.io.CircuitFragment;

import java.util.ArrayList;
import java.util.List;

/**
 * Adds a fragment with its (0,0) at {@code at}, giving every element a fresh id of the target document.
 * Ids are chosen on the first {@link #apply}; redo reuses them. Wires follow the {@link AddWireCommand} merge rules.
 */
public final class PasteCommand implements Command {

    private final CircuitFragment fragment;
    private final GridPoint at;
    private CompositeCommand steps;
    private List<ElementId> assignedIds = List.of();
    private List<ElementId> pastedIds = List.of();

    public PasteCommand(CircuitFragment fragment, GridPoint at) {
        this.fragment = fragment;
        this.at = at;
    }

    @Override
    public void apply(CircuitDocument document) {
        if (steps == null) {
            steps = buildSteps(document);
        }
        steps.apply(document);
        pastedIds = assignedIds.stream().filter(id -> document.element(id).isPresent()).toList();
    }

    @Override
    public void revert(CircuitDocument document) {
        if (steps != null) {
            steps.revert(document);
        }
    }

    /**
     * Ids of the pasted elements present in the document after the last {@link #apply}. A pasted wire that was absorbed
     * by another pasted wire (collinear wires are merged) is no longer in the document, so the absorbing wire
     * represents it.
     */
    public List<ElementId> pastedIds() {
        return pastedIds;
    }

    @Override
    public String description() {
        return "command.paste";
    }

    private CompositeCommand buildSteps(CircuitDocument document) {
        List<DocumentElement> elements = new ArrayList<>();
        List<ElementId> ids = new ArrayList<>();
        for (Component component : fragment.components()) {
            Component placed = new Component(document.newComponentId(), component.typeId(),
                    component.position().plus(at), component.rotation(), component.parameters(), component.label());
            elements.add(placed);
            ids.add(placed.id());
        }
        for (TextLabel label : fragment.labels()) {
            TextLabel placed = new TextLabel(document.newLabelId(), label.position().plus(at), label.text());
            elements.add(placed);
            ids.add(placed.id());
        }
        List<Command> commands = new ArrayList<>();
        commands.add(new AddElementsCommand(elements));
        for (Wire wire : fragment.wires()) {
            Wire placed = new Wire(document.newWireId(), wire.start().plus(at), wire.end().plus(at));
            commands.add(new AddWireCommand(placed));
            ids.add(placed.id());
        }
        assignedIds = List.copyOf(ids);
        return new CompositeCommand(description(), commands);
    }
}
