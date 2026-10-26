package cl.almejo.vsim.core.wiring;

import cl.almejo.vsim.core.TestComponentTypes;
import cl.almejo.vsim.core.bundled.BundledExamples;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static cl.almejo.vsim.core.DocumentFixtures.wireId;
import static org.assertj.core.api.Assertions.assertThat;

class WireTidyDetourTest {

    private static final TidyOptions ALL = new TidyOptions(true, true, true);

    private final ComponentTypeRegistry registry = TestComponentTypes.registry();
    private final CircuitDocument document = new CircuitDocument();

    /** Buffer at (x,y), unrotated: input pin at (x, y+1), output pin at (x+2, y+1), body x..x+2, y..y+2. */
    private void buffer(int x, int y) {
        place(TestComponentTypes.BUFFER, x, y, Rotation.R0);
    }

    private void place(String type, int x, int y, Rotation rotation) {
        document.add(new Component(document.newComponentId(), type, new GridPoint(x, y), rotation, Map.of(), ""));
    }

    private TidyPlan plan(TidyOptions options) {
        return plan(allWires(), options);
    }

    private TidyPlan plan(Set<ElementId> scope, TidyOptions options) {
        return WireTidyTest.planOf(document, registry, scope, options);
    }

    private Set<ElementId> allWires() {
        Set<ElementId> ids = new HashSet<>();
        document.wires().forEach(wire -> ids.add(wire.id()));
        return ids;
    }

    /** The final wires as (start, end) pairs, ids ignored. */
    private Set<String> finalSegments(TidyPlan plan) {
        Set<Wire> wires = new HashSet<>(document.wires());
        plan.removed().forEach(wires::remove);
        wires.addAll(plan.added());
        return wires.stream().map(WireTidyDetourTest::segment).collect(Collectors.toSet());
    }

    private static String segment(Wire wire) {
        return wire.start().x() + "," + wire.start().y() + "-" + wire.end().x() + "," + wire.end().y();
    }

    /** Buffers at both ends of row 1 and a U of depth 4 hanging below between x=6 and x=12. */
    private void equalLegsU() {
        buffer(0, 0);
        buffer(20, 0);
        wireId(document, 2, 1, 6, 1);
        wireId(document, 6, 1, 6, 5);
        wireId(document, 6, 5, 12, 5);
        wireId(document, 12, 5, 12, 1);
        wireId(document, 12, 1, 20, 1);
    }

    @Test
    void equalLegsVanishAndTheMiddleLandsOnTheLineAndMergesWithItsNeighbours() {
        equalLegsU();
        TidyPlan plan = plan(ALL);
        assertThat(plan.detoursFlattened()).isEqualTo(1);
        assertThat(finalSegments(plan)).containsExactly("2,1-20,1");
    }

    @Test
    void theShorterRightLegDisappearsAndTheLeftOneShortens() {
        buffer(0, 0);
        buffer(20, 2);
        wireId(document, 2, 1, 6, 1);
        wireId(document, 6, 1, 6, 5);
        ElementId middle = wireId(document, 6, 5, 12, 5);
        wireId(document, 12, 5, 12, 3);
        wireId(document, 12, 3, 20, 3);
        TidyPlan plan = plan(ALL);
        assertThat(plan.detoursFlattened()).isEqualTo(1);
        assertThat(finalSegments(plan)).containsExactlyInAnyOrder("2,1-6,1", "6,1-6,3", "6,3-20,3");
        assertThat(plan.added()).extracting(Wire::id).contains(middle);
    }

    @Test
    void theShorterLeftLegDisappearsAndTheRightOneShortens() {
        buffer(0, 2);
        buffer(20, 0);
        wireId(document, 2, 3, 6, 3);
        wireId(document, 6, 3, 6, 5);
        wireId(document, 6, 5, 12, 5);
        wireId(document, 12, 5, 12, 1);
        wireId(document, 12, 1, 20, 1);
        TidyPlan plan = plan(ALL);
        assertThat(plan.detoursFlattened()).isEqualTo(1);
        assertThat(finalSegments(plan)).containsExactlyInAnyOrder("2,3-12,3", "12,1-12,3", "12,1-20,1");
    }

    @Test
    void aUOpeningDownwardsFlattensToo() {
        buffer(0, 4);
        buffer(20, 4);
        wireId(document, 2, 5, 6, 5);
        wireId(document, 6, 5, 6, 1);
        wireId(document, 6, 1, 12, 1);
        wireId(document, 12, 1, 12, 5);
        wireId(document, 12, 5, 20, 5);
        TidyPlan plan = plan(ALL);
        assertThat(plan.detoursFlattened()).isEqualTo(1);
        assertThat(finalSegments(plan)).containsExactly("2,5-20,5");
    }

