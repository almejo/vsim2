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
     * The flag LEDs N, C, V, Z of the user's hand test (2026-11-07): Z's driver is already at its pin's height (its
     * wire takes a detour above, so moving the row adds no corner to it), N, C and V are fed from further down. Every shift that
     * straightens one of N, C, V breaks Z, so none makes one more wire straight: only the total distance would shrink,
     * and that alone is no reason to move.
     */
    @Test
    void aRowWhereNoShiftStraightensOneMorePairStaysPut() {
        CircuitDocument document = new CircuitDocument();
        List<Component> row = new ArrayList<>();
        Set<ElementId> drivers = new LinkedHashSet<>();
        for (int index = 0; index < 4; index++) {
            row.add(led(document, 20 + index * 4, 0));
        }
        for (int index = 0; index < 3; index++) {
            drivers.add(gate(registry, document, 0, 10 + index * 6).id());
            int pinX = 21 + index * 4;
            int driverY = 12 + index * 6;
            wire(document, 4, driverY, pinX, driverY);
            wire(document, pinX, driverY, pinX, 3);
        }
        drivers.add(gate(registry, document, 0, 1).id());
        wire(document, 4, 3, 6, 3);
        wire(document, 6, 3, 6, -3);
        wire(document, 6, -3, 31, -3);
        wire(document, 31, -3, 31, 3);
        wire(document, 31, 3, 33, 3);
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
     * The same row with none of its wires straight: a shift that straightens one of them makes one more wire
     * straight, so the whole row moves by it.
     */
    @Test
    void aRowMovesWhenAShiftStraightensOneMorePair() {
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

        int shift = topOf(plan, row.get(0)) - row.get(0).position().y();
        assertThat(shift).isNotZero();
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

    /** The same row fed by one gate whose wire reaches every LED at the same height: the whole row moves as one. */
    @Test
    void aRowWhoseWiresAllSitAtTheSameOffsetMovesAsOne() {
        CircuitDocument document = new CircuitDocument();
        List<Component> row = new ArrayList<>();
        Component driver = gate(registry, document, 0, 10);
        int trunkX = 4;
        for (int index = 0; index < 4; index++) {
            row.add(led(document, 20 + index * 4, 0));
            int pinX = 21 + index * 4;
            wire(document, trunkX, 12, pinX, 12);
            wire(document, pinX, 12, pinX, 3);
            trunkX = pinX;
        }

        CircuitTidyPlan plan = straighten(document, allBut(document, driver));

        for (Component member : row) {
            assertThat(positionOf(plan, member)).isEqualTo(new GridPoint(member.position().x(), 9));
        }
        assertThat(plan.pairsStraightened()).isEqualTo(4);
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
