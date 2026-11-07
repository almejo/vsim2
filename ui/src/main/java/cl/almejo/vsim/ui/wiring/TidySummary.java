package cl.almejo.vsim.ui.wiring;

import cl.almejo.vsim.core.layout.CircuitTidyPlan;
import cl.almejo.vsim.ui.i18n.Messages;

import java.util.ArrayList;
import java.util.List;

/** The texts of a tidy plan: the live summary of the dialog (future) and the notice after tidying (past). */
public final class TidySummary {

    private TidySummary() {
    }

    /**
     * «Se moverán 3 partes, 2 textos y se borrará 1 punta suelta», «…y se aplanará 1 vuelta; codos: 252 → 235», or «el
     * circuito ya está ordenado».
     */
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
        addPart(layoutClauses, prefix + ".straightened", plan.pairsStraightened());
        boolean moreFollow = !layoutClauses.isEmpty() || !wireClauses.isEmpty();
        List<String> clauses = new ArrayList<>();
        addMoved(clauses, prefix + ".moved", plan, moreFollow);
        clauses.addAll(layoutClauses);
        clauses.addAll(wireClauses);
        if (plan.isEmpty() || clauses.isEmpty()) {
            return Messages.text(alreadyTidyKey);
        }
        String done = capitalized(listed(clauses));
        if (plan.cornersBefore() == plan.cornersAfter()) {
            return done;
        }
        // the corners are not one more thing done: they follow the list after a semicolon
        return done + "; " + Messages.format(prefix + ".corners", plan.cornersBefore(), plan.cornersAfter());
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
     * When more clauses follow, the two are separated by a comma (the «y» goes before the last clause only). The parts
     * moved to align a column are among the moved ones, so they are said in brackets after them, never counted twice:
     * «6 partes (4 para alinear)», «3 partes (para alinear)» when they all were.
     */
    private static void addMoved(List<String> clauses, String keyPrefix, CircuitTidyPlan plan, boolean moreFollow) {
        int partsMoved = plan.partsMoved();
        int textsMoved = plan.textsMoved();
        List<String> nouns = new ArrayList<>();
        addNoun(nouns, "tidy.moved.part", partsMoved);
        if (partsMoved > 0 && plan.partsAligned() > 0) {
            nouns.set(0, nouns.get(0) + alignedAmongThem(plan.partsAligned(), partsMoved));
        }
        addNoun(nouns, "tidy.moved.text", textsMoved);
        if (!nouns.isEmpty()) {
            String list = moreFollow ? String.join(", ", nouns) : listed(nouns);
            clauses.add(Messages.format(keyPrefix + (partsMoved + textsMoved == 1 ? ".one" : ".other"), list));
        }
    }

    /** « (para alinear)» when every moved part was aligned, else « (4 para alinear)». */
    private static String alignedAmongThem(int partsAligned, int partsMoved) {
        return partsAligned >= partsMoved ? Messages.text("tidy.moved.part.allAligned")
                : Messages.format("tidy.moved.part.someAligned", partsAligned);
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
