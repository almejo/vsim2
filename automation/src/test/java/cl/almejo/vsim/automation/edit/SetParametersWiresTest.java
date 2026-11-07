package cl.almejo.vsim.automation.edit;

import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.edit.EditOperation.AddWire;
import cl.almejo.vsim.automation.edit.EditOperation.PlacePart;
import cl.almejo.vsim.automation.edit.EditOperation.SetParameters;
import cl.almejo.vsim.core.component.standard.ConstantValue;
import cl.almejo.vsim.core.component.standard.KeyboardTypes;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** An AI parameter edit that moves pins carries their wires along, as the inspector and dragging an edge do. */
class SetParametersWiresTest {

    private static final String DOCUMENT = "circuit-1";

    private final FakeCircuitHost host = new FakeCircuitHost();
    private final FakeCircuitHost.Circuit circuit = host.open(DOCUMENT);
    private final EditService service = new EditService(host, new CopyOnWriteArrayList<>(), Duration.ofSeconds(5),
            Runnable::run);
    private int nextOperationId = 1;

    @Test
    void aWiderTecladoCarriesTheWireOfAv() {
        EditReceipt placed = service.edit(request(
                new PlacePart(Optional.of("teclado"), StandardComponentTypes.KEYBOARD, 10, 10, Rotation.R0, Map.of(),
                        ""),
                new PlacePart(Optional.empty(), StandardComponentTypes.BUFFER, 40, 10, Rotation.R0, Map.of(), ""),
                new AddWire(List.of(new GridPoint(26, 12), new GridPoint(40, 12)))));

        service.edit(request(new SetParameters(placed.aliases().get("teclado"),
                Map.of(KeyboardTypes.PART_WIDTH, 24))));

        List<GridPoint> wireEnds = circuit.document().wires().stream()
                .flatMap(wire -> Stream.of(wire.start(), wire.end())).toList();
        assertThat(wireEnds).contains(new GridPoint(34, 12)).doesNotContain(new GridPoint(26, 12));
    }

    @Test
    void anAiChangingOnlyAConstantsFormatKeepsTheBitsItDrives() {
        EditReceipt placed = service.edit(request(new PlacePart(Optional.of("constante"),
                StandardComponentTypes.CONSTANT, 10, 10, Rotation.R0, Map.of(StandardComponentTypes.WIDTH, 16,
                StandardComponentTypes.VALUE, "10"), "")));

        service.edit(request(new SetParameters(placed.aliases().get("constante"),
                Map.of(ConstantValue.FORMAT, ConstantValue.FIXED, ConstantValue.FRACTION, 4))));

        assertThat(circuit.document().components().getFirst().parameters())
                .containsEntry(StandardComponentTypes.VALUE, "0.625");
    }

    private EditRequest request(EditOperation... operations) {
        return new EditRequest(new DocumentId(DOCUMENT), circuit.version(), "op-" + nextOperationId++,
                Optional.empty(), List.of(operations), false);
    }
}
