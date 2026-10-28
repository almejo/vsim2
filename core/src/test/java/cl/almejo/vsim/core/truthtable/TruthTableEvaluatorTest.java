package cl.almejo.vsim.core.truthtable;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.simulation.SimulationEngine;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TruthTableEvaluatorTest {

    private static final Value CONFLICT = Value.ZERO.withConflict();

    private static Preparation.Ready ready(TruthTableCircuits circuits, Set<ElementId> selection) {
        return (Preparation.Ready) TruthTableScope.prepare(circuits.document, selection, circuits.registry);
    }

    private static TruthTable evaluate(Preparation.Ready ready) {
        return TruthTableEvaluator.evaluate(ready, percent -> { }, () -> false).orElseThrow();
    }

    private static Row row(List<Value> inputs, List<Value> outputs) {
        return new Row(inputs, outputs, true);
    }

    private static Value bit(int value) {
        return Value.of(value == 1);
    }

    @Test
    void theHalfAdderTableIsExact() {
        TruthTable table = evaluate(ready(TruthTableCircuits.halfAdder(), Set.of()));

        assertThat(table.rows()).containsExactly(
                row(List.of(bit(0), bit(0)), List.of(bit(0), bit(0))),
                row(List.of(bit(0), bit(1)), List.of(bit(1), bit(0))),
                row(List.of(bit(1), bit(0)), List.of(bit(1), bit(0))),
                row(List.of(bit(1), bit(1)), List.of(bit(0), bit(1))));
        assertThat(table.hasFeedback()).isFalse();
        assertThat(table.inputs()).extracting(Column::name).containsExactly("A", "B");
    }

    private static void assertFullAdder(TruthTable table) {
        assertThat(table.rows()).hasSize(8);
        for (int index = 0; index < 8; index++) {
            int first = index >> 2 & 1;
            int second = index >> 1 & 1;
            int carry = index & 1;
            int total = first + second + carry;
            assertThat(table.rows().get(index)).as("row %d", index)
                    .isEqualTo(row(List.of(bit(first), bit(second), bit(carry)),
                            List.of(bit(total % 2), bit(total / 2))));
        }
    }

    @Test
    void theFullAdderAddsInEveryRowWholeOrSelected() {
        assertFullAdder(evaluate(ready(TruthTableCircuits.fullAdder(), Set.of())));

        TruthTableCircuits circuits = TruthTableCircuits.fullAdder();
        Set<ElementId> adder = circuits.ids();
        circuits.addUnrelatedPart();
        assertFullAdder(evaluate(ready(circuits, adder)));
    }

    @Test
    void aSingleInverterInstanceGivesItsOwnTable() {
        TruthTableCircuits circuits = new TruthTableCircuits();
        Component instance = circuits.inverterInstance();

        TruthTable table = evaluate(ready(circuits, Set.of(instance.id())));

        assertThat(table.rows()).containsExactly(row(List.of(bit(0)), List.of(bit(1))),
                row(List.of(bit(1)), List.of(bit(0))));
    }

    @Test
    void aBusInputCountsAllItsValuesAndABusOutputShowsThem() {
        TruthTable table = evaluate(ready(TruthTableCircuits.busPassThrough(), Set.of()));

        assertThat(table.rows()).hasSize(4);
        for (int index = 0; index < 4; index++) {
            assertThat(table.rows().get(index)).isEqualTo(row(List.of(Value.of(2, index)),
                    List.of(Value.of(2, index))));
        }
        assertThat(TruthTableText.cells(table.rows().get(3))).containsExactly("0x3", "0x3");
    }

    @Test
    void aDisconnectedLedReadsZAndShortedSwitchesReadX() {
        TruthTable floating = evaluate(ready(TruthTableCircuits.disconnectedLed(), Set.of()));
        TruthTable shorted = evaluate(ready(TruthTableCircuits.shortedSwitches(), Set.of()));

        assertThat(floating.rows()).extracting(found -> found.outputs().get(0))
                .containsExactly(Value.HIGH_IMPEDANCE, Value.HIGH_IMPEDANCE);
        assertThat(shorted.rows()).extracting(found -> TruthTableText.cell(found.outputs().get(0)))
                .containsExactly("0", "X", "X", "1");
        assertThat(shorted.rows().get(1).outputs().get(0).conflict()).isTrue();
        assertThat(shorted.rows()).allMatch(Row::settled);
    }

    @Test
    void aRingThatOscillatesLeavesItsRowUnsettled() {
        TruthTable table = evaluate(ready(TruthTableCircuits.gatedRing(), Set.of()));

        assertThat(table.hasFeedback()).isTrue();
        assertThat(table.rows().get(0)).isEqualTo(row(List.of(bit(0)), List.of(bit(1))));
        assertThat(table.rows().get(1).settled()).isFalse();
        assertThat(TruthTableText.cells(table.rows().get(1))).containsExactly("1", "~");
    }

    @Test
    void aNorLatchGivesEveryRowAndKeepsTheFeedbackFlag() {
        TruthTable table = evaluate(ready(TruthTableCircuits.norLatch(), Set.of()));

        assertThat(table.hasFeedback()).isTrue();
        assertThat(table.rows()).hasSize(4);
        assertThat(table.inputs()).extracting(Column::name).containsExactly("R", "S");
        assertThat(table.rows().get(1)).as("R = 0, S = 1 sets")
                .isEqualTo(row(List.of(bit(0), bit(1)), List.of(bit(1), bit(0))));
        assertThat(table.rows().get(2)).as("R = 1, S = 0 resets")
                .isEqualTo(row(List.of(bit(1), bit(0)), List.of(bit(0), bit(1))));
        assertThat(table.rows().get(0).settled()).as("R = S = 0: each row starts without memory, the loop oscillates")
                .isFalse();
        assertThat(TruthTableText.cells(table.rows().get(0))).containsExactly("0", "0", "~", "~");
        assertThat(table.rows().get(3)).as("R = S = 1 forces both outputs low")
                .isEqualTo(row(List.of(bit(1), bit(1)), List.of(bit(0), bit(0))));
    }

    @Test
    void aTimeDiagramInScopeDoesNotMakeEveryRowOscillate() {
        TruthTableCircuits circuits = TruthTableCircuits.halfAdder();
        circuits.place(StandardComponentTypes.TIME_DIAGRAM, 40, 0, "");

        TruthTable table = evaluate(ready(circuits, Set.of()));

        assertThat(table.rows()).hasSize(4).allMatch(Row::settled);
        assertThat(table.rows().get(3)).isEqualTo(row(List.of(bit(1), bit(1)), List.of(bit(0), bit(1))));
    }

    @Test
    void aSlowButLegalGateSettlesBecauseTheLimitScalesWithTheSlowestDelay() {
        TruthTableCircuits circuits = new TruthTableCircuits();
        Component input = circuits.place(StandardComponentTypes.SWITCH, 0, 0, "A");                 // out (4,1)
        Component gate = circuits.place(StandardComponentTypes.BUFFER, 10, -1, "",
                Map.of("delay", 200_000, StandardComponentTypes.NEGATE, true)); // (10,1) → (14,1)
        circuits.wire(circuits.pin(input, 0), circuits.pin(gate, 0));
        circuits.place(StandardComponentTypes.LED, 13, -2, "Y");                                    // in (14,1)
        Preparation.Ready ready = ready(circuits, Set.of());

        assertThat(TruthTableEvaluator.settleTimeLimit(ready.circuit())).isEqualTo(200_000_000L);
        assertThat(evaluate(ready).rows()).as("200 000 units is more than the old 100 000 limit, and it settles")
                .containsExactly(row(List.of(bit(0)), List.of(bit(1))), row(List.of(bit(1)), List.of(bit(0))));
        assertThat(TruthTableEvaluator.settleTimeLimit(ready(TruthTableCircuits.halfAdder(), Set.of()).circuit()))
                .as("delay-1 gates keep the base limit").isEqualTo(TruthTableEvaluator.SETTLE_TIME_LIMIT);
    }

    @Test
    void progressGrowsToOneHundredInPercent() {
        List<Integer> progress = new ArrayList<>();

        TruthTableEvaluator.evaluate(ready(TruthTableCircuits.halfAdder(), Set.of()), progress::add, () -> false);

        assertThat(progress).containsExactly(25, 50, 75, 100);
    }

    @Test
    void cancellingStopsBeforeTheNextRowAndGivesNothing() {
        AtomicInteger rowsDone = new AtomicInteger();

        assertThat(TruthTableEvaluator.evaluate(ready(TruthTableCircuits.fullAdder(), Set.of()),
                percent -> rowsDone.incrementAndGet(), () -> rowsDone.get() >= 2)).isEmpty();
        assertThat(rowsDone).hasValue(2);
    }

    @Test
    void cancellingInsideAnOscillatingRowStopsAtASettleCheck() {
        AtomicInteger checks = new AtomicInteger();

        // checks 1 and 2 are the starts of rows 0 and 1 (row 0 settles at once); row 1 oscillates, so the third
        // check can only come from inside its settle loop, after CANCEL_CHECK_INTERVAL event times
        assertThat(TruthTableEvaluator.evaluate(ready(TruthTableCircuits.gatedRing(), Set.of()), percent -> { },
                () -> checks.incrementAndGet() > 2)).isEmpty();
        assertThat(checks).as("cancelled at the first check inside the oscillating row").hasValue(3);
    }

    @Test
    void inputValuesCountInBinaryWithTheFirstColumnMostSignificant() {
        List<Column> columns = List.of(new Column("A", "c1", 1), new Column("D", "c2", 2));

        assertThat(TruthTableEvaluator.inputValues(columns, 0b110)).containsExactly(Value.ONE, Value.of(2, 2));
        assertThat(TruthTableEvaluator.inputValues(columns, 0b011)).containsExactly(Value.ZERO, Value.of(2, 3));
    }

    /**
     * Spec-required documentation (spec §5.3, ruling P7): the evaluator never receives an engine, so this cannot fail
     * by construction; the guarantee is that {@code evaluate} takes only {@link Preparation.Ready}.
     */
    @Test
    void theLiveEngineIsNeverTouched() {
        Preparation.Ready ready = ready(TruthTableCircuits.halfAdder(), Set.of());
        SimulationEngine live = new SimulationEngine();
        live.update(ready.circuit());
        live.advance(10);
        SimulationSnapshot before = live.snapshot();

        evaluate(ready);

        assertThat(live.snapshot()).isEqualTo(before);
        assertThat(live.now()).isEqualTo(10);
    }
}
