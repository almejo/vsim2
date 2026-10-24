package cl.almejo.vsim.ui.problems;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.NetId;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.simulation.DisplayBusOverride;
import cl.almejo.vsim.core.simulation.ConnectivityProblem;
import cl.almejo.vsim.core.simulation.InstancePath;
import cl.almejo.vsim.core.simulation.Problem;
import cl.almejo.vsim.core.simulation.ShortCircuit;
import cl.almejo.vsim.core.simulation.TunnelProblem;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.simulation.WidthMismatch;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/** Walks the problems of the displayed snapshot (F8 / Shift+F8) and shows each one on the canvas (spec §6.12). */
public final class ProblemNavigator {

    private final Supplier<CircuitCanvas> canvas;
    private final NetBlink blink;
    private List<Problem> problems = List.of();
    private int current = -1;

    /** {@code canvas} is resolved at every travel: the window's active pane. */
    public ProblemNavigator(Supplier<CircuitCanvas> canvas, NetBlink blink) {
        this.canvas = canvas;
        this.blink = blink;
    }

    /**
     * Takes the problems of {@code snapshot} in {@link ProblemText#ordered} order, keeping the cursor on the same
     * problem ({@link #indexToKeep}).
     */
    public void showSnapshot(SimulationSnapshot snapshot) {
        List<Problem> ordered = ProblemText.ordered(snapshot.problems());
        if (ordered.equals(problems)) {
            return;
        }
        Optional<Problem> previous = current >= 0 ? Optional.of(problems.get(current)) : Optional.empty();
        problems = ordered;
        current = previous.map(problem -> indexToKeep(ordered, problem)).orElse(-1);
    }

    /**
     * Where {@code previous} is in the refreshed {@code problems}: the same problem when it is still there, else the
     * first one on its net (a short whose start time changed, a mismatch whose pins changed), else -1. Several problems
     * can share a net (every tunnel problem of a name, a mismatch on the same net), so the net alone would jump.
     */
    static int indexToKeep(List<Problem> problems, Problem previous) {
        int same = problems.indexOf(previous);
        if (same >= 0) {
            return same;
        }
        for (int index = 0; index < problems.size(); index++) {
            if (problems.get(index).netId().equals(previous.netId())) {
                return index;
            }
        }
        return -1;
    }

    public List<Problem> problems() {
        return problems;
    }

    public Optional<Problem> next() {
        return step(1);
    }

    public Optional<Problem> previous() {
        return step(-1);
    }

    /**
     * Centers the view on the net of {@code problem}, selects its wires (or its components when it has none),
     * makes the wires blink and focuses the canvas. A net that lives only inside templates (its id is hierarchical,
     * such as {@code c5/n:…}) centers on and selects the top-level instances that hold it, its drivers or its pins,
     * and stops the blink of the previous net.
     * False, with nothing changed, when neither the net nor any of those instances exists.
     */
    public boolean navigateTo(Problem problem) {
        int index = problems.indexOf(problem);
        if (index >= 0) {
            current = index;
        }
        if (problem instanceof ConnectivityProblem connectivity) {
            return navigateToConnectivity(connectivity);
        }
        if (problem instanceof TunnelProblem tunnel && navigateToTunnels(tunnel)) {
            return true;
        }
        Optional<Net> net = canvas.get().openDocument().netlist().net(new NetId(problem.netId()));
        if (net.isEmpty()) {
            return navigateToInstances(problem);
        }
        focusNet(canvas.get(), blink, net.get());
        return true;
    }

    private boolean navigateToConnectivity(ConnectivityProblem problem) {
        CircuitCanvas pane = canvas.get();
        CircuitDocument document = pane.openDocument().document();
        ElementId visibleId = new ElementId(InstancePath.topLevel(problem.elementId()));
        Optional<Component> component = document.component(visibleId);
        if (component.isPresent()) {
            pane.travelTo(pane.openDocument().registry().typeOf(component.get()).bounds(component.get()));
        } else if (!InstancePath.isNested(problem.elementId()) && document.wire(visibleId).isPresent()) {
            pane.travelTo(Bounds.of(problem.position(), problem.position()));
        } else {
            return false;
        }
        pane.selection().set(List.of(visibleId));
        blink.stop();
        pane.requestFocusInWindow();
        return true;
    }

