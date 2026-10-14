package cl.almejo.vsim.core.wiring;

import cl.almejo.vsim.core.TestComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** A rule pass is checked once at its end and replayed change by change only when that check fails. */
class TidyStateTest {

    private final CircuitDocument document = new CircuitDocument();
    private final Wire joining;
    private final Wire lonely;

    TidyStateTest() {
        document.add(new Component(document.newComponentId(), TestComponentTypes.BUFFER, new GridPoint(0, 0),
                Rotation.R0, Map.of(), ""));
        document.add(new Component(document.newComponentId(), TestComponentTypes.BUFFER, new GridPoint(10, 0),
                Rotation.R0, Map.of(), ""));
        joining = new Wire(document.newWireId(), new GridPoint(2, 1), new GridPoint(10, 1));
        lonely = new Wire(document.newWireId(), new GridPoint(0, 8), new GridPoint(4, 8));
        document.add(joining);
        document.add(lonely);
    }

    /** Removes the given wires one by one and counts how often it ran. */
    private static final class RemovingRule implements TidyRule {
        private final List<Wire> toRemove;
        private int runs;

        RemovingRule(List<Wire> toRemove) {
            this.toRemove = toRemove;
        }

        @Override
        public boolean apply(TidyState state) {
            runs++;
            boolean changed = false;
            for (Wire wire : toRemove) {
                changed |= state.tryChange(List.of(wire), List.of(), Map.of());
            }
            return changed;
        }
    }

    private TidyState state() {
        Set<ElementId> scope = new HashSet<>();
        document.wires().forEach(wire -> scope.add(wire.id()));
        return new TidyState(document, TestComponentTypes.registry(), scope);
    }

    @Test
    void anEquivalentPassIsCheckedOnceAndKept() {
        TidyState state = state();
        RemovingRule rule = new RemovingRule(List.of(lonely));

        assertThat(state.run(rule)).isTrue();

        assertThat(rule.runs).isEqualTo(1);
        assertThat(state.wires()).containsExactly(joining);
    }

    @Test
    void aPassThatChangesTheConnectivityIsReplayedWithEveryChangeChecked() {
        TidyState state = state();
        RemovingRule rule = new RemovingRule(List.of(joining, lonely));

        assertThat(state.run(rule)).isTrue();

        assertThat(rule.runs).isEqualTo(2);
        assertThat(state.wires()).containsExactly(joining);
        assertThat(state.toPlan().removed()).containsExactly(lonely);
    }
}
