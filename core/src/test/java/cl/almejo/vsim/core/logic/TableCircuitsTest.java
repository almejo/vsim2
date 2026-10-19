package cl.almejo.vsim.core.logic;

import cl.almejo.vsim.core.command.PasteCommand;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinDirection;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.truthtable.Column;
import cl.almejo.vsim.core.truthtable.Row;
import cl.almejo.vsim.core.truthtable.TruthTable;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TableCircuitsTest {

    private static final ComponentTypeRegistry REGISTRY = StandardComponentTypes.registry();

    /** P = B'C + BD, M = A + BD + BC; rows 10..15 are X. */
    static TruthTableSpec bcd() {
        List<LogicValue> first = new ArrayList<>();
        List<LogicValue> second = new ArrayList<>();
        for (int row = 0; row < 16; row++) {
            first.add(row >= 10 ? LogicValue.DONT_CARE : List.of(2, 3, 5, 7).contains(row) ? LogicValue.ONE
                    : LogicValue.ZERO);
            second.add(row >= 10 ? LogicValue.DONT_CARE : List.of(5, 6, 7, 8, 9).contains(row) ? LogicValue.ONE
                    : LogicValue.ZERO);
        }
        return new TruthTableSpec("BCD", List.of("A", "B", "C", "D"), List.of("P", "M"), List.of(first, second));
    }

    private static GeneratedCircuit generated(TruthTableSpec spec) {
        return CircuitGenerator.generate(spec, Minimizer.minimize(spec, Minimization.Form.SUM_OF_PRODUCTS),
                GeneratorOptions.DEFAULTS, REGISTRY);
    }

    @Test
    void theGeneratedCircuitPassesTheCheck() {
        TableCircuits.Check check = TableCircuits.check(bcd(), generated(bcd()).fragment(), REGISTRY);
        assertThat(check.evaluated()).isTrue();
        assertThat(check.passed()).isTrue();
    }

    @Test
    void aCircuitThatDoesNotGiveTheTableIsReportedRowByRowIgnoringXs() {
        TruthTableSpec spec = bcd();
        TruthTableSpec other = spec.withValue(0, 0, LogicValue.ONE).withValue(1, 15, LogicValue.ONE);
        TableCircuits.Check check = TableCircuits.check(other, generated(spec).fragment(), REGISTRY);
        assertThat(check.evaluated()).isTrue();
        assertThat(check.passed()).isFalse();
        // Row 15 was X in the circuit's table: whatever the circuit gives there may differ from 1.
        assertThat(check.mismatches()).first().isEqualTo(new TableCircuits.Mismatch(0, "P", LogicValue.ONE));
    }

    @Test
    void aCircuitWithOtherColumnsCannotBeChecked() {
        TruthTableSpec renamed = new TruthTableSpec("BCD", List.of("W", "X", "Y", "Z"), List.of("P", "M"),
                bcd().outputs());
        TableCircuits.Check check = TableCircuits.check(renamed, generated(bcd()).fragment(), REGISTRY);
        assertThat(check.evaluated()).isFalse();
        assertThat(check.passed()).isFalse();
    }

    @Test
    void theComponentHasThePinsOfTheTableInOrderAndNoFrame() {
        TruthTableSpec spec = new TruthTableSpec("Mezcla", List.of("Cin", "A", "B"), List.of("Suma", "Cout"),
                bcdLikeOutputs());
        DefinitionBundle bundle = TableCircuits.component(spec, generated(spec).fragment(), "mezcla", "Mezcla",
                REGISTRY);
        ComponentDefinition definition = bundle.root();
        assertThat(definition.id()).isEqualTo("mezcla");
        assertThat(definition.name()).isEqualTo("Mezcla");
        assertThat(definition.pinout()).extracting(pin -> pin.spec().name())
                .containsExactly("Cin", "A", "B", "Suma", "Cout");
        assertThat(definition.pinout()).extracting(pin -> pin.spec().direction())
                .containsExactly(PinDirection.INPUT, PinDirection.INPUT, PinDirection.INPUT, PinDirection.OUTPUT,
                        PinDirection.OUTPUT);
        assertThat(definition.body().frames()).isEmpty();
        assertThat(bundle.dependencies()).isEmpty();
    }

    @Test
    void theInstanceFragmentCarriesItsDefinition() {
        DefinitionBundle bundle = TableCircuits.component(bcd(), generated(bcd()).fragment(), "bcd", "BCD",
                REGISTRY);
        CircuitFragment instance = TableCircuits.instance(bundle);
        assertThat(instance.components()).singleElement()
                .satisfies(component -> assertThat(component.typeId())
                        .isEqualTo(SubcircuitTypes.typeIdOf("bcd")));
        assertThat(instance.definitions()).containsExactlyElementsOf(bundle.all());

        CircuitDocument document = new CircuitDocument();
        new PasteCommand(instance, GridPoint.ORIGIN).apply(document);
        assertThat(document.definition("bcd")).isPresent();
        assertThat(document.components()).hasSize(1);
    }

    @Test
    void theTableOfACircuitBecomesASpecWithUnsettledAndUndefinedOutputsAsX() {
        Column first = new Column("A", "c1", 1);
        Column second = new Column(" ", "c2", 1);
        Column out = new Column("a", "c3", 1);
        Column other = new Column("Y", "c4", 1);
        List<Row> rows = List.of(
                new Row(List.of(Value.of(false), Value.of(false)), List.of(Value.of(false), Value.of(true)), true),
                new Row(List.of(Value.of(false), Value.of(true)), List.of(Value.of(true), Value.of(true)), false),
                new Row(List.of(Value.of(true), Value.of(false)), List.of(Value.highImpedance(1), Value.of(false)),
                        true),
                new Row(List.of(Value.of(true), Value.of(true)), List.of(Value.of(true), Value.of(false)), true));
        TruthTable table = new TruthTable(List.of(first, second), List.of(out, other), rows, false);

        TruthTableSpec spec = TableCircuits.specOf(table, "Tabla");

        assertThat(spec.title()).isEqualTo("Tabla");
        assertThat(spec.inputNames()).containsExactly("A", "B");
        assertThat(spec.outputNames()).containsExactly("a2", "Y");
        assertThat(spec.outputs().get(0)).containsExactly(LogicValue.ZERO, LogicValue.DONT_CARE,
                LogicValue.DONT_CARE, LogicValue.ONE);
        assertThat(spec.outputs().get(1)).containsExactly(LogicValue.ONE, LogicValue.DONT_CARE, LogicValue.ZERO,
                LogicValue.ZERO);
    }

    @Test
    void aWideColumnCannotBecomeASpec() {
        TruthTable table = new TruthTable(List.of(new Column("A", "c1", 2)), List.of(new Column("Y", "c2", 1)),
                List.of(), false);
        assertThatThrownBy(() -> TableCircuits.specOf(table, "Tabla")).isInstanceOf(IllegalArgumentException.class);
    }

    /** A 3-input, 2-output table: Suma = Cin xor A xor B, Cout = majority. */
    private static List<List<LogicValue>> bcdLikeOutputs() {
        List<LogicValue> sum = new ArrayList<>();
        List<LogicValue> carry = new ArrayList<>();
        for (int row = 0; row < 8; row++) {
            int ones = Integer.bitCount(row);
            sum.add(ones % 2 == 1 ? LogicValue.ONE : LogicValue.ZERO);
            carry.add(ones >= 2 ? LogicValue.ONE : LogicValue.ZERO);
        }
        return List.of(sum, carry);
    }
}
