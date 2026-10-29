package cl.almejo.vsim.ui.stepping;

import cl.almejo.vsim.core.simulation.ClockEdge;
import cl.almejo.vsim.core.simulation.StepReport;
import cl.almejo.vsim.core.simulation.StepResult;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.waveform.TimeAxis;

import java.util.Optional;
import java.util.OptionalLong;

/** The status label of «Paso a paso» (M6.1-B, spec §4.1). Pure. */
final class StepStatusText {

    private StepStatusText() {
    }

    /**
     * «t = 1 234 · próximo evento: t = 1 235»; «t = 2 000 (+1 000) · …» right after a step that jumped (the latest
     * step ended at {@code time} and moved time), «t = 40 (+10) · flanco de subida · …» right after an edge step; the
     * stable text when nothing is pending.
     */
    static String of(long time, Optional<StepReport> latestStep, OptionalLong nextEventTime) {
        if (nextEventTime.isEmpty()) {
            return Messages.text("step.stable");
        }
        String now = TimeAxis.format(time);
        String next = TimeAxis.format(nextEventTime.getAsLong());
        Optional<StepResult> justMade = latestStep.map(StepReport::result).filter(result -> result.time() == time);
        if (justMade.isPresent() && justMade.get().time() > justMade.get().fromTime()) {
            now += " (+" + TimeAxis.format(justMade.get().time() - justMade.get().fromTime()) + ")";
        }
        Optional<ClockEdge> edge = justMade.flatMap(StepResult::edge);
        return edge.isPresent()
                ? Messages.format("step.status.edge", now, edgeText(edge.get()), next)
                : Messages.format("step.status", now, next);
    }

    private static String edgeText(ClockEdge edge) {
        return switch (edge) {
            case RISING -> Messages.text("step.edge.rising");
            case FALLING -> Messages.text("step.edge.falling");
            case BOTH -> Messages.text("step.edge.both");
        };
    }
}
