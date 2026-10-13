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
    private final TruthTableFeatures.Confirmation confirmation;
    private final Supplier<TruthTableRunner> runners;

    public TruthTableExtension() {
        this(TruthTableWindow::open,
                (parent, message) -> JOptionPane.showMessageDialog(parent, message),
                TruthTableFeatures.Confirmation.dialog(),
                TruthTableRunner::create);
    }

    /**
     * {@code windows} opens table windows, {@code messages} shows refusals, {@code confirmation} asks before more than
     * 1 024 rows, {@code runners} makes one runner per view.
     */
    public TruthTableExtension(TruthTableDisplay.Factory windows, BiConsumer<Component, String> messages,
                               TruthTableFeatures.Confirmation confirmation, Supplier<TruthTableRunner> runners) {
        this.windows = windows;
        this.messages = messages;
        this.confirmation = confirmation;
        this.runners = runners;
    }

    @Override
    public Runnable install(EditorView view) {
        TruthTableFeatures features = new TruthTableFeatures(view, windows, messages, confirmation, runners.get());
        return features::dispose;
    }
}
