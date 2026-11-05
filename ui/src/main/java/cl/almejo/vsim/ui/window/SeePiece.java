package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.simulation.InstancePath;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;

import java.util.List;
import java.util.Optional;

/**
 * «Ver la pieza» (spec §5): shows the place that holds an instance (the main tab, or the component tab of the
 * containing definition with the route above the instance as its context), selects the instance there and travels to
 * it. The place bar shows its context's instance; the Proyecto panel any row's. Both go through
 * {@link WindowCommands#goToPart}, as F8 does.
 */
public final class SeePiece {

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
     * Shows the instance at {@code instancePath} ({@code c5/c2}) of {@code circuit} where it is. False when the path
     * names no instance or there is no window to show it in.
     */
    public static boolean show(WindowCommands window, OpenDocument circuit, String instancePath) {
        Optional<List<Component>> chain = InstanceNames.chain(instancePath, circuit.document());
        if (chain.isEmpty() || chain.get().isEmpty()) {
            return false;
        }
        List<Component> route = chain.get();
        Optional<String> containerPath = Optional.empty();
        Optional<String> containerDefinition = Optional.empty();
        if (route.size() > 1) {
            containerPath = Optional.of(String.join(InstancePath.SEPARATOR, route.subList(0, route.size() - 1).stream()
                    .map(component -> component.id().value()).toList()));
            containerDefinition = SubcircuitTypes.definitionIdOf(route.get(route.size() - 2).typeId());
            if (containerDefinition.isEmpty()) {
                return false;
            }
        }
        return window.goToPart(circuit, containerDefinition, containerPath, route.getLast().id());
    }

    /** Selects {@code part} in {@code view} and travels to it (nothing to travel to when it is gone). */
    public static void selectAndTravel(EditorView view, ElementId part) {
        OpenDocument place = view.openDocument();
        CircuitCanvas canvas = view.canvas();
        place.document().component(part).map(component -> place.registry().typeOf(component).bounds(component))
                .or(() -> place.document().wire(part).map(wire -> Bounds.of(wire.start(), wire.end())))
                .ifPresent(canvas::travelTo);
        view.selection().set(List.of(part));
        canvas.requestFocusInWindow();
    }
}
