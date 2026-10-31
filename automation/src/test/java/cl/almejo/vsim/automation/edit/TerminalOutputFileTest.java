package cl.almejo.vsim.automation.edit;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.edit.EditOperation.PlacePart;
import cl.almejo.vsim.automation.edit.EditOperation.SetParameters;
import cl.almejo.vsim.core.component.standard.TerminalTypes;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** «Guardar salida» writes a file of the user's: the AI can neither turn it on nor name the file. */
class TerminalOutputFileTest {

    private static final String DOCUMENT = "circuit-1";

    private final FakeCircuitHost host = new FakeCircuitHost();
    private EditService service;
    private FakeCircuitHost.Circuit circuit;
    private int nextOperationId = 1;
    private String terminalId;

    @BeforeEach
    void aTerminal() {
        circuit = host.open(DOCUMENT);
        service = new EditService(host, new CopyOnWriteArrayList<>(), Duration.ofSeconds(5), Runnable::run);
        EditReceipt placed = service.edit(request(new PlacePart(Optional.of("pantalla"), "terminal", 0, 0,
                Rotation.R0, Map.of(), "")));
        terminalId = placed.aliases().get("pantalla");
    }

    @Test
    void theAiCannotSetTheOutputFileOrTurnTheSavingOn() {
        AutomationException file = refused(new SetParameters(terminalId,
                Map.of(TerminalTypes.OUTPUT_FILE, "/home/alguien/tesis.txt")));
        assertThat(file.error()).isEqualTo(AutomationError.NOT_ALLOWED);
        assertThat(file.getMessage()).contains("save-output", "output-file", "ask the user");
        assertThat(refused(new SetParameters(terminalId,
                Map.of(TerminalTypes.SAVE_OUTPUT, TerminalTypes.SAVE_OUTPUT_TO_FILE))).error())
                .isEqualTo(AutomationError.NOT_ALLOWED);
        assertThat(refused(new PlacePart(Optional.empty(), "terminal", 0, 60, Rotation.R0,
                Map.of(TerminalTypes.OUTPUT_FILE, "x.txt"), "")).error()).isEqualTo(AutomationError.NOT_ALLOWED);
    }

    @Test
    void theOtherParametersOfATerminalStillChange() {
        service.edit(request(new SetParameters(terminalId, Map.of(TerminalTypes.ROWS, 10))));

        assertThat(circuit.document().components()).anySatisfy(component ->
                assertThat(component.parameters()).containsEntry(TerminalTypes.ROWS, 10));
    }

    private AutomationException refused(EditOperation operation) {
        List<AutomationException> refusals = new ArrayList<>();
        assertThatThrownBy(() -> service.edit(request(operation)))
                .isInstanceOfSatisfying(AutomationException.class, refusals::add);
        return refusals.getFirst();
    }

    private EditRequest request(EditOperation... operations) {
        return new EditRequest(new DocumentId(DOCUMENT), circuit.version(), "op-" + nextOperationId++,
                Optional.empty(), List.of(operations), false);
    }
}
