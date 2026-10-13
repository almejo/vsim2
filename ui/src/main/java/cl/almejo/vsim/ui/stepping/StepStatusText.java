package cl.almejo.vsim.ui.stepping;

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
     * step ended at {@code time} and moved time); the stable text when nothing is pending.
     */
    static String of(long time, Optional<StepReport> latestStep, OptionalLong nextEventTime) {
        if (nextEventTime.isEmpty()) {
            return Messages.text("step.stable");
        }
        String now = TimeAxis.format(time);
        if (latestStep.isPresent()) {
            StepResult result = latestStep.get().result();
            if (result.time() == time && result.time() > result.fromTime()) {
                now += " (+" + TimeAxis.format(result.time() - result.fromTime()) + ")";
            }
        }
        return Messages.format("step.status", now, TimeAxis.format(nextEventTime.getAsLong()));
    }
}
