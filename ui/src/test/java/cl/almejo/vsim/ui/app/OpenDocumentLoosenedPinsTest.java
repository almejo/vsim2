package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.netlist.PinRef;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenDocumentLoosenedPinsTest {

    @Test
    void aNewDocumentHasNoLoosenedPinsAndTheSetIsReadOnly() {
        OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        try {
            assertThat(openDocument.loosenedPins()).isEmpty();
            assertThatThrownBy(() -> openDocument.loosenedPins().add(new PinRef(new ElementId("u5"), 1)))
                    .isInstanceOf(UnsupportedOperationException.class);
        } finally {
            openDocument.close();
        }
    }

    @Test
    void theTestSupportSetsThePins() {
        OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        try {
            PinRef pin = new PinRef(new ElementId("u5"), 1);
            LoosenedPinsTestSupport.set(openDocument, Set.of(pin));
            assertThat(openDocument.loosenedPins()).containsExactly(pin);
        } finally {
            openDocument.close();
        }
    }
}
