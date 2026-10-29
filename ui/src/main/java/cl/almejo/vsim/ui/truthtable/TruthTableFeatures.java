package cl.almejo.vsim.ui.truthtable;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.truthtable.Preparation;
import cl.almejo.vsim.core.truthtable.TruthTable;
import cl.almejo.vsim.core.truthtable.TruthTableScope;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.actions.ContextMenuContributor;
import cl.almejo.vsim.ui.app.Delay;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.Selection;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.karnaugh.KarnaughHandoff;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import cl.almejo.vsim.ui.window.Menus;
import cl.almejo.vsim.ui.window.panes.EveryPane;

import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import java.awt.Component;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * The truth-table feature of one editor view (spec §5.1): Simulación ▸ «Tabla de verdad…», the canvas context-menu
 * item, the shortcut registration, one runner and the open windows. {@link #dispose()} removes all of it and closes
 * the windows; the context-menu contributor cannot be removed from the view and does nothing afterwards.
 */
public final class TruthTableFeatures {

    /** After this much work a window shows «Calculando… N %» with «Cancelar», whatever its row count. */
    public static final int PROGRESS_DELAY_MILLISECONDS = 300;

    private static final Set<String> INPUT_TYPES = Set.of(StandardComponentTypes.SWITCH,
            StandardComponentTypes.PUSH_BUTTON, StandardComponentTypes.INPUT_PIN);
    private static final Set<String> OUTPUT_TYPES = Set.of(StandardComponentTypes.LED,
            StandardComponentTypes.OUTPUT_PIN);

    /** Asks the user a yes/no question before a long computation (spec §5.4); true means go ahead. */
    @FunctionalInterface
    public interface Confirmation {
        boolean confirm(Component parent, String title, String question);

        /** A dialog with «Calcular» and «Cancelar». */
        static Confirmation dialog() {
            return (parent, title, question) -> {
                Object[] options = {TruthTableMessages.computeOption(), Messages.text("truthTable.cancel")};
                return JOptionPane.showOptionDialog(parent, question, title, JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.QUESTION_MESSAGE, null, options, options[0]) == 0;
            };
        }
    }

    private final EditorView view;
    private final TruthTableDisplay.Factory windows;
    private final BiConsumer<Component, String> messages;
    private final Confirmation confirmation;
    private final TruthTableRunner runner;
    private final Delay delay;
    private final Action action;
    private final JMenu simulationMenu;
    private final Component separator;
    private final Runnable itemRemover;
    private final List<Session> sessions = new ArrayList<>();
    /** On the canvas while a window is open: moves each window's highlight to the row of the live inputs. */
    private final Consumer<SimulationSnapshot> snapshotListener = this::followSnapshot;
    /** Undoes the listening to the active pane's snapshots; empty while not following. */
    private Optional<Runnable> stopFollowing = Optional.empty();
    private boolean disposed;

    public TruthTableFeatures(EditorView view, TruthTableDisplay.Factory windows,
                              BiConsumer<Component, String> messages, Confirmation confirmation,
                              TruthTableRunner runner) {
        this(view, windows, messages, confirmation, runner, Delay.swing());
    }

    /**
     * {@code confirmation} asks before more than 1 024 rows; {@code delay} decides when a computation still running
     * shows its progress (tests run it by hand).
     */
    public TruthTableFeatures(EditorView view, TruthTableDisplay.Factory windows,
                              BiConsumer<Component, String> messages, Confirmation confirmation,
                              TruthTableRunner runner, Delay delay) {
        this.view = view;
        this.windows = windows;
        this.messages = messages;
        this.confirmation = confirmation;
        this.runner = runner;
        this.delay = delay;
        this.action = Actions.of(Messages.text("action.truthTable"), () -> open(view.selection().ids()));
        this.simulationMenu = view.menu(MenuId.SIMULATION);
        simulationMenu.addSeparator();
        this.separator = simulationMenu.getMenuComponent(simulationMenu.getMenuComponentCount() - 1);
        this.itemRemover = Menus.install(view, MenuId.SIMULATION, -1, action, "simulation.truthTable");
        view.addContextMenuContributor(new Contributor());
    }

    public Action action() {
        return action;
    }

    /** The windows open now, oldest first. */
    public int openWindowCount() {
        return sessions.size();
    }

    /**
     * Computes the table of {@code selection} (empty: the whole circuit) and shows it in a new window; a refusal is
     * shown as a message and opens nothing. More than 1 024 rows are computed only after the user confirms.
     */
    public void open(Set<ElementId> selection) {
        if (disposed) {
            return;
        }
        Set<ElementId> scope = Set.copyOf(selection);
        Preparation preparation = prepare(scope);
        if (preparation instanceof Preparation.Refused refused) {
            messages.accept(view, TruthTableMessages.refusal(refused));
            return;
        }
        Preparation.Ready ready = (Preparation.Ready) preparation;
        String scopeName = scopeName(scope);
        String title = TruthTableMessages.title(scopeName);
        if (!confirmed(ready, 0, title)) {
            return;
        }
        Session session = new Session(scope, title, scopeName);
        session.display = windows.open(view, title, session);
        sessions.add(session);
        updateFollowing();
        session.run(ready);
    }

    /** Highlights in every window the row of the inputs {@code snapshot} shows (the canvas calls it every frame). */
    void followSnapshot(SimulationSnapshot snapshot) {
        for (Session session : List.copyOf(sessions)) {
            session.follow(snapshot);
        }
    }

    /** Listens to the active pane's snapshots exactly while a window is open. */
    private void updateFollowing() {
        boolean wanted = !sessions.isEmpty() && !disposed;
        if (wanted && stopFollowing.isEmpty()) {
            stopFollowing = Optional.of(EveryPane.addActivePaneSnapshotListener(view, snapshotListener));
        } else if (!wanted && stopFollowing.isPresent()) {
            stopFollowing.get().run();
            stopFollowing = Optional.empty();
        }
    }

    /**
     * True when {@code ready} may be computed: up to 1 024 rows, no more than {@code accepted} rows the user already
     * accepted for this window, or the user says yes now.
     */
    private boolean confirmed(Preparation.Ready ready, int accepted, String title) {
        return !ready.needsConfirmation() || ready.rowCount() <= accepted
                || confirmation.confirm(view, title, TruthTableMessages.confirmation(ready.inputBits()));
    }

    private Preparation prepare(Set<ElementId> scope) {
        OpenDocument openDocument = view.openDocument();
        return TruthTableScope.prepare(openDocument.document(), scope, openDocument.registry());
    }

    private String scopeName(Set<ElementId> scope) {
        if (scope.isEmpty()) {
            return view.openDocument().displayName();
        }
        return TruthTableScope.singleInstance(view.openDocument().document(), scope)
                .map(ComponentDefinition::name)
                .orElseGet(TruthTableMessages::selectionName);
    }

    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        itemRemover.run();
        simulationMenu.remove(separator);
        runner.dispose();
        for (Session session : List.copyOf(sessions)) {
            session.closed = true;
            session.stopProgressDelay.run();
            session.display.close();
        }
        sessions.clear();
        updateFollowing();
    }

    /**
     * Whether {@code scope} (empty: the whole circuit) holds at least one switch or input pin and one LED or output
     * pin, so a table can come out of it.
     */
    private static boolean hasInputAndOutput(CircuitDocument document, Set<ElementId> scope) {
        boolean input = false;
        boolean output = false;
        for (cl.almejo.vsim.core.document.Component component : document.components()) {
            if (scope.isEmpty() || scope.contains(component.id())) {
                input |= INPUT_TYPES.contains(component.typeId());
                output |= OUTPUT_TYPES.contains(component.typeId());
            }
        }
        return input && output;
    }

    /** One window: its scope, its display and the computation running for it. */
    private final class Session implements TruthTableDisplay.Controls, TruthTableRunner.Callbacks {

        private final Set<ElementId> scope;
        private final String title;
        /** What the table is of (the circuit's or the component's name): the wizard's title. */
        private final String scopeName;
        private TruthTableDisplay display;
        /** The most rows the user accepted for this window: «Recalcular» asks again only for more. */
        private int acceptedRows;
        private boolean running;
        private boolean restarting;
        private boolean showsProgress;
        private boolean closed;
        private int lastPercent;
        private Runnable stopProgressDelay = () -> {
        };
        /** The table shown, where its inputs are live, and the row highlighted (spec §5.4). */
        private TruthTable table;
        private LiveInputs live;
        private LiveInputs pendingLive;
        private OptionalInt shownRow = OptionalInt.empty();
        /** Whether a double-click on a row sets the switches (every input a top-level switch), for the table shown. */
        private boolean tryable;
        private boolean pendingTryable;

        Session(Set<ElementId> scope, String title, String scopeName) {
            this.scope = scope;
            this.title = title;
            this.scopeName = scopeName;
        }

        void run(Preparation.Ready ready) {
            acceptedRows = Math.max(acceptedRows, ready.rowCount());
            OpenDocument openDocument = view.openDocument();
            pendingLive = LiveInputs.resolve(ready.liveInputPins(), openDocument.netlist(),
                    openDocument.structureRevision());
            pendingTryable = ready.inputsAreTopLevelSwitches();
            stopProgressDelay.run();
            if (running) {
                restarting = true; // a restart is not a cancellation: no «cancelado» notice
                try {
                    runner.cancel();
                } finally {
                    restarting = false;
                }
            }
            showsProgress = false;
            lastPercent = 0;
            running = true;
            stopProgressDelay = delay.after(PROGRESS_DELAY_MILLISECONDS, this::progressDue);
            runner.start(ready, this);
        }

        /** Moves the highlight to the row of the live inputs, telling the window only when it changes. */
        void follow(SimulationSnapshot snapshot) {
            if (closed || table == null) {
                return;
            }
            OptionalInt row = live.rowIn(table, snapshot, view.openDocument().structureRevision());
            if (!row.equals(shownRow)) {
                shownRow = row;
                display.showCurrentRow(row);
            }
        }

        /** 300 ms of work and still running: the progress line and «Cancelar» show from now on. */
        private void progressDue() {
            if (running && !closed && !disposed) {
                showsProgress = true;
                display.showProgress(lastPercent);
            }
        }

        @Override
        public void recalculate() {
            if (disposed || closed) {
                return;
            }
            Preparation preparation = prepare(scope);
            if (preparation instanceof Preparation.Refused refused) {
                messages.accept(view, TruthTableMessages.refusal(refused));
            } else if (confirmed((Preparation.Ready) preparation, acceptedRows, title)) {
                run((Preparation.Ready) preparation);
            }
        }

        @Override
        public void cancel() {
            if (running) {
                runner.cancel();
            }
        }

        @Override
        public void closed() {
            closed = true;
            sessions.remove(this);
            updateFollowing();
            cancel();
            stopProgressDelay.run();
        }

        /**
         * Sets the table's switches to row {@code row} in the live simulation (spec §5.4), when every input is a switch
         * still in the document; the highlight then follows the snapshot like after clicks.
         */
        @Override
        public void tryRow(int row) {
            if (disposed || closed || table == null || !tryable || row < 0 || row >= table.rows().size()) {
                return;
            }
            CircuitDocument document = view.openDocument().document();
            Map<ElementId, Value> values = new HashMap<>();
            for (int column = 0; column < table.inputs().size(); column++) {
                ElementId id = new ElementId(table.inputs().get(column).componentId());
                boolean isSwitch = document.component(id)
                        .map(component -> StandardComponentTypes.isSwitchOrPushButton(component.typeId()))
                        .orElse(false);
                if (!isSwitch) {
                    return;
                }
                values.put(id, table.rows().get(row).inputs().get(column));
            }
            view.openDocument().setInputs(values);
        }

        /** The table shown goes to a new Karnaugh wizard, titled after what it is the table of. */
        @Override
        public void openInWizard() {
            if (disposed || closed || table == null || KarnaughHandoff.refusal(table).isPresent()) {
                return;
            }
            TruthTable shown = table;
            view.tableWizard().ifPresent(wizard -> wizard.accept(KarnaughHandoff.spec(shown, scopeName)));
        }

        @Override
        public void progress(int percent) {
            lastPercent = percent;
            if (showsProgress && !closed) {
                display.showProgress(percent);
            }
        }

        @Override
        public void finished(TruthTable table) {
            running = false;
            stopProgressDelay.run();
            if (!closed) {
                tryable = pendingTryable;
                display.allowTryingRows(tryable);
                display.showTable(table);
                if (view.tableWizard().isPresent()) {
                    display.offerWizard(KarnaughHandoff.refusal(table));
                }
                this.table = table;
                live = pendingLive;
                shownRow = OptionalInt.empty();
                follow(view.openDocument().latestSnapshot());
            }
        }

        @Override
        public void cancelled() {
            running = false;
            stopProgressDelay.run();
            if (!closed && !restarting) {
                display.showMessage(Messages.text("truthTable.cancelled"));
            }
        }

        @Override
        public void failed(Throwable throwable) {
            running = false;
            stopProgressDelay.run();
            if (!closed) {
                display.showMessage(Messages.format("truthTable.failed", String.valueOf(throwable.getMessage() == null ? throwable.getClass().getSimpleName()
                        : throwable.getMessage())));
            }
        }
    }

    /**
     * «Tabla de verdad…» on a selection or on the empty canvas when the scope has an input and an output; «… de
     * «Nombre»…» on one user-component instance; nothing otherwise (a lone gate or wire could only be refused).
     */
    private final class Contributor implements ContextMenuContributor {
        @Override
        public void contribute(JPopupMenu menu, Optional<DocumentElement> target, Selection selection,
                               Point2D gridPosition) {
            if (disposed) {
                return;
            }
            Set<ElementId> scope = selection.ids();
            CircuitDocument document = view.openDocument().document();
            Optional<ComponentDefinition> instance = TruthTableScope.singleInstance(document, scope);
            if (instance.isEmpty() && !hasInputAndOutput(document, scope)) {
                return;
            }
            String text = instance
                    .map(definition -> TruthTableMessages.instanceMenuText(definition.name()))
                    .orElseGet(() -> Messages.text("action.truthTable"));
            menu.add(new JMenuItem(Actions.of(text, () -> open(scope))));
        }
    }
}
