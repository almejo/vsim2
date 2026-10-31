package cl.almejo.vsim.ui.terminal;

import cl.almejo.vsim.core.terminal.ExpectedOutput;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The sentences of the «Salida esperada» tab (spec §8.9, mockup §5); the comparison itself is core's. */
class ExpectedOutputTest {

    @BeforeEach
    void spanish() {
        Messages.initialize(Messages.SPANISH);
    }

    @Test
    void equalGoingWellAndDiffersSayWhatTheMockupSays() {
        assertThat(ExpectedOutputText.message(ExpectedOutput.compare("ab", "ab")))
                .isEqualTo("✓ La salida es igual a la esperada.");
        assertThat(ExpectedOutputText.message(ExpectedOutput.compare("? 5\nfact(5)=120\n!", "? 5\nfact")))
                .isEqualTo("… va bien: 8 de 17 caracteres iguales, faltan los demás.");
        assertThat(ExpectedOutputText.message(ExpectedOutput.compare("fact(5)=120", "fact(5)=220")))
                .isEqualTo("✗ Difiere en el carácter 9: se esperaba '1' y llegó '2'.");
    }

    @Test
    void aLineFeedAndTheEndAreNamed() {
        assertThat(ExpectedOutputText.message(ExpectedOutput.compare("a\n!", "ab")))
                .isEqualTo("✗ Difiere en el carácter 2: se esperaba salto de línea y llegó 'b'.");
        assertThat(ExpectedOutputText.message(ExpectedOutput.compare("a", "ab")))
                .isEqualTo("✗ Difiere en el carácter 2: se esperaba nada y llegó 'b'.");
    }

    @Test
    void aMissingExpectedOutputSaysWhatToDo() {
        assertThat(ExpectedOutputText.none())
                .isEqualTo("Este Terminal no tiene salida esperada: elígela en «Salida esperada» de sus propiedades.");
        assertThat(ExpectedOutputText.noFile()).isEqualTo("Falta elegir el archivo .txt con la salida esperada.");
        assertThat(ExpectedOutputText.unreadable("salida.txt")).isEqualTo("No se pudo leer «salida.txt».");
    }
}
