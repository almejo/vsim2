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
}
