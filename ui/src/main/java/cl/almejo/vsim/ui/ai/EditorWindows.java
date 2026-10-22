package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.present.PresentationSettings;
import cl.almejo.vsim.core.geometry.GridPoint;
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

    /**
     * The overrides of every window of {@code document}, its first one first: the AI's «Presentar» pace shows in all
     * of them (Ver ▸ «Ritmo de Presentar» and the panel's switch). The first window's only by default.
     */
    default List<SettingsOverride> settingsOverridesOfEveryWindow(OpenDocument document) {
        return settingsOverride(document).stream().toList();
    }

    /**
     * The presentation mode of the window the AI works in for {@code document} (its first window), read from that
     * window's preferences ({@link PresentationPreferences}); the working mode by default (IA.2, spec §4).
     */
    default PresentationSettings presentationSettings(OpenDocument document) {
        return PresentationSettings.WORKING;
    }

    /** Switches «Presentar» on or off in the preferences of {@code document}'s window, as the user's toggle does. */
    default void setPresenting(OpenDocument document, boolean presenting) {
        throw new AutomationException(AutomationError.NOT_ALLOWED, "This editor cannot present the AI's work");
    }

    /**
     * The windows of {@code document} travel smoothly to {@code origin}, where the AI builds the stage that begins
     * (IA.2 storyboard, spec §6); reduced motion respected. Nothing by default.
     */
    default void travelToStageOrigin(OpenDocument document, GridPoint origin) {
    }

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
