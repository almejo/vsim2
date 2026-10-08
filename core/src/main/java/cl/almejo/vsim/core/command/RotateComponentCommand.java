package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;

public final class RotateComponentCommand implements Command {

    private final ElementId id;
    private final boolean clockwise;

    public RotateComponentCommand(ElementId id, boolean clockwise) {
        this.id = id;
        this.clockwise = clockwise;
    }

    @Override
    public void apply(CircuitDocument document) {
        rotate(document, clockwise);
    }

    @Override
    public void revert(CircuitDocument document) {
        rotate(document, !clockwise);
    }

    private void rotate(CircuitDocument document, boolean towardsClockwise) {
        Component component = document.component(id).orElseThrow();
        document.replace(component.withRotation(towardsClockwise
                ? component.rotation().clockwise()
                : component.rotation().counterClockwise()));
    }

    @Override
    public String description() {
        return "command.rotate";
    }
}
