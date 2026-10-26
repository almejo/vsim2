package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.wiring.FollowPlan;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cl.almejo.vsim.core.DocumentFixtures.wire;
import static org.assertj.core.api.Assertions.assertThat;

class MoveWithWiresCommandTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();

    private static Component gate(CircuitDocument document, int x, int y) {
        Component component = new Component(document.newComponentId(), StandardComponentTypes.NOT,
                new GridPoint(x, y), Rotation.R0, Map.of(), "");
        document.add(component);
        return component;
    }

    /** Every element with its draw order, in draw order. */
    private static Map<DocumentElement, Long> snapshot(CircuitDocument document) {
        Map<DocumentElement, Long> snapshot = new LinkedHashMap<>();
        document.elements().forEach(element -> snapshot.put(element, document.orderOf(element.id())));
        return snapshot;
    }

    /**
     * A gate whose output goes along a wire to a T (an L is inserted) and whose input wire has a simple corner (it
     * stretches the neighbour); a second gate has a wire with a free end (it moves whole).
     */
    private record Scene(CircuitDocument document, Component gate) {
    }

    private static Scene scene() {
        CircuitDocument document = new CircuitDocument();
        Component gate = gate(document, 10, 10);
        wire(document, 14, 12, 20, 12);
        wire(document, 20, 5, 20, 12);
        wire(document, 20, 12, 20, 16);
        wire(document, 4, 12, 10, 12);
        wire(document, 4, 12, 4, 30);
        gate(document, 30, 30);
        wire(document, 34, 32, 38, 32);
        document.add(new TextLabel(document.newLabelId(), new GridPoint(0, 0), "a"));
        return new Scene(document, gate);
    }

    @Test
    void undoRestoresIdsAndDrawOrderExactlyAndRedoReappliesTheSamePlan() {
        Scene scene = scene();
        CircuitDocument document = scene.document();
        Component other = document.components().get(1);
        Map<DocumentElement, Long> before = snapshot(document);
        MoveWithWiresCommand command = MoveWithWiresCommand.of(document, registry,
                Set.of(scene.gate().id(), other.id()), 4, 3);
        FollowPlan plan = command.plan().orElseThrow();
        assertThat(plan.addedWires()).isNotEmpty();
        assertThat(plan.replacedWires()).isNotEmpty();
        History history = new History(document);
        history.execute(command);
        Set<DocumentElement> after = Set.copyOf(document.elements());
        assertThat(after).isNotEqualTo(before.keySet());

        history.undo();
        assertThat(snapshot(document)).containsExactlyEntriesOf(before);
        history.redo();
        assertThat(Set.copyOf(document.elements())).isEqualTo(after);
        history.undo();
        assertThat(snapshot(document)).containsExactlyEntriesOf(before);
    }

    @Test
    void aRemovedWireComesBackAtItsDrawOrder() {
        CircuitDocument document = new CircuitDocument();
        Component gate = gate(document, 10, 10);
        Wire shortWire = wire(document, 14, 12, 18, 12);
        wire(document, 0, 0, 0, 5);
        Map<DocumentElement, Long> before = snapshot(document);
        MoveWithWiresCommand command = MoveWithWiresCommand.of(document, registry, Set.of(gate.id()), 4, 0);
        assertThat(command.plan().orElseThrow().removedWireIds()).containsExactly(shortWire.id());
        command.apply(document);
        assertThat(document.wire(shortWire.id())).isEmpty();
        command.revert(document);
        assertThat(snapshot(document)).containsExactlyEntriesOf(before);
    }

    @Test
    void theDescriptionIsAMove() {
        assertThat(MoveWithWiresCommand.nudge(registry, Set.of(new ElementId("c1")), 1, 0).description())
                .isEqualTo("command.move");
    }

    @Test
    void aRunOfFiveNudgesEqualsOneMoveByTheTotalInOneUndoStep() {
        Scene nudged = scene();
        Scene moved = scene();
        Map<DocumentElement, Long> before = snapshot(nudged.document());
        History history = new History(nudged.document());
        for (int step = 0; step < 5; step++) {
            history.execute(MoveWithWiresCommand.nudge(registry, Set.of(nudged.gate().id()), 0, 1));
        }
        MoveWithWiresCommand.of(moved.document(), registry, Set.of(moved.gate().id()), 0, 5)
                .apply(moved.document());

        assertThat(Set.copyOf(nudged.document().elements())).isEqualTo(Set.copyOf(moved.document().elements()));
        assertThat(nudged.document().wires()).hasSize(moved.document().wires().size());

        history.undo();
        assertThat(snapshot(nudged.document())).containsExactlyEntriesOf(before);
        assertThat(history.canUndo()).isFalse();
        history.redo();
        assertThat(Set.copyOf(nudged.document().elements())).isEqualTo(Set.copyOf(moved.document().elements()));
    }

    /** A gate whose output wire and input wire both end on a T: moving it needs an L on each side. */
    private static Scene twoTees() {
        CircuitDocument document = new CircuitDocument();
        Component gate = gate(document, 10, 10);
        wire(document, 14, 12, 20, 12);
        wire(document, 20, 5, 20, 20);
        wire(document, 10, 12, 10, 20);
        wire(document, 0, 20, 30, 20);
        return new Scene(document, gate);
    }

    /** The wires as segments, ignoring their ids (a run and one move may number new wires differently). */
    private static List<String> segments(CircuitDocument document) {
        return document.wires().stream().map(wire -> wire.start() + "-" + wire.end()).sorted().toList();
    }

    @Test
    void aRunOfNudgesInTwoDirectionsNeverReusesAWireIdAndEqualsOneMove() {
        Scene nudged = twoTees();
        Scene moved = twoTees();
        Map<DocumentElement, Long> before = snapshot(nudged.document());
        History history = new History(nudged.document());
        int[][] steps = {{1, 0}, {1, 0}, {0, 1}, {0, 1}, {1, 0}};
        for (int[] step : steps) {
            history.execute(MoveWithWiresCommand.nudge(registry, Set.of(nudged.gate().id()), step[0], step[1]));
        }
        MoveWithWiresCommand.of(moved.document(), registry, Set.of(moved.gate().id()), 3, 2)
                .apply(moved.document());

        assertThat(segments(nudged.document())).isEqualTo(segments(moved.document()));
        assertThat(nudged.document().component(nudged.gate().id()))
                .isEqualTo(moved.document().component(moved.gate().id()));
        history.undo();
        assertThat(snapshot(nudged.document())).containsExactlyEntriesOf(before);
        assertThat(history.canUndo()).isFalse();
        history.redo();
        assertThat(segments(nudged.document())).isEqualTo(segments(moved.document()));
    }

    @Test
    void nudgesThatComeBackLeaveTheDocumentAsItWas() {
        Scene scene = scene();
        Set<DocumentElement> before = Set.copyOf(scene.document().elements());
        History history = new History(scene.document());
        history.execute(MoveWithWiresCommand.nudge(registry, Set.of(scene.gate().id()), 0, 1));
        history.execute(MoveWithWiresCommand.nudge(registry, Set.of(scene.gate().id()), 0, 1));
        history.execute(MoveWithWiresCommand.nudge(registry, Set.of(scene.gate().id()), 0, -2));
        assertThat(Set.copyOf(scene.document().elements())).isEqualTo(before);
    }

    @Test
    void aRunWithWiresThatComesBackLeavesNoUndoStep() {
        Scene scene = scene();
        Map<DocumentElement, Long> before = snapshot(scene.document());
        History history = new History(scene.document());
        history.markSaved();
        history.execute(MoveWithWiresCommand.nudge(registry, Set.of(scene.gate().id()), 0, 1));
        history.execute(MoveWithWiresCommand.nudge(registry, Set.of(scene.gate().id()), 0, -1));

        assertThat(snapshot(scene.document())).containsExactlyEntriesOf(before);
        assertThat(history.canUndo()).isFalse();
        assertThat(history.isModified()).isFalse();
        assertThat(MoveWithWiresCommand.nudge(registry, Set.of(scene.gate().id()), 0, 0).changesNothing()).isTrue();
        assertThat(MoveWithWiresCommand.nudge(registry, Set.of(scene.gate().id()), 0, 1).changesNothing()).isFalse();
    }

    @Test
    void aRunThatComesBackDoesNotTidyWiresItNeverMoved() {
        CircuitDocument document = new CircuitDocument();
        Wire left = wire(document, 0, 0, 5, 0);
        Wire right = wire(document, 5, 0, 9, 0);
        Map<DocumentElement, Long> before = snapshot(document);
        History history = new History(document);
        history.execute(MoveWithWiresCommand.nudge(registry, Set.of(left.id(), right.id()), -1, 0));
        history.execute(MoveWithWiresCommand.nudge(registry, Set.of(left.id(), right.id()), 1, 0));
        assertThat(snapshot(document)).containsExactlyEntriesOf(before);
    }

    @Test
    void nudgesOfOtherIdsOrADragDoNotMerge() {
        Scene scene = scene();
        Component other = scene.document().components().get(1);
        History history = new History(scene.document());
        history.execute(MoveWithWiresCommand.nudge(registry, Set.of(scene.gate().id()), 0, 1));
        history.execute(MoveWithWiresCommand.nudge(registry, Set.of(other.id()), 0, 1));
        history.execute(MoveWithWiresCommand.of(scene.document(), registry, Set.of(other.id()), 1, 0));
        history.undo();
        history.undo();
        assertThat(history.canUndo()).isTrue();
        history.undo();
        assertThat(history.canUndo()).isFalse();
    }

    @Test
    void aMergedRunNotifiesTheDocumentOnce() {
        Scene scene = scene();
        History history = new History(scene.document());
        history.execute(MoveWithWiresCommand.nudge(registry, Set.of(scene.gate().id()), 0, 1));
        long revision = scene.document().revision();
        history.execute(MoveWithWiresCommand.nudge(registry, Set.of(scene.gate().id()), 0, 1));
        assertThat(scene.document().revision()).isEqualTo(revision + 1);
    }

    /**
     * The gate's output wire has a simple corner, so moving the gate up 4 slides it up and its vertical neighbour
     * vanishes: the slid wire then ends where an untouched wire continues it in line.
     */
    private record InLine(CircuitDocument document, Component gate, Wire attached, Wire corner, Wire beyond) {
    }

    private static InLine inLine() {
        CircuitDocument document = new CircuitDocument();
        Component gate = gate(document, 10, 10);
        Wire attached = wire(document, 14, 12, 20, 12);
        Wire corner = wire(document, 20, 8, 20, 12);
        Wire beyond = wire(document, 20, 8, 26, 8);
        return new InLine(document, gate, attached, corner, beyond);
    }

    @Test
    void theCleanUpMergesAStretchedWireWithTheWireItEndsInLineWithInTheSameUndoStep() {
        InLine scene = inLine();
        CircuitDocument document = scene.document();
        Map<DocumentElement, Long> before = snapshot(document);
        History history = new History(document);
        history.execute(MoveWithWiresCommand.of(document, registry, Set.of(scene.gate().id()), 0, -4));

        assertThat(document.wires()).containsExactly(
                new Wire(scene.attached().id(), new GridPoint(14, 8), new GridPoint(26, 8)));
        history.undo();
        assertThat(snapshot(document)).containsExactlyEntriesOf(before);
        assertThat(history.canUndo()).isFalse();
        history.redo();
        assertThat(document.wires()).containsExactly(
                new Wire(scene.attached().id(), new GridPoint(14, 8), new GridPoint(26, 8)));
    }

    @Test
    void aRunOfNudgesEndsCleanToo() {
        InLine scene = inLine();
        History history = new History(scene.document());
        for (int step = 0; step < 4; step++) {
            history.execute(MoveWithWiresCommand.nudge(registry, Set.of(scene.gate().id()), 0, -1));
        }
        assertThat(scene.document().wires()).containsExactly(
                new Wire(scene.attached().id(), new GridPoint(14, 8), new GridPoint(26, 8)));
        history.undo();
        assertThat(history.canUndo()).isFalse();
        assertThat(scene.document().wires()).containsExactly(scene.attached(), scene.corner(), scene.beyond());
    }

    @Test
    void aMovedWireTheCleanUpMergesIntoAnEarlierWireNamesItsSurvivor() {
        CircuitDocument document = new CircuitDocument();
        Wire earlier = wire(document, 0, 0, 4, 0);
        Wire moved = wire(document, 4, 3, 9, 3);
        MoveWithWiresCommand command = MoveWithWiresCommand.of(document, registry, Set.of(moved.id()), 0, -3);
        assertThat(command.mergedInto()).isEmpty();
        new History(document).execute(command);

        assertThat(document.wires()).containsExactly(
                new Wire(earlier.id(), new GridPoint(0, 0), new GridPoint(9, 0)));
        assertThat(command.mergedInto()).containsExactlyEntriesOf(Map.of(moved.id(), earlier.id()));
    }

    @Test
    void aMoveWithoutACleanUpNamesNoSurvivors() {
        CircuitDocument document = new CircuitDocument();
        wire(document, 0, 0, 4, 0);
        Wire moved = wire(document, 4, 3, 9, 3);
        MoveWithWiresCommand command = MoveWithWiresCommand.of(document, registry, Set.of(moved.id()), 0, 2);
        command.apply(document);
        assertThat(command.mergedInto()).isEmpty();
    }

    @Test
    void anUntouchedSplitWireFarFromTheMoveStaysSplit() {
        CircuitDocument document = new CircuitDocument();
        Component gate = gate(document, 10, 10);
        Wire attached = wire(document, 14, 12, 20, 12);
        Wire left = wire(document, 0, 30, 5, 30);
        Wire right = wire(document, 5, 30, 9, 30);
        MoveWithWiresCommand.of(document, registry, Set.of(gate.id()), 2, 0).apply(document);
        assertThat(document.wires()).contains(left, right);
        assertThat(document.wire(attached.id()).orElseThrow().start()).isEqualTo(new GridPoint(16, 12));
    }

    @Test
    void anExplicitPlanIsAppliedAsGiven() {
        CircuitDocument document = new CircuitDocument();
        Component gate = gate(document, 10, 10);
        Wire attached = wire(document, 14, 12, 30, 12);
        FollowPlan plan = new FollowPlan(Set.of(), List.of(attached.withEndpoints(new GridPoint(16, 12),
                attached.end())), List.of(), Set.of(), List.of());
        new MoveWithWiresCommand(Set.of(gate.id()), 2, 0, plan).apply(document);
        assertThat(document.wire(attached.id()).orElseThrow().start()).isEqualTo(new GridPoint(16, 12));
    }
}
