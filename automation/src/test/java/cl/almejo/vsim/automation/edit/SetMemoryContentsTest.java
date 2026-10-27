package cl.almejo.vsim.automation.edit;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.edit.EditOperation.PlacePart;
import cl.almejo.vsim.automation.edit.EditOperation.SetMemoryContents;
import cl.almejo.vsim.automation.edit.EditOperation.SetParameters;
import cl.almejo.vsim.core.component.standard.MemoryTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.memory.MemoryContents;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The AI's «set memory contents»: words of a RAM or ROM's initial contents, all or nothing, one undo step. */
class SetMemoryContentsTest {

    private static final String DOCUMENT = "circuit-1";

    private final FakeCircuitHost host = new FakeCircuitHost();
    private EditService service;
    private FakeCircuitHost.Circuit circuit;
    private int nextOperationId = 1;
    private String memoryId;

    @BeforeEach
    void aSixteenWordMemoryOfEightBits() {
        circuit = host.open(DOCUMENT);
        service = new EditService(host, new CopyOnWriteArrayList<>(), Duration.ofSeconds(5), Runnable::run);
        EditReceipt placed = service.edit(request(new PlacePart(Optional.of("memory"), "rom", 0, 0, Rotation.R0,
                Map.of("words", "16", "width", 8), "")));
        memoryId = placed.aliases().get("memory");
    }

    @Test
    void threeWordsAreSetInOneUndoStep() {
        service.edit(request(new SetMemoryContents(memoryId, 2, List.of("0xAB", "cd", "1"))));

        assertThat(contents().word(2)).isEqualTo(0xAB);
        assertThat(contents().word(3)).isEqualTo(0xCD);
        assertThat(contents().word(4)).isEqualTo(1);
        assertThat(contents().count()).isEqualTo(3);
        circuit.history().undo();
        assertThat(contents().count()).isZero();
    }

    @Test
    void otherWordsKeepTheirValue() {
        service.edit(request(new SetMemoryContents(memoryId, 0, List.of("11", "22"))));
        service.edit(request(new SetMemoryContents(memoryId, 1, List.of("33"))));

        assertThat(contents().word(0)).isEqualTo(0x11);
        assertThat(contents().word(1)).isEqualTo(0x33);
    }

    @Test
    void aWordWiderThanTheWidthRefusesTheWholeCall() {
        assertThat(refused(new SetMemoryContents(memoryId, 0, List.of("01", "1FF"))).error())
                .isEqualTo(AutomationError.INVALID_PARAMETER);

        assertThat(contents().count()).isZero();
    }

    @Test
    void wordsPastTheLastAddressOrThatAreNotHexAreRefused() {
        assertThat(refused(new SetMemoryContents(memoryId, 15, List.of("01", "02"))).error())
                .isEqualTo(AutomationError.INVALID_PARAMETER);
        assertThat(refused(new SetMemoryContents(memoryId, 0, List.of("xyz"))).error())
                .isEqualTo(AutomationError.INVALID_PARAMETER);
    }

    @Test
    void aLinkedMemoryIsRefusedWithAMessageForTheUser() {
        Component memory = circuit.document().component(new ElementId(memoryId)).orElseThrow();
        Map<String, Object> parameters = new HashMap<>(memory.parameters());
        parameters.put(MemoryTypes.LINK, "rom.hex");
        circuit.document().replace(new Component(memory.id(), memory.typeId(), memory.position(),
                memory.rotation(), parameters, memory.label()));

        AutomationException refusal = refused(new SetMemoryContents(memoryId, 0, List.of("01")));

        assertThat(refusal.error()).isEqualTo(AutomationError.NOT_ALLOWED);
        assertThat(refusal.getMessage()).contains("ask the user");
    }

    @Test
    void aPartThatIsNotAMemoryIsRefused() {
        EditReceipt gate = service.edit(request(new PlacePart(Optional.of("gate"), "nand2", 30, 30, Rotation.R0,
                Map.of(), "")));

        assertThat(refused(new SetMemoryContents(gate.aliases().get("gate"), 0, List.of("1"))).error())
                .isEqualTo(AutomationError.INVALID_REFERENCE);
    }

    @Test
    void theAiCannotSetTheContentsOrTheLinkAsParameters() {
        assertThat(refused(new SetParameters(memoryId, Map.of("contents", "@0 1"))).error())
                .isEqualTo(AutomationError.NOT_ALLOWED);
        assertThat(refused(new SetParameters(memoryId, Map.of("link", "x.hex"))).error())
                .isEqualTo(AutomationError.NOT_ALLOWED);
        assertThat(refused(new PlacePart(Optional.empty(), "ram", 0, 40, Rotation.R0, Map.of("link", "x.hex"), ""))
                .error()).isEqualTo(AutomationError.NOT_ALLOWED);
    }

    private MemoryContents contents() {
        Component memory = circuit.document().component(new ElementId(memoryId)).orElseThrow();
        Map<String, Object> resolved = circuit.registry().typeOf(memory).resolveParameters(memory.parameters());
        return MemoryContents.decode((String) resolved.get(MemoryTypes.CONTENTS));
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
