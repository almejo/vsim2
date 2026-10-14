package cl.almejo.vsim.ui.wiring;

import cl.almejo.vsim.core.wiring.TidyPlan;
import cl.almejo.vsim.ui.i18n.Messages;

import java.util.ArrayList;
import java.util.List;

/** The texts of a tidy plan: the live summary of the dialog (future) and the notice after tidying (past). */
public final class TidySummary {

    private TidySummary() {
    }

    /** «Se borrarán 3 puntas sueltas, se unirán 2 tramos y se aplanará 1 vuelta», or «ya están ordenados». */
    public static String preview(TidyPlan plan) {
        return describe(plan, "tidy.preview", "tidy.preview.alreadyTidy");
    }

    /** «Se borraron 3 puntas sueltas y se unieron 2 tramos», or «ya estaban ordenados». */
    public static String notice(TidyPlan plan) {
        return describe(plan, "tidy.notice", "tidy.notice.alreadyTidy");
    }

    private static String describe(TidyPlan plan, String prefix, String alreadyTidyKey) {
        List<String> parts = new ArrayList<>();
        addPart(parts, prefix + ".stubs", plan.stubsRemoved() + plan.zeroLengthRemoved());
        addPart(parts, prefix + ".merged", plan.segmentsMerged());
        addPart(parts, prefix + ".detours", plan.detoursFlattened());
        if (plan.isEmpty() || parts.isEmpty()) {
            return Messages.text(alreadyTidyKey);
        }
        String joined = parts.size() == 1 ? parts.get(0)
                : String.join(", ", parts.subList(0, parts.size() - 1))
                        + Messages.text("tidy.join.last") + parts.get(parts.size() - 1);
        return Character.toUpperCase(joined.charAt(0)) + joined.substring(1);
    }

    private static void addPart(List<String> parts, String keyPrefix, int count) {
        if (count > 0) {
            parts.add(count == 1 ? Messages.text(keyPrefix + ".one") : Messages.format(keyPrefix + ".other", count));
        }
    }
}
