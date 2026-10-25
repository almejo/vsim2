package cl.almejo.vsim.ui.appearance;

import cl.almejo.vsim.ui.library.LibraryDialogs;
import cl.almejo.vsim.ui.library.LibraryService;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;

import java.util.Optional;

/** Installs user-component appearance editing after the library palette is available. */
public final class AppearanceExtension implements EditorExtension {
    private final LibraryService library;
    private final LibraryDialogs dialogs;
    private final Optional<ShapeEditor> editor;
    private final AppearancePrompts prompts;

    public AppearanceExtension(LibraryService library, LibraryDialogs dialogs) {
        this(library, dialogs, Optional.empty(), new SwingAppearancePrompts());
    }

    public AppearanceExtension(LibraryService library, LibraryDialogs dialogs, Optional<ShapeEditor> editor,
                               AppearancePrompts prompts) {
        this.library = library;
        this.dialogs = dialogs;
        this.editor = editor;
        this.prompts = prompts;
    }

    @Override
    public Runnable install(EditorView view) {
        ShapeEditor opener = editor.orElseGet(() -> new SwingShapeEditor(view.services().preferences(),
                view.services().appearance()::scheme));
        AppearanceFeatures features = new AppearanceFeatures(view, library, dialogs, opener, prompts);
        features.install();
        return features::dispose;
    }
}
