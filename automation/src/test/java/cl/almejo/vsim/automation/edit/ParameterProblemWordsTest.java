package cl.almejo.vsim.automation.edit;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.edit.EditOperation.PlacePart;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A parameter the part's own check refuses reaches the AI as a sentence, never as a bundle key. */
class ParameterProblemWordsTest {

    private final FakeCircuitHost host = new FakeCircuitHost();
    private EditService service;
    private FakeCircuitHost.Circuit circuit;
    private int nextOperationId = 1;

    @BeforeEach
    void anEmptyCircuit() {
        circuit = host.open("circuit-1");
        service = new EditService(host, new CopyOnWriteArrayList<>(), Duration.ofSeconds(5), Runnable::run);
    }

    private String refusalOf(String typeId, Map<String, Object> parameters) {
        EditRequest request = new EditRequest(new DocumentId("circuit-1"), circuit.version(),
                "op-" + nextOperationId++, Optional.empty(),
                List.of(new PlacePart(Optional.empty(), typeId, 0, 0, Rotation.R0, parameters, "")), false);
        try {
            service.edit(request);
        } catch (AutomationException refusal) {
            assertThat(refusal.error()).isEqualTo(AutomationError.INVALID_PARAMETER);
            return refusal.getMessage();
        }
        throw new AssertionError("expected a refusal");
    }

    @Test
    void aConstantThatDoesNotFitSaysHowManyBitsItNeeds() {
        assertThat(refusalOf(StandardComponentTypes.CONSTANT, Map.of("value", "'A'", "width", 1)))
                .contains("value 'A' does not fit in 1 bit (needs 7)").contains("width")
                .doesNotContain("constant.doesNotFit");
    }

    @Test
    void aConstantWhoseValueIsNotANumberSaysSo() {
        assertThat(refusalOf(StandardComponentTypes.CONSTANT, Map.of("value", "zz")))
                .contains("value \"zz\" is not a number").doesNotContain("constant.invalidValue");
    }

    @Test
    void aSwitchWithZOnAWideBusSaysSo() {
        assertThat(refusalOf(StandardComponentTypes.SWITCH, Map.of("value", "Z", "width", 4)))
                .contains("high impedance").contains("1-bit").doesNotContain("switch.noZ");
    }

    @Test
    void groupsThatDoNotFitStillSayWhy() {
        assertThat(refusalOf(StandardComponentTypes.BUS_SPLITTER, Map.of("width", 8, "groups", "0-3|40")))
                .contains("bit 40 does not exist on a bus of 8 bits");
    }
}
