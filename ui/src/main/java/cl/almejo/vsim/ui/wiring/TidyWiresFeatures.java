package cl.almejo.vsim.ui.wiring;

import cl.almejo.vsim.core.command.TidyWiresCommand;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.wiring.TidyOptions;
import cl.almejo.vsim.core.wiring.TidyPlan;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.app.Delay;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;

import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JToolBar;
import java.awt.Component;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The wire tidying of one editor view: two items at the end of Editar (after a separator), their shortcut
 * registrations and the notice. {@link #dispose()} takes all of it back.
 */
public final class TidyWiresFeatures {

    /** A tidy still running after this long shows the progress window. */
    public static final int PROGRESS_DELAY_MILLISECONDS = 2000;

    private final EditorView view;
    private final TidyChooser chooser;
    private final Action tidy;
    private final Action tidyWithOptions;
    private final JMenu editMenu;
    private final JPopupMenu.Separator separator = new JPopupMenu.Separator();
    private final JMenuItem tidyItem;
    private final JMenuItem optionsItem;
    private final JToolBar toolBar;
    private final JButton toolBarButton;
    private final Runnable tidyShortcutRemover;
    private final Runnable optionsShortcutRemover;
    private final TidyRunner runner;
    private final TidyRunner previewRunner;
    private final Delay delay;
    private final TidyProgress.Factory progressFactory;
    private final Set<Runnable> previewCancels = new java.util.LinkedHashSet<>();
    private Running running;
    private boolean disposed;

    /** One tidy in flight: its job, the pending 2 s timer and the progress window once it opened. */
    private static final class Running {
        TidyRunner.Job job;
        Runnable stopTimer = () -> { };
        TidyProgress progress;
        int round;
    }

    public TidyWiresFeatures(EditorView view, TidyChooser chooser) {
        this(view, chooser, TidyRunner.create(), TidyRunner.create(), Delay.swing(), TidyProgress.Factory.dialog());
    }

    /**
     * {@code runner} computes the tidy of the actions, {@code previewRunner} the dialog's summary; {@code delay}
     * decides when a slow tidy shows its progress window. Tests pass manual ones.
     */
    public TidyWiresFeatures(EditorView view, TidyChooser chooser, TidyRunner runner, TidyRunner previewRunner,
                             Delay delay, TidyProgress.Factory progressFactory) {
        this.view = view;
        this.chooser = chooser;
        this.runner = runner;
        this.previewRunner = previewRunner;
        this.delay = delay;
        this.progressFactory = progressFactory;
        this.tidy = Actions.of(Messages.text("action.tidyWires"), () -> tidyNow());
        this.tidyWithOptions = Actions.of(Messages.text("action.tidyWiresOptions"), () -> tidyWithDialog());
        // 16 px in the menu, 24 px in the toolbar; ActionIcons (installed after this extension) makes the button
        // icon-only with the action name as tooltip, and the shortcuts extension appends the shortcut
        Icons.install(tidy, TidyIcons.TIDY_WIRES);
        this.editMenu = view.menu(MenuId.EDIT);
        this.tidyItem = new JMenuItem(tidy);
        this.optionsItem = new JMenuItem(tidyWithOptions);
        editMenu.add(separator);
        editMenu.add(tidyItem);
        editMenu.add(optionsItem);
        this.toolBar = view.toolBar();
        this.toolBarButton = new JButton(tidy);
        toolBar.add(toolBarButton, indexAfterRedo());
        this.tidyShortcutRemover = view.registerShortcutAction("edit.tidyWires", tidy);
        this.optionsShortcutRemover = view.registerShortcutAction("edit.tidyWiresOptions", tidyWithOptions);
    }

    /** Right after the «Rehacer» button (the end of the toolbar if it is not there). */
    private int indexAfterRedo() {
        Component[] components = toolBar.getComponents();
        for (int index = 0; index < components.length; index++) {
            if (components[index] instanceof AbstractButton button && button.getAction() == view.actions().redo()) {
                return index + 1;
            }
        }
        return components.length;
    }

    public Action tidyAction() {
        return tidy;
    }

    public Action tidyWithOptionsAction() {
        return tidyWithOptions;
    }

    /** The wires of the selection, or none (then the whole circuit is tidied). */
    private Set<ElementId> selectedWires() {
        Set<ElementId> wires = new LinkedHashSet<>();
        for (ElementId id : view.selection().ids()) {
            if (view.openDocument().document().wire(id).isPresent()) {
                wires.add(id);
            }
        }
        return wires;
    }

    private Set<ElementId> allWires() {
        Set<ElementId> wires = new LinkedHashSet<>();
        for (Wire wire : view.openDocument().document().wires()) {
            wires.add(wire.id());
        }
        return wires;
    }

    /** The document as the worker reads it: a copy made on the EDT, never touched by the editor afterwards. */
    private CircuitDocument snapshot() {
        CircuitDocument original = view.openDocument().document();
        CircuitDocument copy = new CircuitDocument();
        original.definitions().forEach(copy::putDefinition);
        for (DocumentElement element : original.elements()) {
            copy.restore(element, original.orderOf(element.id()));
        }
        copy.setProbes(original.probes());
        return copy;
    }

    private ComponentTypeRegistry registryFor(CircuitDocument snapshot) {
        ComponentTypeRegistry registry = view.openDocument().registry().copy();
        registry.addResolver(SubcircuitTypes.resolverFor(snapshot));
        return registry;
    }

    /** The planner of the dialog's live summary: background, cancellable, on a snapshot of the moment. */
    private Runnable preview(TidyOptions options, boolean selectionOnly, Consumer<TidyPlan> onResult) {
        if (disposed) {
            return () -> { };
        }
        CircuitDocument snapshot = snapshot();
        Set<ElementId> scope = selectionOnly ? selectedWires() : allWires();
        TidyRunner.Job job = previewRunner.start(snapshot, registryFor(snapshot), scope, options,
                new TidyRunner.Callbacks() {
                    @Override
                    public void progress(int round) {
                    }

                    @Override
                    public void finished(TidyPlan plan) {
                        onResult.accept(plan);
                    }

                    @Override
                    public void failed(Throwable throwable) {
                        onResult.accept(TidyPlan.EMPTY);
                    }
                });
        Runnable cancel = job::cancel;
        previewCancels.add(cancel);
        return () -> {
            previewCancels.remove(cancel);
            cancel.run();
        };
    }

    /** Applies the saved options at once, on the selection's wires if there are any, else on everything. */
    void tidyNow() {
        if (disposed) {
            return;
        }
        run(view.services().preferences().tidyOptions(), !selectedWires().isEmpty());
    }

    void tidyWithDialog() {
        if (disposed) {
            return;
        }
        Optional<TidyChoice> choice = chooser.choose(view, !selectedWires().isEmpty(),
                view.services().preferences().tidyOptions(), this::preview);
        previewCancels.forEach(Runnable::run);
        previewCancels.clear();
        if (choice.isEmpty()) {
            return;
        }
        view.services().preferences().setTidyOptions(choice.get().options());
        run(choice.get().options(), choice.get().selectionOnly());
    }

    /** Whether a tidy is computing right now. */
    public boolean isRunning() {
        return running != null;
    }

    private void run(TidyOptions options, boolean selectionOnly) {
        stopRunning();
        long revisionAtStart = view.openDocument().document().revision();
        CircuitDocument snapshot = snapshot();
        Set<ElementId> scope = selectionOnly ? selectedWires() : allWires();
        Running mine = new Running();
        running = mine;
        // the timer first: a manual runner may deliver the result before start returns
        mine.stopTimer = delay.after(PROGRESS_DELAY_MILLISECONDS, () -> {
            if (running == mine && mine.progress == null) {
                mine.progress = progressFactory.open(view, this::cancelByUser);
                if (mine.round > 0) {
                    mine.progress.round(mine.round);
                }
            }
        });
        mine.job = runner.start(snapshot, registryFor(snapshot), scope, options, new TidyRunner.Callbacks() {
            @Override
            public void progress(int round) {
                if (running == mine) {
                    mine.round = round;
                    if (mine.progress != null) {
                        mine.progress.round(round);
                    }
                }
            }

            @Override
            public void finished(TidyPlan plan) {
                if (running != mine) {
                    return;
                }
                stopRunning();
                if (view.openDocument().document().revision() != revisionAtStart) {
                    view.statusBar().showNotice(Messages.text("tidy.changedMeanwhile"));
                    return;
                }
                if (!plan.isEmpty()) {
                    view.openDocument().execute(new TidyWiresCommand(plan));
                }
                view.statusBar().showNotice(TidySummary.notice(plan));
            }

            @Override
            public void failed(Throwable throwable) {
                if (running == mine) {
                    stopRunning();
                    view.statusBar().showNotice(Messages.format("tidy.failed", String.valueOf(throwable.getMessage())));
                }
            }
        });
    }

    private void cancelByUser() {
        if (running != null) {
            stopRunning();
            view.statusBar().showNotice(Messages.text("tidy.cancelled"));
        }
    }

    /** Cancels the job, the timer and the window, whichever exist. */
    private void stopRunning() {
        Running current = running;
        running = null;
        if (current != null) {
            if (current.job != null) {
                current.job.cancel();
            }
            current.stopTimer.run();
            if (current.progress != null) {
                current.progress.close();
            }
        }
    }

    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        stopRunning();
        previewCancels.forEach(Runnable::run);
        previewCancels.clear();
        runner.dispose();
        previewRunner.dispose();
        tidyShortcutRemover.run();
        optionsShortcutRemover.run();
        toolBar.remove(toolBarButton);
        editMenu.remove(tidyItem);
        editMenu.remove(optionsItem);
        editMenu.remove(separator);
    }
}
