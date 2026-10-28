package cl.almejo.vsim.core.wiring;

import cl.almejo.vsim.core.command.MoveWithWiresCommand;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.PinRef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static cl.almejo.vsim.core.DocumentFixtures.wire;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec M6.7 §2–§4: how the wires attached to a move stretch, and the accidental contacts. */
class WireFollowTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitDocument document = new CircuitDocument();

    /** A NOT gate: input pin at (x, y + 2), output pin at (x + 4, y + 2). */
    private Component gate(int x, int y) {
        return component(StandardComponentTypes.BUFFER, x, y, Map.of(StandardComponentTypes.NEGATE, true));
    }

    /** An LED: its only pin at (x + 1, y + 3). */
    private Component led(int x, int y) {
        return component(StandardComponentTypes.LED, x, y, Map.of());
    }

    private Component component(String typeId, int x, int y, Map<String, Object> parameters) {
        Component component = new Component(document.newComponentId(), typeId, new GridPoint(x, y), Rotation.R0,
                parameters, "");
        document.add(component);
        return component;
    }

    /**
     * Applies the move with the wires following, as planned (without the clean-up that {@code
     * MoveWithWiresCommand.of} adds, so the rules show as they are), and returns the wires afterwards, as text, sorted.
     */
    private Set<String> move(int deltaX, int deltaY, ElementId... ids) {
        new MoveWithWiresCommand(Set.of(ids), deltaX, deltaY,
                WireFollow.plan(document, registry, Set.of(ids), deltaX, deltaY)).apply(document);
        return wires();
    }

    private Set<String> wires() {
        return document.wires().stream().map(WireFollowTest::text).collect(Collectors.toCollection(TreeSet::new));
    }

    private static String text(Wire wire) {
        return "(" + wire.start().x() + "," + wire.start().y() + ")-(" + wire.end().x() + "," + wire.end().y() + ")";
    }

    private Wire current(Wire wire) {
        return document.wire(wire.id()).orElseThrow();
    }

    private static String span(int startX, int startY, int endX, int endY) {
        return text(new Wire(new ElementId("x"), new GridPoint(startX, startY), new GridPoint(endX, endY)));
    }

    // --- rule 1: along the axis ---

    @Test
    void aHorizontalWireStretchesAndShrinksAlongItsAxis() {
        Component gate = gate(10, 10);
        Wire attached = wire(document, 14, 12, 30, 12);
        assertThat(move(3, 0, gate.id())).containsExactlyInAnyOrder(span(17, 12, 30, 12));
        assertThat(move(-6, 0, gate.id())).containsExactlyInAnyOrder(span(11, 12, 30, 12));
        assertThat(current(attached).id()).isEqualTo(attached.id());
    }

    @Test
    void aVerticalWireStretchesAndShrinksAlongItsAxis() {
        Wire moved = wire(document, 0, 0, 0, 5);
        Wire attached = wire(document, 0, 5, 0, 10);
        move(0, 2, moved.id());
        assertThat(current(attached)).isEqualTo(new Wire(attached.id(), new GridPoint(0, 7), new GridPoint(0, 10)));
        move(0, -4, moved.id());
        assertThat(current(attached)).isEqualTo(new Wire(attached.id(), new GridPoint(0, 3), new GridPoint(0, 10)));
    }

    @Test
    void aWireThatShrinksToNothingIsRemoved() {
        Component gate = gate(10, 10);
        Wire attached = wire(document, 14, 12, 18, 12);
        FollowPlan plan = WireFollow.plan(document, registry, Set.of(gate.id()), 4, 0);
        assertThat(plan.removedWireIds()).containsExactly(attached.id());
        assertThat(move(4, 0, gate.id())).isEmpty();
    }

    @Test
    void shrinkingNeverDropsAJunctionOnTheWire() {
        Component gate = gate(10, 10);
        wire(document, 14, 12, 30, 12);
        wire(document, 20, 12, 20, 20);
        assertThat(move(10, 0, gate.id())).containsExactlyInAnyOrder(span(14, 12, 30, 12), span(20, 12, 20, 20));
    }

    // --- rule 2: a simple corner keeps the route's shape ---

    @Test
    void aSimpleCornerMovesWithTheWireSoTheRouteKeepsItsShape() {
        Component gate = gate(10, 10);
        wire(document, 14, 12, 20, 12);
        wire(document, 20, 12, 20, 20);
        assertThat(move(0, 2, gate.id())).containsExactlyInAnyOrder(span(14, 14, 20, 14), span(20, 14, 20, 20));
        assertThat(move(0, -5, gate.id())).containsExactlyInAnyOrder(span(14, 9, 20, 9), span(20, 9, 20, 20));
    }

    @Test
    void aSimpleCornerOnAVerticalWire() {
        Component lamp = led(0, 0);
        wire(document, 1, 3, 1, 10);
        wire(document, 1, 10, 8, 10);
        assertThat(move(2, 0, lamp.id())).containsExactlyInAnyOrder(span(3, 3, 3, 10), span(3, 10, 8, 10));
        assertThat(move(-4, 0, lamp.id())).containsExactlyInAnyOrder(span(-1, 3, -1, 10), span(-1, 10, 8, 10));
    }

    @Test
    void aTwoCornerRouteKeepsItsShape() {
        Component gate = gate(10, 10);
        Component target = gate(30, 18);
        wire(document, 14, 12, 20, 12);
        wire(document, 20, 12, 20, 20);
        wire(document, 20, 20, 30, 20);
        assertThat(move(3, 2, gate.id()))
                .containsExactlyInAnyOrder(span(17, 14, 20, 14), span(20, 14, 20, 20), span(20, 20, 30, 20));
        assertThat(document.component(target.id())).isPresent();
        assertThat(move(0, 6, gate.id()))
                .containsExactlyInAnyOrder(span(17, 20, 20, 20), span(20, 20, 30, 20));
    }

    // --- rule 3: an L otherwise ---

    @Test
    void aTeeAtTheFarEndGetsAnLThatLeavesItHorizontally() {
        Component gate = gate(10, 10);
        Wire attached = wire(document, 14, 12, 20, 12);
        wire(document, 20, 5, 20, 12);
        wire(document, 20, 12, 20, 16);
        FollowPlan plan = WireFollow.plan(document, registry, Set.of(gate.id()), 3, 2);
        assertThat(plan.replacedWires()).containsExactly(
                new Wire(attached.id(), new GridPoint(17, 12), new GridPoint(20, 12)));
        assertThat(plan.addedWires()).hasSize(1);
        assertThat(text(plan.addedWires().get(0))).isEqualTo(span(17, 12, 17, 14));
    }

    @Test
    void aPinAtTheFarEndOfAVerticalWireGetsAnLThatLeavesItVertically() {
        Component lamp = led(0, 0);
        led(0, 10);
        wire(document, 1, 3, 1, 13);
        assertThat(move(2, 4, lamp.id())).containsExactlyInAnyOrder(span(1, 7, 1, 13), span(1, 7, 3, 7));
    }

    @Test
    void anLWhoseFirstLegVanishesKeepsTheWireIdOnTheSecond() {
        Component gate = gate(10, 10);
        Wire attached = wire(document, 14, 12, 20, 12);
        wire(document, 20, 5, 20, 12);
        wire(document, 20, 12, 20, 16);
        FollowPlan plan = WireFollow.plan(document, registry, Set.of(gate.id()), 6, 3);
        assertThat(plan.addedWires()).isEmpty();
        assertThat(plan.replacedWires()).containsExactly(
                new Wire(attached.id(), new GridPoint(20, 12), new GridPoint(20, 15)));
    }

    // --- rule 4: a free end ---

    @Test
    void aWireWithAFreeEndMovesWhole() {
        Component gate = gate(10, 10);
        wire(document, 14, 12, 20, 12);
        assertThat(move(0, 2, gate.id())).containsExactlyInAnyOrder(span(14, 14, 20, 14));
        assertThat(move(1, -3, gate.id())).containsExactlyInAnyOrder(span(15, 11, 21, 11));
    }

    @Test
    void aVerticalWireWithAFreeEndMovesWhole() {
        Wire moved = wire(document, -5, 5, 0, 5);
        wire(document, 0, 5, 0, 10);
        assertThat(move(3, 0, moved.id())).containsExactlyInAnyOrder(span(-2, 5, 3, 5), span(3, 5, 3, 10));
        assertThat(move(-2, 0, moved.id())).containsExactlyInAnyOrder(span(-4, 5, 1, 5), span(1, 5, 1, 10));
    }

    // --- §2: moved whole ---

    @Test
    void aWireBetweenTwoMovedComponentsMovesWhole() {
        Component first = gate(10, 10);
        Component second = gate(20, 10);
        Wire between = wire(document, 14, 12, 20, 12);
        FollowPlan plan = WireFollow.plan(document, registry, Set.of(first.id(), second.id()), 2, 3);
        assertThat(plan.movedWholeWireIds()).containsExactly(between.id());
        assertThat(plan.replacedWires()).isEmpty();
        assertThat(move(2, 3, first.id(), second.id())).containsExactlyInAnyOrder(span(16, 15, 22, 15));
    }

    @Test
    void aWireEndingOnAMovedWiresInteriorFollowsIt() {
        Wire moved = wire(document, 0, 0, 10, 0);
        wire(document, 5, 0, 5, 8);
        assertThat(move(0, 2, moved.id())).containsExactlyInAnyOrder(span(0, 2, 10, 2), span(5, 2, 5, 8));
    }

    @Test
    void labelsDoNotDragWires() {
        Wire wire = wire(document, 0, 0, 10, 0);
        TextLabel label = new TextLabel(document.newLabelId(), new GridPoint(0, 0), "a");
        document.add(label);
        assertThat(WireFollow.plan(document, registry, Set.of(label.id()), 3, 3).isEmpty()).isTrue();
        assertThat(current(wire)).isEqualTo(wire);
    }

    @Test
    void aZeroDeltaPlansNothing() {
        Component gate = gate(10, 10);
        wire(document, 14, 12, 20, 12);
        assertThat(WireFollow.plan(document, registry, Set.of(gate.id()), 0, 0).isEmpty()).isTrue();
    }

    // --- buses ---

    @Test
    void aBusWireFollowsLikeASingleWire() {
        Component source = component(StandardComponentTypes.INPUT_PIN, 0, 0, Map.of(InterfacePins.WIDTH, 8));
        component(StandardComponentTypes.OUTPUT_PIN, 20, 4, Map.of(InterfacePins.WIDTH, 8));
        wire(document, 2, 1, 10, 1);
        wire(document, 10, 1, 10, 5);
        wire(document, 10, 5, 20, 5);
        assertThat(move(0, 2, source.id())).containsExactlyInAnyOrder(span(2, 3, 10, 3), span(10, 3, 10, 5),
                span(10, 5, 20, 5));
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        Net net = netlist.netForPin(new PinRef(source.id(), 0)).orElseThrow();
        assertThat(net.pins()).hasSize(2);
        assertThat(net.width()).isEqualTo(8);
    }

    // --- §4: accidental contacts ---

    @Test
    void aNewContactIsDetectedAndTheOtherOrientationChosenWhenClean() {
        Component gate = gate(10, 10);
        Wire attached = wire(document, 14, 12, 20, 12);
        wire(document, 20, 5, 20, 12);
        wire(document, 20, 12, 20, 14);
        led(11, 9);
        FollowPlan plan = WireFollow.plan(document, registry, Set.of(gate.id()), -4, 6);
        assertThat(plan.newContacts()).isEmpty();
        assertThat(plan.replacedWires()).containsExactly(
                new Wire(attached.id(), new GridPoint(20, 12), new GridPoint(20, 18)));
        assertThat(plan.addedWires()).extracting(WireFollowTest::text).containsExactly(span(10, 18, 20, 18));
    }

    @Test
    void whenBothOrientationsCollideTheFirstIsKeptAndTheContactListed() {
        Component gate = gate(10, 10);
        Wire attached = wire(document, 14, 12, 20, 12);
        wire(document, 20, 5, 20, 12);
        wire(document, 20, 12, 20, 14);
        Component lamp = led(11, 9);
        led(14, 15);
        FollowPlan plan = WireFollow.plan(document, registry, Set.of(gate.id()), -4, 6);
        assertThat(plan.replacedWires()).containsExactly(
                new Wire(attached.id(), new GridPoint(10, 12), new GridPoint(20, 12)));
        assertThat(plan.newContacts()).containsExactly(
                new NewContact(new GridPoint(12, 12), Optional.of(lamp.id()), Optional.of("in"), Optional.empty(),
                        Optional.of("in")));
    }

    @Test
    void aGateDroppedOnAWireIsANewContact() {
        Component gate = gate(10, 10);
        wire(document, 0, 20, 30, 20);
        FollowPlan plan = WireFollow.plan(document, registry, Set.of(gate.id()), 0, 8);
        assertThat(plan.newContacts()).extracting(NewContact::point)
                .containsExactly(new GridPoint(10, 20), new GridPoint(14, 20));
    }

    @Test
    void stretchingAlongExistingConnectionsIsNotAContact() {
        Component gate = gate(10, 10);
        wire(document, 14, 12, 20, 12);
        wire(document, 20, 12, 20, 20);
        assertThat(WireFollow.plan(document, registry, Set.of(gate.id()), 0, 3).newContacts()).isEmpty();
    }

    // --- connections the rules alone would lose ---

    @Test
    void aMovedPinOnAWiresInteriorStaysConnected() {
        Component gate = gate(10, 10);
        wire(document, 0, 12, 10, 12);
        Wire through = wire(document, 14, 0, 14, 30);
        move(3, 2, gate.id());
        assertThat(current(through)).isEqualTo(through);
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        assertThat(netlist.netForPin(new PinRef(gate.id(), 1)).orElseThrow().wires()).contains(through.id());
    }

    @Test
    void aMovedWireEndingOnAFixedWiresInteriorStaysConnected() {
        Wire fixed = wire(document, 0, 0, 20, 0);
        Wire moved = wire(document, 10, 0, 10, 8);
        move(3, 2, moved.id());
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        assertThat(netlist.netForWire(moved.id())).isEqualTo(netlist.netForWire(fixed.id()));
        assertThat(netlist.nets()).hasSize(1);
    }

    @Test
    void thePlanIsDeterministic() {
        Component gate = gate(10, 10);
        wire(document, 14, 12, 20, 12);
        wire(document, 20, 5, 20, 12);
        wire(document, 20, 12, 20, 14);
        led(11, 9);
        CircuitDocument copy = new CircuitDocument();
        document.elements().forEach(copy::add);
        FollowPlan first = WireFollow.plan(document, registry, Set.of(gate.id()), 2, 3);
        FollowPlan second = WireFollow.plan(copy, registry, Set.of(gate.id()), 2, 3);
        assertThat(second).isEqualTo(first);
        assertThat(List.copyOf(first.addedWires())).isNotEmpty();
    }
}
