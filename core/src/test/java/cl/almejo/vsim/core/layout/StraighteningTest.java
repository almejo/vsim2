package cl.almejo.vsim.core.layout;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ElementBounds;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.wiring.Connectivity;
import cl.almejo.vsim.core.wiring.TidyOptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cl.almejo.vsim.core.DocumentFixtures.gate;
import static cl.almejo.vsim.core.DocumentFixtures.place;
import static cl.almejo.vsim.core.DocumentFixtures.wire;
import static org.assertj.core.api.Assertions.assertThat;

class StraighteningTest {

    private static final CircuitTidyOptions STRAIGHTEN_ONLY = new CircuitTidyOptions(false, false, true,
            TidyOptions.DEFAULTS);

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();

    private static Set<ElementId> everything(CircuitDocument document) {
        Set<ElementId> ids = new LinkedHashSet<>();
        document.elements().forEach(element -> ids.add(element.id()));
        return ids;
    }

    private static Set<ElementId> allBut(CircuitDocument document, Component... fixed) {
        Set<ElementId> ids = everything(document);
        for (Component component : fixed) {
            ids.remove(component.id());
        }
        return ids;
    }

    private CircuitTidyPlan straighten(CircuitDocument document, Set<ElementId> scope) {
        return CircuitTidy.plan(document, registry, scope, STRAIGHTEN_ONLY, TextBoxes.NONE, () -> false,
                CircuitTidyProgress.NONE).orElseThrow();
    }

    private CircuitTidyPlan straighten(CircuitDocument document) {
        return straighten(document, everything(document));
    }

    private static GridPoint positionOf(CircuitTidyPlan plan, Component component) {
        return plan.after().component(component.id()).orElseThrow().position();
    }

    private Set<Set<PinRef>> nets(CircuitDocument document) {
        return Connectivity.of(document.wires(), document, registry).pinNets();
    }

    private Bounds bodies(CircuitDocument document) {
        return ElementBounds.union(document.components(), registry).orElseThrow();
    }

