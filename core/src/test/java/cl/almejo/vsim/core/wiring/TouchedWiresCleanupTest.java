package cl.almejo.vsim.core.wiring;

import cl.almejo.vsim.core.TestComponentTypes;
import cl.almejo.vsim.core.command.TidyWiresCommand;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TouchedWiresCleanupTest {

    private final ComponentTypeRegistry registry = TestComponentTypes.registry();
    private final CircuitDocument document = new CircuitDocument();

    private ElementId wire(int startX, int startY, int endX, int endY) {
        ElementId id = document.newWireId();
        document.add(new Wire(id, new GridPoint(startX, startY), new GridPoint(endX, endY)));
        return id;
    }

    /** Buffer at (x,y): input pin at (x, y+1), output pin at (x+2, y+1). */
    private void buffer(int x, int y) {
        document.add(new Component(document.newComponentId(), TestComponentTypes.BUFFER, new GridPoint(x, y),
                Rotation.R0, Map.of(), ""));
    }

    @Test
    void twoTouchedCollinearWiresEndToEndAreMerged() {
        ElementId first = wire(0, 0, 4, 0);
        ElementId second = wire(4, 0, 9, 0);
        TidyPlan plan = TouchedWiresCleanup.plan(document, registry, Set.of(first, second));
        assertThat(plan.added()).containsExactly(new Wire(first, new GridPoint(0, 0), new GridPoint(9, 0)));
        assertThat(plan.removed()).hasSize(2);
        assertThat(plan.probeRetargets()).isEmpty();
    }

    @Test
    void overlappingTouchedCollinearWiresAreMerged() {
        ElementId first = wire(0, 0, 6, 0);
        ElementId second = wire(3, 0, 9, 0);
        TidyPlan plan = TouchedWiresCleanup.plan(document, registry, Set.of(first, second));
        assertThat(plan.added()).containsExactly(new Wire(first, new GridPoint(0, 0), new GridPoint(9, 0)));
    }

    @Test
    void anUntouchedCollinearPairIsLeftAlone() {
        wire(0, 0, 4, 0);
        wire(4, 0, 9, 0);
        ElementId unrelated = wire(0, 5, 3, 5);
        assertThat(TouchedWiresCleanup.plan(document, registry, Set.of(unrelated)).isEmpty()).isTrue();
    }

    @Test
    void aPairWithOnlyOneTouchedWireIsLeftAlone() {
        ElementId touched = wire(0, 0, 4, 0);
        wire(4, 0, 9, 0);
        assertThat(TouchedWiresCleanup.plan(document, registry, Set.of(touched)).isEmpty()).isTrue();
    }

    @Test
    void aMergeThatWouldChangeConnectivityIsSkipped() {
        // the vertical wire passes through (4,0): the two halves are joined to it only by their ends, and one
        // merged wire would merely cross it
        ElementId first = wire(0, 0, 4, 0);
        ElementId second = wire(4, 0, 9, 0);
        ElementId vertical = wire(4, -3, 4, 3);
        TidyPlan plan = TouchedWiresCleanup.plan(document, registry, Set.of(first, second, vertical));
        assertThat(plan.isEmpty()).isTrue();
    }

    @Test
    void aMergeAtAPinKeepsThePinConnected() {
        // the input pin of the buffer sits at (4,1), the shared point: merging keeps it on the wire
        buffer(4, 0);
        ElementId first = wire(0, 1, 4, 1);
        ElementId second = wire(4, 1, 8, 1);
        TidyPlan plan = TouchedWiresCleanup.plan(document, registry, Set.of(first, second));
        List<Wire> after = new ArrayList<>(document.wires());
        after.removeIf(wire -> plan.removed().stream().anyMatch(removed -> removed.id().equals(wire.id())));
        after.addAll(plan.added());
        assertThat(Connectivity.of(after, document, registry, plan.probeRetargets()))
                .isEqualTo(Connectivity.of(document.wires(), document, registry));
    }

    @Test
    void aPerpendicularWireAtTheSharedPointKeepsConnectivity() {
        ElementId first = wire(0, 0, 4, 0);
        ElementId second = wire(4, 0, 9, 0);
        ElementId branch = wire(4, 0, 4, 5);
        TidyPlan plan = TouchedWiresCleanup.plan(document, registry, Set.of(first, second, branch));
        List<Wire> after = new ArrayList<>(document.wires());
        after.removeIf(wire -> plan.removed().stream().anyMatch(removed -> removed.id().equals(wire.id())));
        after.addAll(plan.added());
        assertThat(Connectivity.of(after, document, registry, plan.probeRetargets()))
                .isEqualTo(Connectivity.of(document.wires(), document, registry));
        assertThat(plan.added()).extracting(Wire::id).doesNotContain(branch);
    }

    @Test
    void stubsAndDetoursAreNotTouched() {
        buffer(0, 0);
        ElementId stub = wire(5, 5, 8, 5);
        ElementId toPin = wire(2, 1, 6, 1);
        ElementId firstSide = wire(10, 0, 10, 4);
        ElementId middle = wire(10, 4, 14, 4);
        ElementId lastSide = wire(14, 4, 14, 0);
        assertThat(TouchedWiresCleanup.plan(document, registry,
                Set.of(stub, toPin, firstSide, middle, lastSide)).isEmpty()).isTrue();
    }

    @Test
    void emptyPlanWhenNothingToDoOrNothingTouched() {
        ElementId only = wire(0, 0, 4, 0);
        assertThat(TouchedWiresCleanup.plan(document, registry, Set.of(only)).isEmpty()).isTrue();
        assertThat(TouchedWiresCleanup.plan(document, registry, Set.of())).isSameAs(TidyPlan.EMPTY);
    }

    @Test
    void theResultAppliesAndRevertsExactlyWithTheTidyCommand() {
        wire(0, 9, 2, 9);
        ElementId first = wire(0, 0, 4, 0);
        wire(0, 7, 2, 7);
        ElementId second = wire(4, 0, 9, 0);
        List<Wire> before = document.wires();
        TidyWiresCommand command = new TidyWiresCommand(
                TouchedWiresCleanup.plan(document, registry, Set.of(first, second)));

        command.apply(document);
        assertThat(document.wires()).hasSize(3)
                .contains(new Wire(first, new GridPoint(0, 0), new GridPoint(9, 0)));
        assertThat(document.wire(second)).isEmpty();
        assertThat(document.wires().get(1).id()).isEqualTo(first);

        command.revert(document);
        assertThat(document.wires()).containsExactlyElementsOf(before);
    }
}
