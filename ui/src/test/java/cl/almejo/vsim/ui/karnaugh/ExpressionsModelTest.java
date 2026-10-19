package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.logic.ExpressionParser;
import cl.almejo.vsim.core.logic.Minimization;
import cl.almejo.vsim.core.logic.Minimizer;
import cl.almejo.vsim.core.logic.TruthTableSpec;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ExpressionsModelTest {

    private ExpressionsModel model;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        model = new ExpressionsModel(true);
    }

    @Test
    void itStartsWithOneEmptyLineNamedFAndTheDefaultTitle() {
        assertThat(model.title()).isEqualTo("Expresiones");
        assertThat(model.lines()).containsExactly("F = ");
        assertThat(model.lineProblem(0)).as("not before the user types").isEmpty();
        assertThat(model.spec()).isEmpty();
        assertThat(model.inputsText()).isEqualTo("Entradas: ninguna");
    }

    @Test
    void aLineProblemNamesItsPositionCountingFromOne() {
        model.setLine(0, "F = (A + B");
        assertThat(model.lineProblem(0)).contains("Falta cerrar un paréntesis (posición 5)");
        model.setLine(0, "F A");
        assertThat(model.lineProblem(0)).contains("Falta el «=»: escribe NOMBRE = expresión.");
    }

    @Test
    void validLinesGiveTheInputsInNaturalOrderAndTheTable() {
        model.setLine(0, "Suma = A ^ B ^ Cin");
        model.addLine();
        model.setLine(1, "Cout = A B + Cin (A ^ B)");

        assertThat(model.lineProblem(0)).isEmpty();
        assertThat(model.lineProblem(1)).isEmpty();
        assertThat(model.inputNames()).containsExactly("A", "B", "Cin");
        assertThat(model.inputsText()).isEqualTo("Entradas: A, B, Cin (3)");
        TruthTableSpec spec = model.spec().orElseThrow();
        assertThat(spec.title()).isEqualTo("Expresiones");
        assertThat(spec.outputNames()).containsExactly("Suma", "Cout");
        assertThat(model.expressionLines()).hasValueSatisfying(lines -> assertThat(lines).hasSize(2));
    }

    @Test
    void theInputsOfTheValidLinesAreListedEvenWhenAnotherLineIsWrong() {
        model.setLine(0, "F = B + A");
        model.addLine();
        model.setLine(1, "G = (C");
        assertThat(model.inputsText()).isEqualTo("Entradas: A, B (2)");
        assertThat(model.spec()).isEmpty();
        assertThat(model.tableProblems()).isEmpty();
    }

    @Test
    void tableProblemsAreListedOnceWhenEveryLineReads() {
        model.setLine(0, "F = A");
        model.addLine();
        model.setLine(1, "f = B");
        assertThat(model.tableProblems()).containsExactly("Hay más de una salida llamada «f».");
        assertThat(model.spec()).isEmpty();

        model.setLine(0, "F = A1");
        model.setLine(1, "G = A1 + A2 + A3 + A4 + A5 + A6 + A7 + A8 + A9");
        assertThat(model.tableProblems()).containsExactly("9 entradas son demasiadas: el máximo es 8.");
        assertThat(model.inputsText()).isEqualTo("Entradas: A1, A2, A3, A4, A5, A6, A7, A8, A9 (9)");
    }

    @Test
    void addedLinesTakeTheNextFreeNameUpToEightAndAtLeastOneStays() {
        model.setLine(0, "G = A");
        assertThat(model.addLine()).isTrue();
        assertThat(model.lines()).containsExactly("G = A", "F = ");
        while (model.canAddLine()) {
            model.addLine();
        }
        assertThat(model.lineCount()).isEqualTo(8);
        assertThat(model.addLine()).isFalse();
        assertThat(model.lines()).doesNotHaveDuplicates();

        for (int index = 0; index < 10; index++) {
            model.removeLine(0);
        }
        assertThat(model.lineCount()).isEqualTo(1);
        assertThat(model.canRemoveLine()).isFalse();
    }

    @Test
    void mergedLettersWarnUntilTheNameIsDismissed() {
        model.setLine(0, "F = AB + Cin");
        assertThat(model.mergedWarning(0)).contains("AB");
        assertThat(ExpressionsModel.mergedText("ABC")).isEqualTo("¿Quisiste decir A·B·C? Sepáralas con espacio o *");
        assertThat(model.spec()).isPresent();

        model.dismissMerged("AB");

        assertThat(model.mergedWarning(0)).isEmpty();
    }

    @Test
    void listenersHearEveryChange() {
        AtomicInteger heard = new AtomicInteger();
        model.addListener(heard::incrementAndGet);
        model.setLine(0, "F = A");
        model.setTitle("Otro");
        model.setSimplify(false);
        model.setOptions(model.options().withShareTerms(false));
        model.addLine();
        model.removeLine(1);
        assertThat(heard).hasValue(6);
    }

    @Test
    void aSumOfProductsWithTheMinimizedTermsIsAlreadySimplified() {
        assertThat(previewOf("F = A'B + C")).isEqualTo("ya está simplificada");
        assertThat(previewOf("F = C + B A'")).isEqualTo("ya está simplificada");
        assertThat(previewOf("F = A B + A B'")).isEqualTo("→ F = A");
        assertThat(previewOf("F = (A + B)'")).isEqualTo("→ F = A'B'");
        assertThat(previewOf("F = A ^ B")).isEqualTo("→ F = A'B + A·B'");
    }

    @Test
    void thePreviewSeparatesLettersSoItReadsBackInThisDialog() {
        assertThat(previewOf("F = A B C + A B C'")).isEqualTo("→ F = A·B");
        model.setOptions(model.options().withForm(Minimization.Form.PRODUCT_OF_SUMS));
        assertThat(previewOf("F = A B C + A B C'")).isEqualTo("→ F = B·A");
    }

    @Test
    void aRepeatedTermOrLiteralIsNotAlreadySimplified() {
        assertThat(previewOf("F = A + A")).isEqualTo("→ F = A");
        assertThat(previewOf("F = A B + B A")).isEqualTo("→ F = A·B");
        model.setOptions(model.options().withForm(Minimization.Form.PRODUCT_OF_SUMS));
        assertThat(previewOf("F = A A")).isEqualTo("→ F = A");
    }

    @Test
    void anEmptyLineShowsItsProblemOnlyOnceItWasEdited() {
        model.addLine();
        assertThat(model.lineProblem(1)).isEmpty();
        model.setLine(0, "F = A");
        model.setLine(0, "F = ");
        assertThat(model.lineProblem(0)).contains("Falta la expresión a la derecha del «=» (posición 4)");
        model.setLine(1, "");
        assertThat(model.lineProblem(1)).isPresent();
        model.removeLine(0);
        assertThat(model.lineProblem(0)).as("the edited line moved up").isPresent();
        model.addLine();
        assertThat(model.lineProblem(1)).isEmpty();
        assertThat(model.spec()).isEmpty();
    }

    @Test
    void aProductOfSumsIsComparedWithTheGroupsOfZeros() {
        model.setOptions(model.options().withForm(Minimization.Form.PRODUCT_OF_SUMS));
        assertThat(previewOf("F = (A + B)(A' + C)")).isEqualTo("ya está simplificada");
        assertThat(previewOf("F = A B")).isEqualTo("ya está simplificada");
    }

    @Test
    void theWizardTakesTwoToFourInputsAndUpToFourOutputs() {
        model.setLine(0, "F = A");
        assertThat(model.canOpenInWizard()).isFalse();
        model.setLine(0, "F = A B");
        assertThat(model.canOpenInWizard()).isTrue();
        model.setLine(0, "F = A B C D E");
        assertThat(model.canOpenInWizard()).isFalse();
        model.setLine(0, "F = (A");
        assertThat(model.canOpenInWizard()).isFalse();
    }

    @Test
    void aBlankTitleUsesTheDefaultAndContentsTrackTitleAndLines() {
        ExpressionsModel.Contents start = model.contents();
        model.setLine(0, "F = A");
        model.setTitle("  ");
        assertThat(model.spec().orElseThrow().title()).isEqualTo("Expresiones");
        assertThat(model.contents()).isNotEqualTo(start);
        model.setLine(0, "F = ");
        model.setTitle("Expresiones");
        assertThat(model.contents()).isEqualTo(start);
    }

    private String previewOf(String line) {
        model.setLine(0, line);
        TruthTableSpec spec = model.spec().orElseThrow();
        Minimization minimization = Minimizer.minimize(spec, model.options().form());
        return ExpressionsModel.previewText(ExpressionParser.parseLine(line).line().orElseThrow(), minimization, 0);
    }
}
