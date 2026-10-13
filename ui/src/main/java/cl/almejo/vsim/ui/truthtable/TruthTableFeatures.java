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
import cl.almejo.vsim.ui.actions.ContextMenuContributor;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.Selection;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.Timer;
import java.awt.Component;
import java.awt.event.ActionEvent;
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
            StandardComponentTypes.INPUT_PIN);
    private static final Set<String> OUTPUT_TYPES = Set.of(StandardComponentTypes.LED,
            StandardComponentTypes.OUTPUT_PIN);

    /** Runs {@code task} once after a delay, on the EDT; the returned runnable stops it if it has not run yet. */
    @FunctionalInterface
    public interface Delay {
        Runnable after(int milliseconds, Runnable task);

        /** A one-shot Swing {@link Timer}. */
        static Delay swing() {
            return (milliseconds, task) -> {
                Timer timer = new Timer(milliseconds, event -> task.run());
                timer.setRepeats(false);
                timer.start();
                return timer::stop;
            };
        }
    }

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
    private final JMenuItem item;
    private final Runnable shortcutRemover;
    private final List<Session> sessions = new ArrayList<>();
    /** On the canvas while a window is open: moves each window's highlight to the row of the live inputs. */
    private final Consumer<SimulationSnapshot> snapshotListener = this::followSnapshot;
    private boolean following;
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
        this.action = new AbstractAction(Messages.text("action.truthTable")) {
            @Override
            public void actionPerformed(ActionEvent event) {
                open(view.canvas().selection().ids());
            }
        };
        this.simulationMenu = view.menu(MenuId.SIMULATION);
        simulationMenu.addSeparator();
        this.separator = simulationMenu.getMenuComponent(simulationMenu.getMenuComponentCount() - 1);
        this.item = new JMenuItem(action);
        simulationMenu.add(item);
        this.shortcutRemover = view.registerShortcutAction("simulation.truthTable", action);
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
        String title = TruthTableMessages.title(scopeName(scope));
        if (!confirmed(ready, 0, title)) {
            return;
        }
        Session session = new Session(scope, title);
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

    /** Listens to the canvas snapshots exactly while a window is open. */
    private void updateFollowing() {
        boolean wanted = !sessions.isEmpty() && !disposed;
        if (wanted && !following) {
            view.canvas().addSnapshotListener(snapshotListener);
        } else if (!wanted && following) {
            view.canvas().removeSnapshotListener(snapshotListener);
        }
        following = wanted;
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
        shortcutRemover.run();
        simulationMenu.remove(item);
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
        private TruthTableDisplay display;
        /** The most rows the user accepted for this window: «Recalcular» asks again only for more. */
        private int acceptedRows;
        private boolean running;
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

        Session(Set<ElementId> scope, String title) {
            this.scope = scope;
            this.title = title;
        }

        void run(Preparation.Ready ready) {
            acceptedRows = Math.max(acceptedRows, ready.rowCount());
            OpenDocument openDocument = view.openDocument();
            pendingLive = LiveInputs.resolve(ready.liveInputPins(), openDocument.netlist(),
                    openDocument.netlistToken());
            pendingTryable = ready.inputsAreTopLevelSwitches();
            stopProgressDelay.run();
            if (running) {
                runner.cancel(); // this window's previous computation says «cancelado» now, not after the new start
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
            OptionalInt row = live.rowIn(table, snapshot);
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
                        .map(component -> component.typeId().equals(StandardComponentTypes.SWITCH))
                        .orElse(false);
                if (!isSwitch) {
                    return;
                }
                values.put(id, table.rows().get(row).inputs().get(column));
            }
            view.openDocument().setInputs(values);
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
            if (!closed) {
                display.showMessage(Messages.text("truthTable.cancelled"));
            }
        }

        @Override
        public void failed(RuntimeException exception) {
            running = false;
            stopProgressDelay.run();
            if (!closed) {
                display.showMessage(Messages.format("truthTable.failed", String.valueOf(exception.getMessage())));
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
            menu.add(new JMenuItem(new AbstractAction(text) {
                @Override
                public void actionPerformed(ActionEvent event) {
                    open(scope);
                }
            }));
        }
    }
}
