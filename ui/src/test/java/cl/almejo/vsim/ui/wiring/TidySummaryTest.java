package cl.almejo.vsim.ui.wiring;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.wiring.TidyPlan;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TidySummaryTest {

    private static TidyPlan plan(int stubs, int merged, int detours) {
        Wire wire = new Wire(new ElementId("w1"), new GridPoint(0, 0), new GridPoint(2, 0));
        return new TidyPlan(List.of(wire), List.of(), Map.of(), 0, stubs, merged, detours);
    }

    @Test
    void spanishFuturePluralsAndSingulars() {
        Messages.initialize(Messages.SPANISH);
        assertThat(TidySummary.preview(plan(3, 2, 1)))
                .isEqualTo("Se borrarán 3 puntas sueltas, se unirán 2 tramos y se aplanará 1 vuelta");
        assertThat(TidySummary.preview(plan(1, 0, 0))).isEqualTo("Se borrará 1 punta suelta");
        assertThat(TidySummary.preview(plan(0, 1, 2))).isEqualTo("Se unirá 1 tramo y se aplanarán 2 vueltas");
        assertThat(TidySummary.preview(TidyPlan.EMPTY)).isEqualTo("Los cables ya están ordenados");
    }

    @Test
    void spanishPast() {
        Messages.initialize(Messages.SPANISH);
        assertThat(TidySummary.notice(plan(3, 2, 0))).isEqualTo("Se borraron 3 puntas sueltas y se unieron 2 tramos");
        assertThat(TidySummary.notice(TidyPlan.EMPTY)).isEqualTo("Los cables ya estaban ordenados");
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
