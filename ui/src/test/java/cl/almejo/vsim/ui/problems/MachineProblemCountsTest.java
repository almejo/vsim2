package cl.almejo.vsim.ui.problems;

import cl.almejo.vsim.core.machine.MachineFinding;
import cl.almejo.vsim.core.machine.MachineFinding.Kind;
import cl.almejo.vsim.core.simulation.MachineProblem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** A machine's errors are counted on the red badge; its warnings only in the list, like the tunnels'. */
class MachineProblemCountsTest {

    @Test
    void machineErrorsCountAsErrorsAndWarningsAsWarnings() {
        MachineProblem error = problem(Kind.NONDETERMINISTIC);
        MachineProblem warning = problem(Kind.UNREACHABLE_STATE);

        assertThat(ProblemsController.countsOf(List.of(error, warning), 0))
                .isEqualTo(new ProblemsController.Counts(1, 1));
    }

    private static MachineProblem problem(Kind kind) {
        return MachineProblem.of("maquina-1", Optional.empty(), new MachineFinding(kind, List.of("s0"), List.of(),
                List.of(), Map.of(), MachineFinding.NO_COLUMN));
    }
}
