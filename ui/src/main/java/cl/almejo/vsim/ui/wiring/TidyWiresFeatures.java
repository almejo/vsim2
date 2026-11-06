package cl.almejo.vsim.ui.wiring;

import cl.almejo.vsim.core.command.ReplaceContentCommand;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.DocumentListener;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameContents;
import cl.almejo.vsim.core.layout.CircuitTidyOptions;
import cl.almejo.vsim.core.layout.CircuitTidyPlan;
import cl.almejo.vsim.core.layout.CircuitTidyProgress;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.app.Delay;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.stepping.StepIcons;
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

/**
 * «Ordenar circuito» of one editor view: two items at the end of Editar (after a separator), the toolbar button with
 * its ▾ to the dialog, their shortcut registrations and the notice. {@link #dispose()} takes all of it back.
 */
public final class TidyWiresFeatures {

    /** A tidy still running after this long shows the progress window. */
    public static final int PROGRESS_DELAY_MILLISECONDS = 2000;

    /**
     * A change of the circuit while the dialog is open recomputes its summary and ghost this long after the last change
     * of a burst (an AI editing step by step), so a burst costs one computation.
     */
    static final int PREVIEW_REFRESH_DELAY_MILLISECONDS = 150;

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
    /** The small ▾ right after the button, which opens the dialog: «Ordenar circuito» as a split button. */
    private final JButton optionsArrow;
    private final Runnable tidyShortcutRemover;
    private final Runnable optionsShortcutRemover;
    private final TidyRunner runner;
    private final TidyRunner previewRunner;
    private final Delay delay;
    private final TidyProgress.Factory progressFactory;
    private final TidyGhostOverlay ghost;
    private final Set<Runnable> previewCancels = new LinkedHashSet<>();
    private final DocumentListener circuitChanged = (document, change) -> circuitChangedWhileTheDialogIsOpen();
    /** The last preview plan the dialog showed (null: none, or the circuit changed since). */
    private DeliveredPreview delivered;
    /** Who hears of the dialog's latest computation (null: no dialog open). */
    private TidyPlanner.Results latestResults;
    private Runnable stopPreviewRefresh = () -> { };
    private Runnable stopWatchingTheCircuit = () -> { };
    private Running running;
    private boolean disposed;

    /** One tidy in flight: its job, the pending 2 s timer and the progress window once it opened. */
    private static final class Running {
        TidyRunner.Job job;
        Runnable stopTimer = () -> { };
        TidyProgress progress;
        CircuitTidyProgress.Stage stage;
        int done;
        int total;
    }

    /**
     * A plan the dialog's preview delivered (and its ghost showed), with what it was computed for: the circuit's
     * revision, the scope and the options. «Ordenar» applies it as it is while all three still match.
     */
    private record DeliveredPreview(long revision, Set<ElementId> scope, CircuitTidyOptions options,
                                    CircuitDocument snapshot, CircuitTidyPlan plan) {

        boolean describes(long currentRevision, Set<ElementId> currentScope, CircuitTidyOptions chosen) {
            return revision == currentRevision && scope.equals(currentScope) && options.equals(chosen);
        }
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
        this.ghost = new TidyGhostOverlay(view);
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
        int afterRedo = indexAfterRedo();
        toolBar.add(toolBarButton, afterRedo);
        this.optionsArrow = optionsArrow(tidyWithOptions);
        toolBar.add(optionsArrow, afterRedo + 1);
        this.tidyShortcutRemover = view.registerShortcutAction("edit.tidyWires", tidy);
        this.optionsShortcutRemover = view.registerShortcutAction("edit.tidyWiresOptions", tidyWithOptions);
    }

