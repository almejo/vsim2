package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.logic.GroupExplanation;
import cl.almejo.vsim.core.logic.LogicValue;
import cl.almejo.vsim.core.logic.Minimization;
import cl.almejo.vsim.core.logic.Minimizer;
import cl.almejo.vsim.core.logic.Term;
import cl.almejo.vsim.core.logic.TruthTableSpec;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class KarnaughTextsTest {

    private final TruthTableSpec bcd = KarnaughModelTest.bcd();

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
    }

    @Test
    void theGroupOfTheTalkSaysWhichVariablesStayAndWhichGo() {
        GroupExplanation explanation = GroupExplanation.of(new Term(0b0110, 0b0010), bcd);
        assertThat(KarnaughTexts.variablesLine(explanation))
                .isEqualTo("A cambia → se va · B = 0 → B' · C = 1 → C · D cambia → se va");
        assertThat(KarnaughTexts.sizeLine(explanation)).isEqualTo("4 celdas: quedan 2 variables");
    }

    @Test
    void theSizeLineSpeaksOfOneCellAndOfNoVariableLeft() {
        GroupExplanation single = GroupExplanation.of(new Term(0b1111, 0b0010), bcd);
        assertThat(KarnaughTexts.sizeLine(single)).isEqualTo("1 celda: quedan las 4 variables");
        GroupExplanation whole = GroupExplanation.of(new Term(0, 0), bcd);
        assertThat(KarnaughTexts.sizeLine(whole)).isEqualTo("16 celdas: no queda ninguna variable");
        GroupExplanation eight = GroupExplanation.of(new Term(0b1000, 0b1000), bcd);
        assertThat(KarnaughTexts.sizeLine(eight)).isEqualTo("8 celdas: queda 1 variable");
    }

    @Test
    void whyAppliesTheRuleStepByStepOnTheCells() {
        GroupExplanation explanation = GroupExplanation.of(new Term(0b0110, 0b0010), bcd);
        assertThat(KarnaughTexts.algebra(explanation, bcd.inputNames())).containsExactly(
                "Cada celda es un producto con todas las variables. Sumamos las celdas del grupo y usamos "
                        + "P·Q' + P·Q = P·(Q' + Q) = P, porque Q' + Q = 1: la variable que cambia se va.",
                "Celdas: A'B'CD' + A'B'CD + AB'CD' + AB'CD",
                "Se va A:",
                "    A'B'CD' + AB'CD' = B'CD'·(A' + A) = B'CD'",
                "    A'B'CD + AB'CD = B'CD·(A' + A) = B'CD",
                "Se va D:",
                "    B'CD' + B'CD = B'C·(D' + D) = B'C",
                "Queda B'C.");
    }

    @Test
    void whyOnASingleCellSaysThereIsNothingToSimplify() {
        GroupExplanation single = GroupExplanation.of(new Term(0b1111, 0b0010), bcd);
        assertThat(KarnaughTexts.algebra(single, bcd.inputNames())).containsExactly(
                "Una sola celda: no hay nada que simplificar; el término lleva todas las variables.",
                "Queda A'B'CD'.");
    }

    @Test
    void whyInAProductOfSumsUsesTheDualRule() {
        TruthTableSpec spec = TruthTableSpec.zeros("T", List.of("A", "B"), List.of("F"));
        // F = 0 when B = 0: the sum (B).
        GroupExplanation explanation = GroupExplanation.of(new Term(0b01, 0b00), spec,
                Minimization.Form.PRODUCT_OF_SUMS);
        assertThat(KarnaughTexts.variablesLine(explanation)).isEqualTo("A cambia → se va · B = 0 → B");
        assertThat(KarnaughTexts.algebra(explanation, spec.inputNames())).containsExactly(
                "Cada celda de 0 es una suma con todas las variables. Multiplicamos las sumas del grupo y usamos "
                        + "(P + Q)·(P + Q') = P + Q·Q' = P, porque Q·Q' = 0: la variable que cambia se va.",
                "Celdas: (A + B)·(A' + B)",
                "Se va A:",
                "    (A + B)·(A' + B) = B + A·A' = B",
                "Queda B.");
    }

    @Test
    void constantOutputsAreExplained() {
        TruthTableSpec spec = new TruthTableSpec("T", List.of("A", "B"), List.of("F", "G"),
                List.of(Collections.nCopies(4, LogicValue.ZERO), Collections.nCopies(4, LogicValue.ONE)));
        Minimization sum = Minimizer.minimize(spec, Minimization.Form.SUM_OF_PRODUCTS);
        assertThat(KarnaughTexts.specialCase(sum, 0)).contains("F = 0: no hay unos que agrupar.");
        assertThat(KarnaughTexts.specialCase(sum, 1)).contains("G = 1: todo el mapa es un solo grupo.");
        Minimization product = Minimizer.minimize(spec, Minimization.Form.PRODUCT_OF_SUMS);
        assertThat(KarnaughTexts.specialCase(product, 0)).contains("F = 0: todo el mapa es un solo grupo de ceros.");
        assertThat(KarnaughTexts.specialCase(product, 1)).contains("G = 1: no hay ceros que agrupar.");
        Minimization bcdSum = Minimizer.minimize(bcd, Minimization.Form.SUM_OF_PRODUCTS);
        assertThat(KarnaughTexts.specialCase(bcdSum, 0)).isEmpty();
    }

    @Test
    void theRulesTalkAboutOnesOrZerosByForm() {
        assertThat(KarnaughTexts.rules(Minimization.Form.SUM_OF_PRODUCTS)).first().asString()
                .startsWith("Se agrupan solo los 1 (y las X, si ayudan a hacer un grupo más grande); nunca un 0.");
        assertThat(KarnaughTexts.rules(Minimization.Form.PRODUCT_OF_SUMS)).first().asString()
                .startsWith("Se agrupan solo los 0");
        assertThat(KarnaughTexts.rules(Minimization.Form.SUM_OF_PRODUCTS)).hasSize(6);
    }

    @Test
    void whySaysWhichCellsAreXAndThatTheyAreTakenAsOne() {
        // BCD: rows 10 to 15 are X; the group BD (rows 5, 7, 13, 15) uses two of them.
        GroupExplanation explanation = GroupExplanation.of(new Term(0b0101, 0b0101), bcd,
                Minimization.Form.SUM_OF_PRODUCTS, 0);
        assertThat(KarnaughTexts.algebra(explanation, bcd.inputNames())).element(2).isEqualTo(
                "Las celdas 13 y 15 son X («no importa»): aquí las tomamos como 1 para agrandar el grupo.");
        assertThat(KarnaughTexts.algebra(explanation, bcd.inputNames())).element(1)
                .isEqualTo("Celdas: A'BC'D + A'BCD + ABC'D + ABCD");
    }

    @Test
    void oneXCellOrThreeAreSaidNaturallyAndAsZeroInAProductOfSums() {
        TruthTableSpec spec = TruthTableSpec.zeros("T", List.of("A", "B", "C"), List.of("F"))
                .withValue(0, 0, LogicValue.ONE).withValue(0, 1, LogicValue.DONT_CARE)
                .withValue(0, 3, LogicValue.DONT_CARE).withValue(0, 5, LogicValue.DONT_CARE)
                .withValue(0, 2, LogicValue.ONE).withValue(0, 4, LogicValue.ONE).withValue(0, 6, LogicValue.ONE);
        // Zeros only on row 7; as a product of sums, the group of C = 1 (rows 1, 3, 5, 7) uses three X.
        GroupExplanation zeros = GroupExplanation.of(new Term(0b001, 0b001), spec,
                Minimization.Form.PRODUCT_OF_SUMS, 0);
        assertThat(KarnaughTexts.algebra(zeros, spec.inputNames())).element(2).isEqualTo(
                "Las celdas 1, 3 y 5 son X («no importa»): aquí las tomamos como 0 para agrandar el grupo.");
        // The group A'C' (rows 0 and 2) uses none; A'B' (rows 0, 1) uses one.
        GroupExplanation one = GroupExplanation.of(new Term(0b110, 0b000), spec,
                Minimization.Form.SUM_OF_PRODUCTS, 0);
        assertThat(KarnaughTexts.algebra(one, spec.inputNames())).element(2).isEqualTo(
                "La celda 1 es X («no importa»): aquí la tomamos como 1 para agrandar el grupo.");
        GroupExplanation none = GroupExplanation.of(new Term(0b101, 0b000), spec,
                Minimization.Form.SUM_OF_PRODUCTS, 0);
        assertThat(KarnaughTexts.algebra(none, spec.inputNames())).element(2).asString().startsWith("Se va");

        Messages.initialize(Locale.ENGLISH);
        assertThat(KarnaughTexts.algebra(zeros, spec.inputNames())).element(2).isEqualTo(
                "Cells 1, 3 and 5 are X (“don’t care”): here we take them as 0 to make the group bigger.");
        assertThat(KarnaughTexts.algebra(one, spec.inputNames())).element(2).isEqualTo(
                "Cell 1 is X (“don’t care”): here we take it as 1 to make the group bigger.");
    }
}
