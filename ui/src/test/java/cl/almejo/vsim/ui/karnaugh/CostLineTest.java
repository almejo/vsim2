package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.logic.Minimization;
import cl.almejo.vsim.core.logic.Minimizer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CostLineTest {

    @Test
    void theCostLineCountsFamiliesInAFixedOrderAndSaysWhenThereAreNoGates() {
        assertThat(CostLine.text(Map.of("not", 1, "or4", 1, "and2", 2, "and3", 1, "constant", 1)))
                .isEqualTo("3 AND, 1 OR, 1 NOT (5 compuertas)");
        assertThat(CostLine.text(Map.of("nor2", 1))).isEqualTo("1 NOR (1 compuerta)");
        assertThat(CostLine.text(Map.of())).isEqualTo("Sin compuertas: cada salida es una entrada o una constante");
    }

    @Test
    void theGeneratorsCostOfTheBcdExampleIsThreeAndsTwoOrsAndOneNot() {
        Minimization minimization = Minimizer.minimize(KarnaughModelTest.bcd(), Minimization.Form.SUM_OF_PRODUCTS);
        Map<String, Integer> gates = KarnaughCost.GENERATOR.gatesByType(minimization, WizardOptions.defaults());
        assertThat(CostLine.text(gates)).isEqualTo("3 AND, 2 OR, 1 NOT (6 compuertas)");
        assertThat(KarnaughCost.GENERATOR.gatesByType(minimization, WizardOptions.defaults().withShareTerms(false)))
                .containsEntry("and2", 4);
        Map<String, Integer> nands = KarnaughCost.GENERATOR.gatesByType(minimization,
                WizardOptions.defaults().withGates(WizardOptions.GateChoice.NAND_ONLY));
        assertThat(nands.keySet()).allMatch(type -> type.startsWith("nand"));
    }
}
