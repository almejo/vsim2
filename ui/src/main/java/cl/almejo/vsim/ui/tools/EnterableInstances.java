package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.simulation.InstancePath;
import cl.almejo.vsim.ui.app.OpenDocument;

import java.util.Optional;

/**
 * Which parts can be entered (spec §4.4) and with which context: an instance of a user component whose definition is
 * embedded in the circuit (copies from the library or a Logisim import included); never a built-in part. Shared by
 * every way of entering (double click, Enter, the «Entrar ↘» chip, the context menu) so they agree.
 */
public final class EnterableInstances {

    private EnterableInstances() {
    }

    /** The definition {@code component} is an instance of, when the circuit of {@code place} has it; else empty. */
    public static Optional<String> definitionOf(OpenDocument place, Component component) {
        OpenDocument circuit = place.parent().orElse(place);
        return SubcircuitTypes.definitionIdOf(component.typeId())
                .filter(definitionId -> circuit.document().definition(definitionId).isPresent());
    }

    /**
     * The instance path entering {@code instance} shows: its id on a circuit's main place; under the context of a body
     * place («c5/c2»); empty («Sin valores») inside a body that shows no values, where no path leads to it.
     */
    public static Optional<String> contextOf(OpenDocument place, Component instance) {
        String id = instance.id().value();
        if (!place.isBody()) {
            return Optional.of(id);
        }
        return place.contextPath().map(path -> InstancePath.child(path, id));
    }
}
