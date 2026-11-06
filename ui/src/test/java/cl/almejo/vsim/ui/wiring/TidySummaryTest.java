package cl.almejo.vsim.ui.wiring;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.layout.CircuitTidyPlan;
import cl.almejo.vsim.core.wiring.TidyPlan;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TidySummaryTest {

    private static CircuitTidyPlan plan(int stubs, int merged, int detours) {
        return plan(0, 0, 0, stubs, merged, detours);
    }

    private static CircuitTidyPlan plan(int parts, int notes, int notSeparated, int stubs, int merged, int detours) {
        Wire wire = new Wire(new ElementId("w1"), new GridPoint(0, 0), new GridPoint(2, 0));
        TidyPlan wires = stubs + merged + detours == 0 ? TidyPlan.EMPTY
                : new TidyPlan(List.of(wire), List.of(), Map.of(), 0, stubs, merged, detours);
        Set<ElementId> unseparated = new java.util.LinkedHashSet<>();
        for (int index = 0; index < notSeparated; index++) {
            unseparated.add(new ElementId("p" + index));
        }
        return new CircuitTidyPlan(new CircuitDocument(), parts, notes, unseparated, wires);
    }

    private static final CircuitTidyPlan EMPTY = plan(0, 0, 0, 0, 0, 0);

    @Test
    void summaryCountsMovedPartsAndTexts() {
        assertThat(TidySummary.preview(plan(3, 2, 0, 0, 0, 0))).isEqualTo("Se moverán 3 partes y 2 textos");
        assertThat(TidySummary.preview(plan(1, 1, 0, 0, 0, 0))).isEqualTo("Se moverán 1 parte y 1 texto");
        assertThat(TidySummary.notice(plan(3, 0, 0, 0, 2, 0))).isEqualTo("Se movieron 3 partes y se unieron 2 tramos");
    }

    @Test
    void onlyTheLastItemOfTheSummaryTakesAnY() {
        assertThat(TidySummary.preview(plan(1, 1, 0, 1, 0, 0)))
                .isEqualTo("Se moverán 1 parte, 1 texto y se borrará 1 punta suelta");
        assertThat(TidySummary.notice(plan(2, 3, 0, 1, 2, 0)))
                .isEqualTo("Se movieron 2 partes, 3 textos, se borró 1 punta suelta y se unieron 2 tramos");
    }

    @Test
    void noticeCountsWhatCouldNotBeSeparated() {
        assertThat(TidySummary.notice(plan(1, 0, 1, 0, 0, 0)))
                .isEqualTo("Se movió 1 parte; no se pudo separar 1 cosa");
        assertThat(TidySummary.notice(plan(2, 0, 3, 0, 0, 0)))
                .isEqualTo("Se movieron 2 partes; no se pudieron separar 3 cosas");
        assertThat(TidySummary.notice(plan(0, 0, 2, 0, 0, 0))).isEqualTo("No se pudieron separar 2 cosas");
        assertThat(TidySummary.preview(plan(0, 0, 2, 0, 0, 0))).isEqualTo("El circuito ya está ordenado");
    }

    @Test
    void spanishFuturePluralsAndSingulars() {
        assertThat(TidySummary.preview(plan(3, 2, 1)))
                .isEqualTo("Se borrarán 3 puntas sueltas, se unirán 2 tramos y se aplanará 1 vuelta");
        assertThat(TidySummary.preview(plan(1, 0, 0))).isEqualTo("Se borrará 1 punta suelta");
        assertThat(TidySummary.preview(plan(0, 1, 2))).isEqualTo("Se unirá 1 tramo y se aplanarán 2 vueltas");
        assertThat(TidySummary.preview(EMPTY)).isEqualTo("El circuito ya está ordenado");
    }

    @Test
    void spanishPast() {
        assertThat(TidySummary.notice(plan(3, 2, 0))).isEqualTo("Se borraron 3 puntas sueltas y se unieron 2 tramos");
        assertThat(TidySummary.notice(EMPTY)).isEqualTo("El circuito ya estaba ordenado");
    }

    @Test
    void english() {
        Messages.initialize(Locale.ENGLISH);
        try {
            assertThat(TidySummary.preview(plan(3, 2, 1)))
                    .isEqualTo("3 loose ends will be deleted, 2 segments will be merged and 1 detour will be flattened");
            assertThat(TidySummary.notice(plan(1, 0, 0))).isEqualTo("1 loose end was deleted");
        } finally {
            Messages.initialize(Messages.SPANISH);
        }
    }

    private static CircuitTidyPlan planWith(int moved, int aligned, int straightened, int cornersBefore,
                                            int cornersAfter, int stubs) {
        Wire wire = new Wire(new ElementId("w1"), new GridPoint(0, 0), new GridPoint(2, 0));
        TidyPlan wires = stubs == 0 ? TidyPlan.EMPTY
                : new TidyPlan(List.of(wire), List.of(), Map.of(), 0, stubs, 0, 0);
        return new CircuitTidyPlan(new CircuitDocument(), moved, 0, Set.of(), wires, aligned, straightened,
                cornersBefore, cornersAfter);
    }

    @Test
    void summaryCountsAlignedStraightenedAndCorners() {
        CircuitTidyPlan plan = planWith(6, 4, 12, 140, 96, 0);
        assertThat(TidySummary.preview(plan))
                .isEqualTo("Se moverán 6 partes, se alinearán 4 partes, se enderezarán 12 cables y codos: 140 → 96");
        assertThat(TidySummary.notice(plan))
                .isEqualTo("Se movieron 6 partes, se alinearon 4 partes, se enderezaron 12 cables y codos: 140 → 96");
    }

    @Test
    void singularFormsAndCornersOnlyWhenTheyDiffer() {
        assertThat(TidySummary.preview(planWith(1, 1, 1, 5, 5, 0)))
                .isEqualTo("Se moverá 1 parte, se alineará 1 parte y se enderezará 1 cable");
        assertThat(TidySummary.preview(planWith(2, 0, 0, 9, 7, 3)))
                .isEqualTo("Se moverán 2 partes, se borrarán 3 puntas sueltas y codos: 9 → 7");
    }
}
