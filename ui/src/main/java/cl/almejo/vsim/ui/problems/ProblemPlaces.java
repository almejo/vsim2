package cl.almejo.vsim.ui.problems;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.machine.MachineCopies;
import cl.almejo.vsim.core.simulation.ConnectivityProblem;
import cl.almejo.vsim.core.simulation.DisplayBusOverride;
import cl.almejo.vsim.core.simulation.DriverRef;
import cl.almejo.vsim.core.simulation.FlatPinRef;
import cl.almejo.vsim.core.simulation.LinkedFileProblem;
import cl.almejo.vsim.core.simulation.MachineProblem;
import cl.almejo.vsim.core.simulation.Problem;
import cl.almejo.vsim.core.simulation.ShortCircuit;
import cl.almejo.vsim.core.simulation.TunnelProblem;
import cl.almejo.vsim.core.simulation.WidthMismatch;

import java.util.Optional;

/**
 * Which part a problem of the circuit is shown on (spec §6.7), by its simulation id ({@code c5/c2}); where that part
 * is (the circuit, or the body of the definition of the instance above it) is {@link
 * cl.almejo.vsim.ui.window.SeePiece#targetOf}'s, as for every «Ver la pieza». A machine's problem is shown on its
 * copy: the running copy it is about, or the machine's first copy for a problem of its model.
 */
final class ProblemPlaces {

    private ProblemPlaces() {
    }

    /**
     * The simulation id of the part {@code problem} of circuit {@code root} is about first; empty when it names none
     * (a machine no piece places).
     */
    static Optional<String> focusOf(Problem problem, CircuitDocument root) {
        return switch (problem) {
            case ShortCircuit shortCircuit -> shortCircuit.drivers().stream().findFirst()
                    .map(DriverRef::componentId);
            case WidthMismatch mismatch -> mismatch.pins().stream().findFirst().map(FlatPinRef::componentId);
            case DisplayBusOverride display -> Optional.of(display.componentId());
            case TunnelProblem tunnel -> tunnel.tunnels().stream().findFirst();
            case ConnectivityProblem connectivity -> Optional.of(connectivity.elementId())
                    .filter(id -> !id.equals(ConnectivityProblem.NO_ELEMENT));
            case LinkedFileProblem linkedFile -> Optional.of(linkedFile.elementId());
            case MachineProblem machine -> machine.instancePath()
                    .or(() -> MachineCopies.firstPathOf(root, machine.definitionId()));
        };
    }
}
