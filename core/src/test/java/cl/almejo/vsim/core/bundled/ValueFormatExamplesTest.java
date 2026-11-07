package cl.almejo.vsim.core.bundled;

import cl.almejo.vsim.core.component.standard.ConstantValue;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** «Formatos de valor» is wired and explained cleanly and its pins write the same bits in their own formats. */
class ValueFormatExamplesTest {

    /** The clock of the example: 500 up, 500 down, so a rising edge every 1000 units (the first at 500). */
    private static final long PERIOD = 1000;

    private static String shown(ExampleSimulation simulation, String label, String format, int fractionBits) {
        Value value = simulation.outputPin(label);
        return ConstantValue.display(value.bits(), value.width(), format, fractionBits);
    }

    @Test
    void theExampleIsWiredAndExplainedCleanly() {
        ExampleChecks.assertExample(ValueFormatExamples.valueFormats());
    }

    @Test
    void everyBytePinHoldsTheSameCountAndEachPinIsInItsOwnFormat() {
        ExampleSimulation simulation = new ExampleSimulation(ValueFormatExamples.valueFormats(), Map.of());
        simulation.runUntil(PERIOD / 2 + PERIOD * 65 + 100);
        for (String[] pin : ValueFormatExamples.BYTE_PINS) {
            assertThat(simulation.outputPin(pin[0])).as(pin[0]).isEqualTo(Value.of(8, 65));
            Component component = simulation.component(pin[0], "output-pin");
            assertThat(component.parameters().get(ConstantValue.FORMAT)).as(pin[0]).isEqualTo(pin[1]);
        }
        assertThat(shown(simulation, "Binario", "binary", 0)).isEqualTo("01000001");
        assertThat(shown(simulation, "Hexadecimal", "hex", 0)).isEqualTo("0x41");
        assertThat(shown(simulation, "Octal", "octal", 0)).isEqualTo("0o101");
        assertThat(shown(simulation, "Carácter", "ascii", 0)).isEqualTo("'A'");
        assertThat(shown(simulation, "Punto fijo", "fixed", ValueFormatExamples.FIXED_FRACTION_BITS))
                .isEqualTo("4.0625");
    }

    @Test
    void theSixteenBitConstantIsOnePointFiveAsAHalfFloat() {
        ExampleSimulation simulation = new ExampleSimulation(ValueFormatExamples.valueFormats(), Map.of());
        simulation.runUntil(100);
        List<String> labels = ValueFormatExamples.HALF_PINS.stream().map(pin -> pin[0]).toList();
        for (String label : labels) {
            assertThat(simulation.outputPin(label)).as(label).isEqualTo(Value.of(16, 0x3E00));
        }
        assertThat(shown(simulation, "Flotante 16", "float", 0)).isEqualTo("1.5");
        assertThat(shown(simulation, "Decimal 16", "decimal", 0)).isEqualTo("15872");
    }
}
