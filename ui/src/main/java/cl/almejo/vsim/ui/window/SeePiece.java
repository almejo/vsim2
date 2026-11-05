package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.component.ElementBounds;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.simulation.InstancePath;
import cl.almejo.vsim.ui.app.OpenDocument;

import java.util.List;
import java.util.Optional;

/**
 * «Ver la pieza ↗» of the place bar (spec §5.1): shows the place that holds the instance the tab's context points
 * at (the main tab, or the component tab of the containing definition with the route above the instance as its
 * context), selects the instance there and travels to it.
 */
public final class SeePiece {

    private SeePiece() {
    }

    /** Does it for the place {@code view} shows; nothing without a context («Sin valores») or a real window. */
    public static void show(EditorView view) {
        OpenDocument place = view.openDocument();
        Optional<OpenDocument> parent = place.parent();
        if (parent.isEmpty() || place.contextPath().isEmpty()) {
            return;
        }
        Optional<List<Component>> chain = InstanceNames.chain(place.contextPath().get(), parent.get().document());
        if (chain.isEmpty() || chain.get().isEmpty()) {
            return;
        }
        List<Component> route = chain.get();
        Component instance = route.getLast();
        Optional<String> containerPath = Optional.empty();
        Optional<String> containerDefinition = Optional.empty();
        if (route.size() > 1) {
            containerPath = Optional.of(String.join(InstancePath.SEPARATOR, route.subList(0, route.size() - 1).stream()
                    .map(component -> component.id().value()).toList()));
            containerDefinition = SubcircuitTypes.definitionIdOf(route.get(route.size() - 2).typeId());
            if (containerDefinition.isEmpty()) {
                return;
            }
        }
        view.windowCommands().showPlace(parent.get(), containerDefinition, containerPath).ifPresent(target -> {
            target.canvas().selection().select(instance.id());
            target.canvas().travelTo(ElementBounds.of(instance, target.openDocument().registry()));
        });
    }
}
