package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.logic.LogicValue;
import cl.almejo.vsim.core.machine.MachineTest;
import cl.almejo.vsim.core.machine.MachineTests;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** «Pruebas: 2 pruebas: 2 pasan ✓» and its other forms: what a machine says about its tests in Propiedades. */
class MachineTestsTextTest {

    /** The detector run with x = 1 1 1: Z is 0, 0, 1 before each edge; the expectations decide the verdict. */
    private static MachineTest test(String id, LogicValue... expectedZ) {
        MachineTest test = MachineTest.empty(id, "Prueba " + id, expectedZ.length);
        for (int column = 0; column < expectedZ.length; column++) {
            test = MachineTests.withInput(test, column, "x", true);
            test = MachineTests.withExpected(test, column, "Z", expectedZ[column]);
        }
        return test;
    }

    private static StateMachine withTests(MachineTest... tests) {
        return MachineFixtures.detector().withTests(List.of(tests));
    }

    private static MachineTest passing() {
        return test("ok", LogicValue.ZERO, LogicValue.ZERO, LogicValue.ONE);
    }

    private static MachineTest failing() {
        return test("mal", LogicValue.ONE, LogicValue.ZERO, LogicValue.ONE);
    }

    private static MachineTest nothing() {
        return test("nada", LogicValue.DONT_CARE);
    }

    @BeforeEach
    void spanish() {
        Messages.initialize(Messages.SPANISH);
    }

    @Test
    void noTestsSaysNinguna() {
        assertThat(MachineTestsText.of(MachineFixtures.detector())).isEqualTo("ninguna");
    }

    @Test
    void allPassing() {
        assertThat(MachineTestsText.of(withTests(passing(), passing().withId("ok2")))).isEqualTo("2 pruebas: 2 pasan ✓");
        assertThat(MachineTestsText.of(withTests(passing()))).isEqualTo("1 prueba: pasa ✓");
    }

    @Test
    void someFailingSaysHowMany() {
        assertThat(MachineTestsText.of(withTests(failing()))).isEqualTo("1 prueba: falla ✗");
        assertThat(MachineTestsText.of(withTests(passing(), failing()))).isEqualTo("2 pruebas: 1 pasa ✓, 1 falla ✗");
        assertThat(MachineTestsText.of(withTests(failing(), failing().withId("mal2"), passing())))
                .isEqualTo("3 pruebas: 1 pasa ✓, 2 fallan ✗");
        assertThat(MachineTestsText.of(withTests(passing(), passing().withId("ok2"), failing())))
                .isEqualTo("3 pruebas: 2 pasan ✓, 1 falla ✗");
    }

    @Test
    void nothingExpected() {
        assertThat(MachineTestsText.of(withTests(nothing()))).isEqualTo("1 prueba: sin valores esperados");
        assertThat(MachineTestsText.of(withTests(passing(), nothing())))
                .isEqualTo("2 pruebas: 1 pasa ✓, 1 sin valores esperados");
    }

    @Test
    void inEnglish() {
        Messages.initialize(java.util.Locale.ENGLISH);
        assertThat(MachineTestsText.of(MachineFixtures.detector())).isEqualTo("none");
        assertThat(MachineTestsText.of(withTests(failing()))).isEqualTo("1 test: fails ✗");
        assertThat(MachineTestsText.of(withTests(passing()))).isEqualTo("1 test: passes ✓");
        assertThat(MachineTestsText.of(withTests(passing(), passing().withId("ok2"), failing())))
                .isEqualTo("3 tests: 2 pass ✓, 1 fails ✗");
    }
}
