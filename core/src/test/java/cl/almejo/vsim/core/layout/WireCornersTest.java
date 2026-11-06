package cl.almejo.vsim.core.layout;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static cl.almejo.vsim.core.DocumentFixtures.wire;
import static org.assertj.core.api.Assertions.assertThat;

class WireCornersTest {

    @Test
    void anLHasOneCorner() {
        CircuitDocument document = new CircuitDocument();
        wire(document, 0, 0, 4, 0);
        wire(document, 4, 0, 4, 4);

        assertThat(WireCorners.count(document)).isEqualTo(1);
    }

    @Test
    void aJogHasTwoCorners() {
        CircuitDocument document = new CircuitDocument();
        wire(document, 0, 0, 4, 0);
        wire(document, 4, 0, 4, 2);
        wire(document, 4, 2, 8, 2);

        assertThat(WireCorners.count(document)).isEqualTo(2);
    }

    @Test
    void aTJunctionIsNotACorner() {
        CircuitDocument document = new CircuitDocument();
        wire(document, 0, 0, 8, 0);
        wire(document, 4, 0, 4, 4);

        assertThat(WireCorners.count(document)).isZero();
    }

    @Test
    void aTJunctionOfThreeEndsIsNotACorner() {
        CircuitDocument document = new CircuitDocument();
        wire(document, 0, 0, 4, 0);
        wire(document, 4, 0, 8, 0);
        wire(document, 4, 0, 4, 4);

        assertThat(WireCorners.count(document)).isZero();
    }

    @Test
    void aCrossingIsNotACorner() {
        CircuitDocument document = new CircuitDocument();
        wire(document, 0, 2, 4, 2);
        wire(document, 2, 0, 2, 4);

        assertThat(WireCorners.count(document)).isZero();
    }

    @Test
    void twoWiresInLineAreNotACorner() {
        CircuitDocument document = new CircuitDocument();
        wire(document, 0, 0, 4, 0);
        wire(document, 4, 0, 8, 0);

        assertThat(WireCorners.count(document)).isZero();
    }

    @Test
    void anLWhoseCornerAnotherWireRunsThroughIsNotACorner() {
        CircuitDocument document = new CircuitDocument();
        wire(document, 0, 0, 4, 0);
        wire(document, 4, 0, 4, 4);
        wire(document, 2, 0, 6, 0);

        assertThat(WireCorners.count(document)).isZero();
    }

    @Test
    void thePlanCountsTheCornersBeforeAndAfter() {
        CircuitDocument document = new CircuitDocument();
        wire(document, 0, 0, 4, 0);
        wire(document, 4, 0, 4, 2);
        wire(document, 4, 2, 8, 2);
        Set<ElementId> scope = new LinkedHashSet<>();
        document.elements().forEach(element -> scope.add(element.id()));

        CircuitTidyPlan plan = CircuitTidy.plan(document, StandardComponentTypes.registry(), scope,
                CircuitTidyOptions.ALL, TextBoxes.NONE, () -> false, CircuitTidyProgress.NONE).orElseThrow();

        assertThat(plan.cornersBefore()).isEqualTo(2);
        assertThat(plan.cornersAfter()).isEqualTo(WireCorners.count(plan.after()));
    }

    /** The final review's minor 1: the corners belong to aligning and straightening, the summary's «codos: A → B». */
    @Test
    void withoutAligningOrStraighteningTheCornersAreNotCounted() {
        CircuitDocument document = new CircuitDocument();
        wire(document, 0, 0, 4, 0);
        wire(document, 4, 0, 4, 2);
        wire(document, 4, 2, 8, 2);
        Set<ElementId> scope = new LinkedHashSet<>();
        document.elements().forEach(element -> scope.add(element.id()));

        CircuitTidyPlan plan = CircuitTidy.plan(document, StandardComponentTypes.registry(), scope,
                CircuitTidyOptions.DEFAULTS, TextBoxes.NONE, () -> false, CircuitTidyProgress.NONE).orElseThrow();

        assertThat(plan.cornersBefore()).isZero();
        assertThat(plan.cornersAfter()).isZero();
    }
}
