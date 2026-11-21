package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.panels.StatusBar;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** APP-5 (review I1): a circuit that cannot be written says so in its own tabs until the user dismisses it. */
class CannotSaveNoticesTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final OpenDocument circuit = OpenDocument.createEmpty(registry);
    private final OpenDocument other = OpenDocument.createEmpty(registry);
    private final StatusBar circuitTab = new StatusBar();
    private final StatusBar circuitSecondTab = new StatusBar();
    private final StatusBar otherTab = new StatusBar();
    private final CannotSaveNotices notices = new CannotSaveNotices(document -> Map.of(
            circuit, List.of(circuitTab, circuitSecondTab), other, List.of(otherTab)).get(document));

    @BeforeEach
    void speakSpanish() {
        Messages.initialize(Messages.SPANISH);
    }

    @AfterEach
    void close() {
        circuit.close();
        other.close();
    }

    @Test
    void everyTabOfThatCircuitSaysItCannotBeSavedNorCopiedAndNoOtherTabDoes() {
        notices.cannotSave(circuit);

        assertThat(circuitTab.text()).startsWith("VSim no puede hacer la copia de recuperación de «"
                + circuit.displayName() + "» ni guardarlo");
        assertThat(circuitSecondTab.text()).isEqualTo(circuitTab.text());
        assertThat(otherTab.text()).isEmpty();
    }

    @Test
    void anotherNoticeDoesNotTakeItAway() {
        notices.cannotSave(circuit);

        circuitTab.showNotice("Guardado");

        assertThat(circuitTab.text()).contains("ni guardarlo").contains("Guardado");
    }

    @Test
    void dismissingItInOneTabTakesItAwayFromEveryTabOfTheCircuit() {
        notices.cannotSave(circuit);

        circuitTab.lastingNoticeDismiss().doClick();

        assertThat(circuitTab.text()).isEmpty();
        assertThat(circuitSecondTab.text()).isEmpty();
    }

    @Test
    void itGoesWhenTheCircuitCanBeWrittenAgain() {
        notices.cannotSave(circuit);

        notices.canSaveAgain(circuit);

        assertThat(circuitTab.text()).isEmpty();
        assertThat(circuitSecondTab.text()).isEmpty();
    }
}
