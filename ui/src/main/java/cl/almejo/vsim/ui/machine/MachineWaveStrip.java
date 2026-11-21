package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.simulation.InstancePath;
import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.waveform.ProbeActions;
import cl.almejo.vsim.ui.waveform.WaveformFeatures;
import cl.almejo.vsim.ui.window.BottomDock;
import cl.almejo.vsim.ui.window.EditorView;

import javax.swing.Action;
import java.util.List;
import java.util.Optional;

/**
 * The wave strip under a machine tab's diagram (hito 2, task 6; mockup «La onda es de la pestaña de la máquina»): the
 * view's own waveform panel shows the copy's channels ({@link MachineStripChannels}) instead of the body's probes, so
 * its dock, Ver ▸ Formas de onda, «Ocultar», the pin and presentation mode's bottom edge work as for any panel. It
 * opens with the tab when there is a copy to show. Its link «Ver estados y salidas en formas de onda» (task 7) adds the
 * copy's state and outputs to the circuit's own panel, for a copy placed in the main circuit.
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
        Action showInCircuitPanel = Actions.of(Messages.text("waveform.menu.showMachine"),
                () -> addToTheCircuitsPanel(view, place));
        showInCircuitPanel.putValue(Action.SHORT_DESCRIPTION, Messages.text("machine.strip.showMachineTip"));
        Actions.disabledBecause(showInCircuitPanel, () -> Messages.text("machine.strip.onlyTopLevel"));
        Runnable refreshLink = () -> refreshLink(showInCircuitPanel, place);
        refreshLink.run();
        MachineStripChannels strip = new MachineStripChannels(place, definitionId, List.of(showInCircuitPanel));
        Runnable showProbes = features.showChannels(strip);
        Runnable stopFollowing = canvas.addChangeListener(() -> {
            refreshLink.run();
            features.channelsChanged();
        });
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

    /** The copy a top-level piece is: only those have probes, which live in the main circuit. */
    private static Optional<String> topLevelCopy(OpenDocument place) {
        return place.contextPath().filter(path -> !InstancePath.isNested(path));
    }

    /** On for a copy placed in the main circuit; otherwise off, its tooltip saying why. */
    private static void refreshLink(Action link, OpenDocument place) {
        link.setEnabled(topLevelCopy(place).isPresent());
    }

    /**
     * Adds the copy's state and outputs to the circuit's waveform panel, one undo step of the circuit, and says in the
     * status bar what happened (that panel is in the circuit's tab, not here).
     */
    private static void addToTheCircuitsPanel(EditorView view, OpenDocument place) {
        Optional<String> copy = topLevelCopy(place);
        if (copy.isEmpty()) {
            return;
        }
        OpenDocument circuit = place.parent().orElse(place);
        ProbeActions.AddResult result = new ProbeActions(circuit).addMachineStatesAndOutputs(new ElementId(copy.get()));
        if (result.capReached()) {
            view.showNotice(Messages.format("waveform.capReached", ProbeActions.MAXIMUM_CHANNELS));
        } else if (result.addedAny()) {
            view.showNotice(Messages.text("machine.strip.added"));
        } else if (result.existingId().isPresent()) {
            view.showNotice(Messages.text("machine.strip.alreadyThere"));
        }
    }
}
