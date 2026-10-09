package cl.almejo.vsim.ui.problems;

import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.NetId;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.simulation.Problem;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;

import java.util.List;
import java.util.Optional;

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
     * makes the wires blink and focuses the canvas. False, with nothing changed, when the net no longer exists.
     */
    public boolean navigateTo(Problem problem) {
        int index = problems.indexOf(problem);
        if (index >= 0) {
            current = index;
        }
        Optional<Net> net = canvas.openDocument().netlist().net(new NetId(problem.netId()));
        if (net.isEmpty()) {
            return false;
        }
        Net found = net.get();
        canvas.centerOn(bounds(found));
        if (found.wires().isEmpty()) {
            canvas.selection().set(found.pins().stream().map(PinRef::componentId).distinct().toList());
        } else {
            canvas.selection().set(found.wires());
        }
        blink.start(found.wires());
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
