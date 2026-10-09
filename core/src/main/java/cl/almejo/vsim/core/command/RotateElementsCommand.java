package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ElementBounds;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Turns components (position and rotation), wires and text labels a quarter turn as a group, around the grid point
 * nearest the center of their bounds. Text labels move but stay upright. Wires are not merged (like a move).
 */
public final class RotateElementsCommand implements Command {

    private final List<ElementId> ids;
    private final boolean clockwise;
    private final ComponentTypeRegistry registry;
    private List<DocumentElement> previous = List.of();

    public RotateElementsCommand(Collection<ElementId> ids, boolean clockwise, ComponentTypeRegistry registry) {
        this.ids = List.copyOf(ids);
        this.clockwise = clockwise;
        this.registry = registry;
    }

    @Override
    public void apply(CircuitDocument document) {
        List<DocumentElement> elements = ids.stream().map(document::element).flatMap(Optional::stream).toList();
        previous = elements;
        Optional<Bounds> bounds = ElementBounds.union(elements, registry);
        if (bounds.isEmpty()) {
            return;
        }
        GridPoint pivot = pivotOf(bounds.get());
        Rotation turn = clockwise ? Rotation.R90 : Rotation.R270;
        document.batch(() -> elements.forEach(element -> document.replace(rotated(element, pivot, turn))));
    }

    @Override
    public void revert(CircuitDocument document) {
        document.batch(() -> previous.forEach(document::replace));
    }

    @Override
    public String description() {
        return "command.rotate";
    }

    static GridPoint pivotOf(Bounds bounds) {
        return new GridPoint((int) Math.round(bounds.x() + bounds.width() / 2.0),
                (int) Math.round(bounds.y() + bounds.height() / 2.0));
    }

    private DocumentElement rotated(DocumentElement element, GridPoint pivot, Rotation turn) {
        return switch (element) {
            case Component component -> component
                    .withPosition(around(component.position(), pivot, turn))
                    .withRotation(clockwise ? component.rotation().clockwise() : component.rotation().counterClockwise());
            case Wire wire -> wire.withEndpoints(around(wire.start(), pivot, turn), around(wire.end(), pivot, turn));
            case TextLabel label -> new TextLabel(label.id(), around(label.position(), pivot, turn), label.text());
        };
    }

    private static GridPoint around(GridPoint point, GridPoint pivot, Rotation turn) {
        return pivot.plus(turn.apply(new GridPoint(point.x() - pivot.x(), point.y() - pivot.y())));
    }
}
