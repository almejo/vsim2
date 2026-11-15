package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.waveform.WaveformFeatures;
import cl.almejo.vsim.ui.window.BottomDock;
import cl.almejo.vsim.ui.window.EditorView;

import java.util.Optional;

/**
 * The wave strip under a machine tab's diagram (hito 2, task 6; mockup «La onda es de la pestaña de la máquina»): the
 * view's own waveform panel shows the copy's channels ({@link MachineStripChannels}) instead of the body's probes, so
 * its dock, Ver ▸ Formas de onda, «Ocultar», the pin and presentation mode's bottom edge work as for any panel. It
 * opens with the tab when there is a copy to show.
 */
final class MachineWaveStrip {

    private MachineWaveStrip() {
    }

    /**
     * Shows the strip of the copy {@code place} is entered through in {@code view}'s waveform panel, following
     * {@code canvas}'s changes (another copy, an edit of the machine). Returns what gives the panel its probes back;
     * nothing to do in a view without waveforms.
     */
    static Runnable install(EditorView view, OpenDocument place, String definitionId, MachineCanvas canvas) {
        Optional<WaveformFeatures> waveforms = WaveformFeatures.of(view);
        if (waveforms.isEmpty()) {
            return () -> { };
        }
        WaveformFeatures features = waveforms.get();
        MachineStripChannels strip = new MachineStripChannels(place, definitionId);
        Runnable showProbes = features.showChannels(strip);
        Runnable stopFollowing = canvas.addChangeListener(features::channelsChanged);
        BottomDock dock = view.bottomDock();
        boolean openedHere = strip.hasChannels() && !dock.isDockShowing();
        if (openedHere) {
            dock.showDock();
        }
        return () -> {
            stopFollowing.run();
            showProbes.run();
            if (openedHere && !features.panel().hasChannels()) {
                dock.hideDock();
            }
        };
    }
}
