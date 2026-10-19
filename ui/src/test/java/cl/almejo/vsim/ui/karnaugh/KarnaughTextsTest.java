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
                        + "X·Y' + X·Y = X·(Y' + Y) = X, porque Y' + Y = 1: la variable que cambia se va.",
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
                        + "(X + Y)·(X + Y') = X + Y·Y' = X, porque Y·Y' = 0: la variable que cambia se va.",
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
}
