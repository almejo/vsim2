package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.machine.MachineTestRun;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.i18n.Messages;

import java.util.ArrayList;
import java.util.List;

/**
 * What a machine says about its saved tests in Propiedades (the value after «Pruebas»), in words: «ninguna»,
 * «1 prueba: pasa ✓», «1 prueba: falla ✗», «3 pruebas: 2 pasan ✓, 1 falla ✗». A test with nothing expected says
 * «sin valores esperados». The same for the piece in the circuit and for the machine's own tab.
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
        boolean alone = summary.tests() == 1;
        List<String> parts = new ArrayList<>();
        if (summary.passing() > 0) {
            parts.add(alone ? Messages.text("machine.tests.pass.alone")
                    : Messages.format(summary.passing() == 1 ? "machine.tests.pass.one" : "machine.tests.pass.many",
                    summary.passing()));
        }
        if (summary.failing() > 0) {
            parts.add(alone ? Messages.text("machine.tests.fail.alone")
                    : Messages.format(summary.failing() == 1 ? "machine.tests.fail.one" : "machine.tests.fail.many",
                    summary.failing()));
        }
        if (summary.nothingExpected() > 0) {
            parts.add(alone ? Messages.text("machine.tests.nothing.alone")
                    : Messages.format("machine.tests.nothing.counted", summary.nothingExpected()));
        }
        return Messages.format(alone ? "machine.tests.counted.one" : "machine.tests.counted.many", summary.tests(),
                String.join(", ", parts));
    }
}
