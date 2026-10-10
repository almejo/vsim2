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
import cl.almejo.vsim.core.simulation.InstancePath;
import cl.almejo.vsim.core.simulation.Problem;
import cl.almejo.vsim.core.simulation.ShortCircuit;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.simulation.WidthMismatch;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Walks the problems of the displayed snapshot (F8 / Shift+F8) and shows each one on the canvas (spec §6.12). */
public final class ProblemNavigator {

    private final CircuitCanvas canvas;
    private final NetBlink blink;
    private List<Problem> problems = List.of();
    private int current = -1;

    public ProblemNavigator(CircuitCanvas canvas, NetBlink blink) {
        this.canvas = canvas;
        this.blink = blink;
    }

    /** Takes the problems of {@code snapshot} in {@link ProblemText#ordered} order, keeping the cursor on its net. */
    public void showSnapshot(SimulationSnapshot snapshot) {
        List<Problem> ordered = ProblemText.ordered(snapshot.problems());
        if (ordered.equals(problems)) {
            return;
        }
        String currentNet = current >= 0 ? problems.get(current).netId() : null;
        problems = ordered;
        current = -1;
        for (int index = 0; index < problems.size(); index++) {
            if (problems.get(index).netId().equals(currentNet)) {
                current = index;
                break;
            }
        }
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
        Optional<Net> net = canvas.openDocument().netlist().net(new NetId(problem.netId()));
        if (net.isEmpty()) {
            return navigateToInstances(problem);
        }
        focusNet(canvas, blink, net.get());
        return true;
    }

    /**
     * Centers {@code canvas} on {@code net} (when it has points), selects its wires (or the components of its pins
     * when it has none), makes the wires blink and focuses the canvas. Shared by problem navigation and the
     * waveforms' «Mostrar en el circuito».
     */
    public static void focusNet(CircuitCanvas canvas, NetBlink blink, Net net) {
        Bounds bounds = bounds(net);
        if (bounds != null) {
            canvas.centerOn(bounds);
        }
        if (net.wires().isEmpty()) {
            canvas.selection().set(net.pins().stream().map(PinRef::componentId).distinct().toList());
        } else {
            canvas.selection().set(net.wires());
        }
        blink.start(net.wires());
        canvas.requestFocusInWindow();
    }

    private boolean navigateToInstances(Problem problem) {
        Set<String> ids = new LinkedHashSet<>();
        ids.add(problem.netId());
        switch (problem) {
            case ShortCircuit shortCircuit -> shortCircuit.drivers().forEach(driver -> ids.add(driver.componentId()));
            case WidthMismatch mismatch -> mismatch.pins().forEach(pin -> ids.add(pin.componentId()));
        }
        CircuitDocument document = canvas.openDocument().document();
        ComponentTypeRegistry registry = canvas.openDocument().registry();
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
        canvas.centerOn(result);
        canvas.selection().set(instances.stream().map(Component::id).toList());
        blink.stop();
        canvas.requestFocusInWindow();
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
