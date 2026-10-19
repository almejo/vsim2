package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.logic.LogicValue;
import cl.almejo.vsim.core.logic.Minimization;
import cl.almejo.vsim.core.logic.Term;
import cl.almejo.vsim.core.logic.TruthTableSpec;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class KarnaughModelTest {

    private KarnaughModel model;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        model = new KarnaughModel();
    }

    /** P (prime digit) and M (at least 5) of a BCD digit; rows 10..15 are X. */
    static TruthTableSpec bcd() {
        List<LogicValue> prime = new java.util.ArrayList<>();
        List<LogicValue> atLeastFive = new java.util.ArrayList<>();
        for (int row = 0; row < 16; row++) {
            if (row >= 10) {
                prime.add(LogicValue.DONT_CARE);
                atLeastFive.add(LogicValue.DONT_CARE);
            } else {
                prime.add(List.of(2, 3, 5, 7).contains(row) ? LogicValue.ONE : LogicValue.ZERO);
                atLeastFive.add(row >= 5 ? LogicValue.ONE : LogicValue.ZERO);
            }
        }
        return new TruthTableSpec("BCD", List.of("A", "B", "C", "D"), List.of("P", "M"),
                List.of(prime, atLeastFive));
    }

    @Test
    void startsWithThreeInputsOneOutputAllZerosAndTheDefaultNames() {
        assertThat(model.title()).isEqualTo("Tabla");
        assertThat(model.inputNames()).containsExactly("A", "B", "C");
        assertThat(model.outputNames()).containsExactly("F");
        assertThat(model.rowCount()).isEqualTo(8);
        assertThat(model.value(0, 5)).isEqualTo(LogicValue.ZERO);
        assertThat(model.problem()).isEmpty();
        assertThat(model.options()).isEqualTo(WizardOptions.defaults());
    }

    @Test
    void clicksCycleZeroOneDontCareAndListenersHear() {
        AtomicInteger changes = new AtomicInteger();
        model.addListener(changes::incrementAndGet);
        model.cycle(0, 3);
        assertThat(model.value(0, 3)).isEqualTo(LogicValue.ONE);
        model.cycle(0, 3);
        assertThat(model.value(0, 3)).isEqualTo(LogicValue.DONT_CARE);
        model.cycle(0, 3);
        assertThat(model.value(0, 3)).isEqualTo(LogicValue.ZERO);
        assertThat(changes).hasValue(3);
    }

    @Test
    void anAddedInputKeepsTheFunctionAndARemovedOneKeepsItsZeroRows() {
        model.setValue(0, 0b101, LogicValue.ONE); // A=1 B=0 C=1
        model.setValue(0, 0b011, LogicValue.DONT_CARE);

        model.setInputCount(4);

        assertThat(model.inputNames()).containsExactly("A", "B", "C", "D");
        assertThat(model.rowCount()).isEqualTo(16);
        assertThat(model.value(0, 0b1010)).isEqualTo(LogicValue.ONE);
        assertThat(model.value(0, 0b1011)).isEqualTo(LogicValue.ONE);
        assertThat(model.value(0, 0b0110)).isEqualTo(LogicValue.DONT_CARE);
        assertThat(model.value(0, 0b0111)).isEqualTo(LogicValue.DONT_CARE);
        assertThat(model.value(0, 0b0000)).isEqualTo(LogicValue.ZERO);

        model.setValue(0, 0b1010, LogicValue.ZERO); // D = 0 row of 101 now 0, D = 1 row still 1
        model.setInputCount(3);

        assertThat(model.inputNames()).containsExactly("A", "B", "C");
        assertThat(model.rowCount()).isEqualTo(8);
        assertThat(model.value(0, 0b101)).as("the D = 0 row wins").isEqualTo(LogicValue.ZERO);
        assertThat(model.value(0, 0b011)).isEqualTo(LogicValue.DONT_CARE);
    }

    @Test
    void outputsAreAddedAtZeroAndRemovedFromTheEndKeepingTheOthers() {
        model.setValue(0, 2, LogicValue.ONE);
        model.setOutputCount(3);
        assertThat(model.outputNames()).containsExactly("F", "G", "H");
        assertThat(model.value(0, 2)).isEqualTo(LogicValue.ONE);
        assertThat(model.value(2, 2)).isEqualTo(LogicValue.ZERO);
        model.setSelectedOutput(2);
        model.setOutputCount(1);
        assertThat(model.outputNames()).containsExactly("F");
        assertThat(model.value(0, 2)).isEqualTo(LogicValue.ONE);
        assertThat(model.selectedOutput()).isZero();
    }

    @Test
    void addedNamesSkipNamesAlreadyTaken() {
        model.setInputName(0, "G");
        model.setOutputCount(2);
        assertThat(model.outputNames()).containsExactly("F", "H");
    }

    @Test
    void namesAndTitleAreValidatedWithAReason() {
        model.setInputName(1, "  ");
        assertThat(model.problem()).contains("Escribe un nombre para la entrada 2.");
        model.setInputName(1, "f");
        assertThat(model.problem()).contains("Hay más de una columna llamada «F»: cada entrada y salida necesita "
                + "su propio nombre.");
        model.setInputName(1, "B");
        model.setOutputName(0, "");
        assertThat(model.problem()).contains("Escribe un nombre para la salida 1.");
        model.setOutputName(0, "F");
        model.setTitle(" ");
        assertThat(model.problem()).contains("Escribe un título para la tabla.");
        model.setTitle("Mi tabla");
        assertThat(model.problem()).isEmpty();
        assertThat(model.spec().title()).isEqualTo("Mi tabla");
    }

    @Test
    void loadingASpecAndMinimizingFollowTheForm() {
        model.load(bcd());
        assertThat(model.inputNames()).containsExactly("A", "B", "C", "D");
        assertThat(model.outputNames()).containsExactly("P", "M");

        Minimization sum = model.minimization();
        assertThat(sum.form()).isEqualTo(Minimization.Form.SUM_OF_PRODUCTS);
        assertThat(sum.outputs().get(0).terms()).containsExactly(new Term(0b0110, 0b0010), new Term(0b0101, 0b0101));

        model.setOptions(model.options().withForm(Minimization.Form.PRODUCT_OF_SUMS));
        assertThat(model.minimization().form()).isEqualTo(Minimization.Form.PRODUCT_OF_SUMS);

        model.setValue(0, 0, LogicValue.ONE);
        assertThat(model.minimization().spec().value(0, 0)).isEqualTo(LogicValue.ONE);
    }

    @Test
    void sharedTermsShareTheirColourAndTheOtherGroupsTakeTheFirstColoursFreeOnTheirMap() {
        model.load(bcd());
        // P = B'C + BD, M = BD + BC + A: BD keeps its colour on M; BC and A take the first ones free there.
        assertThat(model.colorIndex(0, 0)).isZero();
        assertThat(model.colorIndex(0, 1)).isEqualTo(1);
        assertThat(model.colorIndex(1, 0)).isEqualTo(1);
        assertThat(model.colorIndex(1, 1)).isZero();
        assertThat(model.colorIndex(1, 2)).isEqualTo(2);
        assertThat(model.sharedWith(1, 0)).containsExactly("P");
        assertThat(model.sharedWith(1, 1)).isEmpty();

        model.setOptions(model.options().withShareTerms(false));
        assertThat(model.colorIndex(1, 0)).isZero();
        assertThat(model.colorIndex(1, 2)).isEqualTo(2);
        assertThat(model.sharedWith(1, 0)).isEmpty();
    }

    @Test
    void noTwoGroupsOfOneMapShareAColourEvenWithManySharedTerms() {
        java.util.Random random = new java.util.Random(7);
        for (int round = 0; round < 300; round++) {
            List<List<LogicValue>> columns = new java.util.ArrayList<>();
            for (int output = 0; output < 4; output++) {
                List<LogicValue> column = new java.util.ArrayList<>();
                for (int row = 0; row < 16; row++) {
                    column.add(random.nextInt(3) == 0 ? LogicValue.ONE : LogicValue.ZERO);
                }
                columns.add(column);
            }
            model.load(new TruthTableSpec("T", List.of("A", "B", "C", "D"), List.of("F", "G", "H", "I"), columns));
            for (int output = 0; output < 4; output++) {
                int terms = model.minimization().outputs().get(output).terms().size();
                java.util.Set<Integer> colours = new java.util.HashSet<>();
                for (int term = 0; term < terms; term++) {
                    colours.add(Math.floorMod(model.colorIndex(output, term), GroupPalette.size()));
                }
                assertThat(colours).as("round %d, output %d", round, output).hasSize(terms);
            }
        }
    }

    @Test
    void aOneLiteralTermIsNotSaidToBeSharedItIsNotAGate() {
        // F = A, G = A + B: the term A is a wire, not a shared gate.
        List<LogicValue> first = List.of(LogicValue.ZERO, LogicValue.ZERO, LogicValue.ONE, LogicValue.ONE);
        List<LogicValue> second = List.of(LogicValue.ZERO, LogicValue.ONE, LogicValue.ONE, LogicValue.ONE);
        model.load(new TruthTableSpec("T", List.of("A", "B"), List.of("F", "G"), List.of(first, second)));
        assertThat(model.sharedWith(1, 0)).isEmpty();
        assertThat(model.sharedWith(0, 0)).isEmpty();
    }
}
