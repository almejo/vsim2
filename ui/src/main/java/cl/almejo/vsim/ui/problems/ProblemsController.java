package cl.almejo.vsim.ui.problems;

import cl.almejo.vsim.core.command.HistoryListener;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.MultiBitLayout;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.DocumentListener;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.library.KeptOldShape;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.simulation.FlatPinRef;
import cl.almejo.vsim.core.simulation.InstancePath;
import cl.almejo.vsim.core.simulation.ConnectivityProblem;
import cl.almejo.vsim.core.memory.MemoryLinkPaths;
import cl.almejo.vsim.core.simulation.LinkedFileProblem;
import cl.almejo.vsim.core.simulation.Problem;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.simulation.TunnelProblem;
import cl.almejo.vsim.core.simulation.WidthMismatch;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.canvas.BusValueText;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.ConnectionPoints;
import cl.almejo.vsim.ui.canvas.MultiBitCells;
import cl.almejo.vsim.ui.canvas.MultiBitReadout;
import cl.almejo.vsim.ui.canvas.TopLevelNets;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.panels.StatusBar;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import cl.almejo.vsim.ui.window.SeePiece;
import cl.almejo.vsim.ui.window.panes.EveryPane;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Short-circuit navigation of one editor window (spec §6.12): F8 / Shift+F8, the status bar warning and its click,
 * the Problems tab, the driver tooltip, the pulse of shorted wires and the "pause on short circuit" option.
 * <p>
 * Only snapshots of the current netlist ({@code token == openDocument.netlistToken()}) are shown; a stale snapshot
 * is ignored. {@link SimulationSnapshot#EMPTY} always applies: it is the reset.
 */
public final class ProblemsController {

    private static final String LOOSENED_NET_PREFIX = "loosened:";
    private static final String PLACED_ELSEWHERE_NET_PREFIX = "placedElsewhere:";
    private static final KeyStroke NEXT_KEY = KeyStroke.getKeyStroke(KeyEvent.VK_F8, 0);
    private static final KeyStroke PREVIOUS_KEY = KeyStroke.getKeyStroke(KeyEvent.VK_F8, InputEvent.SHIFT_DOWN_MASK);

    private final OpenDocument openDocument;
    /**
     * The circuit whose problems are listed: the view's own document, or the circuit of a component tab (every tab of
     * a circuit lists the same problems, spec §6.7).
     */
    private final OpenDocument circuit;
    private final EditorView view;
    private final StatusBar statusBar;
    private final JMenu viewMenu;
    private final JMenu simulationMenu;
    private final NetBlink blink;
    private final ShortCircuitPulse pulse;
    private final ConnectivityOverlay connectivityOverlay;
    private final ProblemNavigator navigator;
    private final ProblemsPanel panel;
    private final Action nextProblem;
    private final Action previousProblem;
    private final Action pauseOnShortCircuit;
    private final JMenuItem nextItem;
    private final JMenuItem previousItem;
    private final JCheckBoxMenuItem pauseItem;
    private final MenuListener pauseListener;
    private final Consumer<SimulationSnapshot> snapshotListener = this::showSnapshot;
    private final DocumentListener structureListener = (document, change) -> refreshStructure();
    private final HistoryListener historyListener = history -> refreshStructure();
    private final Runnable removeFromPanes;
    private SimulationSnapshot snapshot = SimulationSnapshot.EMPTY;
    private List<ConnectivityProblem> outdatedPieces = List.of();
    private List<LinkedFileProblem> reportedLinkedFiles = List.of();
    private boolean disposed;
    private final ConnectionPoints connectionPoints;

    public ProblemsController(EditorView view) {
        this.openDocument = view.openDocument();
        this.circuit = openDocument.parent().orElse(openDocument);
        this.view = view;
        this.connectionPoints = new ConnectionPoints(openDocument.registry());
        this.statusBar = view.statusBar();
        Runnable repaintPanes = EveryPane.repaint(view);
        this.blink = new NetBlink(() -> view.canvas().scheme(), repaintPanes);
        this.pulse = new ShortCircuitPulse(() -> view.canvas().highlights(), repaintPanes, System::nanoTime);
        this.connectivityOverlay = new ConnectivityOverlay(openDocument, () -> view.canvas().scheme(), this::linkedFileProblems);
        this.navigator = new ProblemNavigator(view::canvas, blink, this::showElsewhere);
        this.panel = new ProblemsPanel(this::describe, navigator::navigateTo);

        nextProblem = Actions.of(Messages.text("action.nextProblem"), navigator::next);
        nextProblem.putValue(Action.SHORT_DESCRIPTION, Messages.text("tooltip.nextProblem"));
        previousProblem = Actions.of(Messages.text("action.previousProblem"), navigator::previous);
        previousProblem.putValue(Action.SHORT_DESCRIPTION, Messages.text("tooltip.previousProblem"));
        pauseOnShortCircuit = new AbstractAction(Messages.text("action.pauseOnShortCircuit")) {
            @Override
            public void actionPerformed(ActionEvent event) {
                boolean enabled = event.getSource() instanceof AbstractButton button ? button.isSelected()
                        : !openDocument.pauseOnShortCircuit();
                openDocument.setPauseOnShortCircuit(enabled);
                putValue(Action.SELECTED_KEY, enabled);
            }
        };
        pauseOnShortCircuit.putValue(Action.SELECTED_KEY, openDocument.pauseOnShortCircuit());

        removeFromPanes = view.forEachPane(this::installOn);
        statusBar.onShortCircuitsClicked(() -> {
            if (!disposed) {
                navigator.next();
            }
        });
        view.inspector().addTab(Messages.text("problems.tab"), panel);

        viewMenu = view.menu(MenuId.VIEW);
        viewMenu.addSeparator();
        nextItem = viewMenu.add(nextProblem);
        previousItem = viewMenu.add(previousProblem);
        simulationMenu = view.menu(MenuId.SIMULATION);
        simulationMenu.addSeparator();
        pauseItem = new JCheckBoxMenuItem(pauseOnShortCircuit);
        simulationMenu.add(pauseItem);
        pauseListener = new MenuListener() {
            @Override
            public void menuSelected(MenuEvent event) {
                // Another window of the same document may have changed the option.
                pauseOnShortCircuit.putValue(Action.SELECTED_KEY, openDocument.pauseOnShortCircuit());
            }

            @Override
            public void menuDeselected(MenuEvent event) {
            }

            @Override
            public void menuCanceled(MenuEvent event) {
            }
        };
        simulationMenu.addMenuListener(pauseListener);
        openDocument.document().addListener(structureListener);
        if (circuit != openDocument) {
            circuit.document().addListener(structureListener);
        }
        // pieces that took a new pin elsewhere are known once the inside edit is applied, after its document change
        circuit.history().addListener(historyListener);
        showSnapshot(SimulationSnapshot.EMPTY);
    }

    /**
     * A problem in words: the view's own memory and library warnings with its own document, the circuit's problems
     * with the circuit (a component tab names them by their route from the circuit).
     */
    private String describe(Problem problem) {
        OpenDocument named = isOwn(problem) ? openDocument : circuit;
        return ProblemText.describe(problem, named.document(), named.registry());
    }

    /** The problems this view finds itself (linked memory files, outdated pieces), about its own document. */
    private boolean isOwn(Problem problem) {
        return outdatedPieces.contains(problem) || reportedLinkedFiles.contains(problem);
    }

    /**
     * Shows {@code problem} in the tab where its part is, when that is not this canvas (spec §6.7): from the circuit's
     * tab a problem inside a component enters that component with the instance of its route and selects the part;
     * from a component tab every problem of the circuit goes to its place. False when this canvas shows it (also
     * outside a real window, where there are no other tabs: the circuit's canvas then selects the piece on it).
     */
    private boolean showElsewhere(Problem problem) {
        if (isOwn(problem)) {
            return false;
        }
        Optional<String> focus = ProblemPlaces.focusOf(problem);
        boolean inside = focus.filter(InstancePath::isNested).isPresent();
        if (!openDocument.isBody() && !inside) {
            return false;
        }
        return focus.map(id -> SeePiece.show(view.windowCommands(), circuit, id)).orElse(false);
    }

    /**
     * The glow, the blink, the tooltips and F8 / Shift+F8 in {@code pane}; the snapshots of the active pane only are
     * shown (every pane shows the same document and simulation). Returns what undoes it.
     */
    private Runnable installOn(CircuitCanvas pane) {
        Consumer<SimulationSnapshot> paneSnapshots = latest -> {
            if (pane == view.canvas()) {
                snapshotListener.accept(latest);
            }
        };
        pane.addOverlay(pulse);
        pane.addOverlay(blink);
        pane.addOverlay(connectivityOverlay);
        pane.setToolTipProvider(gridPoint -> tooltipAt(pane, gridPoint));
        pane.addSnapshotListener(paneSnapshots);
        pane.bindKey(NEXT_KEY, "nextProblem", nextProblem);
        pane.bindKey(PREVIOUS_KEY, "previousProblem", previousProblem);
        return () -> {
            pane.removeSnapshotListener(paneSnapshots);
            pane.removeOverlay(pulse);
            pane.removeOverlay(blink);
            pane.removeOverlay(connectivityOverlay);
            pane.clearToolTipProvider();
            pane.unbindKey(NEXT_KEY, "nextProblem");
            pane.unbindKey(PREVIOUS_KEY, "previousProblem");
        };
    }

    /** Called with every new displayed snapshot; the parts only rebuild when their problems change. */
    public void showSnapshot(SimulationSnapshot latest) {
        boolean current = latest.equals(SimulationSnapshot.EMPTY) || latest.token() == openDocument.netlistToken();
        if (disposed || !current) {
            return;
        }
        snapshot = latest;
        refreshProblems();
        pulse.showSnapshot(latest);
        statusBar.showProblems(latest.shortCircuits().size(), latest.widthMismatches().size(),
                tunnelErrorCount(latest));
    }

    /** Refreshes structural warnings even while the simulation is paused or its snapshot is still stale. */
    private void refreshStructure() {
        if (disposed) {
            return;
        }
        if (snapshot.token() != openDocument.netlistToken()) {
            snapshot = SimulationSnapshot.EMPTY;
        }
        refreshProblems();
        EveryPane.repaint(view).run();
    }

    private void refreshProblems() {
        List<Problem> problems = new ArrayList<>(listedSnapshot().problems().stream()
                .filter(problem -> !(problem instanceof ConnectivityProblem)).toList());
        problems.addAll(circuit.connectivityProblems());
        problems.addAll(loosenedPinProblems());
        problems.addAll(pinsPlacedElsewhere());
        problems.addAll(outdatedPieces);
        problems.addAll(linkedFileProblems());
        SimulationSnapshot combined = new SimulationSnapshot(snapshot.revision(), openDocument.netlistToken(),
                snapshot.time(), snapshot.running(), snapshot.netValues(), snapshot.componentStates(), problems,
                snapshot.failure());
        navigator.showSnapshot(combined);
        panel.showSnapshot(combined);
        boolean anyProblem = !problems.isEmpty();
        nextProblem.setEnabled(anyProblem);
        previousProblem.setEnabled(anyProblem);
    }

    /**
     * The snapshot whose problems are listed: the one shown, or in a component tab the circuit's latest (the canvas
     * shows the context instance's part of it), while it belongs to the circuit's current netlist.
     */
    private SimulationSnapshot listedSnapshot() {
        if (circuit == openDocument) {
            return snapshot;
        }
        SimulationSnapshot latest = circuit.latestSnapshot();
        return latest.token() == circuit.netlistToken() ? latest : SimulationSnapshot.EMPTY;
    }

    /**
     * One problem per new pin an inside edit could not put where its component puts it, for the pieces still on the
     * canvas (spec §6.2 a): the pin is on another spot of the piece's edge, and its old shape stays.
     */
    private List<ConnectivityProblem> pinsPlacedElsewhere() {
        List<ConnectivityProblem> placed = new ArrayList<>();
        for (KeptOldShape kept : circuit.keptOldShapes()) {
            Optional<Component> component = circuit.document().component(kept.instance());
            if (component.isEmpty()) {
                continue;
            }
            ComponentType type = circuit.registry().typeOf(component.get());
            for (String pinName : kept.newPinNames()) {
                for (int pinIndex = 0; pinIndex < type.pinCount(); pinIndex++) {
                    if (type.pin(pinIndex).name().equals(pinName)) {
                        placed.add(new ConnectivityProblem(PLACED_ELSEWHERE_NET_PREFIX + kept.instance().value() + ":"
                                + pinIndex, kept.instance().value(), pinIndex,
                                type.pinLocation(component.get(), pinIndex),
                                ConnectivityProblem.Cause.PIN_PLACED_ELSEWHERE));
                    }
                }
            }
        }
        return placed;
    }

    /** One problem per pin a shape change left loose in this session, for the pieces still on the canvas. */
    private List<ConnectivityProblem> loosenedPinProblems() {
        List<ConnectivityProblem> loosened = new ArrayList<>();
        for (PinRef pin : circuit.loosenedPins()) {
            Optional<Component> component = circuit.document().component(pin.componentId());
            if (component.isEmpty()) {
                continue;
            }
            ComponentType type = circuit.registry().typeOf(component.get());
            if (pin.pinIndex() < type.pinCount()) {
                loosened.add(new ConnectivityProblem(LOOSENED_NET_PREFIX + pin.componentId().value() + ":"
                        + pin.pinIndex(), pin.componentId().value(), pin.pinIndex(),
                        type.pinLocation(component.get(), pin.pinIndex()),
                        ConnectivityProblem.Cause.LOOSENED_BY_SHAPE_CHANGE));
            }
        }
        loosened.sort(Comparator.comparing(ConnectivityProblem::netId));
        return loosened;
    }

    /**
     * Lists the pieces that use an older version than the library's and would break if updated (what
     * {@code OutdatedPieceChecks} found); replaces the previous list.
     */
    public void showOutdatedPieces(List<ConnectivityProblem> pieces) {
        if (disposed || pieces.equals(outdatedPieces)) {
            return;
        }
        outdatedPieces = List.copyOf(pieces);
        refreshStructure();
    }

    /**
     * Lists the linked memory files that are missing or unreadable (what the document's {@code LinkedMemoryWatcher}
     * found); replaces the previous list.
     */
    public void showLinkedFileProblems(List<LinkedFileProblem> found) {
        if (disposed || found.equals(reportedLinkedFiles)) {
            return;
        }
        reportedLinkedFiles = List.copyOf(found);
        refreshStructure();
    }

    /**
     * The reported linked-file problems that are still true of the document: a memory that was deleted, or whose link
     * was removed or changed, drops out at once, without waiting for the next look at the files.
     */
    List<LinkedFileProblem> linkedFileProblems() {
        return reportedLinkedFiles.stream().filter(problem -> openDocument.document()
                .component(new ElementId(problem.elementId()))
                .flatMap(memory -> MemoryLinkPaths.linkedFile(memory, openDocument.path()).map(file ->
                        MemoryLinkPaths.displayPath(MemoryLinkPaths.linkOf(memory), openDocument.path())))
                .filter(problem.link()::equals).isPresent())
                .sorted(Comparator.comparing(LinkedFileProblem::elementId)).toList();
    }

    /** The tunnel problems that are errors (the warnings show only in the Problems tab, like display warnings). */
    private static int tunnelErrorCount(SimulationSnapshot snapshot) {
        return (int) snapshot.problems().stream()
                .filter(problem -> problem instanceof TunnelProblem tunnel && !tunnel.isWarning())
                .count();
    }

    /**
     * Shows the component library's load problems in the Problems tab (texts already localized); only
     * {@code fileCount} of them count as files in the header.
     */
    public void showLibraryProblems(List<String> descriptions, int fileCount) {
        if (!disposed) {
            panel.showLibraryProblems(descriptions, fileCount);
        }
    }

    /** The heading of the library section of the Problems tab. */
    public String libraryProblemsHeader() {
        return panel.libraryHeader().getText();
    }

    public List<String> libraryProblems() {
        return panel.libraryProblemTexts();
    }

    public Action nextProblem() {
        return nextProblem;
    }

    public Action previousProblem() {
        return previousProblem;
    }

    public Action pauseOnShortCircuit() {
        return pauseOnShortCircuit;
    }

    public ProblemNavigator navigator() {
        return navigator;
    }

    ProblemsPanel panel() {
        return panel;
    }

    NetBlink blink() {
        return blink;
    }

    ShortCircuitPulse pulse() {
        return pulse;
    }

    /**
     * Stops the timers and removes what this controller registered on the canvas and the menus. The Problems tab and
     * the status bar click stay (neither can be unregistered) but do nothing any more.
     */
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        openDocument.document().removeListener(structureListener);
        circuit.document().removeListener(structureListener);
        circuit.history().removeListener(historyListener);
        blink.dispose();
        pulse.dispose();
        removeFromPanes.run();
        viewMenu.remove(nextItem);
        viewMenu.remove(previousItem);
        simulationMenu.remove(pauseItem);
        simulationMenu.removeMenuListener(pauseListener);
    }

    /**
     * A pin within {@link ConnectionPoints#PIN_RADIUS} of {@code gridPoint} comes first (M6.1-E): its name, bit, width
     * and value, then the short circuit or width mismatch of its net. Else, when the snapshot matches the current
     * netlist: for the wire under it, the drivers of its short circuit, else the pins of its width mismatch, else the
     * value of its bus; for a 7-segment display showing «–», why. Problems are looked up under the
     * id the engine reports them with: a top-level net joined to another through a template uses the merged net's root
     * id ({@link TopLevelNets}); a net whose own pins disagree falls back to the netlist's mismatch flag and its pins.
     */
    Optional<String> tooltipAt(Point2D gridPoint) {
        return tooltipAt(view.canvas(), gridPoint);
    }

    private Optional<String> tooltipAt(CircuitCanvas canvas, Point2D gridPoint) {
        CircuitDocument document = openDocument.document();
        boolean current = snapshot.token() == openDocument.netlistToken();
        Optional<ConnectionPoints.PinHit> pin = connectionPoints.pinNear(document, gridPoint);
        if (pin.isPresent()) {
            return Optional.of(pinTooltip(canvas, document, pin.get(), current));
        }
        Optional<DocumentElement> hit = canvas.hitTester().elementAt(document, gridPoint);
        if (hit.isPresent() && hit.get() instanceof Component component
                && MultiBitLayout.isMultiBit(openDocument.registry().typeOf(component))) {
            return multiBitTooltip(component, gridPoint, current);
        }
        if (!current) {
            return Optional.empty();
        }
        if (hit.isPresent() && hit.get() instanceof Wire wire) {
            return openDocument.netlist().netForWire(wire.id()).flatMap(net -> wireTooltip(canvas, document, net));
        }
        if (hit.isPresent() && hit.get() instanceof Component component) {
            return displayTooltip(component);
        }
        return Optional.empty();
    }

    /**
     * Any pin of any component (M6.1-E): {@link PinText} with the value of its net while a current snapshot is shown;
     * a short circuit or width mismatch of that net follows after « · ». Shown even with a stale snapshot, without
     * the value.
     */
    private String pinTooltip(CircuitCanvas canvas, CircuitDocument document, ConnectionPoints.PinHit pin,
                              boolean current) {
        Component component = pin.component();
        Optional<Net> net = openDocument.netlist().netForPin(new PinRef(component.id(), pin.pinIndex()));
        boolean withValues = current && !snapshot.equals(SimulationSnapshot.EMPTY);
        Optional<Value> value = withValues
                ? net.map(found -> snapshot.netValues().get(found.id().value()))
                : Optional.empty();
        boolean unconnected = connectionPoints.isOpen(document, component, pin.pinIndex(), net);
        String text = PinText.describe(component, openDocument.registry().typeOf(component),
                pin.pinIndex(), unconnected, value);
        Optional<String> problem = current ? net.flatMap(found -> problemText(canvas, document, found))
                : Optional.empty();
        return problem.map(found -> text + PinText.SEPARATOR + found).orElse(text);
    }

    /**
     * A multi-bit Switch or LED: its value (only from a current snapshot) and the bit under the pointer, so a stale
     * picture still names the bit.
     */
    private Optional<String> multiBitTooltip(Component component, Point2D gridPoint, boolean current) {
        Optional<Value> value = current
                ? MultiBitReadout.valueOf(snapshot.componentState(component.id().value()))
                : Optional.empty();
        ComponentType type = openDocument.registry().typeOf(component);
        return MultiBitReadout.text(value, MultiBitCells.bitAt(type, component, gridPoint),
                MultiBitReadout.numbersOf(type, component.parameters()));
    }

    /** A 7-segment display showing «–» (a Z input) explains it (M6.1-E); any other component has no tooltip. */
    private Optional<String> displayTooltip(Component component) {
        return snapshot.componentState(component.id().value()) instanceof VisibleState.SevenSegmentsState display
                && !display.value().isFullyDefined()
                ? Optional.of(Messages.text("display.undefined"))
                : Optional.empty();
    }

    private Optional<String> wireTooltip(CircuitCanvas canvas, CircuitDocument document, Net net) {
        Optional<String> problem = problemText(canvas, document, net);
        if (problem.isPresent()) {
            return problem;
        }
        Value value = snapshot.netValues().get(net.id().value());
        if (net.width() > 1 && value != null) {
            return Optional.of(value.conflict()
                    ? Messages.format("bus.tooltipConflict", net.width(), BusValueText.of(value))
                    : Messages.format("bus.tooltip", net.width(), BusValueText.of(value), BusValueText.binary(value)));
        }
        return Optional.empty();
    }

    /** The drivers of the short circuit on {@code net}, else its width-mismatch text, if it has either. */
    private Optional<String> problemText(CircuitCanvas canvas, CircuitDocument document, Net net) {
        String reportedId = reportedId(canvas, document, net);
        Optional<String> drivers = snapshot.shortCircuits().stream()
                .filter(shortCircuit -> shortCircuit.netId().equals(reportedId))
                .findFirst()
                .map(shortCircuit -> ProblemText.drivers(shortCircuit, document));
        if (drivers.isPresent()) {
            return drivers;
        }
        return mismatchText(document, net, reportedId);
    }

    /** The id the engine reports the problems of {@code net} under (the merged net's root through a template). */
    private String reportedId(CircuitCanvas canvas, CircuitDocument document, Net net) {
        String netId = net.id().value();
        // Tooltips are asked on every mouse move: the canvas's cache flattens once per revision and netlist.
        return snapshot.problems().isEmpty() ? netId
                : canvas.highlights().topLevelNets(document, openDocument.netlist()).rootOf(netId);
    }

    /** The width-mismatch text of {@code net}: the one reported under {@code reportedId}, else its own pins'. */
    private Optional<String> mismatchText(CircuitDocument document, Net net, String reportedId) {
        Optional<WidthMismatch> mismatch = snapshot.widthMismatches().stream()
                .filter(widthMismatch -> widthMismatch.netId().equals(reportedId))
                .findFirst();
        if (mismatch.isEmpty() && net.widthMismatch()) {
            mismatch = Optional.of(new WidthMismatch(net.id().value(), net.pins().stream()
                    .filter(pin -> hasAWidthOfItsOwn(document, pin))
                    .map(pin -> new FlatPinRef(pin.componentId().value(), pin.pinIndex()))
                    .toList()));
        }
        return mismatch.map(found -> ProblemText.describe(found, document, openDocument.registry()));
    }

    /** False for a tunnel's pin: it takes the net's width, so it is never one side of a mismatch (like the engine). */
    private boolean hasAWidthOfItsOwn(CircuitDocument document, PinRef pin) {
        return document.component(pin.componentId())
                .map(component -> !openDocument.registry().typeOf(component).pin(pin.pinIndex()).isAdaptiveWidth())
                .orElse(true);
    }
}
