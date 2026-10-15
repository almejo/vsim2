package cl.almejo.vsim.core.truthtable;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ConstantTruthTableTest {

    @Test
    void aConstantIsNotAnInputAndHoldsItsValue() {
        TruthTableCircuits circuits = new TruthTableCircuits();
        Component constant = circuits.place(StandardComponentTypes.CONSTANT, 0, 0, "K",
                Map.of("width", 1, "value", "1"));
        Component gate = circuits.place(StandardComponentTypes.AND2, 10, 1, "");
        Component toggle = circuits.place(StandardComponentTypes.SWITCH, 0, 4, "A");
        circuits.wire(circuits.pin(constant, 0), circuits.pin(gate, 0));
        circuits.wire(circuits.pin(toggle, 0), circuits.pin(gate, 1));
        circuits.place(StandardComponentTypes.LED, 13, 0, "Y");

        Preparation.Ready ready = (Preparation.Ready) TruthTableScope.prepare(circuits.document, Set.of(),
                circuits.registry);
        TruthTable table = TruthTableEvaluator.evaluate(ready, percent -> { }, () -> false).orElseThrow();

        assertThat(table.inputs()).extracting(Column::name).containsExactly("A");
        assertThat(table.rows()).hasSize(2);
        assertThat(table.rows().get(0).outputs()).containsExactly(Value.ZERO);
        assertThat(table.rows().get(1).outputs()).containsExactly(Value.ONE);
    }
}
