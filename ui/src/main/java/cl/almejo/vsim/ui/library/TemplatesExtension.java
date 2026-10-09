package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;

/** Installs the templates and library features into every editor view (overview R9). */
public record TemplatesExtension(LibraryService service, LibraryDialogs dialogs) implements EditorExtension {

    @Override
    public Runnable install(EditorView view) {
        TemplateFeatures features = TemplateFeatures.install(view, service, dialogs);
        return features::dispose;
    }
}
