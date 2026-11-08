package cl.almejo.vsim.ui.waveform;

import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;

import javax.swing.Action;
import javax.swing.JToggleButton;

/**
 * The toolbar toggle «Formas de onda» (agreed 2026-11-08): on the same action as Ver ▸ Formas de onda (shortcut id
 * {@code waveforms.toggle}, no default key), so it is pressed while the dock shows, also in presentation mode, where
 * it hides a pinned panel as the item does. Installed right after «Presentar lo que hace la IA», at the end of the
 * toolbar, and before {@code ActionIcons}, which makes it an icon with its name as tooltip. Nothing without the
 * waveform feature.
 */
public final class WaveformsToolbarExtension implements EditorExtension {

    private static final String TOGGLE_ID = "waveforms.toggle";

    @Override
    public Runnable install(EditorView view) {
        Action toggle = view.shortcutActions().get(TOGGLE_ID);
        if (toggle == null) {
            return () -> {
            };
        }
        JToggleButton button = new JToggleButton(toggle);
        button.setFocusable(false);
        return view.addToolbarComponent(button);
    }
}
