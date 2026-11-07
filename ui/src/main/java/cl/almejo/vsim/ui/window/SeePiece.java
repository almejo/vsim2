package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.simulation.InstancePath;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;

import java.util.List;
import java.util.Optional;

/**
 * «Ver la pieza» (spec §5): shows the place that holds a piece (the main tab, or the component tab of the containing
 * definition with the route above the piece as its context), selects the piece there and travels to it. The place
 * bar shows its context's instance; the circuit tree and F8 any piece. All go through {@link WindowCommands#goToPart}.
 */
public final class SeePiece {

    /**
     * Where a piece is: part {@code part} of the circuit ({@code definitionId} empty) or of the body of
     * {@code definitionId} with the values of the instance at {@code contextPath}.
     */
    public record Target(Optional<String> definitionId, Optional<String> contextPath, ElementId part) {
    }

    private SeePiece() {
    }

    /** The place bar's: the instance {@code view}'s context points at; nothing without a context («Sin valores»). */
    public static boolean show(EditorView view) {
        OpenDocument place = view.openDocument();
        Optional<OpenDocument> parent = place.parent();
        if (parent.isEmpty() || place.contextPath().isEmpty()) {
            return false;
        }
        return show(view.windowCommands(), parent.get(), place.contextPath().get());
    }

    /**
     * Shows the piece at {@code path} ({@code c5/c2}) of {@code circuit} where it is. False when the path names no
     * piece or there is no window to show it in.
     */
    public static boolean show(WindowCommands window, OpenDocument circuit, String path) {
        return targetOf(path, circuit.document())
                .map(target -> window.goToPart(circuit, target.definitionId(), target.contextPath(), target.part()))
                .orElse(false);
    }

    /**
     * Where the piece {@code path} of {@code circuit} is: on the circuit when it is a top-level id, else in the body of
     * the definition of the instance above it ({@code c5/c3/c2} → the definition of {@code c5/c3}, with {@code c5/c3}
     * as the values, part {@code c2}). Empty when an instance above the part is not in the circuit any more (the part
     * itself may be a wire: it is not looked for).
     */
    public static Optional<Target> targetOf(String path, CircuitDocument circuit) {
        List<String> segments = InstancePath.segments(path);
        ElementId part = new ElementId(segments.getLast());
        List<Component> level = circuit.components();
        Optional<ComponentDefinition> definition = Optional.empty();
        for (String segment : segments.subList(0, segments.size() - 1)) {
            Optional<Component> instance = level.stream()
                    .filter(component -> component.id().value().equals(segment)).findFirst();
            definition = instance.flatMap(found -> SubcircuitTypes.definitionIdOf(found.typeId()))
                    .flatMap(circuit::definition);
            if (definition.isEmpty()) {
                return Optional.empty();
            }
            level = definition.get().body().components();
        }
        Optional<String> container = segments.size() == 1 ? Optional.empty()
                : Optional.of(path.substring(0, path.lastIndexOf(InstancePath.SEPARATOR)));
        return Optional.of(new Target(definition.map(ComponentDefinition::id), container, part));
    }

    /** Selects {@code part} in {@code view} and travels to it (nothing to travel to when it is gone). */
    public static void selectAndTravel(EditorView view, ElementId part) {
        OpenDocument place = view.openDocument();
        CircuitCanvas canvas = view.canvas();
        place.document().component(part).map(component -> place.registry().typeOf(component).bounds(component))
                .or(() -> place.document().wire(part).map(wire -> Bounds.of(wire.start(), wire.end())))
                .ifPresent(bounds -> {
                    canvas.travelTo(bounds);
                    canvas.glow(bounds); // the eye finds the piece after the view moved (spec §5.1)
                });
        view.selection().set(List.of(part));
        canvas.requestFocusInWindow();
    }
}
