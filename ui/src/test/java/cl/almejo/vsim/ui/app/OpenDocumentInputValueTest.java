package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.core.command.ChangeParametersCommand;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static cl.almejo.vsim.ui.UiTestSupport.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

/** M7: a value typed into a bus input pin goes to the running simulation, like a click on a switch. */
class OpenDocumentInputValueTest {

    private OpenDocument document;
    private Component input;
    private Component output;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        document = OpenDocument.createEmpty(StandardComponentTypes.registry());
        // the input pin's pin (2, 1) touches the output pin's pin (2, 1)
        input = place(document, StandardComponentTypes.INPUT_PIN, 0, 0, Map.of("width", 4));
        output = place(document, StandardComponentTypes.OUTPUT_PIN, 2, 0, Map.of("width", 4));
    }

    @AfterEach
    void close() {
        document.close();
    }

    private VisibleState outputState() {
        return document.latestSnapshot().componentState(output.id().value());
    }

    @Test
    void theValueReachesTheOutputWithoutChangingTheDocument() {
        waitUntil(() -> outputState().equals(new VisibleState.LedState(Value.of(4, 0))));
        boolean modified = document.isModified();
        long changes = document.changeCount();
        Object undo = document.history().undoDescription();

        document.setInputValue(input.id(), Value.of(4, 5));

        waitUntil(() -> outputState().equals(new VisibleState.LedState(Value.of(4, 5))));
        assertThat(document.latestSnapshot().componentState(input.id().value()))
                .isEqualTo(new VisibleState.SwitchState(Value.of(4, 5)));
        assertThat(document.isModified()).isEqualTo(modified);
        assertThat(document.changeCount()).isEqualTo(changes);
        assertThat(document.history().undoDescription()).isEqualTo(undo);
        assertThat(document.document().component(input.id()).orElseThrow().parameters().get("value"))
                .isEqualTo("0");
    }

    @Test
    void reiniciarKeepsTheTypedValue() {
        document.setInputValue(input.id(), Value.of(4, 5));
        waitUntil(() -> outputState().equals(new VisibleState.LedState(Value.of(4, 5))));
        document.setStepMode(true);
        waitUntil(() -> !document.latestSnapshot().running() && document.latestSnapshot().time() > 0);

        document.restart();

        waitUntil(() -> document.latestSnapshot().time() == 0 && document.latestNextEventTime().isEmpty());
        assertThat(document.latestSnapshot().componentState(input.id().value()))
                .isEqualTo(new VisibleState.SwitchState(Value.of(4, 5)));
        assertThat(outputState()).isEqualTo(new VisibleState.LedState(Value.of(4, 5)));
    }

    @Test
    void changingTheFormatKeepsTheTypedValue() {
        document.setInputValue(input.id(), Value.of(4, 5));
        waitUntil(() -> outputState().equals(new VisibleState.LedState(Value.of(4, 5))));
        long revision = document.latestSnapshot().revision();
        document.execute(new ChangeParametersCommand(input.id(), document.registry().typeOf(input),
                Map.of("value", "0", "width", 4, "format", "signed")));
        waitUntil(() -> document.latestSnapshot().revision() > revision);

        long nextRevision = document.latestSnapshot().revision();
        document.execute(new ChangeParametersCommand(output.id(), document.registry().typeOf(output),
                Map.of("width", 4, "format", "hex")));
        waitUntil(() -> document.latestSnapshot().revision() > nextRevision);

        assertThat(document.latestSnapshot().componentState(input.id().value()))
                .isEqualTo(new VisibleState.SwitchState(Value.of(4, 5)));
        assertThat(outputState()).isEqualTo(new VisibleState.LedState(Value.of(4, 5)));
    }
}
