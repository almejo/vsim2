package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * How a machine's Propiedades tells how to draw (hand test of hito 2, round 2: the English «after a slash go the
 * arrow's outputs» read badly): short and natural in both languages, with the slash notation's example.
 */
class MachineHintTextTest {

    @AfterEach
    void restore() {
        Messages.initialize(Messages.SPANISH);
    }

    @Test
    void howToDrawReadsNaturallyInSpanish() {
        Messages.initialize(Messages.SPANISH);

        assertThat(Messages.text("machine.properties.howToDraw")).isEqualTo("Doble clic en el vacío crea un estado."
                + " Arrastra desde el centro para moverlo, o desde el borde para unirlo con otro. Doble clic en una"
                + " condición la edita; las salidas van después de una barra: x / Z=1.");
    }

    @Test
    void howToDrawReadsNaturallyInEnglish() {
        Messages.initialize(Locale.ENGLISH);

        assertThat(Messages.text("machine.properties.howToDraw")).isEqualTo("Double-click an empty spot to add a"
                + " state. Drag a state from its centre to move it, or from its edge to connect it to another."
                + " Double-click a condition to edit it; outputs go after a slash: x / Z=1.");
        assertThat(Messages.text("machine.label.notation")).isEqualTo("Write condition / outputs, for example x / Z=1");
    }
}
