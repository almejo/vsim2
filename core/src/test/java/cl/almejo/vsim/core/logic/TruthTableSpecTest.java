package cl.almejo.vsim.core.logic;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TruthTableSpecTest {

    @Test
    void rowsReadTheFirstInputAsTheMostSignificantBit() {
        TruthTableSpec spec = TruthTableSpec.zeros("Tabla", List.of("A", "B", "C"), List.of("F"));

        assertThat(spec.rowCount()).isEqualTo(8);
        assertThat(spec.inputValue(5, 0)).isTrue();
        assertThat(spec.inputValue(5, 1)).isFalse();
        assertThat(spec.inputValue(5, 2)).isTrue();
    }

    @Test
    void withValueChangesOneCellAndCellsCycleZeroOneDontCare() {
        TruthTableSpec spec = TruthTableSpec.zeros("Tabla", List.of("A", "B"), List.of("F", "G"));

        TruthTableSpec changed = spec.withValue(1, 2, LogicValue.ZERO.next());

        assertThat(changed.value(1, 2)).isEqualTo(LogicValue.ONE);
        assertThat(changed.value(0, 2)).isEqualTo(LogicValue.ZERO);
        assertThat(spec.value(1, 2)).isEqualTo(LogicValue.ZERO);
        assertThat(LogicValue.ONE.next()).isEqualTo(LogicValue.DONT_CARE);
        assertThat(LogicValue.DONT_CARE.next()).isEqualTo(LogicValue.ZERO);
    }

    @Test
    void namesMustBePresentAndUniqueIgnoringCase() {
        assertThatThrownBy(() -> TruthTableSpec.zeros("T", List.of("A", "a"), List.of("F")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TruthTableSpec.zeros("T", List.of("A", " "), List.of("F")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TruthTableSpec.zeros("T", List.of("A"), List.of("A")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void limitsAreOneToTenInputs() {
        List<String> ten = List.of("A", "B", "C", "D", "E", "G", "H", "I", "J", "K");
        assertThat(TruthTableSpec.zeros("T", ten, List.of("F")).rowCount()).isEqualTo(1024);
        List<String> eleven = List.of("A", "B", "C", "D", "E", "G", "H", "I", "J", "K", "L");
        assertThatThrownBy(() -> TruthTableSpec.zeros("T", eleven, List.of("F")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TruthTableSpec.zeros("T", List.of(), List.of("F")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void upToSixteenOutputs() {
        assertThat(TruthTableSpec.MAXIMUM_OUTPUTS).isEqualTo(16);
        assertThat(TruthTableSpec.zeros("T", List.of("A"), outputs(16)).outputCount()).isEqualTo(16);
        assertThatThrownBy(() -> TruthTableSpec.zeros("T", List.of("A"), outputs(17)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TruthTableSpec.zeros("T", List.of("A"), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static List<String> outputs(int count) {
        return java.util.stream.IntStream.range(0, count).mapToObj(index -> "Y" + index).toList();
    }

    @Test
    void aTermCoversTheRowsOfItsGroup() {
        // B'C over A, B, C, D: B (bit 2) = 0, C (bit 1) = 1 → rows 2, 3, 10, 11
        Term term = new Term(0b0110, 0b0010);

        assertThat(java.util.stream.IntStream.range(0, 16).filter(term::covers).boxed().toList())
                .containsExactly(2, 3, 10, 11);
        assertThat(term.literalCount()).isEqualTo(2);
        assertThat(term.uses(0, 4)).isFalse();
        assertThat(term.uses(1, 4)).isTrue();
        assertThat(term.valueOf(1, 4)).isFalse();
        assertThat(term.valueOf(2, 4)).isTrue();
    }
}