    /**
     * Takes {@code canvas} to {@code net} (when it has points), selects its wires (or the components of its pins
     * when it has none), makes the wires blink and focuses the canvas. Shared by problem navigation and the
     * waveforms' «Mostrar en el circuito».
     */
    public static void focusNet(CircuitCanvas canvas, NetBlink blink, Net net) {
        Bounds bounds = bounds(net);
        if (bounds != null) {
            canvas.travelTo(bounds);
        }
        if (net.wires().isEmpty()) {
            canvas.selection().set(net.pins().stream().map(PinRef::componentId).distinct().toList());
        } else {
            canvas.selection().set(net.wires());
        }
        blink.start(net.wires());
        canvas.requestFocusInWindow();
    }

    /**
     * A tunnel problem of the top circuit: travels to its tunnels (the two entradas of a duplicate, the lone salida)
     * and selects them, which says more than the net they share. False when none of them is in the top circuit (a
     * problem inside a component goes to its instance instead).
     */
    private boolean navigateToTunnels(TunnelProblem problem) {
        CircuitDocument document = canvas.get().openDocument().document();
        ComponentTypeRegistry registry = canvas.get().openDocument().registry();
        List<Component> tunnels = problem.tunnels().stream()
                .filter(id -> !InstancePath.isNested(id))
                .map(id -> document.component(new ElementId(id)))
                .flatMap(Optional::stream)
                .toList();
        if (tunnels.isEmpty()) {
            return false;
        }
        Bounds result = null;
        for (Component tunnel : tunnels) {
            Bounds bounds = registry.typeOf(tunnel).bounds(tunnel);
            result = result == null ? bounds : result.union(bounds);
        }
        canvas.get().travelTo(result);
        canvas.get().selection().set(tunnels.stream().map(Component::id).toList());
        blink.stop();
        canvas.get().requestFocusInWindow();
        return true;
    }

    private boolean navigateToInstances(Problem problem) {
        Set<String> ids = new LinkedHashSet<>();
        ids.add(problem.netId());
        switch (problem) {
            case ShortCircuit shortCircuit -> shortCircuit.drivers().forEach(driver -> ids.add(driver.componentId()));
            case WidthMismatch mismatch -> mismatch.pins().forEach(pin -> ids.add(pin.componentId()));
            case DisplayBusOverride display -> ids.add(display.componentId());
            case TunnelProblem tunnel -> ids.addAll(tunnel.tunnels());
            case ConnectivityProblem connectivity -> ids.add(connectivity.elementId());
        }
        CircuitDocument document = canvas.get().openDocument().document();
        ComponentTypeRegistry registry = canvas.get().openDocument().registry();
        List<Component> instances = ids.stream()
                .filter(InstancePath::isNested)
                .map(InstancePath::topLevel)
                .distinct()
                .map(id -> document.component(new ElementId(id)))
                .flatMap(Optional::stream)
                .toList();
        Bounds result = null;
        for (Component instance : instances) {
            Optional<ComponentType> type = registry.findTypeOf(instance);
            if (type.isPresent()) {
                Bounds bounds = type.get().bounds(instance);
                result = result == null ? bounds : result.union(bounds);
            }
        }
        if (result == null) {
            return false;
        }
        canvas.get().travelTo(result);
        canvas.get().selection().set(instances.stream().map(Component::id).toList());
        blink.stop();
        canvas.get().requestFocusInWindow();
        return true;
    }

    static Bounds bounds(Net net) {
        Bounds result = null;
        for (GridPoint point : net.points()) {
            Bounds single = Bounds.of(point, point);
            result = result == null ? single : result.union(single);
        }
        return result;
    }

    private Optional<Problem> step(int direction) {
        if (problems.isEmpty()) {
            return Optional.empty();
        }
        int start = current >= 0 ? current : (direction > 0 ? -1 : 0);
        Problem problem = problems.get(Math.floorMod(start + direction, problems.size()));
        navigateTo(problem);
        return Optional.of(problem);
    }
}
