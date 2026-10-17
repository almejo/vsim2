package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.LabelOffset;

import java.util.Optional;

/** Moves a component's label to {@code offset}, or back to its automatic place when the offset is empty. */
public final class SetLabelOffsetCommand implements Command {

    private final ElementId componentId;
    private final Optional<LabelOffset> offset;
    private Optional<LabelOffset> previousOffset = Optional.empty();

    public SetLabelOffsetCommand(ElementId componentId, Optional<LabelOffset> offset) {
        this.componentId = componentId;
        this.offset = offset == null ? Optional.empty() : offset;
    }

    @Override
    public void apply(CircuitDocument document) {
        Component component = document.component(componentId).orElseThrow();
        previousOffset = component.labelOffset();
        document.replace(component.withLabelOffset(offset));
    }

    @Override
    public void revert(CircuitDocument document) {
        document.replace(document.component(componentId).orElseThrow().withLabelOffset(previousOffset));
    }

    @Override
    public String description() {
        return offset.isPresent() ? "command.moveLabel" : "command.resetLabelPosition";
    }
}
