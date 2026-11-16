package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.ui.app.OpenDocument;
import org.junit.jupiter.api.Test;

import static cl.almejo.vsim.ui.UiTestSupport.awaitSimulation;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hand test of hito 2 (item 9): a placed machine's face has its state line while the circuit simulates. The renderer
 * hands each part the snapshot's state under the part's own id; for a machine that is the state the face writes
 * («S0 → S0» here, with nothing on CLK), never «—».
 */
class MachineFaceWhileSimulatingTest {

    @Test
    void theSnapshotGivesEachPlacedMachineItsStateUnderItsOwnId() {
        OpenDocument document = MachineFixtures.circuitWithGeneratedMachines(2);
        try {
            awaitSimulation(document);

            for (String copy : MachineFixtures.copies(document)) {
                assertThat(document.latestSnapshot().componentState(copy)).as(copy)
                        .isInstanceOfSatisfying(VisibleState.MachineVisibleState.class, state -> {
                            assertThat(state.stateName()).isEqualTo("S0");
                            assertThat(state.nextStateId()).isPresent();
                        });
            }
        } finally {
            document.close();
        }
    }
}
