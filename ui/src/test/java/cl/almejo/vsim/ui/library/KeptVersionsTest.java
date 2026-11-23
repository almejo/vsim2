package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.machine.MachineTest;
import cl.almejo.vsim.core.machine.MachineTests;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.machine.MachineFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * «Se mantiene la versión del circuito» names only nested definitions that really differ: a machine's tests live only
 * in the circuit (review of hito 4, finding 9), so the library's copy without them is the same version.
 */
class KeptVersionsTest {

    private final StateMachine detector = MachineFixtures.detector();
    private final CircuitDocument document = new CircuitDocument();

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        document.putDefinition(MachineFixtures.definition(detector.withTests(
                List.of(MachineTest.empty("p1", "Prueba 1", MachineTests.DEFAULT_COLUMNS)))));
    }

    /** A fragment carrying {@code nested} without placing it (as another circuit's sub-circuit carries it). */
    private static CircuitFragment carrying(ComponentDefinition nested) {
        return new CircuitFragment(List.of(), List.of(), List.of(), List.of(nested));
    }

    @Test
    void aNestedMachineThatDiffersOnlyByItsTestsIsTheSameVersion() {
        assertThat(KeptVersions.message(document, carrying(MachineFixtures.definition(detector)))).isEmpty();
    }

    @Test
    void aNestedMachineThatReallyDiffersIsStillNamed() {
        ComponentDefinition renamedState = MachineFixtures.definition(
                detector.withState("s1", state -> state.withName("UNO")));

        assertThat(KeptVersions.message(document, carrying(renamedState))).isPresent();
    }
}
