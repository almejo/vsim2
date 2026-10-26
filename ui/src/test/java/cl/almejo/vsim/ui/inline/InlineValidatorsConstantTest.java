package cl.almejo.vsim.ui.inline;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class InlineValidatorsConstantTest {

    private final ComponentType constant = StandardComponentTypes.registry().require(StandardComponentTypes.CONSTANT);

    private Function<String, Optional<String>> at(int width) {
        return InlineValidators.constantValue(constant, Map.of("width", width, "format", "auto"));
    }

    @Test
    void aValueThatFitsIsValidInEveryNotation() {
        assertThat(at(8).apply("0x00ff")).isEmpty();
        assertThat(at(8).apply("255")).isEmpty();
        assertThat(at(4).apply("0b1010")).isEmpty();
    }

    @Test
    void aValueTooBigSaysItDoesNotFit() {
        assertThat(at(4).apply("0x1F")).contains("No cabe en 4 bits");
    }

    @Test
    void aTextThatIsNoNumberIsInvalid() {
        assertThat(at(8).apply("hola")).contains("Valor no válido (usa 0b…, 0x… o un número)");
        assertThat(at(8).apply("")).isPresent();
    }

    @Test
    void theWidthOfTheComponentDecidesWhatFits() {
        assertThat(at(1).apply("2")).contains("No cabe en 1 bits");
    }

    @Test
    void theValidatorReadsTheCurrentParametersEveryTime() {
        java.util.concurrent.atomic.AtomicReference<Map<String, Object>> current =
                new java.util.concurrent.atomic.AtomicReference<>(Map.of("width", 8, "format", "auto"));
        Function<String, Optional<String>> validator = InlineValidators.constantValue(constant, current::get);
        assertThat(validator.apply("0xFF")).isEmpty();
        current.set(Map.of("width", 4, "format", "auto"));
        assertThat(validator.apply("0xFF")).contains("No cabe en 4 bits");
    }
}
