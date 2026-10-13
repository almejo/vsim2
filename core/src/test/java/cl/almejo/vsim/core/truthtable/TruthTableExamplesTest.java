package cl.almejo.vsim.core.truthtable;

import cl.almejo.vsim.core.bundled.BundledExamples;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.io.CircuitFormatException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Truth tables of the bundled examples (M6.1-A) that need more than the direct 1 024 rows (spec §5.4). */
class TruthTableExamplesTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();

    @Test
    void theAluExampleHasElevenInputBitsAndIsReadyAfterAQuestion() throws CircuitFormatException {
        CircuitDocument alu = BundledExamples.open("alu-4-bits", registry);

        Preparation preparation = TruthTableScope.prepare(alu, Set.of(), registry);

        assertThat(preparation).isInstanceOf(Preparation.Ready.class);
        Preparation.Ready ready = (Preparation.Ready) preparation;
        assertThat(ready.inputs()).extracting(Column::name)
                .containsExactly("Cin", "A0", "B0", "A1", "B1", "A2", "B2", "A3", "B3", "S1", "S0");
        assertThat(ready.outputs()).extracting(Column::name).containsExactly("Cero", "Negativo", "Acarreo");
        assertThat(ready.inputBits()).isEqualTo(11);
        assertThat(ready.rowCount()).isEqualTo(2048);
        assertThat(ready.needsConfirmation()).isTrue();
    }

    @Test
    void theAluTableAddsWhenTheOperationIsZero() throws CircuitFormatException {
        Preparation.Ready ready = (Preparation.Ready) TruthTableScope.prepare(
                BundledExamples.open("alu-4-bits", registry), Set.of(), registry);

        TruthTable table = TruthTableEvaluator.evaluate(ready, percent -> { }, () -> false).orElseThrow();

        assertThat(table.rows()).hasSize(2048).allMatch(Row::settled);
        // 1111 + 0001 with S1 S0 = 00 and Cin = 0: 1 0000, so Cero and Acarreo light and Negativo does not.
        Map<String, String> inputs = Map.ofEntries(Map.entry("Cin", "0"), Map.entry("A0", "1"), Map.entry("B0", "1"),
                Map.entry("A1", "1"), Map.entry("B1", "0"), Map.entry("A2", "1"), Map.entry("B2", "0"),
                Map.entry("A3", "1"), Map.entry("B3", "0"), Map.entry("S1", "0"), Map.entry("S0", "0"));
        List<String> wanted = table.inputs().stream().map(column -> inputs.get(column.name())).toList();
        Row row = table.rows().stream()
                .filter(candidate -> TruthTableText.cells(candidate).subList(0, 11).equals(wanted))
                .findFirst().orElseThrow();
        assertThat(TruthTableText.cells(row).subList(11, 14)).containsExactly("1", "0", "1");
    }
}
