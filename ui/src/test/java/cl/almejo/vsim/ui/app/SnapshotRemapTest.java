package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.simulation.FlatPinRef;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.simulation.WidthMismatch;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** The parent's snapshot seen from inside one instance (spec §10.3): its entries, without the path prefix. */
class SnapshotRemapTest {

    private final SimulationSnapshot snapshot = new SimulationSnapshot(7, 3, 120, true,
            Map.of("n:0,0", Value.ONE, "c5/n:2,2", Value.ZERO, "c9/n:2,2", Value.ONE, "c5/c3/n:1,1", Value.ONE),
            Map.of("c5/c2", new VisibleState.SwitchState(Value.ONE), "c9/c2",
                    new VisibleState.SwitchState(Value.ZERO), "c50/c2", new VisibleState.SwitchState(Value.ZERO)),
            List.of(new WidthMismatch("c5/n:2,2", List.of(new FlatPinRef("c5/c2", 0), new FlatPinRef("c7", 1))),
                    new WidthMismatch("n:0,0", List.of(new FlatPinRef("c7", 0)))),
            Optional.empty());

    @Test
    void underKeepsOnlyThePrefixedEntriesAndStripsThePrefix() {
        SimulationSnapshot inside = SnapshotRemap.under(snapshot, Optional.of("c5"));

        assertThat(inside.netValues()).containsOnly(Map.entry("n:2,2", Value.ZERO), Map.entry("c3/n:1,1", Value.ONE));
        assertThat(inside.componentStates()).containsOnlyKeys("c2");
        assertThat(inside.componentState("c2")).isEqualTo(new VisibleState.SwitchState(Value.ONE));
        assertThat(inside.problems()).containsExactly(
                new WidthMismatch("n:2,2", List.of(new FlatPinRef("c2", 0))));
        assertThat(inside.revision()).isEqualTo(7);
        assertThat(inside.time()).isEqualTo(120);
        assertThat(inside.running()).isTrue();
    }

    @Test
    void emptyPathKeepsNothing() {
        SimulationSnapshot inside = SnapshotRemap.under(snapshot, Optional.empty());

        assertThat(inside.netValues()).isEmpty();
        assertThat(inside.componentStates()).isEmpty();
        assertThat(inside.problems()).isEmpty();
        assertThat(inside.time()).isEqualTo(120);
    }
}
