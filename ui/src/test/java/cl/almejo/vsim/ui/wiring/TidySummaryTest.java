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

    /** The aligned parts are among the moved ones: said inside the moved clause, never added as a second count. */
    @Test
    void summaryCountsAlignedStraightenedAndCorners() {
        CircuitTidyPlan plan = planWith(6, 4, 12, 140, 96, 0);
        assertThat(TidySummary.preview(plan))
                .isEqualTo("Se moverán 6 partes (4 para alinear) y se enderezarán 12 cables; codos: 140 → 96");
        assertThat(TidySummary.notice(plan))
                .isEqualTo("Se movieron 6 partes (4 para alinear) y se enderezaron 12 cables; codos: 140 → 96");
    }

    @Test
    void singularFormsAndCornersOnlyWhenTheyDiffer() {
        assertThat(TidySummary.preview(planWith(1, 1, 1, 5, 5, 0)))
                .isEqualTo("Se moverá 1 parte (para alinear) y se enderezará 1 cable");
        assertThat(TidySummary.preview(planWith(3, 3, 0, 5, 5, 0)))
                .isEqualTo("Se moverán 3 partes (para alinear)");
        assertThat(TidySummary.preview(planWith(2, 0, 0, 9, 7, 3)))
                .isEqualTo("Se moverán 2 partes y se borrarán 3 puntas sueltas; codos: 9 → 7");
    }

    private static CircuitTidyPlan planWithTexts(int moved, int texts, int straightened, int detours,
                                                 int cornersBefore, int cornersAfter) {
        Wire wire = new Wire(new ElementId("w1"), new GridPoint(0, 0), new GridPoint(2, 0));
        TidyPlan wires = detours == 0 ? TidyPlan.EMPTY
                : new TidyPlan(List.of(wire), List.of(), Map.of(), 0, 0, 0, detours);
        return new CircuitTidyPlan(new CircuitDocument(), moved, texts, Set.of(), wires, 0, straightened,
                cornersBefore, cornersAfter);
    }

    /** The corners are not one more thing done: they go after a semicolon, never in the list with «y». */
    @Test
    void theCornersGoAfterASemicolon() {
        CircuitTidyPlan plan = planWithTexts(34, 0, 8, 1, 252, 235);
        assertThat(TidySummary.preview(plan))
                .isEqualTo("Se moverán 34 partes, se enderezarán 8 cables y se aplanará 1 vuelta; codos: 252 → 235");
        assertThat(TidySummary.preview(planWithTexts(3, 2, 0, 0, 9, 7)))
                .isEqualTo("Se moverán 3 partes y 2 textos; codos: 9 → 7");
        Messages.initialize(Locale.ENGLISH);
        try {
            assertThat(TidySummary.preview(plan)).isEqualTo("34 parts will be moved, 8 wires will be straightened and"
                    + " 1 detour will be flattened; corners: 252 → 235");
        } finally {
            Messages.initialize(Messages.SPANISH);
        }
    }

    /** After the corners, what could not be separated follows after another semicolon. */
    @Test
    void theNoticeSaysTheCornersBeforeWhatCouldNotBeSeparated() {
        CircuitTidyPlan plan = new CircuitTidyPlan(new CircuitDocument(), 2, 0, Set.of(new ElementId("p1")),
                TidyPlan.EMPTY, 0, 1, 9, 7);
        assertThat(TidySummary.notice(plan))
                .isEqualTo("Se movieron 2 partes y se enderezó 1 cable; codos: 9 → 7; no se pudo separar 1 cosa");
    }

    @Test
    void theAlignedPartsInEnglish() {
        Messages.initialize(Locale.ENGLISH);
        try {
            assertThat(TidySummary.preview(planWith(6, 4, 0, 5, 5, 0))).startsWith("6 parts (4 of them to align)");
            assertThat(TidySummary.preview(planWith(1, 1, 0, 5, 5, 0))).startsWith("1 part (to align)");
        } finally {
            Messages.initialize(Messages.SPANISH);
        }
    }
}
