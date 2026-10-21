package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.ui.app.OpenDocument;

import java.util.List;
import java.util.Optional;

/**
 * What the editor's windows give the AI's host (EDT only): new circuits, each circuit's settings override and the
 * user's component library.
 */
public interface EditorWindows {

    /** The user's library («Mis componentes»): the AI may place its components too. None by default. */
    default List<DefinitionBundle> libraryComponents() {
        return List.of();
    }

    /** Opens {@code name} as a new, unsaved circuit in its own window (as Archivo ▸ Nuevo) and returns it. */
    OpenDocument openNewCircuit(String name);

    /** The override of the window the AI works in for {@code document} (its first window); empty when it has none. */
    Optional<SettingsOverride> settingsOverride(OpenDocument document);

    /** No windows: new circuits are refused and no document has an override. */
    EditorWindows NONE = new EditorWindows() {
        @Override
        public OpenDocument openNewCircuit(String name) {
            throw new UnsupportedOperationException("no windows");
        }

        @Override
        public Optional<SettingsOverride> settingsOverride(OpenDocument document) {
            return Optional.empty();
        }
    };
}
