package cl.almejo.vsim.core.truthtable;

import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.logic.LogicValue;
import cl.almejo.vsim.core.logic.TableCircuits;
import cl.almejo.vsim.core.logic.TruthTableSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link TableCircuits#check} on a circuit with a loop (never a generated one): it goes through the evaluator. */
class TableCircuitsLoopTest {

    @Test
    void aLoopIsCheckedRowByRowOnFreshEnginesAndAnOscillatingRowIsAMismatch() {
        TruthTableCircuits latch = TruthTableCircuits.norLatch();
        CircuitFragment fragment = new CircuitFragment(latch.document.components(),
                latch.document.wires(), List.of());
        LogicValue zero = LogicValue.ZERO;
        LogicValue one = LogicValue.ONE;
        // Rows R S: 00 (oscillates without memory), 01 sets, 10 resets, 11 forces both low.
        TruthTableSpec spec = new TruthTableSpec("Latch", List.of("R", "S"), List.of("Q", "Qn"),
                List.of(List.of(one, one, zero, zero), List.of(zero, zero, one, zero)));

        TableCircuits.Check check = TableCircuits.check(spec, fragment, latch.registry);

        assertThat(check.evaluated()).isTrue();
        assertThat(check.mismatches()).containsExactly(new TableCircuits.Mismatch(0, "Q", one),
                new TableCircuits.Mismatch(0, "Qn", zero));
    }

    @Test
    void withoutItsUnsettledRowTheLatchPasses() {
        TruthTableCircuits latch = TruthTableCircuits.norLatch();
        CircuitFragment fragment = new CircuitFragment(latch.document.components(),
                latch.document.wires(), List.of());
        LogicValue unknown = LogicValue.DONT_CARE;
        TruthTableSpec spec = new TruthTableSpec("Latch", List.of("R", "S"), List.of("Q", "Qn"),
                List.of(List.of(unknown, LogicValue.ONE, LogicValue.ZERO, LogicValue.ZERO),
                        List.of(unknown, LogicValue.ZERO, LogicValue.ONE, LogicValue.ZERO)));

        assertThat(TableCircuits.check(spec, fragment, latch.registry).passed()).isTrue();
    }
}
