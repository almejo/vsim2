package cl.almejo.vsim.ui.problems;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.DocumentListener;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.simulation.FlatPinRef;
import cl.almejo.vsim.core.simulation.ConnectivityProblem;
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
import cl.almejo.vsim.ui.canvas.TopLevelNets;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.panels.StatusBar;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
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
    private static final KeyStroke NEXT_KEY = KeyStroke.getKeyStroke(KeyEvent.VK_F8, 0);
    private static final KeyStroke PREVIOUS_KEY = KeyStroke.getKeyStroke(KeyEvent.VK_F8, InputEvent.SHIFT_DOWN_MASK);

    private final OpenDocument openDocument;
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
    private final Runnable removeFromPanes;
    private SimulationSnapshot snapshot = SimulationSnapshot.EMPTY;
    private List<ConnectivityProblem> outdatedPieces = List.of();
    private boolean disposed;
    private final ConnectionPoints connectionPoints;

    public ProblemsController(EditorView view) {
        this.openDocument = view.openDocument();
        this.view = view;
        this.connectionPoints = new ConnectionPoints(openDocument.registry());
        this.statusBar = view.statusBar();
        Runnable repaintPanes = EveryPane.repaint(view);
        this.blink = new NetBlink(() -> view.canvas().scheme(), repaintPanes);
        this.pulse = new ShortCircuitPulse(() -> view.canvas().highlights(), repaintPanes, System::nanoTime);
        this.connectivityOverlay = new ConnectivityOverlay(openDocument, () -> view.canvas().scheme());
        this.navigator = new ProblemNavigator(view::canvas, blink);
        this.panel = new ProblemsPanel(openDocument, navigator::navigateTo);

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
        showSnapshot(SimulationSnapshot.EMPTY);
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
        List<Problem> problems = new ArrayList<>(snapshot.problems().stream()
                .filter(problem -> !(problem instanceof ConnectivityProblem)).toList());
        problems.addAll(openDocument.connectivityProblems());
        problems.addAll(loosenedPinProblems());
        problems.addAll(outdatedPieces);
        SimulationSnapshot combined = new SimulationSnapshot(snapshot.revision(), openDocument.netlistToken(),
                snapshot.time(), snapshot.running(), snapshot.netValues(), snapshot.componentStates(), problems,
                snapshot.failure());
        navigator.showSnapshot(combined);
        panel.showSnapshot(combined);
        boolean anyProblem = !problems.isEmpty();
        nextProblem.setEnabled(anyProblem);
        previousProblem.setEnabled(anyProblem);
    }

    /** One problem per pin a shape change left loose in this session, for the pieces still on the canvas. */
    private List<ConnectivityProblem> loosenedPinProblems() {
        List<ConnectivityProblem> loosened = new ArrayList<>();
        for (PinRef pin : openDocument.loosenedPins()) {
            Optional<Component> component = openDocument.document().component(pin.componentId());
            if (component.isEmpty()) {
                continue;
            }
            ComponentType type = openDocument.registry().typeOf(component.get());
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
        if (!current) {
            return Optional.empty();
        }
        Optional<DocumentElement> hit = canvas.hitTester().elementAt(document, gridPoint);
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
