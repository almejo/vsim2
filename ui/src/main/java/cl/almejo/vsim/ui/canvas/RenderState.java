package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Everything one frame needs. {@code netlistToken} is compared with {@code snapshot.token()};
 * {@code selectionDashPhase} is the marching-ants phase of the selection outlines and {@code problemPulsePhase}
 * (in [0, 1)) the phase of the glow under problem wires, see {@link ProblemPulse}, or {@link #NO_PROBLEM_GLOW} to
 * paint no glow at all (exported images). {@code hiddenWires} are wires left out of the frame because a drag preview
 * draws them where they are going (M6.7); empty but for the canvas during such a drag. {@code policy} holds the
 * decisions that differ on paper ({@link PaintPolicy#print}); every constructor but the canonical one uses
 * {@link PaintPolicy#CANVAS}. {@code capturingPart} is the part that has the keyboard of the window (a Teclado
 * paints «Escribiendo…»); only the canvas passes one, so exports and printing never show the capture look.
 * {@code previewedParts} are parts painted as a drag preview has them instead of as the document has them, by id (a
 * Teclado whose «↔» tab is dragged, at its new width); empty but for the canvas during such a drag.
 * {@code pieceContents} are the memory contents a component tab shows for the piece of «Valores de» instead of the
 * component's, by memory id (spec §6.6b); empty but for such a tab's canvas.
 */
public record RenderState(CircuitDocument document, Netlist netlist, long netlistToken, SimulationSnapshot snapshot,
                          Set<ElementId> selection, double selectionDashPhase, double problemPulsePhase,
                          Set<ElementId> hiddenWires, PaintPolicy policy, Optional<ElementId> capturingPart,
                          Map<ElementId, Component> previewedParts, Map<ElementId, String> pieceContents) {

    /** The {@code problemPulsePhase} of a frame without the glow under problem wires. */
    public static final double NO_PROBLEM_GLOW = Double.NaN;

    public RenderState {
        hiddenWires = Set.copyOf(hiddenWires);
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(capturingPart, "capturingPart");
        previewedParts = Map.copyOf(previewedParts);
        pieceContents = Map.copyOf(pieceContents);
    }

    /** A frame that shows the component's own memory contents. */
    public RenderState(CircuitDocument document, Netlist netlist, long netlistToken, SimulationSnapshot snapshot,
                       Set<ElementId> selection, double selectionDashPhase, double problemPulsePhase,
                       Set<ElementId> hiddenWires, PaintPolicy policy, Optional<ElementId> capturingPart,
                       Map<ElementId, Component> previewedParts) {
        this(document, netlist, netlistToken, snapshot, selection, selectionDashPhase, problemPulsePhase, hiddenWires,
                policy, capturingPart, previewedParts, Map.of());
    }

    /** A frame without previewed parts. */
    public RenderState(CircuitDocument document, Netlist netlist, long netlistToken, SimulationSnapshot snapshot,
                       Set<ElementId> selection, double selectionDashPhase, double problemPulsePhase,
                       Set<ElementId> hiddenWires, PaintPolicy policy, Optional<ElementId> capturingPart) {
        this(document, netlist, netlistToken, snapshot, selection, selectionDashPhase, problemPulsePhase, hiddenWires,
                policy, capturingPart, Map.of());
    }

    /** {@code component} as this frame paints it: its preview when a drag previews it, else itself. */
    public Component shown(Component component) {
        return previewedParts.getOrDefault(component.id(), component);
    }

    /** A frame in which no part has the keyboard (exports, printing, previews). */
    public RenderState(CircuitDocument document, Netlist netlist, long netlistToken, SimulationSnapshot snapshot,
                       Set<ElementId> selection, double selectionDashPhase, double problemPulsePhase,
                       Set<ElementId> hiddenWires, PaintPolicy policy) {
        this(document, netlist, netlistToken, snapshot, selection, selectionDashPhase, problemPulsePhase, hiddenWires,
                policy, Optional.empty());
    }

    /** A frame painted as the canvas paints it. */
    public RenderState(CircuitDocument document, Netlist netlist, long netlistToken, SimulationSnapshot snapshot,
                       Set<ElementId> selection, double selectionDashPhase, double problemPulsePhase,
                       Set<ElementId> hiddenWires) {
        this(document, netlist, netlistToken, snapshot, selection, selectionDashPhase, problemPulsePhase, hiddenWires,
                PaintPolicy.CANVAS);
    }

    /** A frame that shows every wire. */
    public RenderState(CircuitDocument document, Netlist netlist, long netlistToken, SimulationSnapshot snapshot,
                       Set<ElementId> selection, double selectionDashPhase, double problemPulsePhase) {
        this(document, netlist, netlistToken, snapshot, selection, selectionDashPhase, problemPulsePhase, Set.of());
    }

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
