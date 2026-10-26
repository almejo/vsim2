package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.io.CircuitFormatException;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.DefinitionIds;
import cl.almejo.vsim.core.library.DefinitionNames;
import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.Icons;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.Action;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * "Importar componente…" (spec §5.6): copies {@code .vsimcomponent.json} files into the library. An id the library
 * already has asks to replace it or keep both (the copy gets a new id and, if needed, a new name).
 */
public final class LibraryImport {

    private static final Logger LOGGER = LoggerFactory.getLogger(LibraryImport.class);

    /** Reads one component file; the service in the application, a stand-in in tests. */
    @FunctionalInterface
    interface BundleReader {
        DefinitionBundle read(Path file) throws IOException, CircuitFormatException;
    }

    private final LibraryService service;
    private final LibraryDialogs dialogs;
    private final BundleReader reader;

    public LibraryImport(LibraryService service, LibraryDialogs dialogs) {
        this(service, dialogs, service::read);
    }

    LibraryImport(LibraryService service, LibraryDialogs dialogs, BundleReader reader) {
        this.service = service;
        this.dialogs = dialogs;
        this.reader = reader;
    }

    /** The menu action: chooses files with {@code parent} as dialog owner and imports them. */
    public Action action(java.awt.Component parent) {
        Action action = Actions.of(Messages.text("action.importComponent"),
                () -> importFiles(parent, dialogs.chooseImportFiles(parent)));
        Icons.install(action, TemplateIcons.IMPORT_COMPONENT);
        return action;
    }

    /** Imports each file; the ones that fail are reported together at the end, the others are still imported. */
    public void importFiles(java.awt.Component parent, List<Path> files) {
        List<String> failures = new ArrayList<>();
        for (Path file : files) {
            DefinitionBundle bundle;
            try {
                bundle = reader.read(file);
            } catch (IOException exception) {
                LOGGER.warn("Could not import {}", file, exception);
                failures.add(Messages.format("library.import.failed", file.getFileName(),
                        String.valueOf(exception.getMessage())));
                continue;
            } catch (CircuitFormatException exception) {
                LOGGER.warn("Could not import {}", file, exception);
                failures.add(Messages.format("library.import.failed", file.getFileName(), Messages.readError(exception)));
                continue;
            } catch (RuntimeException exception) {
                // Nothing a file holds may escape to the EDT: an unexpected failure reads as an invalid element.
                LOGGER.warn("Could not import {}", file, exception);
                String detail = exception.getMessage() == null
                        ? exception.getClass().getName() : exception.getMessage();
                failures.add(Messages.format("library.import.failed", file.getFileName(), Messages.readError(
                        new CircuitFormatException(CircuitFormatException.Reason.INVALID_ELEMENT, detail, exception))));
                continue;
            }
            Optional<DefinitionBundle> accepted = resolveCollision(parent, bundle);
            if (accepted.isEmpty()) {
                continue;
            }
            try {
                service.save(accepted.get());
            } catch (IOException exception) {
                LOGGER.warn("Could not write {} to the component library", accepted.get().root().id(), exception);
                failures.add(service.writeError(accepted.get().root().id(), exception));
            }
        }
        if (!failures.isEmpty()) {
            dialogs.showError(parent, String.join("\n", failures));
        }
    }

    /** The same component under an id and a name the library does not use, for "Conservar ambos". */
    static DefinitionBundle copyWithNewIdentity(DefinitionBundle bundle, ComponentLibrary library) {
        ComponentDefinition root = bundle.root();
        Set<String> takenIds = new HashSet<>(library.ids());
        bundle.dependencies().forEach(dependency -> takenIds.add(dependency.id()));
        List<String> takenNames = library.entries().stream().map(entry -> entry.root().name()).toList();
        ComponentDefinition copy = new ComponentDefinition(DefinitionIds.unique(root.id(), takenIds),
                DefinitionNames.unique(root.name(), takenNames), root.description(), root.size(), root.pinout(),
                root.body(), root.appearance(), root.revision());
        return new DefinitionBundle(copy, bundle.dependencies());
    }

    /**
     * {@code bundle} as it may enter the library: as is when its id is free, otherwise as the user chooses (replace,
     * keep both under a new identity, or nothing).
     */
    Optional<DefinitionBundle> resolveCollision(java.awt.Component parent, DefinitionBundle bundle) {
        if (service.library().find(bundle.root().id()).isEmpty()) {
            return Optional.of(bundle);
        }
        return switch (dialogs.askImportCollision(parent, bundle.root().name())) {
            case REPLACE -> Optional.of(bundle);
            case KEEP_BOTH -> Optional.of(copyWithNewIdentity(bundle, service.library()));
            case CANCEL -> Optional.empty();
        };
    }
}
