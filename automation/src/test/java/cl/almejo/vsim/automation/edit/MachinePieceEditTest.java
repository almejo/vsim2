package cl.almejo.vsim.automation.edit;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.edit.EditOperation.Connect;
import cl.almejo.vsim.automation.edit.EditOperation.Delete;
import cl.almejo.vsim.automation.edit.EditOperation.Move;
import cl.almejo.vsim.automation.edit.EditOperation.PlaceInstance;
import cl.almejo.vsim.automation.edit.EditOperation.PlacePart;
import cl.almejo.vsim.automation.edit.EditOperation.SetComponentShape;
import cl.almejo.vsim.automation.edit.EditOperation.SetParameters;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.machine.MachineDefinitions;
import cl.almejo.vsim.core.machine.StateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The AI and state machines until it has machine tools: a machine piece is placed, moved, wired and deleted like any
 * component, and what would touch a machine's inside is refused plainly.
 */
class MachinePieceEditTest {

    private static final String DOCUMENT = "circuit-1";

    private final FakeCircuitHost host = new FakeCircuitHost();
    private EditService service;
    private FakeCircuitHost.Circuit circuit;
    private int nextOperationId = 1;
    private String pieceId;

    @BeforeEach
    void aMachineFromTheLibraryPlacedInTheCircuit() {
        circuit = host.open(DOCUMENT);
        service = new EditService(host, new CopyOnWriteArrayList<>(), Duration.ofSeconds(5), Runnable::run);
        ComponentDefinition machine = MachineDefinitions.edited(MachineDefinitions.create("semaforo", "Semáforo"),
                StateMachine.initial().withPinNames(List.of("go"), List.of("stop")));
        circuit.useLibrary(List.of(new DefinitionBundle(machine, List.of())));
        EditReceipt receipt = service.edit(request(new PlaceInstance(Optional.of("m"), "Semáforo", 10, 10,
                Rotation.R0)));
        pieceId = receipt.aliases().get("m");
    }

    @Test
    void aMachinePieceIsPlacedWiredMovedAndDeletedLikeAnyComponent() {
        EditReceipt placed = service.edit(request(
                new PlacePart(Optional.of("a"), "input-pin", 0, 11, Rotation.R0, Map.of(), "GO"),
                new Connect(PinTarget.named("new:a", "pin"), PinTarget.named(pieceId, "go"), List.of())));
        assertThat(placed.aliases()).containsKey("a");
        assertThat(circuit.document().wires()).isNotEmpty();

        service.edit(request(new Move(List.of(pieceId), 4, 0)));
        Component moved = circuit.document().component(new ElementId(pieceId)).orElseThrow();
        assertThat(moved.position()).isEqualTo(new GridPoint(14, 10));

        service.edit(request(new Delete(List.of(pieceId))));
        assertThat(circuit.document().component(new ElementId(pieceId))).isEmpty();
    }

    @Test
    void changingTheShapeOfTheMachineItselfIsRefusedPlainly() {
        AutomationException refusal = refused(new SetComponentShape(Optional.empty(), Optional.of("Semáforo"),
                Optional.empty(), List.of(new SetComponentShape.PinPosition("go", 0, 1)), false));

        assertThat(refusal.error()).isEqualTo(AutomationError.NOT_ALLOWED);
        assertThat(refusal.getMessage()).contains("cannot edit state machines yet");
    }

    @Test
    void parametersOfAMachinePieceAreRefusedWithoutACrash() {
        AutomationException refusal = refused(new SetParameters(pieceId, Map.of("width", 4)));

        assertThat(refusal.error()).isNotNull();
        assertThat(refusal.getMessage()).isNotBlank();
    }

    private AutomationException refused(EditOperation operation) {
        AutomationException[] caught = new AutomationException[1];
        assertThatThrownBy(() -> service.edit(request(operation)))
                .isInstanceOfSatisfying(AutomationException.class, refusal -> caught[0] = refusal);
        return caught[0];
    }

    private EditRequest request(EditOperation... operations) {
        return new EditRequest(new DocumentId(DOCUMENT), circuit.version(), "op-" + nextOperationId++,
                Optional.empty(), List.of(operations), false);
    }
}
