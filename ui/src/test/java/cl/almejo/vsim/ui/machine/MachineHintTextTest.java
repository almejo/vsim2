package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * How a machine's arrows tell how to write their conditions (hand test of hito 2, round 2: the English «after a slash go the
 * arrow's outputs» read badly): short and natural in both languages, with the slash notation's example.
 */
class MachineHintTextTest {

    @AfterEach
    void restore() {
        Messages.initialize(Messages.SPANISH);
    }

    @Test
    void theNotationHintReadsNaturallyInEnglish() {
        Messages.initialize(Locale.ENGLISH);

        assertThat(Messages.text("machine.label.notation")).isEqualTo("Write condition / outputs, for example x / Z=1");
    }
}