    @Test
    void aPinOnTheMovedPathBlocksIt() {
        equalLegsU();
        // rotated buffer whose input pin sits at (9,1), on the row the middle would move to
        place(TestComponentTypes.BUFFER, 10, 1, Rotation.R90);
        TidyPlan plan = plan(ALL);
        assertThat(plan.detoursFlattened()).isZero();
        assertThat(plan.isEmpty()).isTrue();
    }

    @Test
    void anotherWireCrossingTheMovedPathBlocksIt() {
        equalLegsU();
        buffer(7, -4);
        buffer(9, 2);
        // from (9,-3) to (9,3): its interior would cross the moved middle at (9,1), it does not join it
        wireId(document, 9, -3, 9, 3);
        TidyPlan plan = plan(ALL);
        assertThat(plan.detoursFlattened()).isZero();
        assertThat(plan.isEmpty()).isTrue();
    }

    @Test
    void aComponentBodyInTheWayBlocksIt() {
        equalLegsU();
        // rotated buffer: body x 8..10, y 0..2 (row 1 runs through it), pins at (9,0) and (9,2), off the path
        place(TestComponentTypes.BUFFER, 10, 0, Rotation.R90);
        TidyPlan plan = plan(ALL);
        assertThat(plan.detoursFlattened()).isZero();
        assertThat(plan.isEmpty()).isTrue();
    }

    @Test
    void aUWhoseCornerCarriesATIsNotTouched() {
        equalLegsU();
        buffer(0, 4);
        wireId(document, 2, 5, 6, 5);
        TidyPlan plan = plan(ALL);
        assertThat(plan.detoursFlattened()).isZero();
        assertThat(finalSegments(plan)).contains("6,1-6,5", "12,1-12,5");
    }

    @Test
    void aUInABusWireFlattens() {
        place(TestComponentTypes.WIDE_PROBE, 2, 1, Rotation.R180);
        place(TestComponentTypes.WIDE_PROBE, 20, 1, Rotation.R0);
        wireId(document, 2, 1, 6, 1);
        wireId(document, 6, 1, 6, 5);
        wireId(document, 6, 5, 12, 5);
        wireId(document, 12, 5, 12, 1);
        wireId(document, 12, 1, 20, 1);
        TidyPlan plan = plan(ALL);
        assertThat(plan.detoursFlattened()).isEqualTo(1);
        assertThat(finalSegments(plan)).containsExactly("2,1-20,1");
    }

    @Test
    void aUWithANotchFlattensInSuccessiveRounds() {
        buffer(0, 0);
        buffer(20, 0);
        wireId(document, 2, 1, 6, 1);
        wireId(document, 6, 1, 6, 7);
        wireId(document, 6, 7, 9, 7);
        wireId(document, 9, 7, 9, 5);
        wireId(document, 9, 5, 11, 5);
        wireId(document, 11, 5, 11, 7);
        wireId(document, 11, 7, 14, 7);
        wireId(document, 14, 7, 14, 1);
        wireId(document, 14, 1, 20, 1);
        TidyPlan plan = plan(ALL);
        assertThat(plan.detoursFlattened()).isGreaterThanOrEqualTo(2);
        assertThat(finalSegments(plan)).containsExactly("2,1-20,1");
    }

    @Test
    void outOfScopeWiresAreNotFlattened() {
        equalLegsU();
        Set<ElementId> scope = new HashSet<>(allWires());
        scope.remove(document.wires().get(2).id());
        TidyPlan plan = plan(scope, ALL);
        assertThat(plan.detoursFlattened()).isZero();
    }

    @Test
    void offByOptionTheUIsUntouched() {
        equalLegsU();
        assertThat(plan(TidyOptions.DEFAULTS).isEmpty()).isTrue();
    }

    @Test
    void theBundledExamplesStayEquivalentWithEveryOptionOn() throws Exception {
        ComponentTypeRegistry standard = StandardComponentTypes.registry();
        for (String id : BundledExamples.ids()) {
            CircuitDocument example = BundledExamples.open(id, standard);
            ComponentTypeRegistry resolving = standard.extendedWith(SubcircuitTypes.resolverFor(example));
            Set<ElementId> everything = new HashSet<>();
            example.wires().forEach(wire -> everything.add(wire.id()));
            WireTidyTest.planOf(example, resolving, everything, ALL);
        }
    }
}
