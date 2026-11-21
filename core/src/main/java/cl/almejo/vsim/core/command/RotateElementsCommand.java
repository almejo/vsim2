package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ElementBounds;
import cl.almejo.vsim.core.component.standard.MultiBitOrientation;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Turns components (position and rotation), wires, text labels and frames a quarter turn as a group, around the grid
 * point nearest the center of their bounds. Text labels move but stay upright; a frame's rectangle turns (its title
 * stays on top). A multi-bit Switch or LED never turns its body: its pin moves to the next side, alone with the body
 * in place, in a group to where the turn takes the pin ({@link MultiBitOrientation}). Wires are not merged (like a
 * move). Elements that look the same after the turn (a lone text label) are left untouched, and if none changes the
 * document is not touched at all.
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

    /**
     * {@code elements} turned a quarter turn as a group, exactly as the command would turn them in a document, in the
     * same order (an element that looks the same after the turn comes back as it was). For a floating selection.
     */
    public static List<DocumentElement> turnedGroup(List<DocumentElement> elements, boolean clockwise,
                                                    ComponentTypeRegistry registry) {
        RotateElementsCommand command = new RotateElementsCommand(List.of(), clockwise, registry);
        Map<DocumentElement, DocumentElement> after = new IdentityHashMap<>();
        command.turnedElements(elements).forEach(turn -> after.put(turn.before(), turn.after()));
        return elements.stream().map(element -> after.getOrDefault(element, element)).toList();
    }

    /** The elements the turn changes, in selection order. */
    private List<Turn> turned(CircuitDocument document) {
        return turnedElements(ids.stream().map(document::element).flatMap(Optional::stream).toList());
    }

    private List<Turn> turnedElements(List<DocumentElement> elements) {
        Optional<Bounds> bounds = ElementBounds.union(elements, registry);
        if (bounds.isEmpty()) {
            return List.of();
        }
        Pivot pivot = pivotOf(bounds.get());
        Rotation turn = clockwise ? Rotation.R90 : Rotation.R270;
        if (elements.size() == 1 && elements.getFirst() instanceof Component lone && turnsBySide(lone)) {
            return List.of(new Turn(lone, MultiBitOrientation.turnedInPlace(lone, typeOf(lone), turn)));
        }
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

    /** A turning point in half units: (2, 3) is the grid point (1, 1.5). Its two halves have the same parity. */
    record Pivot(int doubledX, int doubledY) {

        GridPoint turn(GridPoint point, Rotation turn) {
            GridPoint offset = turn.apply(new GridPoint(2 * point.x() - doubledX, 2 * point.y() - doubledY));
            return new GridPoint((doubledX + offset.x()) / 2, (doubledY + offset.y()) / 2);
        }
    }

    /**
     * The point a group turns about. A centre on the grid (or in the middle of a cell) is used as it is. A centre
     * halfway along one axis only would send grid points between grid points, so it moves half a unit to the
     * middle of one of the two cells next to it, picked so that every centre of the four a group has while it turns
     * picks the same cell: the group then turns about one fixed point and four turns bring it back exactly.
     */
    static Pivot pivotOf(Bounds bounds) {
        int doubledX = 2 * bounds.x() + bounds.width();
        int doubledY = 2 * bounds.y() + bounds.height();
        if (Math.floorMod(doubledX, 2) == Math.floorMod(doubledY, 2)) {
            return new Pivot(doubledX, doubledY);
        }
        // Cells of the checkerboard whose lower-left grid corner has an even coordinate sum are the pivots.
        if (doubledX % 2 != 0) {
            int column = Math.floorDiv(doubledX, 2);
            int row = Math.floorDiv(doubledY, 2);
            return new Pivot(doubledX, Math.floorMod(column + row, 2) == 0 ? doubledY + 1 : doubledY - 1);
        }
        int column = Math.floorDiv(doubledX, 2);
        int row = Math.floorDiv(doubledY, 2);
        return new Pivot(Math.floorMod(column + row, 2) == 0 ? doubledX + 1 : doubledX - 1, doubledY);
    }

    private DocumentElement rotated(DocumentElement element, Pivot pivot, Rotation turn) {
        return switch (element) {
            case Component component when turnsBySide(component) ->
                    MultiBitOrientation.turnedAbout(component, typeOf(component), point -> pivot.turn(point, turn),
                            turn);
            case Component component -> component
                    .withPosition(around(component.position(), pivot, turn))
                    .withRotation(clockwise ? component.rotation().clockwise() : component.rotation().counterClockwise());
            case Wire wire -> wire.withEndpoints(around(wire.start(), pivot, turn), around(wire.end(), pivot, turn));
            case TextLabel label -> label.placed(label.id(), around(label.position(), pivot, turn));
            case Frame frame -> rotatedFrame(frame, pivot, turn);
            // The paper keeps its shape; only its anchor moves, like a plain note.
            case StickyNote note -> note.placed(note.id(), around(note.position(), pivot, turn));
        };
    }

    private boolean turnsBySide(Component component) {
        return registry.findTypeOf(component)
                .map(type -> MultiBitOrientation.turnsBySide(component, type)).orElse(false);
    }

    private ComponentType typeOf(Component component) {
        return registry.typeOf(component);
    }

    /**
     * The frame's rectangle turned about {@code pivot}: width and height swap and the title stays on the top edge of
     * the new rectangle. A side that would end up under the minimum size grows to it, rightwards or downwards.
     */
    private static Frame rotatedFrame(Frame frame, Pivot pivot, Rotation turn) {
        GridPoint corner = around(frame.topLeft(), pivot, turn);
        GridPoint opposite = around(frame.topLeft().translate(frame.width(), frame.height()), pivot, turn);
        GridPoint topLeft = new GridPoint(Math.min(corner.x(), opposite.x()), Math.min(corner.y(), opposite.y()));
        return frame.withRectangle(topLeft, Math.max(Frame.MINIMUM_WIDTH, Math.abs(opposite.x() - corner.x())),
                Math.max(Frame.MINIMUM_HEIGHT, Math.abs(opposite.y() - corner.y())));
    }

    private static GridPoint around(GridPoint point, Pivot pivot, Rotation turn) {
        return pivot.turn(point, turn);
    }

    private record Turn(DocumentElement before, DocumentElement after) {
    }
}
