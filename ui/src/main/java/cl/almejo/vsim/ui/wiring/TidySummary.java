package cl.almejo.vsim.ui.wiring;

import cl.almejo.vsim.core.layout.CircuitTidyPlan;
import cl.almejo.vsim.ui.i18n.Messages;

import java.util.ArrayList;
import java.util.List;

/** The texts of a tidy plan: the live summary of the dialog (future) and the notice after tidying (past). */
public final class TidySummary {

    private TidySummary() {
    }

    /** «Se moverán 3 partes, 2 textos y se borrará 1 punta suelta», or «el circuito ya está ordenado». */
    public static String preview(CircuitTidyPlan plan) {
        return describe(plan, "tidy.preview", "tidy.preview.alreadyTidy");
    }

    /**
     * «Se movieron 3 partes y 2 textos; no se pudo separar 1 cosa», or «el circuito ya estaba ordenado». Only the
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
        List<String> wireClauses = new ArrayList<>();
        addPart(wireClauses, prefix + ".stubs", plan.wires().stubsRemoved() + plan.wires().zeroLengthRemoved());
        addPart(wireClauses, prefix + ".merged", plan.wires().segmentsMerged());
        addPart(wireClauses, prefix + ".detours", plan.wires().detoursFlattened());
        List<String> layoutClauses = new ArrayList<>();
        addPart(layoutClauses, prefix + ".aligned", plan.partsAligned());
        addPart(layoutClauses, prefix + ".straightened", plan.pairsStraightened());
        boolean cornersChange = plan.cornersBefore() != plan.cornersAfter();
        boolean moreFollow = !layoutClauses.isEmpty() || !wireClauses.isEmpty() || cornersChange;
        List<String> clauses = new ArrayList<>();
        addMoved(clauses, prefix + ".moved", plan.partsMoved(), plan.textsMoved(), moreFollow);
        clauses.addAll(layoutClauses);
        clauses.addAll(wireClauses);
        if (plan.isEmpty() || clauses.isEmpty()) {
            return Messages.text(alreadyTidyKey);
        }
        if (cornersChange) {
            clauses.add(Messages.format(prefix + ".corners", plan.cornersBefore(), plan.cornersAfter()));
        }
        return capitalized(listed(clauses));
    }

    /** «a», «a y b», «a, b y c»: «y» only before the last one. */
    private static String listed(List<String> items) {
        if (items.size() == 1) {
            return items.get(0);
        }
        return String.join(", ", items.subList(0, items.size() - 1)) + Messages.text("tidy.join.last")
                + items.get(items.size() - 1);
    }

    private static String capitalized(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /**
     * «se moverán 3 partes y 2 textos»: one verb for the parts and the texts (free texts and sticky notes) together.
     * When more clauses follow, the two are separated by a comma (the «y» goes before the last clause only).
     */
    private static void addMoved(List<String> clauses, String keyPrefix, int partsMoved, int textsMoved,
                                 boolean moreFollow) {
        List<String> nouns = new ArrayList<>();
        addNoun(nouns, "tidy.moved.part", partsMoved);
        addNoun(nouns, "tidy.moved.text", textsMoved);
        if (!nouns.isEmpty()) {
            String list = moreFollow ? String.join(", ", nouns) : listed(nouns);
            clauses.add(Messages.format(keyPrefix + (partsMoved + textsMoved == 1 ? ".one" : ".other"), list));
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
