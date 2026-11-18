package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.machine.MachineTestRun;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.i18n.Messages;

/**
 * What a machine says about its saved tests in Propiedades (the value after «Pruebas»): «ninguna», «2, todas ✓»,
 * «2, 1 con ✗», «1, sin valores esperados». The same for the piece in the circuit and for the machine's own tab.
 */
final class MachineTestsText {

    private MachineTestsText() {
    }

    /** The saved tests of {@code machine} and how they did, in a short line. */
    static String of(StateMachine machine) {
        if (machine.tests().isEmpty()) {
            return Messages.text("machine.tests.none");
        }
        MachineTestRun.TestSummary summary = MachineTestRun.summary(machine);
        if (summary.failing() > 0) {
            return Messages.format("machine.tests.failing", summary.tests(), summary.failing());
        }
        if (summary.nothingExpected() == summary.tests()) {
            return Messages.format("machine.tests.nothing", summary.tests());
        }
        if (summary.nothingExpected() > 0) {
            return Messages.format("machine.tests.mixed", summary.tests(), summary.passing(),
                    summary.nothingExpected());
        }
        return Messages.format(summary.tests() == 1 ? "machine.tests.one" : "machine.tests.all", summary.tests());
    }
}
