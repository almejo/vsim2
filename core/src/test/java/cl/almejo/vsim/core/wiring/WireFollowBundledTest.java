package cl.almejo.vsim.core.wiring;

import cl.almejo.vsim.core.bundled.BundledExamples;
import cl.almejo.vsim.core.command.MoveWithWiresCommand;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.io.CircuitFormatException;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.PinRef;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Review focus 1 of M6.7: a move never loses a connection. For every component and every wire of every bundled
 * example, and several deltas, everything that shared a net before the move (pins, and wires that still exist) shares
 * one after it; undo puts the document back exactly.
 */
class WireFollowBundledTest {

    private static final int[][] DELTAS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {3, 2}, {-3, 2}, {3, -2}, {-3, -2}};

    static Stream<String> examples() {
        return BundledExamples.ids().stream();
    }

    @ParameterizedTest
    @MethodSource("examples")
    void movingAnyComponentOrWireNeverLosesAConnection(String exampleId) throws CircuitFormatException {
        CircuitDocument document = BundledExamples.open(exampleId, StandardComponentTypes.registry());
        ComponentTypeRegistry registry = StandardComponentTypes.registry()
                .extendedWith(SubcircuitTypes.resolverFor(document));
        NetlistCompiler compiler = new NetlistCompiler(registry);
        Netlist before = compiler.compile(document);
        Map<DocumentElement, Long> original = snapshot(document);
        List<ElementId> candidates = new ArrayList<>();
        document.components().forEach(component -> candidates.add(component.id()));
        document.wires().forEach(wire -> candidates.add(wire.id()));
        List<String> lost = new ArrayList<>();
        for (ElementId movedId : candidates) {
            for (int[] delta : DELTAS) {
                MoveWithWiresCommand command = MoveWithWiresCommand.of(document, registry, Set.of(movedId),
                        delta[0], delta[1]);
                command.apply(document);
                Netlist after = compiler.compile(document);
                for (Net net : before.nets()) {
                    Set<String> afterNets = new HashSet<>();
                    for (PinRef pin : net.pins()) {
                        afterNets.add(after.netForPin(pin).orElseThrow().id().value());
                    }
                    for (ElementId wireId : net.wires()) {
                        after.netForWire(wireId).ifPresent(found -> afterNets.add(found.id().value()));
                    }
                    if (afterNets.size() > 1) {
                        lost.add(exampleId + ": moving " + movedId + " by (" + delta[0] + "," + delta[1]
                                + ") splits " + net.id().value());
                    }
                }
                command.revert(document);
                assertThat(snapshot(document)).as("undo of %s by (%s,%s)", movedId, delta[0], delta[1])
                        .containsExactlyEntriesOf(original);
            }
        }
        assertThat(lost).isEmpty();
    }

    private static Map<DocumentElement, Long> snapshot(CircuitDocument document) {
        Map<DocumentElement, Long> snapshot = new LinkedHashMap<>();
        document.elements().forEach(element -> snapshot.put(element, document.orderOf(element.id())));
        return snapshot;
    }
}
