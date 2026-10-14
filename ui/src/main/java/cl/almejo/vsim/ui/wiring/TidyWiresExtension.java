package cl.almejo.vsim.ui.wiring;

import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;

/** Adds Editar ▸ «Ordenar cables» and «Ordenar cables…» (spec M6.2 §2.1) to every editor view. */
public final class TidyWiresExtension implements EditorExtension {

    private final TidyChooser chooser;

    public TidyWiresExtension() {
        this(TidyWiresDialog::choose);
    }

    /** {@code chooser} asks for the options; tests pass a canned answer. */
    public TidyWiresExtension(TidyChooser chooser) {
        this.chooser = chooser;
    }

    @Override
    public Runnable install(EditorView view) {
        TidyWiresFeatures features = new TidyWiresFeatures(view, chooser);
        return features::dispose;
    }
}
