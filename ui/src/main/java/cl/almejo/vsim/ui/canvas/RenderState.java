package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;

import java.util.Set;

/**
 * Everything one frame needs. {@code netlistToken} is compared with {@code snapshot.token()};
 * {@code selectionDashPhase} is the marching-ants phase of the selection outlines.
 */
public record RenderState(CircuitDocument document, Netlist netlist, long netlistToken, SimulationSnapshot snapshot,
                          Set<ElementId> selection, double selectionDashPhase) {
}
