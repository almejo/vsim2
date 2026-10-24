package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ElementBounds;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Turns components (position and rotation), wires, text labels and frames a quarter turn as a group, around the grid
 * point nearest the center of their bounds. Text labels move but stay upright; a frame's rectangle turns (its title
 * stays on top). Wires are not merged (like a move). Elements that look the same after the turn (a lone text label) are left
 * untouched, and if none changes the document is not touched at all.
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

    /**
     * Whether applying this command now would change {@code document}. Callers use it to keep a turn that changes
     * nothing (a lone text label) out of the undo history.
     */
    public boolean changes(CircuitDocument document) {
        return !turned(document).isEmpty();
    }

    @Override
    public void apply(CircuitDocument document) {
        List<Turn> turned = turned(document);
        previous = turned.stream().map(Turn::before).toList();
        if (turned.isEmpty()) {
            return;
        }
        document.batch(() -> turned.forEach(change -> document.replace(change.after())));
    }

    /** The elements the turn changes, in selection order. */
    private List<Turn> turned(CircuitDocument document) {
        List<DocumentElement> elements = ids.stream().map(document::element).flatMap(Optional::stream).toList();
        Optional<Bounds> bounds = ElementBounds.union(elements, registry);
        if (bounds.isEmpty()) {
            return List.of();
        }
        GridPoint pivot = pivotOf(bounds.get());
        Rotation turn = clockwise ? Rotation.R90 : Rotation.R270;
        List<Turn> turned = new ArrayList<>();
        for (DocumentElement element : elements) {
            DocumentElement rotated = rotated(element, pivot, turn);
            if (!rotated.equals(element)) {
                turned.add(new Turn(element, rotated));
            }
        }
        return turned;
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
            case TextLabel label -> label.placed(label.id(), around(label.position(), pivot, turn));
            case Frame frame -> rotatedFrame(frame, pivot, turn);
        };
    }

    /**
     * The frame's rectangle turned about {@code pivot}: width and height swap and the title stays on the top edge of
     * the new rectangle. A side that would end up under the minimum size grows to it, rightwards or downwards.
     */
    private static Frame rotatedFrame(Frame frame, GridPoint pivot, Rotation turn) {
        GridPoint corner = around(frame.topLeft(), pivot, turn);
        GridPoint opposite = around(frame.topLeft().translate(frame.width(), frame.height()), pivot, turn);
        GridPoint topLeft = new GridPoint(Math.min(corner.x(), opposite.x()), Math.min(corner.y(), opposite.y()));
        return frame.withRectangle(topLeft, Math.max(Frame.MINIMUM_WIDTH, Math.abs(opposite.x() - corner.x())),
                Math.max(Frame.MINIMUM_HEIGHT, Math.abs(opposite.y() - corner.y())));
    }

    private static GridPoint around(GridPoint point, GridPoint pivot, Rotation turn) {
        return pivot.plus(turn.apply(new GridPoint(point.x() - pivot.x(), point.y() - pivot.y())));
    }

    private record Turn(DocumentElement before, DocumentElement after) {
    }
}
