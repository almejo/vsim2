package cl.almejo.vsim.ui.window;

/** A feature installed into every editor view (menu items, inspector tabs, context-menu contributors, overlays). */
@FunctionalInterface
public interface EditorExtension {

    /**
     * Adds the feature to {@code view}, which is fully built except for later extensions. The returned action must
     * undo every registration on shared objects (document, history, library, preferences, appearance).
     */
    Runnable install(EditorView view);
}
