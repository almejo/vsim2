package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.core.command.PinJoinCheck;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A refused parameter change names the pin that would land on a wire: the sentence used to speak only of Q̅, so turning
 * a LED matrix to «Barrido» over its row wires said «El pin Q̅…».
 */
class PinJoinRefusalTest {

    @Test
    void aNewPinThatTouchesAWireIsNamedInTheSentence() {
        String sentence = PinJoinRefusal.sentence(new PinJoinCheck.PinJoin("Fila", PinJoinCheck.Kind.NEW_PIN_TOUCHES));

        assertThat(sentence).contains("Fila").doesNotContain("Q̅");
    }

    @Test
    void theFlipFlopsNotOutputIsStillNamed() {
        String sentence = PinJoinRefusal.sentence(new PinJoinCheck.PinJoin("nQ", PinJoinCheck.Kind.NEW_PIN_TOUCHES));

        assertThat(sentence).contains("Q̅");
    }
}
