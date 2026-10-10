package cl.almejo.vsim.ui.waveform;

import cl.almejo.vsim.ui.actions.TextPrompt;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;

import javax.swing.JOptionPane;
import java.awt.Component;
import java.util.function.BiConsumer;

/** Adds the waveform panel, Ver ▸ Formas de onda and the «Ver … en formas de onda» items to every editor view. */
public final class WaveformsExtension implements EditorExtension {

    private final TextPrompt prompt;
    private final BiConsumer<Component, String> messages;

    public WaveformsExtension() {
        this(TextPrompt.DIALOG, (parent, message) -> JOptionPane.showMessageDialog(parent, message));
    }

    /** {@code prompt} asks for a channel's new name; {@code messages} shows a notice (the channel cap). */
    public WaveformsExtension(TextPrompt prompt, BiConsumer<Component, String> messages) {
        this.prompt = prompt;
        this.messages = messages;
    }

    @Override
    public Runnable install(EditorView view) {
        WaveformFeatures features = new WaveformFeatures(view, prompt, messages);
        return features::dispose;
    }
}
