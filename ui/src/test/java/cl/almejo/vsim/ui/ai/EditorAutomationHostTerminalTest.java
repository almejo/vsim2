package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.read.TerminalView;
import cl.almejo.vsim.automation.simulate.TypedText;
import cl.almejo.vsim.core.component.standard.KeyboardTypes;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static cl.almejo.vsim.ui.UiTestSupport.awaitSimulation;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The AI's Terminal and Teclado tools over a real open circuit and its simulation thread (nothing is shown). */
class EditorAutomationHostTerminalTest {

    private static final int CAPACITY = 3;

    private final List<OpenDocument> open = new ArrayList<>();
    private final EditorAutomationHost host = new EditorAutomationHost(() -> List.copyOf(open), document -> List.of());
    private final AutomationApi api = new AutomationApi(host);

    @BeforeEach
    void language() {
        Messages.initialize(Messages.SPANISH);
    }

    @AfterEach
    void close() {
        open.forEach(OpenDocument::close);
    }

    private OpenDocument document() {
        CircuitDocument circuit = new CircuitDocument();
        circuit.setName("Terminal");
        OpenDocument document = new OpenDocument(circuit, StandardComponentTypes.registry(), null, false);
        open.add(document);
        return document;
    }

    private DocumentId idOf(OpenDocument document) {
        return api.listDocuments().get(open.indexOf(document)).id();
    }

    @Test
    void textTypedByTheAiFillsTheQueueUpToItsCapacityAndTheTerminalReadsEmpty() {
        OpenDocument document = document();
        Component keyboard = place(document, StandardComponentTypes.KEYBOARD, 0, 0,
                Map.of(KeyboardTypes.CAPACITY, CAPACITY));
        Component terminal = place(document, StandardComponentTypes.TERMINAL, 0, 40, Map.of());
        awaitSimulation(document);
        DocumentId id = idOf(document);

        boolean modifiedBefore = document.isModified();
        TypedText typed = api.typeText(id, keyboard.id().value(), "hola");
        TerminalView screen = api.readTerminal(id, terminal.id().value(), 0, 0);

        assertThat(typed.accepted()).isEqualTo(CAPACITY);
        assertThat(typed.total()).isEqualTo(4);
        assertThat(typed.queueSize()).isEqualTo(CAPACITY);
        assertThat(screen.rows()).isEqualTo(24);
        assertThat(screen.columns()).isEqualTo(80);
        assertThat(screen.screen()).hasSize(24).containsOnly("");
        assertThat(document.isModified()).as("typing is not an edit").isEqualTo(modifiedBefore);
    }

    @Test
    void aPartThatIsNotOfThatKindIsRefused() {
        OpenDocument document = document();
        Component terminal = place(document, StandardComponentTypes.TERMINAL, 0, 0, Map.of());
        Component keyboard = place(document, StandardComponentTypes.KEYBOARD, 0, 40, Map.of());
        awaitSimulation(document);
        DocumentId id = idOf(document);

        assertThatThrownBy(() -> api.typeText(id, terminal.id().value(), "x"))
                .isInstanceOfSatisfying(AutomationException.class,
                        refusal -> assertThat(refusal.error()).isEqualTo(AutomationError.INVALID_REFERENCE));
        assertThatThrownBy(() -> api.readTerminal(id, keyboard.id().value(), 0, 0))
                .isInstanceOfSatisfying(AutomationException.class,
                        refusal -> assertThat(refusal.error()).isEqualTo(AutomationError.INVALID_REFERENCE));
    }
}
