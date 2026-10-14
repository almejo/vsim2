package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;

import java.util.Set;

/**
 * Everything one frame needs. {@code netlistToken} is compared with {@code snapshot.token()};
 * {@code selectionDashPhase} is the marching-ants phase of the selection outlines and {@code problemPulsePhase}
 * (in [0, 1)) the phase of the glow under problem wires, see {@link ProblemPulse}, or {@link #NO_PROBLEM_GLOW} to
 * paint no glow at all (exported images).
 */
public record RenderState(CircuitDocument document, Netlist netlist, long netlistToken, SimulationSnapshot snapshot,
                          Set<ElementId> selection, double selectionDashPhase, double problemPulsePhase) {

    /** The {@code problemPulsePhase} of a frame without the glow under problem wires. */
    public static final double NO_PROBLEM_GLOW = Double.NaN;

    /** A frame whose problem glow is at its dimmest. */
    public RenderState(CircuitDocument document, Netlist netlist, long netlistToken, SimulationSnapshot snapshot,
                       Set<ElementId> selection, double selectionDashPhase) {
        this(document, netlist, netlistToken, snapshot, selection, selectionDashPhase, ProblemPulse.LOW_PHASE);
    }

    /** False when the frame paints no glow under problem wires. */
    public boolean paintsProblemGlow() {
        return !Double.isNaN(problemPulsePhase);
    }
}
