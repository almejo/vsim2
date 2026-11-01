package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileProblemsTest {

    private final CircuitFormat format = new CircuitFormat(StandardComponentTypes.registry());

    @Test
    void aNewerFileSaysWhichVersionSavedIt() {
        assertThatThrownBy(() -> format.read(
                "{\"format\":\"vsim2\",\"savedWith\":\"9.9\",\"formatVersion\":99,\"kind\":\"circuit\"}"))
                .isInstanceOfSatisfying(CircuitFormatException.class, exception -> {
                    assertThat(exception.reason()).isEqualTo(CircuitFormatException.Reason.NEWER_FORMAT);
                    assertThat(exception.detail()).isEqualTo("99");
                    assertThat(exception.savedWith()).isEqualTo("9.9");
                });
    }

    @Test
    void aBadParameterNamesTheComponentThatHasIt() {
        String json = "{\"formatVersion\":2,\"kind\":\"circuit\",\"components\":[{\"id\":\"c7\",\"type\":\""
                + StandardComponentTypes.INPUT_PIN + "\",\"x\":1,\"y\":1,\"parameters\":{\"value\":\"0x5A\"}}]}";
        assertThatThrownBy(() -> format.read(json)).isInstanceOfSatisfying(CircuitFormatException.class, exception -> {
            assertThat(exception.reason()).isEqualTo(CircuitFormatException.Reason.INVALID_ELEMENT);
            assertThat(exception.detail()).contains("0x5A");
            assertThat(exception.element()).isEqualTo(
                    new CircuitFormatException.Element("components", "c7", StandardComponentTypes.INPUT_PIN));
        });
    }
}
