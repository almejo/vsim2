package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.logic.Minimization;
import cl.almejo.vsim.core.logic.Minimizer;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceholderCostTest {

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
    }

    private static Minimization bcd(Minimization.Form form) {
        return Minimizer.minimize(KarnaughModelTest.bcd(), form);
    }

    @Test
    void theBcdExampleNeedsThreeAndsTwoOrsAndOneNotWhenSharing() {
        Map<String, Integer> gates = PlaceholderCost.INSTANCE.gatesByType(
                bcd(Minimization.Form.SUM_OF_PRODUCTS), WizardOptions.defaults());
        assertThat(gates).containsExactlyInAnyOrderEntriesOf(Map.of("and2", 3, "or2", 1, "or3", 1, "not", 1));
        assertThat(CostLine.text(gates)).isEqualTo("3 AND, 2 OR, 1 NOT (6 compuertas)");
    }

    @Test
    void withoutSharingTheRepeatedTermGetsItsOwnGate() {
        Map<String, Integer> gates = PlaceholderCost.INSTANCE.gatesByType(
                bcd(Minimization.Form.SUM_OF_PRODUCTS), WizardOptions.defaults().withShareTerms(false));
        assertThat(gates.get("and2")).isEqualTo(4);
    }

    @Test
    void nandOnlyUsesOnlyNands() {
        Map<String, Integer> gates = PlaceholderCost.INSTANCE.gatesByType(bcd(Minimization.Form.SUM_OF_PRODUCTS),
                WizardOptions.defaults().withGates(WizardOptions.GateChoice.NAND_ONLY));
        assertThat(gates.keySet()).allMatch(type -> type.startsWith("nand"));
        assertThat(CostLine.text(gates)).isEqualTo("6 NAND (6 compuertas)");
    }

    @Test
    void wideTermsAreSplitIntoTreesOfAtMostFourInputs() {
        assertThat(PlaceholderCost.tree("and", 6)).containsExactly("and4", "and3");
        assertThat(PlaceholderCost.tree("or", 4)).containsExactly("or4");
        assertThat(PlaceholderCost.tree("or", 1)).isEmpty();
    }

    @Test
    void theCostLineCountsFamiliesInAFixedOrderAndSaysWhenThereAreNoGates() {
        assertThat(CostLine.text(Map.of("not", 1, "or4", 1, "and2", 2, "and3", 1, "constant", 1)))
                .isEqualTo("3 AND, 1 OR, 1 NOT (5 compuertas)");
        assertThat(CostLine.text(Map.of("nor2", 1))).isEqualTo("1 NOR (1 compuerta)");
        assertThat(CostLine.text(Map.of())).isEqualTo("Sin compuertas: cada salida es una entrada o una constante");
    }
}
