package cl.almejo.vsim.ui.waveform;

import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.Icons;

import javax.swing.JToggleButton;

/**
 * The pin of a bottom panel in presentation mode: a tilted outline pushpin, and an upright red one while it is pressed
 * (the panel is pinned: docked, the canvas shorter). Icon only; the tooltip says what a press does. Shown only while the
 * mode is on. The waveform panel has one, and so has whatever else the dock shows instead of it (a machine's test
 * table): all of them are the same pin, remembered for every view ({@link WaveformFeatures#addPin}).
 */
public final class PinToggle extends JToggleButton {

    private static final long serialVersionUID = 1L;

    public PinToggle() {
        setIcon(Icons.get(WaveformIcons.PIN, Icons.MENU_SIZE));
        setSelectedIcon(new PinnedIcon(Icons.MENU_SIZE));
        setVisible(false);
        showPinned(false);
    }

    /** Pressed (the red pin) while {@code pinned}; the tooltip says what the next press does. */
    public void showPinned(boolean pinned) {
        setSelected(pinned);
        setToolTipText(Messages.text(pinned ? "waveform.pinnedTip" : "waveform.pinTip"));
    }
}
