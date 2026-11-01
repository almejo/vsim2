package cl.almejo.vsim.automation.edit;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.edit.EditOperation.AddWire;
import cl.almejo.vsim.automation.edit.EditOperation.PlacePart;
import cl.almejo.vsim.automation.edit.EditOperation.SetParameters;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.geometry.GridPoint;
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

/** The AI turning «Mostrar Q̅» on over a Q wire gets an error, never a silent short; a clean part turns it on. */
class ShowInvertedTest {

    private static final String SHOW = StandardComponentTypes.SHOW_INVERTED;

    private final FakeCircuitHost host = new FakeCircuitHost();
    private EditService service;
    private FakeCircuitHost.Circuit circuit;
    private int nextOperationId = 1;
    private String flipFlopId;

    @BeforeEach
    void anOldFlipFlopWithoutQBar() {
        circuit = host.open("circuit-1");
        service = new EditService(host, new CopyOnWriteArrayList<>(), Duration.ofSeconds(5), Runnable::run);
        service.edit(request(new PlacePart(Optional.of("ff"), StandardComponentTypes.FLIP_FLOP_DATA, 0, 0,
                Rotation.R0, Map.of(SHOW, false), "")));
        flipFlopId = circuit.document().components().getFirst().id().value();
    }

    private EditRequest request(EditOperation... operations) {
        return new EditRequest(new DocumentId("circuit-1"), circuit.version(), "op-" + nextOperationId++,
                Optional.empty(), List.of(operations), false);
    }

    private boolean isShown() {
        return circuit.document().components().getFirst().parameters().get(SHOW).equals(true);
    }

    @Test
    void aQWireOverTheFuturePinRefusesWithAClearErrorAndChangesNothing() {
        service.edit(request(new AddWire(List.of(new GridPoint(4, 2), new GridPoint(4, 6)))));
        long versionBefore = circuit.version().value();

        assertThatThrownBy(() -> service.edit(request(new SetParameters(flipFlopId, Map.of(SHOW, true)))))
                .isInstanceOfSatisfying(AutomationException.class, refusal -> {
                    assertThat(refusal.error()).isEqualTo(AutomationError.INVALID_PARAMETER);
                    assertThat(refusal.getMessage()).contains("nQ").contains("wire");
                });
        assertThat(isShown()).isFalse();
        assertThat(circuit.version().value()).isEqualTo(versionBefore);
    }

    @Test
    void aCleanFlipFlopTurnsItOn() {
        service.edit(request(new SetParameters(flipFlopId, Map.of(SHOW, true))));

        assertThat(isShown()).isTrue();
    }
}
