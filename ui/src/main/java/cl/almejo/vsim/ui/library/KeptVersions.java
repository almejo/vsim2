package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.i18n.Messages;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Taking a bundle into a circuit keeps the circuit's own version of a nested definition (preflight S3): placing from
 * the library, "Actualizar desde la librería" and a drop or paste from another circuit say so once, naming them.
 */
final class KeptVersions {

    private KeptVersions() {
    }

    /** Shows one message naming the dependencies of {@code bundle} that {@code document} has in another version. */
    static void warn(CircuitDocument document, DefinitionBundle bundle, LibraryDialogs dialogs,
                     java.awt.Component parent) {
        message(document, bundle.dependencies()).ifPresent(message -> dialogs.showMessage(parent, message));
    }

    /**
     * The same message for a fragment dropped or pasted into {@code document}: it names the nested definitions (the
     * ones its instances do not use directly, as for a bundle's dependencies) that {@code document} has in another
     * version. Empty when there are none.
     */
    static Optional<String> message(CircuitDocument document, CircuitFragment fragment) {
        Set<String> direct = Set.copyOf(SubcircuitTypes.definitionIds(fragment.components()));
        return message(document, fragment.definitions().stream()
                .filter(definition -> !direct.contains(definition.id()))
                .toList());
    }

    private static Optional<String> message(CircuitDocument document, List<ComponentDefinition> nested) {
        List<String> names = new ArrayList<>();
        for (ComponentDefinition dependency : nested) {
            Optional<ComponentDefinition> existing = document.definition(dependency.id());
            if (existing.isPresent() && !existing.get().equals(dependency)) {
                names.add(Messages.format("library.place.componentName", dependency.name()));
            }
        }
        if (names.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Messages.format("library.place.keptVersion", String.join(", ", names)));
    }
}