    private boolean anyBodiesTooClose(CircuitDocument document) {
        List<Component> components = document.components();
        for (int first = 0; first < components.size(); first++) {
            for (int second = first + 1; second < components.size(); second++) {
                if (GreedySeparator.tooClose(ElementBounds.of(components.get(first), registry),
                        ElementBounds.of(components.get(second), registry), LayoutItems.BODY_GAP)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** A wire from (fromX, fromY) to (toX, toY) with its jog at x = {@code jogX}. */
    private static void jog(CircuitDocument document, int fromX, int fromY, int jogX, int toX, int toY) {
        wire(document, fromX, fromY, jogX, fromY);
        if (fromY != toY) {
            wire(document, jogX, fromY, jogX, toY);
        }
        wire(document, jogX, toY, toX, toY);
    }

    private Component andGate(CircuitDocument document, int x, int y) {
        return place(registry, document, StandardComponentTypes.AND, x, y, "");
    }

    /** A NOT gate driving another one whose input is two units lower: one jog, two corners. */
    private CircuitDocument oneJog() {
        CircuitDocument document = new CircuitDocument();
        gate(registry, document, 0, 0);
        gate(registry, document, 10, 2);
        jog(document, 4, 2, 7, 10, 4);
        return document;
    }

    @Test
    void aGateOneJogAboveItsDriverMovesSoTheWireIsStraight() {
        CircuitDocument document = oneJog();

        CircuitTidyPlan plan = straighten(document);

        List<Wire> wires = plan.after().wires();
        assertThat(wires).hasSize(1);
        assertThat(wires.get(0).isHorizontal()).isTrue();
        assertThat(plan.cornersAfter()).isLessThan(plan.cornersBefore());
        assertThat(plan.pairsStraightened()).isEqualTo(1);
        assertThat(nets(plan.after())).isEqualTo(nets(document));
    }

    /**
     * An AND gate whose inputs come from two drivers at different heights and whose output goes to a fixed gate: two
     * units up straightens the first input and the output, two units down only the second input.
     */
    private record PulledGate(CircuitDocument document, Component pulled, Set<ElementId> scope) {
    }

    private PulledGate pulledGate() {
        CircuitDocument document = new CircuitDocument();
        Component firstDriver = gate(registry, document, 0, 0);
        Component secondDriver = gate(registry, document, 0, 8);
        Component pulled = andGate(document, 10, 4);
        Component reader = gate(registry, document, 20, 2);
        jog(document, 4, 2, 7, 10, 4);
        jog(document, 4, 10, 8, 10, 8);
        jog(document, 14, 6, 17, 20, 4);
        return new PulledGate(document, pulled, allBut(document, firstDriver, secondDriver, reader));
    }

    @Test
    void aGatePulledByTwoDriversSettlesOnTheBetterHeight() {
        PulledGate fixture = pulledGate();

        CircuitTidyPlan plan = straighten(fixture.document(), fixture.scope());

        assertThat(positionOf(plan, fixture.pulled())).isEqualTo(new GridPoint(10, 2));
        assertThat(plan.pairsStraightened()).isEqualTo(2);
        assertThat(nets(plan.after())).isEqualTo(nets(fixture.document()));
    }

    @Test
    void itNeverOscillates() {
        PulledGate fixture = pulledGate();
        List<Integer> rounds = new ArrayList<>();

        CircuitTidyPlan first = CircuitTidy.plan(fixture.document(), registry, fixture.scope(), STRAIGHTEN_ONLY,
                TextBoxes.NONE, () -> false, (stage, done, total) -> {
                    if (stage == CircuitTidyProgress.Stage.STRAIGHTENING) {
                        rounds.add(done);
                        assertThat(total).isEqualTo(Straightening.MAXIMUM_ROUNDS);
                    }
                }).orElseThrow();
        CircuitTidyPlan second = straighten(fixture.document(), fixture.scope());

        assertThat(rounds).isNotEmpty().hasSizeLessThanOrEqualTo(Straightening.MAXIMUM_ROUNDS);
        assertThat(rounds.getLast()).isLessThan(Straightening.MAXIMUM_ROUNDS);
        assertThat(snapshot(second.after())).isEqualTo(snapshot(first.after()));
    }

    private static Map<DocumentElement, Long> snapshot(CircuitDocument document) {
        Map<DocumentElement, Long> snapshot = new LinkedHashMap<>();
        document.elements().forEach(element -> snapshot.put(element, document.orderOf(element.id())));
        return snapshot;
    }

    /** A chain of gates farther apart than {@value Straightening#ROW_GAP} units (closer, they would be a row). */
    @Test
    void aLongChainDoesNotDriftTheCircuit() {
        CircuitDocument document = new CircuitDocument();
        Component first = andGate(document, 0, 0);
        for (int index = 1; index < 6; index++) {
            andGate(document, index * 12, 0);
            jog(document, index * 12 - 8, 2, index * 12 - 4, index * 12, 4);
        }
        Bounds before = bodies(document);

        CircuitTidyPlan plan = straighten(document, allBut(document, first));

        Bounds after = bodies(plan.after());
        assertThat(after.y()).isGreaterThanOrEqualTo(before.y() - Straightening.BOUNDS_SLACK);
        assertThat(after.maximumY()).isLessThanOrEqualTo(before.maximumY() + Straightening.BOUNDS_SLACK);
        assertThat(plan.pairsStraightened()).isPositive();
        assertThat(nets(plan.after())).isEqualTo(nets(document));
    }

    @Test
    void straighteningNeverCreatesAnOverlapOrChangesTheNets() {
        CircuitDocument document = new CircuitDocument();
        Component driver = gate(registry, document, 0, 0);
        Component receiver = gate(registry, document, 10, 4);
        jog(document, 4, 2, 7, 10, 6);
        gate(registry, document, 10, -4);
        gate(registry, document, 0, 8);

        CircuitTidyPlan plan = straighten(document);

        assertThat(positionOf(plan, driver)).isEqualTo(driver.position());
        assertThat(positionOf(plan, receiver)).isEqualTo(receiver.position());
        assertThat(anyBodiesTooClose(plan.after())).isFalse();
        assertThat(nets(plan.after())).isEqualTo(nets(document));
        assertThat(plan.pairsStraightened()).isZero();
    }

    @Test
    void aClusterMovesAsOne() {
        CircuitDocument document = new CircuitDocument();
        Component driver = gate(registry, document, 0, 0);
        Component left = gate(registry, document, 10, 4);
        Component right = gate(registry, document, 14, 4);
        jog(document, 4, 2, 7, 10, 6);

        CircuitTidyPlan plan = straighten(document, allBut(document, driver));

        assertThat(positionOf(plan, left)).isEqualTo(new GridPoint(10, 0));
        assertThat(positionOf(plan, right)).isEqualTo(new GridPoint(14, 0));
    }

    @Test
    void onlySelectedPartsMove() {
        CircuitDocument document = oneJog();
        Component driver = document.components().get(0);
        Component receiver = document.components().get(1);

        CircuitTidyPlan plan = straighten(document, Set.of(receiver.id()));

        assertThat(positionOf(plan, driver)).isEqualTo(driver.position());
        assertThat(positionOf(plan, receiver)).isEqualTo(new GridPoint(10, 0));
    }

    @Test
    void verticalPairsAreLeftAlone() {
        CircuitDocument document = new CircuitDocument();
        Component driver = gate(registry, document, 0, 0);
        Component receiver = gate(registry, document, 6, 20);
        jog(document, 4, 2, 5, 6, 22);

        CircuitTidyPlan plan = straighten(document);

        assertThat(positionOf(plan, driver)).isEqualTo(driver.position());
        assertThat(positionOf(plan, receiver)).isEqualTo(receiver.position());
        assertThat(plan.pairsStraightened()).isZero();
    }

    private Component led(CircuitDocument document, int x, int y) {
        return place(registry, document, StandardComponentTypes.LED, x, y, "");
    }

    private static int topOf(CircuitTidyPlan plan, Component component) {
        return positionOf(plan, component).y();
    }

    /**
     * Four flag LEDs side by side in a row (each 2 wide, 2 apart, input at the bottom), each fed by its own gate
     * further down at a different height: straightening one LED to its wire's height would break the row.
     */
    @Test
    void aRowOfLedsFedFromDifferentHeightsStaysARow() {
        CircuitDocument document = new CircuitDocument();
        List<Component> row = new ArrayList<>();
        Set<ElementId> drivers = new LinkedHashSet<>();
        for (int index = 0; index < 4; index++) {
            row.add(led(document, 20 + index * 4, 0));
            Component driver = gate(registry, document, 0, 4 + index * 6);
            drivers.add(driver.id());
            int pinX = 21 + index * 4;
            int driverY = 6 + index * 6;
            wire(document, 4, driverY, pinX, driverY);
            wire(document, pinX, driverY, pinX, 3);
        }
        Set<ElementId> scope = everything(document);
        scope.removeAll(drivers);

        CircuitTidyPlan plan = straighten(document, scope);

        int top = topOf(plan, row.get(0));
        for (Component member : row) {
            assertThat(topOf(plan, member)).isEqualTo(top);
            assertThat(positionOf(plan, member).x()).isEqualTo(member.position().x());
        }
        assertThat(nets(plan.after())).isEqualTo(nets(document));
    }

    /**
     * An AND gate whose first input is already at its driver's height (its wire takes a detour above, so moving the
     * gate adds no corner to it), whose second input jogs 2 units and whose output jogs 4 units to a fixed reader.
     * Moving it down 2 straightens the second input but breaks the first, and down 4 straightens the output but
     * breaks the first: no shift makes one more wire straight. Down 2 would shrink the total distance, and that alone
     * is no reason to move (the user's hand test, 2026-11-07).
     */
    @Test
    void aMoveThatOnlyShortensTheWiresIsNotMade() {
        CircuitDocument document = new CircuitDocument();
        Component firstDriver = gate(registry, document, 0, -2);
        Component secondDriver = gate(registry, document, 0, 4);
        Component pulled = andGate(document, 20, 0);
        Component reader = gate(registry, document, 40, 4);
        wire(document, 4, 0, 6, 0);
        wire(document, 6, 0, 6, -6);
        wire(document, 6, -6, 16, -6);
        wire(document, 16, -6, 16, 0);
        wire(document, 16, 0, 20, 0);
        jog(document, 4, 6, 10, 20, 4);
        jog(document, 24, 2, 30, 40, 6);

        CircuitTidyPlan plan = straighten(document, allBut(document, firstDriver, secondDriver, reader));

        assertThat(positionOf(plan, pulled)).isEqualTo(pulled.position());
        assertThat(plan.pairsStraightened()).isZero();
        assertThat(nets(plan.after())).isEqualTo(nets(document));
    }

    /**
     * The flag LEDs Z, N, C, V of riscv-maniqui.vsim (c59–c62 and wires w237–w252, as in the file; the ALU's outputs
     * at x = 266 stand in as input pins): each wire leaves the ALU, drops below the row and climbs into the LED's
     * input on its bottom edge. Moving the row up 9 puts V's pin at its driver's height, but its wire still comes from
     * below, only longer: a pin on the top or bottom edge of a part is never the end of a straight horizontal wire.
     */
    @Test
    void theFlagLedsOfTheRiscvDummyStayPut() {
        CircuitDocument document = new CircuitDocument();
        List<Component> row = new ArrayList<>();
        Set<ElementId> drivers = new LinkedHashSet<>();
        for (int x : List.of(280, 284, 288, 292)) {
            row.add(led(document, x, 20));
        }
        Component sample = registry.require(StandardComponentTypes.INPUT_PIN).newComponent(new ElementId("sample"),
                GridPoint.ORIGIN, Rotation.R0, Map.of(), "");
        GridPoint output = registry.require(StandardComponentTypes.INPUT_PIN).pinLocation(sample, 0);
        for (int driverY : List.of(2, 6, 10, 14)) {
            drivers.add(place(registry, document, StandardComponentTypes.INPUT_PIN, 266 - output.x(),
                    driverY - output.y(), "").id());
        }
        int[][] wires = {
                {266, 2, 268, 2}, {268, 2, 268, 24}, {268, 24, 281, 24}, {281, 23, 281, 24},
                {266, 6, 283, 6}, {283, 6, 283, 24}, {283, 24, 285, 24}, {285, 23, 285, 24},
                {266, 10, 287, 10}, {287, 10, 287, 24}, {287, 24, 289, 24}, {289, 23, 289, 24},
                {266, 14, 270, 14}, {270, 14, 270, 26}, {270, 26, 293, 26}, {293, 23, 293, 26}};
        for (int[] segment : wires) {
            wire(document, segment[0], segment[1], segment[2], segment[3]);
        }
        Set<ElementId> scope = everything(document);
        scope.removeAll(drivers);

        CircuitTidyPlan plan = straighten(document, scope);

        for (Component member : row) {
            assertThat(positionOf(plan, member)).isEqualTo(member.position());
        }
        assertThat(plan.pairsStraightened()).isZero();
        assertThat(nets(plan.after())).isEqualTo(nets(document));
    }

    /**
     * Two gates side by side in a row (the first feeds the second), its input fed from a gate 10 units lower and its
     * output read by a fixed gate 6 units lower, both through jogs: either shift straightens one of the two wires, so
     * the whole row moves by one of them.
     */
    @Test
    void aRowMovesWhenAShiftStraightensOneMorePair() {
        CircuitDocument document = new CircuitDocument();
        Component driver = gate(registry, document, 0, 10);
        List<Component> row = List.of(gate(registry, document, 20, 0), gate(registry, document, 28, 0));
        Component reader = gate(registry, document, 40, 6);
        jog(document, 4, 12, 12, 20, 2);
        wire(document, 24, 2, 28, 2);
        jog(document, 32, 2, 36, 40, 8);

        CircuitTidyPlan plan = straighten(document, allBut(document, driver, reader));

        int shift = topOf(plan, row.get(0)) - row.get(0).position().y();
        assertThat(shift).isIn(6, 10);
        for (Component member : row) {
            assertThat(positionOf(plan, member)).isEqualTo(member.position().plus(new GridPoint(0, shift)));
        }
        assertThat(plan.pairsStraightened()).isEqualTo(1);
        assertThat(nets(plan.after())).isEqualTo(nets(document));
    }

    /**
     * Rows: a gate and an input pin with the same centre two units apart are one, chained to a gate with the same
     * centre four units further; a gate with the same top farther than {@value Straightening#ROW_GAP} units away and
     * one stacked below are in none.
     */
    @Test
    void rowsChainPartsWithTheSameTopOrCentreThatAreCloseOnX() {
        CircuitDocument document = new CircuitDocument();
        Component left = gate(registry, document, 0, 0);
        Component centred = place(registry, document, StandardComponentTypes.INPUT_PIN, 6, 1, "");
        Component right = gate(registry, document, 12, 0);
        gate(registry, document, 12 + 4 + Straightening.ROW_GAP + 1, 0);
        gate(registry, document, 0, 8);

        List<List<ElementId>> rows = Straightening.rows(
                LayoutItems.of(document, registry, everything(document), TextBoxes.NONE));

        assertThat(rows).containsExactly(List.of(left.id(), centred.id(), right.id()));
    }

    /**
     * Two gates side by side in a row whose input and output wires both jog 10 units down (from a fixed driver, to a
     * fixed reader): the whole row moves down 10 as one and both wires end straight.
     */
    @Test
    void aRowWhoseWiresAllSitAtTheSameOffsetMovesAsOne() {
        CircuitDocument document = new CircuitDocument();
        Component driver = gate(registry, document, 0, 10);
        List<Component> row = List.of(gate(registry, document, 20, 0), gate(registry, document, 28, 0));
        Component reader = gate(registry, document, 50, 10);
        jog(document, 4, 12, 12, 20, 2);
        wire(document, 24, 2, 28, 2);
        jog(document, 32, 2, 36, 50, 12);

        CircuitTidyPlan plan = straighten(document, allBut(document, driver, reader));

        for (Component member : row) {
            assertThat(positionOf(plan, member)).isEqualTo(new GridPoint(member.position().x(), 10));
        }
        assertThat(plan.pairsStraightened()).isEqualTo(2);
        assertThat(nets(plan.after())).isEqualTo(nets(document));
    }

    /**
     * With aligning and straightening off only phase 1 runs: on a circuit where straightening would move a gate, that
     * gate and its jogged wire stay exactly as they were, while the overlap elsewhere is still separated.
     */
    @Test
    void withBothOffOnlyPhaseOneRuns() {
        CircuitDocument document = new CircuitDocument();
        Component driver = gate(registry, document, 0, 0);
        Component jogged = gate(registry, document, 10, 2);
        jog(document, 4, 2, 7, 10, 4);
        gate(registry, document, 40, 0);
        gate(registry, document, 41, 1);
        CircuitTidyOptions phaseOne = new CircuitTidyOptions(true, TidyOptions.DEFAULTS);

        CircuitTidyPlan plan = CircuitTidy.plan(document, registry, everything(document), phaseOne, TextBoxes.NONE,
                () -> false, CircuitTidyProgress.NONE).orElseThrow();

        assertThat(positionOf(plan, driver)).isEqualTo(driver.position());
        assertThat(positionOf(plan, jogged)).isEqualTo(jogged.position());
        assertThat(plan.after().wires()).containsExactlyInAnyOrderElementsOf(document.wires());
        assertThat(anyBodiesTooClose(plan.after())).isFalse();
        assertThat(plan.partsMoved()).isPositive();
        assertThat(plan.partsAligned()).isZero();
        assertThat(plan.pairsStraightened()).isZero();
        assertThat(straighten(document).pairsStraightened()).isEqualTo(1);
    }
}
