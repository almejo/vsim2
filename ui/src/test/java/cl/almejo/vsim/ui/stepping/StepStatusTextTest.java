package cl.almejo.vsim.ui.stepping;

import cl.almejo.vsim.core.simulation.ClockEdge;
import cl.almejo.vsim.core.simulation.StepReport;
import cl.almejo.vsim.core.simulation.StepResult;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StepStatusTextTest {

    @AfterEach
    void restoreSpanish() {
        Messages.initialize(Messages.SPANISH);
    }

    private static Optional<StepReport> step(long fromTime, long time) {
        return Optional.of(new StepReport(1, new StepResult(fromTime, time, Set.of("n1"), OptionalLong.of(time + 1))));
    }

    @Test
    void theCurrentAndTheNextEventTimeWithSpacedThousands() {
        assertThat(StepStatusText.of(1_234, Optional.empty(), OptionalLong.of(1_235)))
                .isEqualTo("t = 1 234 · próximo evento: t = 1 235");
    }

    @Test
    void rightAfterAStepThatMovedTimeTheJumpIsShown() {
        assertThat(StepStatusText.of(2_000, step(1_000, 2_000), OptionalLong.of(3_000)))
                .isEqualTo("t = 2 000 (+1 000) · próximo evento: t = 3 000");
        assertThat(StepStatusText.of(2_000, step(2_000, 2_000), OptionalLong.of(2_001)))
                .as("a click while paused does not move time").isEqualTo("t = 2 000 · próximo evento: t = 2 001");
        assertThat(StepStatusText.of(2_010, step(1_000, 2_000), OptionalLong.of(3_000)))
                .as("after Paso the old jump is gone").isEqualTo("t = 2 010 · próximo evento: t = 3 000");
    }

    private static Optional<StepReport> edgeStep(long fromTime, long time, ClockEdge edge) {
        return Optional.of(new StepReport(1, new StepResult(fromTime, time, Set.of("clock"),
                OptionalLong.of(time + 2), Optional.of(edge))));
    }

    @Test
    void rightAfterAnEdgeStepTheEdgeIsNamed() {
        assertThat(StepStatusText.of(40, edgeStep(30, 40, ClockEdge.RISING), OptionalLong.of(45)))
                .isEqualTo("t = 40 (+10) · flanco de subida · próximo evento: t = 45");
        assertThat(StepStatusText.of(40, edgeStep(30, 40, ClockEdge.FALLING), OptionalLong.of(45)))
                .isEqualTo("t = 40 (+10) · flanco de bajada · próximo evento: t = 45");
        assertThat(StepStatusText.of(40, edgeStep(30, 40, ClockEdge.BOTH), OptionalLong.of(45)))
                .isEqualTo("t = 40 (+10) · flanco de subida y de bajada · próximo evento: t = 45");
        assertThat(StepStatusText.of(45, edgeStep(30, 40, ClockEdge.RISING), OptionalLong.of(50)))
                .as("after the next step the edge is gone").isEqualTo("t = 45 · próximo evento: t = 50");
        Messages.initialize(Locale.ENGLISH);
        assertThat(StepStatusText.of(40, edgeStep(30, 40, ClockEdge.BOTH), OptionalLong.of(45)))
                .isEqualTo("t = 40 (+10) · rising and falling edge · next event: t = 45");
    }

    @Test
    void withNothingPendingTheCircuitIsStable() {
        assertThat(StepStatusText.of(7, step(6, 7), OptionalLong.empty()))
                .isEqualTo("Sin eventos pendientes: el circuito está estable");
        Messages.initialize(Locale.ENGLISH);
        assertThat(StepStatusText.of(7, Optional.empty(), OptionalLong.of(8))).isEqualTo("t = 7 · next event: t = 8");
    }
}
