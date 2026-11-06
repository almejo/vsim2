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

    @Test
    void aLongChainDoesNotDriftTheCircuit() {
        CircuitDocument document = new CircuitDocument();
        Component first = andGate(document, 0, 0);
        for (int index = 1; index < 6; index++) {
            andGate(document, index * 10, 0);
            jog(document, index * 10 - 6, 2, index * 10 - 3, index * 10, 4);
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
