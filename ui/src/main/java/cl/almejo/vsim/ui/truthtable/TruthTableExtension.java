package cl.almejo.vsim.ui.truthtable;

import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;

import javax.swing.JOptionPane;
import java.awt.Component;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/** Adds «Tabla de verdad…» (Simulación menu, canvas context menu, shortcut id) to every editor view. */
public final class TruthTableExtension implements EditorExtension {

    private final TruthTableDisplay.Factory windows;
    private final BiConsumer<Component, String> messages;
    private final Supplier<TruthTableRunner> runners;

    public TruthTableExtension() {
        this(TruthTableWindow::open,
                (parent, message) -> JOptionPane.showMessageDialog(parent, message),
                TruthTableRunner::create);
    }

    /** {@code windows} opens table windows, {@code messages} shows refusals, {@code runners} makes one per view. */
    public TruthTableExtension(TruthTableDisplay.Factory windows, BiConsumer<Component, String> messages,
                               Supplier<TruthTableRunner> runners) {
        this.windows = windows;
        this.messages = messages;
        this.runners = runners;
    }

    @Override
    public Runnable install(EditorView view) {
        TruthTableFeatures features = new TruthTableFeatures(view, windows, messages, runners.get());
        return features::dispose;
    }
}
