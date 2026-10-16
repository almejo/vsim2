package cl.almejo.vsim.ui.inline;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InlineValidatorsTest {

    @Test
    void anyTextAcceptsEverythingEvenBlank() {
        assertThat(InlineValidators.anyText().apply("hola")).isEmpty();
        assertThat(InlineValidators.anyText().apply("")).isEmpty();
        assertThat(InlineValidators.anyText().apply("   ")).isEmpty();
    }
}
