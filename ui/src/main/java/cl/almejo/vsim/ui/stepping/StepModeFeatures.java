package cl.almejo.vsim.ui.stepping;

import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.simulation.StepReport;
import cl.almejo.vsim.core.simulation.StepResult;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.waveform.WaveformFeatures;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * «Paso a paso» in one editor view (M6.1-B, spec §4): the three Simulación items, the toolbar toggle, «Avanzar»,
 * «Avanzar al próximo flanco de reloj» and the status label (right after «Avanzar 10 t», spec §4.4), the glow and
 * pending-outline overlays and the three shortcut registrations. Everything follows the canvas's snapshots: a new
 * {@link StepReport} lights its wires and moves the waveform cursor. {@link #dispose()} takes it all back. EDT only.
 */
public final class StepModeFeatures {

    private final EditorView view;
    private final OpenDocument openDocument;
    private final LongSupplier nanoClock;
    private final Action stepModeAction;
    private final Action advanceAction;
    private final Action clockEdgeAction;
    private final JMenu simulationMenu;
    private final JCheckBoxMenuItem stepModeItem;
    private final JMenuItem advanceItem;
    private final JMenuItem clockEdgeItem;
    private final JToolBar.Separator separator = new JToolBar.Separator();
    private final JToggleButton stepModeButton;
    private final JButton advanceButton;
    private final JButton clockEdgeButton;
    private final JLabel status = new JLabel();
    private final StepHighlightOverlay overlay;
    private final PendingOutlineOverlay pendingOverlay;
    private final Runnable stepModeListener = this::refresh;
    private final Consumer<SimulationSnapshot> snapshotListener = this::snapshotPublished;
    private final List<Runnable> shortcutRemovers;
    private long shownSequence;
    /** The time of the last snapshot shown; a smaller one means «Reiniciar» (M6.1-B). */
    private long shownTime;
    private boolean disposed;

    public StepModeFeatures(EditorView view, LongSupplier nanoClock) {
        this.view = view;
        this.openDocument = view.openDocument();
        this.nanoClock = nanoClock;
        CircuitCanvas canvas = view.canvas();
        this.stepModeAction = new AbstractAction(Messages.text("action.stepMode")) {
            @Override
            public void actionPerformed(ActionEvent event) {
                openDocument.setStepMode(!openDocument.stepMode());
            }
        };
        this.advanceAction = new AbstractAction(Messages.text("action.advance")) {
            @Override
            public void actionPerformed(ActionEvent event) {
                openDocument.advanceToNextEvent();
            }
        };
        this.clockEdgeAction = new AbstractAction(Messages.text("action.advanceToClockEdge")) {
            @Override
            public void actionPerformed(ActionEvent event) {
                openDocument.advanceToNextClockEdge();
            }
        };
        // 16 px in the menu, 24 px in the toolbar; ActionIcons (installed after this extension) hides the button
        // texts and uses the action names as tooltips, like the rest of the main toolbar
        Icons.install(stepModeAction, StepIcons.MODE);
        Icons.install(advanceAction, StepIcons.ADVANCE);
        Icons.install(clockEdgeAction, StepIcons.CLOCK_EDGE);
        this.simulationMenu = view.menu(MenuId.SIMULATION);
        this.stepModeItem = new JCheckBoxMenuItem(stepModeAction);
        this.advanceItem = new JMenuItem(advanceAction);
        this.clockEdgeItem = new JMenuItem(clockEdgeAction);
        int menuIndex = indexAfterFixedStep(simulationMenu.getMenuComponents());
        simulationMenu.insert(stepModeItem, menuIndex);
        simulationMenu.insert(advanceItem, menuIndex + 1);
        simulationMenu.insert(clockEdgeItem, menuIndex + 2);
        this.stepModeButton = new JToggleButton(stepModeAction);
        this.advanceButton = new JButton(advanceAction); // text and tooltip come from the action (ActionIcons)
        this.clockEdgeButton = new JButton(clockEdgeAction);
        JToolBar toolBar = view.toolBar();
        int index = indexAfterFixedStep(toolBar.getComponents());
        toolBar.add(separator, index);
        toolBar.add(stepModeButton, index + 1);
        toolBar.add(advanceButton, index + 2);
        toolBar.add(clockEdgeButton, index + 3);
        status.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 8)); // air before the speed selector
        toolBar.add(status, index + 4);
        this.shortcutRemovers = List.of(view.registerShortcutAction("simulation.stepMode", stepModeAction),
                view.registerShortcutAction("simulation.advance", advanceAction),
                view.registerShortcutAction("simulation.advanceToClockEdge", clockEdgeAction));
        this.overlay = new StepHighlightOverlay(canvas::scheme, canvas::repaint, nanoClock);
        canvas.addOverlay(overlay);
        this.pendingOverlay = new PendingOutlineOverlay(canvas::scheme, openDocument.registry(), canvas::repaint);
        canvas.addOverlay(pendingOverlay);
        // A step made before this view existed is not this view's to show.
        this.shownSequence = openDocument.latestStep().map(StepReport::sequence).orElse(0L);
        openDocument.addStepModeListener(stepModeListener);
        canvas.addSnapshotListener(snapshotListener);
        refresh();
    }

    Action stepModeAction() {
        return stepModeAction;
    }

    Action advanceAction() {
        return advanceAction;
    }

    JButton advanceButton() {
        return advanceButton;
    }

    Action clockEdgeAction() {
        return clockEdgeAction;
    }

    JButton clockEdgeButton() {
        return clockEdgeButton;
    }

    /**
     * Right after «Avanzar N t» and what {@link FixedStepFeatures} put next to it (spec §4.4: B's controls follow the
     * fixed step), in the menu and in the toolbar; at the end when it is not there.
     */
    private int indexAfterFixedStep(Component[] children) {
        for (int index = 0; index < children.length; index++) {
            if (children[index] instanceof AbstractButton button && button.getAction() == view.actions().step()) {
                int after = index + 1;
                while (after < children.length && children[after] instanceof JComponent companion
                        && companion.getClientProperty(FixedStepFeatures.COMPANION) != null) {
                    after++;
                }
                return after;
            }
        }
        return children.length;
    }

    JLabel status() {
        return status;
    }

    StepHighlightOverlay overlay() {
        return overlay;
    }

    PendingOutlineOverlay pendingOverlay() {
        return pendingOverlay;
    }

    /**
     * Called with every snapshot the canvas shows: a step not shown yet lights its wires and, in the mode or when it
     * moved time, moves the waveform cursor; then the actions and the label follow the snapshot.
     */
    void snapshotPublished(SimulationSnapshot snapshot) {
        if (disposed) {
            return;
        }
        if (snapshot.time() < shownTime) {
            overlay.clear(); // «Reiniciar»: what changed before is gone
        }
        shownTime = snapshot.time();
        Optional<StepReport> latest = openDocument.latestStep();
        if (latest.isPresent() && latest.get().sequence() > shownSequence) {
            shownSequence = latest.get().sequence();
            if (latest.get().result().time() <= snapshot.time()) { // later than the snapshot: from before «Reiniciar»
                showStep(latest.get().result(), snapshot);
            }
        }
        showPending(snapshot);
        refresh(snapshot);
    }

    /** Outlines what has an event at the next event time while paused (spec §4.4), in and out of the mode. */
    private void showPending(SimulationSnapshot snapshot) {
        pendingOverlay.show(snapshot.running() ? PendingOutline.NONE
                : PendingOutline.of(openDocument.latestPendingComponents(), openDocument.document(), snapshot.token()));
    }

    private void showStep(StepResult result, SimulationSnapshot snapshot) {
        // the token of the snapshot that carried the step: if the circuit changed since, paint skips the glow
        overlay.show(StepHighlight.of(result.changedNets(), openDocument.netlist(), snapshot.token(),
                nanoClock.getAsLong()));
        // outside the mode a paused click (time unchanged) must not move a cursor the user placed (contract note 10)
        if (openDocument.stepMode() || result.time() != result.fromTime()) {
            WaveformFeatures.followStep(view, result.time());
        }
    }

    private void refresh() {
        refresh(openDocument.latestSnapshot());
    }

    private void refresh(SimulationSnapshot snapshot) {
        if (disposed) {
            return;
        }
        boolean stepMode = openDocument.stepMode();
        OptionalLong next = openDocument.latestNextEventTime();
        stepModeAction.putValue(Action.SELECTED_KEY, stepMode);
        advanceAction.setEnabled(!snapshot.running() && next.isPresent());
        clockEdgeAction.setEnabled(!snapshot.running() && openDocument.hasClock());
        advanceButton.setVisible(stepMode);
        clockEdgeButton.setVisible(stepMode);
        status.setVisible(stepMode);
        String text = StepStatusText.of(snapshot.time(), openDocument.latestStep(), next);
        if (!text.equals(status.getText())) {
            status.setText(text);
        }
    }

    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        shortcutRemovers.forEach(Runnable::run);
        openDocument.removeStepModeListener(stepModeListener);
        CircuitCanvas canvas = view.canvas();
        canvas.removeSnapshotListener(snapshotListener);
        canvas.removeOverlay(overlay);
        overlay.dispose();
        canvas.removeOverlay(pendingOverlay);
        pendingOverlay.dispose();
        simulationMenu.remove(clockEdgeItem);
        simulationMenu.remove(advanceItem);
        simulationMenu.remove(stepModeItem);
        JToolBar toolBar = view.toolBar();
        toolBar.remove(status);
        toolBar.remove(clockEdgeButton);
        toolBar.remove(advanceButton);
        toolBar.remove(stepModeButton);
        toolBar.remove(separator);
        toolBar.revalidate();
        toolBar.repaint();
    }
}
