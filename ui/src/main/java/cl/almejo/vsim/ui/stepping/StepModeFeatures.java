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
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * «Paso a paso» in one editor view (M6.1-B, spec §4): the two Simulación items, the toolbar toggle, «Avanzar» and the
 * status label, the glow overlay and the two shortcut registrations. Everything follows the canvas's snapshots: a new
 * {@link StepReport} lights its wires and moves the waveform cursor. {@link #dispose()} takes it all back. EDT only.
 */
public final class StepModeFeatures {

    /** Where the two items go in the Simulación menu: right after «Avanzar 10 t» (Iniciar, Pausar, Avanzar 10 t; E renamed «Paso»). */
    static final int MENU_INDEX = 3;

    private final EditorView view;
    private final OpenDocument openDocument;
    private final LongSupplier nanoClock;
    private final Action stepModeAction;
    private final Action advanceAction;
    private final JMenu simulationMenu;
    private final JCheckBoxMenuItem stepModeItem;
    private final JMenuItem advanceItem;
    private final JToolBar.Separator separator = new JToolBar.Separator();
    private final JToggleButton stepModeButton;
    private final JButton advanceButton;
    private final JLabel status = new JLabel();
    private final StepHighlightOverlay overlay;
    private final Runnable stepModeListener = this::refresh;
    private final Consumer<SimulationSnapshot> snapshotListener = this::snapshotPublished;
    private final List<Runnable> shortcutRemovers;
    private long shownSequence;
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
        // 16 px in the menu, 24 px in the toolbar; ActionIcons (installed after this extension) hides the button
        // texts and uses the action names as tooltips, like the rest of the main toolbar
        Icons.install(stepModeAction, StepIcons.MODE);
        Icons.install(advanceAction, StepIcons.ADVANCE);
        this.simulationMenu = view.menu(MenuId.SIMULATION);
        this.stepModeItem = new JCheckBoxMenuItem(stepModeAction);
        this.advanceItem = new JMenuItem(advanceAction);
        simulationMenu.insert(stepModeItem, MENU_INDEX);
        simulationMenu.insert(advanceItem, MENU_INDEX + 1);
        this.stepModeButton = new JToggleButton(stepModeAction);
        this.advanceButton = new JButton(advanceAction); // text and tooltip come from the action (ActionIcons, Task 7)
        JToolBar toolBar = view.toolBar();
        toolBar.add(separator);
        toolBar.add(stepModeButton);
        toolBar.add(advanceButton);
        toolBar.add(status);
        this.shortcutRemovers = List.of(view.registerShortcutAction("simulation.stepMode", stepModeAction),
                view.registerShortcutAction("simulation.advance", advanceAction));
        this.overlay = new StepHighlightOverlay(canvas::scheme, canvas::repaint, nanoClock);
        canvas.addOverlay(overlay);
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

    JLabel status() {
        return status;
    }

    StepHighlightOverlay overlay() {
        return overlay;
    }

    /**
     * Called with every snapshot the canvas shows: a step not shown yet lights its wires and, in the mode or when it
     * moved time, moves the waveform cursor; then the actions and the label follow the snapshot.
     */
    void snapshotPublished(SimulationSnapshot snapshot) {
        if (disposed) {
            return;
        }
        Optional<StepReport> latest = openDocument.latestStep();
        if (latest.isPresent() && latest.get().sequence() > shownSequence) {
            shownSequence = latest.get().sequence();
            showStep(latest.get().result());
        }
        refresh(snapshot);
    }

    private void showStep(StepResult result) {
        overlay.show(StepHighlight.of(result.changedNets(), openDocument.netlist(), openDocument.netlistToken(),
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
        advanceButton.setVisible(stepMode);
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
        simulationMenu.remove(advanceItem);
        simulationMenu.remove(stepModeItem);
        JToolBar toolBar = view.toolBar();
        toolBar.remove(status);
        toolBar.remove(advanceButton);
        toolBar.remove(stepModeButton);
        toolBar.remove(separator);
        toolBar.revalidate();
        toolBar.repaint();
    }
}
