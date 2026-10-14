package cl.almejo.vsim.core.wiring;

import cl.almejo.vsim.core.TestComponentTypes;
import cl.almejo.vsim.core.bundled.BundledExamples;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.document.ProbeTarget;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class WireTidyTest {

    private final ComponentTypeRegistry registry = TestComponentTypes.registry();
    private final CircuitDocument document = new CircuitDocument();

    private ElementId wire(int startX, int startY, int endX, int endY) {
        ElementId id = document.newWireId();
        document.add(new Wire(id, new GridPoint(startX, startY), new GridPoint(endX, endY)));
        return id;
    }

    /** Buffer at (x,y): input pin at (x, y+1), output pin at (x+2, y+1). */
    private ElementId buffer(int x, int y) {
        ElementId id = document.newComponentId();
        document.add(new Component(id, TestComponentTypes.BUFFER, new GridPoint(x, y), Rotation.R0, Map.of(), ""));
        return id;
    }

    private TidyPlan plan() {
        return plan(allWires(), TidyOptions.DEFAULTS);
    }

    private Set<ElementId> allWires() {
        Set<ElementId> ids = new HashSet<>();
        document.wires().forEach(wire -> ids.add(wire.id()));
        return ids;
    }

    private TidyPlan plan(Set<ElementId> scope, TidyOptions options) {
        return planOf(document, registry, scope, options);
    }

    /** Plans and asserts the equivalence every plan must keep. */
    static TidyPlan planOf(CircuitDocument document, ComponentTypeRegistry registry, Set<ElementId> scope,
                           TidyOptions options) {
        TidyPlan plan = WireTidy.plan(document, registry, scope, options);
        List<Wire> after = new ArrayList<>(document.wires());
        after.removeIf(wire -> plan.removed().stream().anyMatch(removed -> removed.id().equals(wire.id())));
        after.addAll(plan.added());
        assertThat(Connectivity.of(after, document, registry, plan.probeRetargets()))
                .isEqualTo(Connectivity.of(document.wires(), document, registry));
        plan.removed().forEach(removed -> assertThat(document.wires()).contains(removed));
        return plan;
    }

    private static Set<Wire> finalWires(CircuitDocument document, TidyPlan plan) {
        Set<Wire> wires = new HashSet<>(document.wires());
        wires.removeAll(plan.removed());
        wires.addAll(plan.added());
        return wires;
    }

    @Test
    void aStubOffATIsRemoved() {
        buffer(0, 0);
        buffer(10, 0);
        wire(2, 1, 10, 1);
        ElementId stub = wire(6, 1, 6, 5);
        TidyPlan plan = plan();
        assertThat(plan.removed()).extracting(Wire::id).containsExactly(stub);
        assertThat(plan.added()).isEmpty();
        assertThat(plan.stubsRemoved()).isEqualTo(1);
        assertThat(plan.zeroLengthRemoved()).isZero();
    }

    @Test
    void aStubWhoseInteriorHasAPinIsTrimmedToThePinNotRemoved() {
        buffer(0, 0);
        buffer(5, 0);
        ElementId through = wire(2, 1, 9, 1);
        TidyPlan plan = plan();
        assertThat(plan.removed()).extracting(Wire::id).containsExactly(through);
        assertThat(plan.added()).containsExactly(new Wire(through, new GridPoint(2, 1), new GridPoint(7, 1)));
        assertThat(plan.stubsRemoved()).isEqualTo(1);
    }

    @Test
    void aWireFromAPinToNowhereIsRemoved() {
        buffer(0, 0);
        ElementId dangling = wire(2, 1, 8, 1);
        TidyPlan plan = plan();
        assertThat(plan.removed()).extracting(Wire::id).containsExactly(dangling);
        assertThat(plan.added()).isEmpty();
    }

    @Test
    void aFloatingNetworkDisappearsUnlessAProbeWatchesIt() {
        ElementId first = wire(0, 10, 6, 10);
        ElementId second = wire(6, 10, 6, 14);
        wire(6, 14, 12, 14);
        TidyPlan plan = plan();
        assertThat(plan.removed()).hasSize(3);
        assertThat(plan.added()).isEmpty();

        document.setProbes(List.of(new Probe("p1", new ProbeTarget.WireTarget(second), "watched")));
        TidyPlan watched = plan();
        assertThat(watched.isEmpty()).isTrue();
        assertThat(first).isNotNull();
    }

    @Test
    void aFloatingLoopDisappearsToo() {
        wire(0, 0, 4, 0);
        wire(4, 0, 4, 4);
        wire(0, 4, 4, 4);
        wire(0, 0, 0, 4);
        assertThat(plan().removed()).hasSize(4);
    }

    @Test
    void twoCollinearWiresSharingAnEndpointMerge() {
        buffer(0, 0);
        buffer(10, 0);
        ElementId left = wire(2, 1, 6, 1);
        wire(6, 1, 10, 1);
        TidyPlan plan = plan();
        assertThat(plan.removed()).hasSize(2);
        assertThat(plan.added()).containsExactly(new Wire(left, new GridPoint(2, 1), new GridPoint(10, 1)));
        assertThat(plan.segmentsMerged()).isEqualTo(1);
    }

    @Test
    void threeInARowMergeIntoOneInOnePlan() {
        buffer(0, 0);
        buffer(14, 0);
        ElementId first = wire(2, 1, 5, 1);
        wire(5, 1, 9, 1);
        wire(9, 1, 14, 1);
        TidyPlan plan = plan();
        assertThat(plan.removed()).hasSize(3);
        assertThat(plan.added()).containsExactly(new Wire(first, new GridPoint(2, 1), new GridPoint(14, 1)));
        assertThat(plan.segmentsMerged()).isEqualTo(2);
    }

    @Test
    void overlappingCollinearWiresMerge() {
        buffer(0, 0);
        buffer(14, 0);
        wire(2, 1, 8, 1);
        wire(6, 1, 14, 1);
        TidyPlan plan = plan();
        assertThat(plan.added()).extracting(Wire::start, Wire::end)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(new GridPoint(2, 1), new GridPoint(14, 1)));
    }

    @Test
    void aPerpendicularWiresInteriorAtTheSharedPointBlocksTheMerge() {
        buffer(0, 0);
        buffer(10, 0);
        buffer(4, -5);
        buffer(4, 5);
        wire(2, 1, 6, 1);
        wire(6, 1, 10, 1);
        wire(6, -4, 6, 6);
        assertThat(plan().isEmpty()).isTrue();
    }

    @Test
    void aTJunctionAtTheSharedPointStillMergesAndStaysConnected() {
        buffer(0, 0);
        buffer(10, 0);
        buffer(4, 6);
        wire(2, 1, 6, 1);
        wire(6, 1, 10, 1);
        wire(6, 1, 6, 7);
        TidyPlan plan = plan();
        assertThat(plan.segmentsMerged()).isEqualTo(1);
        assertThat(plan.removed()).hasSize(2);
    }

    @Test
    void aPinAtTheSharedPointDoesNotBlockTheMerge() {
        buffer(0, 0);
        buffer(6, 0);
        wire(2, 1, 6, 1);
        wire(6, 1, 12, 1);
        buffer(12, 0);
        assertThat(plan().segmentsMerged()).isEqualTo(1);
    }

    @Test
    void aProbeOnAMergedAwayWireIsRetargetedToTheSurvivor() {
        document.setProbes(List.of());
        buffer(0, 0);
        buffer(10, 0);
        ElementId left = wire(2, 1, 6, 1);
        ElementId right = wire(6, 1, 10, 1);
        document.setProbes(List.of(new Probe("p1", new ProbeTarget.WireTarget(right), "x")));
        TidyPlan plan = plan();
        assertThat(plan.probeRetargets()).containsExactly(Map.entry(right, left));
    }

    @Test
    void aScopeThatExcludesOneOfTwoMergeableWiresMergesNothing() {
        buffer(0, 0);
        buffer(10, 0);
        ElementId left = wire(2, 1, 6, 1);
        wire(6, 1, 10, 1);
        assertThat(plan(Set.of(left), TidyOptions.DEFAULTS).isEmpty()).isTrue();
    }

    @Test
    void aScopeLimitsStubRemovalToSelectedWiresButStillSeesTheWholeCircuit() {
        buffer(0, 0);
        buffer(10, 0);
        wire(2, 1, 10, 1);
        ElementId selectedStub = wire(6, 1, 6, 5);
        ElementId otherStub = wire(8, 1, 8, 5);
        TidyPlan plan = plan(Set.of(selectedStub), TidyOptions.DEFAULTS);
        assertThat(plan.removed()).extracting(Wire::id).containsExactly(selectedStub);
        assertThat(otherStub).isNotNull();
    }

    @Test
    void optionsTurnTheRulesOffIndependently() {
        buffer(0, 0);
        buffer(10, 0);
        wire(2, 1, 6, 1);
        wire(6, 1, 10, 1);
        wire(8, 1, 8, 5);
        assertThat(plan(allWires(), new TidyOptions(false, true, false)).stubsRemoved()).isZero();
        assertThat(plan(allWires(), new TidyOptions(false, true, false)).segmentsMerged()).isEqualTo(1);
        assertThat(plan(allWires(), new TidyOptions(true, false, false)).segmentsMerged()).isZero();
        assertThat(plan(allWires(), new TidyOptions(true, false, false)).stubsRemoved()).isEqualTo(1);
    }

    @Test
    void thePlanReachesAFixedPointAcrossRounds() {
        buffer(0, 0);
        buffer(14, 0);
        wire(2, 1, 14, 1);
        wire(6, 1, 6, 4);
        wire(6, 4, 9, 4);
        TidyPlan plan = plan();
        assertThat(plan.removed()).hasSize(2);
        assertThat(plan.added()).isEmpty();
        // the result is clean: planning again on it finds nothing
        CircuitDocument tidied = new CircuitDocument();
        document.components().forEach(tidied::add);
        finalWires(document, plan).forEach(tidied::add);
        assertThat(planOf(tidied, registry, Set.of(), TidyOptions.DEFAULTS).isEmpty()).isTrue();
    }

    @Test
    void planningNeverMutatesTheDocument() {
        buffer(0, 0);
        wire(2, 1, 8, 1);
        long revision = document.revision();
        List<Wire> before = document.wires();
        plan();
        assertThat(document.revision()).isEqualTo(revision);
        assertThat(document.wires()).isEqualTo(before);
    }

    @Test
    void theDetourOptionLeavesAStraightWireAlone() {
        buffer(0, 0);
        buffer(10, 0);
        wire(2, 1, 10, 1);
        assertThat(plan(allWires(), new TidyOptions(true, true, true)).isEmpty()).isTrue();
    }

    @Test
    void theBundledExamplesHaveNoLooseEndsAndTidyingThemIsEquivalentAndStable() throws Exception {
        ComponentTypeRegistry standard = StandardComponentTypes.registry();
        for (String id : BundledExamples.ids()) {
            CircuitDocument example = BundledExamples.open(id, standard);
            ComponentTypeRegistry resolving = standard.extendedWith(SubcircuitTypes.resolverFor(example));
            Set<ElementId> everything = new HashSet<>();
            example.wires().forEach(wire -> everything.add(wire.id()));
            TidyPlan plan = planOf(example, resolving, everything, TidyOptions.DEFAULTS);
            assertThat(plan.stubsRemoved()).as(id).isZero();
            // mux-4-1 has collinear pieces end to end (e.g. w18 + w19); those merge, nothing else changes
            assertThat(plan.removed().size() - plan.added().size()).as(id).isEqualTo(plan.segmentsMerged());
            CircuitDocument tidied = new CircuitDocument();
            example.components().forEach(tidied::add);
            finalWires(example, plan).forEach(tidied::add);
            assertThat(planOf(tidied, resolving, everything, TidyOptions.DEFAULTS).isEmpty()).as(id).isTrue();
        }
    }

    @Test
    void aCleanCircuitGivesAnEmptyPlan() {
        buffer(0, 0);
        buffer(10, 0);
        wire(2, 1, 10, 1);
        assertThat(plan().isEmpty()).isTrue();
        assertThat(plan()).isEqualTo(TidyPlan.EMPTY);
    }
}
