package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.app.OpenDocument;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

class CircuitWindowTitleTest {

    @Test
    void titleShowsTheNameAndAMarkWhenModified() {
        try (OpenDocument document = OpenDocument.createEmpty(StandardComponentTypes.registry())) {
            assertThat(CircuitWindow.titleFor(document)).isEqualTo("Sin título — VSim 2");
            place(document, StandardComponentTypes.BUFFER, 0, 0, Map.of(StandardComponentTypes.NEGATE, true));
            assertThat(CircuitWindow.titleFor(document)).isEqualTo("*Sin título — VSim 2");
        }
    }

    @Test
    void aViewOfADocumentWithSeveralCarriesItsNumber() {
        try (OpenDocument document = OpenDocument.createEmpty(StandardComponentTypes.registry())) {
            assertThat(CircuitWindow.titleFor(document, 0)).isEqualTo("Sin título — VSim 2");
            assertThat(CircuitWindow.titleFor(document, 2)).isEqualTo("Sin título (2) — VSim 2");
            place(document, StandardComponentTypes.BUFFER, 0, 0, Map.of(StandardComponentTypes.NEGATE, true));
            assertThat(CircuitWindow.titleFor(document, 2)).isEqualTo("*Sin título (2) — VSim 2");
        }
    }
}
