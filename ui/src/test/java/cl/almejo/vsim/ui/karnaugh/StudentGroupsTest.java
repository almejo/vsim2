package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.logic.DrawnGroup;
import cl.almejo.vsim.core.logic.KarnaughGroup;
import cl.almejo.vsim.core.logic.KarnaughLayout;
import cl.almejo.vsim.core.logic.LogicValue;
import cl.almejo.vsim.core.logic.Minimization;
import cl.almejo.vsim.core.logic.TableCircuits;
import cl.almejo.vsim.core.logic.Term;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static cl.almejo.vsim.core.logic.Minimization.Form.PRODUCT_OF_SUMS;
import static cl.almejo.vsim.core.logic.Minimization.Form.SUM_OF_PRODUCTS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 4 of the practice wizard (spec §2): the student's expression until «Revisar» or «Me rindo» reveals VSim's, and
 * «Usar mis grupos», with which steps 4 and 5 build from the student's groups when they cover the table.
 */
class StudentGroupsTest {

    private static final long A = 0b100;
    private static final long B = 0b010;
    private static final long C = 0b001;

    private final FakeCreationTarget target = new FakeCreationTarget();
    private KarnaughModel model;
    private KarnaughWizard wizard;
    private ExpressionPanel expression;

    /** F(A, B, C) = A'B + AC (rows 2, 3, 5, 7). */
    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        model = new KarnaughModel();
        for (int row : new int[] {2, 3, 5, 7}) {
            model.setValue(0, row, LogicValue.ONE);
        }
        model.setTitle("Mi tabla");
        wizard = new KarnaughWizard(model, KarnaughCost.GENERATOR, target, Background.inline(),
                KarnaughWizard.Mode.PRACTICE);
        wizard.setOnClose(() -> { });
        expression = wizard.expressionPanel();
    }

    private DrawnGroup group(long mask, long value) {
        KarnaughGroup group = KarnaughLayout.of(model.inputCount()).group(new Term(mask, value));
        return new DrawnGroup(group.cells(), group.rectangles());
    }

    /** A'B + AC + BC: equivalent, one group more than VSim's. */
    private void practiseWithAnExtraGroup() {
        model.practice().add(0, SUM_OF_PRODUCTS, group(A | B, B));
        model.practice().add(0, SUM_OF_PRODUCTS, group(A | C, A | C));
        model.practice().add(0, SUM_OF_PRODUCTS, group(B | C, B | C));
    }

    private static List<String> typesOf(cl.almejo.vsim.core.io.CircuitFragment fragment) {
        return fragment.components().stream().map(Component::typeId).toList();
    }

    @Test
    void withoutGroupsTheCheckBoxIsHiddenAndStepFourSaysSoUntilTheOutputIsRevealed() {
        wizard.goTo(KarnaughWizard.Step.EXPRESSION);
        assertThat(expression.useMineCheckBox().isVisible()).isFalse();
        assertThat(expression.useMineReasonText()).isEmpty();
        assertThat(expression.expressionTexts()).containsExactly("F: todavía sin grupos (dibújalos en el paso 3)");
        model.practice().reveal(0, SUM_OF_PRODUCTS);
        assertThat(expression.expressionTexts()).containsExactly("F = A'B + AC");
    }

    @Test
    void anOutputThatNeedsNoGroupsShowsItsConstant() {
        for (int row : new int[] {2, 3, 5, 7}) {
            model.setValue(0, row, LogicValue.ZERO);
        }
        wizard.goTo(KarnaughWizard.Step.EXPRESSION);
        assertThat(expression.expressionTexts()).containsExactly("F = 0");
    }

    @Test
    void theSolutionWizardNeverOffersTheStudentsGroups() {
        KarnaughModel solutionModel = new KarnaughModel();
        for (int row : new int[] {2, 3, 5, 7}) {
            solutionModel.setValue(0, row, LogicValue.ONE);
        }
        KarnaughWizard solution = new KarnaughWizard(solutionModel, KarnaughCost.GENERATOR, target,
                Background.inline());
        solutionModel.practice().add(0, SUM_OF_PRODUCTS, group(A | B, B));
        solutionModel.practice().add(0, SUM_OF_PRODUCTS, group(A | C, A | C));
        solutionModel.practice().add(0, SUM_OF_PRODUCTS, group(B | C, B | C));
        solution.goTo(KarnaughWizard.Step.EXPRESSION);
        assertThat(solution.expressionPanel().useMineCheckBox().isVisible()).isFalse();
        assertThat(solutionModel.usingStudentGroups()).isFalse();
        assertThat(solutionModel.effectiveMinimization()).isEqualTo(solutionModel.minimization());
        assertThat(solution.expressionPanel().expressionTexts()).containsExactly("F = A'B + AC");
    }

    @Test
    void anEquivalentCoverIsUsedByDefaultInTheExpressionTheColoursAndTheCost() {
        practiseWithAnExtraGroup();
        String vsimCost = CostLine.text(KarnaughCost.GENERATOR.gatesByType(model.minimization(), model.options()));
        wizard.goTo(KarnaughWizard.Step.EXPRESSION);
        assertThat(expression.useMineCheckBox().isVisible()).isTrue();
        assertThat(expression.useMineCheckBox().isEnabled()).isTrue();
        assertThat(expression.useMineCheckBox().isSelected()).isTrue();
        assertThat(expression.useMineCheckBox().getText()).isEqualTo("Usar mis grupos");
        assertThat(expression.expressionTexts()).containsExactly("F = A'B + AC + BC");
        assertThat(expression.line(0).runs().stream().map(ColoredExpression.Run::colorIndex)
                .filter(index -> index >= 0).toList()).containsExactly(0, 1, 2);
        String studentCost = CostLine.text(KarnaughCost.GENERATOR.gatesByType(model.effectiveMinimization(),
                model.options()));
        assertThat(expression.costText()).isEqualTo(studentCost).isNotEqualTo(vsimCost);

        expression.useMineCheckBox().doClick();
        assertThat(model.usingStudentGroups()).isFalse();
        assertThat(expression.expressionTexts()).as("VSim's is not revealed yet")
                .containsExactly("F = A'B + AC + BC");
        assertThat(expression.costText()).isEqualTo(vsimCost);
        model.practice().reveal(0, SUM_OF_PRODUCTS);
        assertThat(expression.expressionTexts()).containsExactly("F = A'B + AC");
        expression.useMineCheckBox().doClick();
        assertThat(expression.expressionTexts()).containsExactly("F = A'B + AC + BC");
    }

    @Test
    void aCoverThatIsNotEquivalentDisablesTheCheckBoxWithTheReason() {
        model.practice().add(0, SUM_OF_PRODUCTS, group(A | B, B));
        wizard.goTo(KarnaughWizard.Step.EXPRESSION);
        assertThat(expression.useMineCheckBox().isVisible()).isTrue();
        assertThat(expression.useMineCheckBox().isEnabled()).isFalse();
        assertThat(expression.useMineCheckBox().isSelected()).isFalse();
        assertThat(expression.useMineCheckBox().getToolTipText()).isEqualTo("F: tus grupos no cubren la tabla");
        assertThat(expression.useMineReasonText()).isEqualTo("F: tus grupos no cubren la tabla");
        assertThat(expression.expressionTexts()).as("the student's, not VSim's").containsExactly("F = A'B");
        model.practice().add(0, SUM_OF_PRODUCTS, group(A | C, A | C));
        assertThat(expression.useMineCheckBox().isEnabled()).isTrue();
        assertThat(expression.useMineCheckBox().isSelected()).isTrue();
        assertThat(expression.useMineReasonText()).isEmpty();
        assertThat(expression.expressionTexts()).containsExactly("F = A'B + AC");
    }

    @Test
    void anOutputNotPractisedKeepsVsimsTerms() {
        model.setOutputCount(2);
        model.setValue(1, 0, LogicValue.ONE);
        practiseWithAnExtraGroup();
        wizard.goTo(KarnaughWizard.Step.EXPRESSION);
        assertThat(expression.expressionTexts()).containsExactly("F = A'B + AC + BC",
                "G: todavía sin grupos (dibújalos en el paso 3)");
        assertThat(model.effectiveMinimization().outputs().get(1).terms()).hasSize(1);
        model.practice().reveal(1, SUM_OF_PRODUCTS);
        assertThat(expression.expressionTexts()).containsExactly("F = A'B + AC + BC", "G = A'B'C'");
    }

    @Test
    void theOtherFormUsesItsOwnGroupsOrVsims() {
        practiseWithAnExtraGroup();
        wizard.goTo(KarnaughWizard.Step.EXPRESSION);
        expression.formButton(PRODUCT_OF_SUMS).doClick();
        assertThat(expression.useMineCheckBox().isVisible()).isFalse();
        Minimization vsim = model.minimization();
        assertThat(model.effectiveMinimization()).isEqualTo(vsim);
        assertThat(expression.expressionTexts()).containsExactly("F: todavía sin grupos (dibújalos en el paso 3)");
        expression.formButton(SUM_OF_PRODUCTS).doClick();
        assertThat(expression.expressionTexts()).containsExactly("F = A'B + AC + BC");
    }

    @Test
    void crearBuildsTheStudentsCircuitAndChecksItAgainstTheTable() {
        practiseWithAnExtraGroup();
        wizard.goTo(KarnaughWizard.Step.CREATE);
        wizard.createButton().doClick();
        assertThat(target.newCircuits).singleElement().satisfies(created -> {
            assertThat(typesOf(created.fragment())).contains("or3");
            assertThat(typesOf(created.fragment()).stream().filter("and2"::equals)).hasSize(3);
            assertThat(TableCircuits.check(model.spec(), created.fragment(), StandardComponentTypes.registry())
                    .passed()).isTrue();
            assertThat(created.notice()).isEqualTo("✓ El circuito cumple la tabla.");
        });
    }

    @Test
    void crearBuildsVsimsCircuitWhenTheCheckBoxIsOff() {
        practiseWithAnExtraGroup();
        model.setUseStudentGroups(false);
        wizard.goTo(KarnaughWizard.Step.CREATE);
        wizard.createButton().doClick();
        assertThat(target.newCircuits).singleElement().satisfies(created -> {
            assertThat(typesOf(created.fragment())).contains("or2").doesNotContain("or3");
        });
    }
}
