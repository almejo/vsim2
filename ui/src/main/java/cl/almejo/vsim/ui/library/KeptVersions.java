package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.ui.i18n.Messages;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Taking a bundle into a circuit keeps the circuit's own version of a nested definition (preflight S3): placing from
 * the library and "Actualizar desde la librería" say so once, naming them.
 */
final class KeptVersions {

    private KeptVersions() {
    }

    /** Shows one message naming the dependencies of {@code bundle} that {@code document} has in another version. */
    static void warn(CircuitDocument document, DefinitionBundle bundle, LibraryDialogs dialogs,
                     java.awt.Component parent) {
        List<String> names = new ArrayList<>();
        for (ComponentDefinition dependency : bundle.dependencies()) {
            Optional<ComponentDefinition> existing = document.definition(dependency.id());
            if (existing.isPresent() && !existing.get().equals(dependency)) {
                names.add(Messages.format("library.place.componentName", dependency.name()));
            }
        }
        if (!names.isEmpty()) {
            dialogs.showMessage(parent, Messages.format("library.place.keptVersion", String.join(", ", names)));
        }
    }
}
