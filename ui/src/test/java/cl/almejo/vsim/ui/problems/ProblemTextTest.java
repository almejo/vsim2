package cl.almejo.vsim.ui.problems;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.simulation.DriverRef;
import cl.almejo.vsim.core.simulation.FlatPinRef;
import cl.almejo.vsim.core.simulation.Problem;
import cl.almejo.vsim.core.simulation.ShortCircuit;
import cl.almejo.vsim.core.simulation.WidthMismatch;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemTextTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitDocument document = new CircuitDocument();
    private final Component andGate = place(StandardComponentTypes.AND2, 0, 0, "U3");
    private final Component notGate = place(StandardComponentTypes.NOT, 10, 0, "");

    @BeforeEach
    void spanish() {
        Messages.initialize(Messages.SPANISH);
    }

    @AfterEach
    void restoreSpanish() {
        Messages.initialize(Messages.SPANISH);
    }

    private Component place(String typeId, int x, int y, String label) {
        Component component = registry.require(typeId).newComponent(document.newComponentId(), new GridPoint(x, y),
                Rotation.R0, Map.of(), label);
        document.add(component);
        return component;
    }

    private ShortCircuit fight() {
        return new ShortCircuit("n", List.of(new DriverRef(andGate.id().value(), 2, Value.ONE),
                new DriverRef(notGate.id().value(), 1, Value.ZERO)), 5);
    }

    @Test
    void driversAreNamedByTypeAndLabelWithTheValueTheyImpose() {
        assertThat(ProblemText.drivers(fight(), document)).isEqualTo("AND de 2 entradas 'U3' → 1 · NOT → 0");
        Messages.initialize(Locale.ENGLISH);
        assertThat(ProblemText.drivers(fight(), document)).isEqualTo("AND (2 inputs) 'U3' → 1 · NOT → 0");
    }

    @Test
    void describesAShortCircuitWithItsStartTime() {
        assertThat(ProblemText.describe(fight(), document, registry))
                .isEqualTo("⚠ Cortocircuito desde t = 5: AND de 2 entradas 'U3' → 1 · NOT → 0");
    }

    @Test
    void describesAWidthMismatchWithThePinWidths() {
        WidthMismatch mismatch = new WidthMismatch("n", List.of(new FlatPinRef(notGate.id().value(), 0),
                new FlatPinRef(andGate.id().value(), 0)));
        assertThat(ProblemText.describe(mismatch, document, registry))
                .isEqualTo("⚠ Anchos incompatibles: NOT (1 bit) · AND de 2 entradas 'U3' (1 bit)");
    }

    @Test
    void driverOfADeletedComponentFallsBackToItsId() {
        ShortCircuit stale = new ShortCircuit("n", List.of(new DriverRef("c99", 0, Value.ZERO)), 1);
        assertThat(ProblemText.drivers(stale, document)).isEqualTo("c99 → 0");
        WidthMismatch staleMismatch = new WidthMismatch("n", List.of(new FlatPinRef("c99", 3)));
        assertThat(ProblemText.describe(staleMismatch, document, registry)).isEqualTo("⚠ Anchos incompatibles: c99");
    }

    @Test
    void shortCircuitsComeFirstOldestFirstThenWidthMismatches() {
        Problem mismatch = new WidthMismatch("n:a", List.of());
        Problem late = new ShortCircuit("n:z", List.of(), 9);
        Problem lateTie = new ShortCircuit("n:b", List.of(), 9);
        Problem early = new ShortCircuit("n:c", List.of(), 2);
        assertThat(ProblemText.ordered(List.of(mismatch, late, lateTie, early)))
                .containsExactly(early, lateTie, late, mismatch);
    }
}
