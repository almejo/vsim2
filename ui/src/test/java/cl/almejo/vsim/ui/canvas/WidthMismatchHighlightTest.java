package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.simulation.WidthMismatch;
import cl.almejo.vsim.ui.app.OpenDocument;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class WidthMismatchHighlightTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitDocument document = new CircuitDocument();
    private final Wire bus = new Wire(new ElementId("w1"), new GridPoint(0, 0), new GridPoint(10, 0));

    /** A 4-bit splitter whose bus pin (10, 0) ends the wire (0, 0)-(10, 0). */
    private void busOfFourBits() {
        document.add(registry.require(StandardComponentTypes.BUS_SPLITTER).newComponent(new ElementId("c1"),
                new GridPoint(10, 0), Rotation.R0, Map.of(StandardComponentTypes.WIDTH, 4), ""));
        document.add(bus);
    }

    private WidthMismatchHighlight highlight(SimulationSnapshot snapshot, long token) {
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        return highlightOf(snapshot, netlist, token, document, registry);
    }

    private static WidthMismatchHighlight highlightOf(SimulationSnapshot snapshot, Netlist netlist, long token,
                                                      CircuitDocument document, ComponentTypeRegistry registry) {
        return WidthMismatchHighlight.of(snapshot, netlist, token, document, registry,
                () -> TopLevelNets.of(document, netlist, registry));
    }

    @Test
    void aBusTouchingAOneBitPinMarksItsWiresAndEveryPinWidthEvenWithoutASnapshot() {
        busOfFourBits();
        document.add(registry.require(StandardComponentTypes.LED).newComponent(new ElementId("c2"),
                new GridPoint(-1, -3), Rotation.R0, Map.of(), "")); // its pin is at (0, 0)

        WidthMismatchHighlight marked = highlight(SimulationSnapshot.EMPTY, document.revision());

        assertThat(marked.wires()).containsExactly(bus.id());
        assertThat(marked.pinWidths()).containsOnly(entry(new GridPoint(10, 0), List.of(4)),
                entry(new GridPoint(0, 0), List.of(1)));
    }

    @Test
    void aMatchingBusIsNotMarkedButAMismatchReportedForTheShownNetlistIs() {
        busOfFourBits();
        assertThat(highlight(SimulationSnapshot.EMPTY, document.revision())).isEqualTo(WidthMismatchHighlight.NONE);

        String netId = new NetlistCompiler(registry).compile(document).netForWire(bus.id()).orElseThrow().id().value();
        SimulationSnapshot reported = new SimulationSnapshot(1, document.revision(), 0, true, Map.of(), Map.of(),
                List.of(new WidthMismatch(netId, List.of())), Optional.empty());
        assertThat(highlight(reported, document.revision()).wires()).containsExactly(bus.id());
        assertThat(highlight(reported, document.revision() + 1)).isEqualTo(WidthMismatchHighlight.NONE);
    }

    @Test
    void aMismatchReportedForANetMergedThroughATemplateMarksTheWiresOnBothSides() {
        OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        try {
            PassThroughCircuit circuit = PassThroughCircuit.addTo(openDocument);
            SimulationSnapshot reported = new SimulationSnapshot(1, openDocument.netlistToken(), 0, true, Map.of(),
                    Map.of(), List.of(new WidthMismatch(circuit.rootNetId(openDocument), List.of())), Optional.empty());

            WidthMismatchHighlight marked = highlightOf(reported, openDocument.netlist(),
                    openDocument.netlistToken(), openDocument.document(), openDocument.registry());

            assertThat(marked.wires()).containsExactlyInAnyOrder(circuit.left().id(), circuit.rightAcross().id(),
                    circuit.rightDown().id());
        } finally {
            openDocument.close();
        }
    }
}
