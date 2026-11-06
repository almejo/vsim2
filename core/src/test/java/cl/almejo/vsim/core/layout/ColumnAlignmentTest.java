package cl.almejo.vsim.core.layout;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.wiring.TidyOptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static cl.almejo.vsim.core.DocumentFixtures.frame;
import static cl.almejo.vsim.core.DocumentFixtures.gate;
import static cl.almejo.vsim.core.DocumentFixtures.place;
import static org.assertj.core.api.Assertions.assertThat;

class ColumnAlignmentTest {

    private static final CircuitTidyOptions ALIGN_ONLY = new CircuitTidyOptions(false, true, false,
            TidyOptions.DEFAULTS);

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();

    private static Set<ElementId> everything(CircuitDocument document) {
        Set<ElementId> ids = new LinkedHashSet<>();
        document.elements().forEach(element -> ids.add(element.id()));
        return ids;
    }

    private CircuitTidyPlan align(CircuitDocument document, Set<ElementId> scope) {
        return CircuitTidy.plan(document, registry, scope, ALIGN_ONLY, TextBoxes.NONE, () -> false,
                CircuitTidyProgress.NONE).orElseThrow();
    }

    private CircuitTidyPlan align(CircuitDocument document) {
        return align(document, everything(document));
    }

    private static GridPoint positionOf(CircuitTidyPlan plan, Component component) {
        return plan.after().component(component.id()).orElseThrow().position();
    }

    @Test
    void twoGatesWithLeftEdgesOneUnitApartShareTheEdgeTheMajorityHas() {
        CircuitDocument document = new CircuitDocument();
        Component top = gate(registry, document, 0, 0);
        Component middle = gate(registry, document, 1, 8);
        Component bottom = gate(registry, document, 1, 16);

        CircuitTidyPlan plan = align(document);

        assertThat(positionOf(plan, top)).isEqualTo(new GridPoint(1, 0));
        assertThat(positionOf(plan, middle)).isEqualTo(middle.position());
        assertThat(positionOf(plan, bottom)).isEqualTo(bottom.position());
        assertThat(plan.partsAligned()).isEqualTo(1);
    }

    /** Free texts 6 x 2 from their position; parts have no label boxes. */
    private static final TextBoxes NOTES = new TextBoxes() {
        @Override
        public Optional<Bounds> componentLabel(Component component) {
            return Optional.empty();
        }

        @Override
        public Bounds freeText(TextLabel label) {
            return new Bounds(label.position().x(), label.position().y(), 6, 2);
        }
    };

    /** The final review's I2: the texts pass belongs to «Separar lo que se tapa», even when aligning moved a part. */
    @Test
    void withSeparatingOffAlignmentLeavesCoveredTextsAlone() {
        CircuitDocument document = new CircuitDocument();
        Component top = gate(registry, document, 0, 0);
        gate(registry, document, 1, 8);
        gate(registry, document, 1, 16);
        TextLabel note = new TextLabel(document.newLabelId(), new GridPoint(2, 17), "nota");
        document.add(note);

        CircuitTidyPlan plan = CircuitTidy.plan(document, registry, everything(document), ALIGN_ONLY, NOTES,
                () -> false, CircuitTidyProgress.NONE).orElseThrow();

        assertThat(positionOf(plan, top)).isEqualTo(new GridPoint(1, 0));
        assertThat(plan.after().label(note.id()).orElseThrow()).isEqualTo(note);
        assertThat(plan.textsMoved()).isZero();
    }

    @Test
    void onATieTheSmallestEdgeWins() {
        CircuitDocument document = new CircuitDocument();
        Component left = gate(registry, document, 3, 0);
        Component right = gate(registry, document, 5, 8);

        CircuitTidyPlan plan = align(document);

        assertThat(positionOf(plan, left)).isEqualTo(left.position());
        assertThat(positionOf(plan, right)).isEqualTo(new GridPoint(3, 8));
    }

    @Test
    void aThreeUnitDifferenceIsNotAColumn() {
        CircuitDocument document = new CircuitDocument();
        Component top = gate(registry, document, 0, 0);
        Component bottom = gate(registry, document, 3, 8);

        CircuitTidyPlan plan = align(document);

        assertThat(positionOf(plan, top)).isEqualTo(top.position());
        assertThat(positionOf(plan, bottom)).isEqualTo(bottom.position());
        assertThat(plan.partsAligned()).isZero();
    }

    @Test
    void partsSideBySideInARowNeverAlign() {
        CircuitDocument document = new CircuitDocument();
        Component left = place(registry, document, StandardComponentTypes.CLOCK, 0, 0, "");
        Component right = place(registry, document, StandardComponentTypes.CLOCK, 2, 0, "");
        Component below = place(registry, document, StandardComponentTypes.CLOCK, 1, 4, "");

        CircuitTidyPlan plan = align(document);

        assertThat(positionOf(plan, left)).isEqualTo(left.position());
        assertThat(positionOf(plan, right)).isEqualTo(right.position());
        assertThat(positionOf(plan, below)).isEqualTo(below.position());
        assertThat(plan.partsAligned()).isZero();
    }

    @Test
    void alignmentNeverMovesOntoANeighbour() {
        CircuitDocument document = new CircuitDocument();
        gate(registry, document, 0, 0);
        Component blocked = gate(registry, document, 2, 6);
        gate(registry, document, -3, 6);

        CircuitTidyPlan plan = align(document);

        assertThat(positionOf(plan, blocked)).isEqualTo(blocked.position());
        assertThat(plan.partsAligned()).isZero();
    }

    @Test
    void alignmentNeverCrossesAFrameBorder() {
        CircuitDocument document = new CircuitDocument();
        Frame border = frame(document, 1, 0, 12, 10);
        Component inside = gate(registry, document, 1, 1);
        Component outside = gate(registry, document, 0, 14);

        CircuitTidyPlan plan = align(document);

        assertThat(positionOf(plan, inside)).isEqualTo(inside.position());
        assertThat(positionOf(plan, outside)).isEqualTo(outside.position());
        assertThat(border.encloses(1, 1, 5, 5)).isTrue();
    }

    @Test
    void onlySelectedPartsMove() {
        CircuitDocument document = new CircuitDocument();
        Component fixedTop = gate(registry, document, 0, 0);
        Component selected = gate(registry, document, 2, 8);

        CircuitTidyPlan plan = align(document, Set.of(selected.id()));

        assertThat(positionOf(plan, fixedTop)).isEqualTo(fixedTop.position());
        assertThat(positionOf(plan, selected)).isEqualTo(new GridPoint(0, 8));
    }

    @Test
    void theAligningStageIsReported() {
        CircuitDocument document = new CircuitDocument();
        gate(registry, document, 0, 0);
        gate(registry, document, 1, 8);
        List<String> reports = new ArrayList<>();

        CircuitTidy.plan(document, registry, everything(document), ALIGN_ONLY, TextBoxes.NONE, () -> false,
                (stage, done, total) -> reports.add(stage + " " + done + "/" + total)).orElseThrow();

        assertThat(reports).contains("ALIGNING 1/1");
    }
}
