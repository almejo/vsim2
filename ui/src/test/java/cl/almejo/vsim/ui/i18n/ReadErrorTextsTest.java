package cl.almejo.vsim.ui.i18n;

import cl.almejo.vsim.core.io.CircuitFormatException;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.ResourceBundle;

import static org.assertj.core.api.Assertions.assertThat;

/** Every reason a file can fail to read has a text in both languages, with the technical detail on its own line. */
class ReadErrorTextsTest {

    @Test
    void everyReasonHasATextInBothBundles() {
        for (Locale locale : new Locale[]{Locale.forLanguageTag("es"), Locale.ENGLISH}) {
            ResourceBundle bundle = Messages.bundle(locale);
            for (CircuitFormatException.Reason reason : CircuitFormatException.Reason.values()) {
                assertThat(bundle.containsKey("error.read." + reason.name()))
                        .as(locale + " error.read." + reason).isTrue();
            }
        }
    }

    @Test
    void theTechnicalDetailGoesOnItsOwnLine() {
        Messages.initialize(Messages.SPANISH);
        assertThat(Messages.readError(CircuitFormatException.Reason.INVALID_JSON, "Unexpected character"))
                .isEqualTo("El archivo no es JSON válido.\nDetalle técnico: Unexpected character");
        assertThat(Messages.readError(CircuitFormatException.Reason.EMPTY_FILE, ""))
                .isEqualTo("El archivo está vacío.");
    }
}