    /**
     * The arrow of the split button, as the fixed step's ({@code StepSizeButton}): the same ▾ icon, not focusable. Its
     * action is «Ordenar circuito…», so the shortcuts extension gives it the tooltip «Ordenar circuito… (shortcut)».
     */
    private static JButton optionsArrow(Action options) {
        JButton arrow = new JButton(options);
        arrow.setHideActionText(true);
        arrow.setIcon(Icons.get(StepIcons.SIZE_MENU, Icons.MENU_SIZE));
        arrow.setFocusable(false);
        return arrow;
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

    /**
     * The selected elements of any kind that are in the document (none: the whole circuit is tidied); a selected frame
     * stands for what it holds, as for the AI's {@code TidyWires} (a frame itself never moves in the tidy).
     */
    private Set<ElementId> selectedElements() {
        CircuitDocument document = view.openDocument().document();
        Set<ElementId> selected = new LinkedHashSet<>();
        for (ElementId id : view.selection().ids()) {
            Optional<Frame> frame = document.frame(id);
            if (frame.isPresent()) {
                selected.addAll(FrameContents.of(document, view.openDocument().registry(), frame.get()));
            } else if (document.element(id).isPresent()) {
                selected.add(id);
            }
        }
        return selected;
    }

    /** True when something of the document is selected (an empty frame too: then nothing is tidied, not all). */
    private boolean hasSelection() {
        CircuitDocument document = view.openDocument().document();
        return view.selection().ids().stream().anyMatch(id -> document.element(id).isPresent());
    }

    private Set<ElementId> allElements() {
        Set<ElementId> everything = new LinkedHashSet<>();
        for (DocumentElement element : view.openDocument().document().elements()) {
            everything.add(element.id());
        }
        return everything;
    }

    /**
     * The document as the worker reads it: a detached copy made on the EDT, never touched by the editor afterwards.
     * A whole copy (guides, probes, next ids), since the command is computed against it: what it left out would be
     * taken out of the document by the tidy.
     */
    private CircuitDocument snapshot() {
        return view.openDocument().document().copy();
    }

    private ComponentTypeRegistry registryFor(CircuitDocument snapshot) {
        ComponentTypeRegistry registry = view.openDocument().registry().copy();
        registry.addResolver(SubcircuitTypes.resolverFor(snapshot));
        return registry;
    }

    /** The ghost of the dialog's preview (for tests). */
    TidyGhostOverlay ghost() {
        return ghost;
    }

    /**
     * The planner of the dialog's live summary: background, cancellable, on a snapshot of the moment. Each delivered
     * plan is also shown as a ghost over the canvas and kept for «Ordenar»; a new computation, a failure, a cancel or a
     * change of the circuit takes it away. A plan delivered after the circuit changed shows no ghost: the change's
     * recomputation follows.
     */
    private Runnable preview(CircuitTidyOptions options, boolean selectionOnly, TidyPlanner.Results results) {
        if (disposed) {
            return () -> { };
        }
        ghost.clear();
        delivered = null;
        latestResults = results;
        long revision = view.openDocument().document().revision();
        CircuitDocument snapshot = snapshot();
        Set<ElementId> scope = selectionOnly ? selectedElements() : allElements();
        TidyRunner.Job job = previewRunner.start(snapshot, registryFor(snapshot), scope, options,
                new TidyRunner.Callbacks() {
                    @Override
                    public void progress(CircuitTidyProgress.Stage stage, int done, int total) {
                    }

                    @Override
                    public void finished(CircuitTidyPlan plan) {
                        if (view.openDocument().document().revision() == revision) {
                            ghost.show(snapshot, plan);
                            delivered = new DeliveredPreview(revision, scope, options, snapshot, plan);
                        }
                        results.accept(plan);
                    }

                    @Override
                    public void failed(Throwable throwable) {
                        ghost.clear();
                        results.failed(String.valueOf(throwable.getMessage()));
                    }
                });
        Runnable cancel = job::cancel;
        previewCancels.add(cancel);
        return () -> {
            previewCancels.remove(cancel);
            cancel.run();
            ghost.clear();
        };
    }

    /** Applies the saved options at once, on the selection's wires if there are any, else on everything. */
    void tidyNow() {
        if (disposed) {
            return;
        }
        run(view.services().preferences().tidyOptions(), hasSelection());
    }

    /**
     * The dialog: while it is open the circuit is watched, so the ghost never shows a plan of an older circuit. «Ordenar»
     * applies the plan the ghost showed when the circuit, the scope and the options are still those it was computed
     * for; otherwise it computes again.
     */
    void tidyWithDialog() {
        if (disposed) {
            return;
        }
        watchTheCircuit();
        Optional<TidyChoice> choice = chooser.choose(view, hasSelection(),
                view.services().preferences().tidyOptions(), this::preview);
        DeliveredPreview shown = delivered;
        endPreview();
        if (choice.isEmpty()) {
            return;
        }
        CircuitTidyOptions options = choice.get().options();
        view.services().preferences().setTidyOptions(options);
        Set<ElementId> scope = choice.get().selectionOnly() ? selectedElements() : allElements();
        if (shown != null && shown.describes(view.openDocument().document().revision(), scope, options)) {
            stopRunning();
            apply(shown.snapshot(), shown.plan());
        } else {
            run(options, choice.get().selectionOnly());
        }
    }

    private void watchTheCircuit() {
        CircuitDocument watched = view.openDocument().document();
        watched.addListener(circuitChanged);
        stopWatchingTheCircuit = () -> watched.removeListener(circuitChanged);
    }

    /** The dialog closed: no more watching, computing, refreshing or ghost. */
    private void endPreview() {
        stopWatchingTheCircuit.run();
        stopWatchingTheCircuit = () -> { };
        stopPreviewRefresh.run();
        stopPreviewRefresh = () -> { };
        latestResults = null;
        delivered = null;
        previewCancels.forEach(Runnable::run);
        previewCancels.clear();
        ghost.clear();
    }

    /** The ghost goes at once; the summary is computed again when the burst of changes is over. */
    private void circuitChangedWhileTheDialogIsOpen() {
        ghost.clear();
        delivered = null;
        stopPreviewRefresh.run();
        stopPreviewRefresh = delay.after(PREVIEW_REFRESH_DELAY_MILLISECONDS, () -> {
            stopPreviewRefresh = () -> { };
            if (latestResults != null) {
                latestResults.stale();
            }
        });
    }

    /** Whether a tidy is computing right now. */
    public boolean isRunning() {
        return running != null;
    }

    private void run(CircuitTidyOptions options, boolean selectionOnly) {
        stopRunning();
        long revisionAtStart = view.openDocument().document().revision();
        CircuitDocument snapshot = snapshot();
        Set<ElementId> scope = selectionOnly ? selectedElements() : allElements();
        Running mine = new Running();
        running = mine;
        // the timer first: a manual runner may deliver the result before start returns
        mine.stopTimer = delay.after(PROGRESS_DELAY_MILLISECONDS, () -> {
            if (running == mine && mine.progress == null) {
                mine.progress = progressFactory.open(view, this::cancelByUser);
                if (mine.stage != null) {
                    mine.progress.stage(mine.stage, mine.done, mine.total);
                }
            }
        });
        mine.job = runner.start(snapshot, registryFor(snapshot), scope, options, new TidyRunner.Callbacks() {
            @Override
            public void progress(CircuitTidyProgress.Stage stage, int done, int total) {
                if (running == mine) {
                    mine.stage = stage;
                    mine.done = done;
                    mine.total = total;
                    if (mine.progress != null) {
                        mine.progress.stage(stage, done, total);
                    }
                }
            }

            @Override
            public void finished(CircuitTidyPlan plan) {
                if (running != mine) {
                    return;
                }
                stopRunning();
                if (view.openDocument().document().revision() != revisionAtStart) {
                    view.statusBar().showNotice(Messages.text("tidy.changedMeanwhile"));
                    return;
                }
                apply(snapshot, plan);
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

    /**
     * One undo step from {@code snapshot} to the plan's result, and the notice. The caller vouches that the document is
     * still {@code snapshot} (same revision).
     */
    private void apply(CircuitDocument snapshot, CircuitTidyPlan plan) {
        if (!plan.isEmpty()) {
            view.openDocument().execute(ReplaceContentCommand.between(snapshot, plan.after(), "command.tidyCircuit"));
        }
        view.statusBar().showNotice(TidySummary.notice(plan));
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
        endPreview();
        ghost.dispose();
        runner.dispose();
        previewRunner.dispose();
        tidyShortcutRemover.run();
        optionsShortcutRemover.run();
        toolBar.remove(toolBarButton);
        toolBar.remove(optionsArrow);
        editMenu.remove(tidyItem);
        editMenu.remove(optionsItem);
        editMenu.remove(separator);
    }
}
