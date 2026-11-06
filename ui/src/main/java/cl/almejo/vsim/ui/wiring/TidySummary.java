package cl.almejo.vsim.ui.wiring;

import cl.almejo.vsim.core.layout.CircuitTidyPlan;
import cl.almejo.vsim.ui.i18n.Messages;

import java.util.ArrayList;
import java.util.List;

/** The texts of a tidy plan: the live summary of the dialog (future) and the notice after tidying (past). */
public final class TidySummary {

    private TidySummary() {
    }

    /** «Se moverán 3 partes y 2 notas, se borrará 1 punta suelta», or «el circuito ya está ordenado». */
    public static String preview(CircuitTidyPlan plan) {
        return describe(plan, "tidy.preview", "tidy.preview.alreadyTidy");
    }

    /**
     * «Se movieron 3 partes y 2 notas; no se pudo separar 1 cosa», or «el circuito ya estaba ordenado». Only the
     * count of what could not be separated, no reasons.
     */
    public static String notice(CircuitTidyPlan plan) {
        String done = describe(plan, "tidy.notice", "tidy.notice.alreadyTidy");
        int notSeparated = plan.notSeparated().size();
        if (notSeparated == 0) {
            return done;
        }
        String unseparated = notSeparated == 1 ? Messages.text("tidy.notice.notSeparated.one")
                : Messages.format("tidy.notice.notSeparated.other", notSeparated);
        if (plan.isEmpty()) {
            return capitalized(unseparated);
        }
        return done + "; " + unseparated;
    }

    private static String describe(CircuitTidyPlan plan, String prefix, String alreadyTidyKey) {
        List<String> parts = new ArrayList<>();
        addMoved(parts, prefix + ".moved", plan.partsMoved(), plan.textsMoved());
        addPart(parts, prefix + ".stubs", plan.wires().stubsRemoved() + plan.wires().zeroLengthRemoved());
        addPart(parts, prefix + ".merged", plan.wires().segmentsMerged());
        addPart(parts, prefix + ".detours", plan.wires().detoursFlattened());
        if (plan.isEmpty() || parts.isEmpty()) {
            return Messages.text(alreadyTidyKey);
        }
        String joined = parts.size() == 1 ? parts.get(0)
                : String.join(", ", parts.subList(0, parts.size() - 1))
                        + Messages.text("tidy.join.last") + parts.get(parts.size() - 1);
        return capitalized(joined);
    }

    private static String capitalized(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /** «se moverán 3 partes y 2 notas»: one verb for the parts and the notes together. */
    private static void addMoved(List<String> parts, String keyPrefix, int partsMoved, int notesMoved) {
        List<String> nouns = new ArrayList<>();
        addNoun(nouns, "tidy.moved.part", partsMoved);
        addNoun(nouns, "tidy.moved.note", notesMoved);
        if (!nouns.isEmpty()) {
            String list = String.join(Messages.text("tidy.join.last"), nouns);
            parts.add(Messages.format(keyPrefix + (partsMoved + notesMoved == 1 ? ".one" : ".other"), list));
        }
    }

    private static void addNoun(List<String> nouns, String keyPrefix, int count) {
        if (count > 0) {
            nouns.add(count == 1 ? Messages.text(keyPrefix + ".one") : Messages.format(keyPrefix + ".other", count));
        }
    }

    private static void addPart(List<String> parts, String keyPrefix, int count) {
        if (count > 0) {
            parts.add(count == 1 ? Messages.text(keyPrefix + ".one") : Messages.format(keyPrefix + ".other", count));
        }
    }
}
