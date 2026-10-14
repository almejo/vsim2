package cl.almejo.vsim.ui.export;

import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;

import javax.swing.JOptionPane;
import java.awt.Component;
import java.util.function.BiConsumer;

/** Adds Archivo ▸ «Exportar imagen…» (spec M6.2 §1) to every editor view. */
public final class ExportImageExtension implements EditorExtension {

    private final ExportChooser chooser;
    private final BiConsumer<Component, String> messages;

    public ExportImageExtension() {
        this(ExportOptionsPanel::choose, (parent, message) -> JOptionPane.showMessageDialog(parent, message,
                null, JOptionPane.ERROR_MESSAGE));
    }

    /** {@code chooser} asks for the file and options, {@code messages} shows a failure. */
    public ExportImageExtension(ExportChooser chooser, BiConsumer<Component, String> messages) {
        this.chooser = chooser;
        this.messages = messages;
    }

    @Override
    public Runnable install(EditorView view) {
        ExportImageFeatures features = new ExportImageFeatures(view, chooser, messages);
        return features::dispose;
    }
}
