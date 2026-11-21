package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/** APP-2: «Guardar como» onto a file open in another tab says which file (CircuitWindow passes its name). */
class SaveAsTextsTest {

    @AfterEach
    void restoreSpanish() {
        Messages.initialize(Messages.SPANISH);
    }

    @Test
    void theRefusalNamesTheFileInBothLanguages() {
        Path target = Path.of("circuitos", "sumador.vsim");
        Messages.initialize(Messages.SPANISH);
        assertThat(Messages.format("saveAs.openElsewhere", target.getFileName()))
                .isEqualTo("«sumador.vsim» está abierto en otra pestaña; ciérrala primero o elige otro nombre.");

        Messages.initialize(Locale.ENGLISH);
        assertThat(Messages.format("saveAs.openElsewhere", target.getFileName()))
                .isEqualTo("“sumador.vsim” is open in another tab; close it first or choose another name.");
    }
}
