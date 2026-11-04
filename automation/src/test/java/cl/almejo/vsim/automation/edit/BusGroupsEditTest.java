package cl.almejo.vsim.automation.edit;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.edit.EditOperation.AddWire;
import cl.almejo.vsim.automation.edit.EditOperation.PlacePart;
import cl.almejo.vsim.automation.edit.EditOperation.SetParameters;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The AI changing a «Separador de bus»'s groups or «Ancho» follows the editor's rules: a pin never joins a wire of its
 * own width it was not on, and the groups are stored in their canonical text.
 */
class BusGroupsEditTest {

    private static final String DOCUMENT = "circuit-1";
    private static final String SPLITTER = "splitter";

    private final FakeCircuitHost host = new FakeCircuitHost();
    private final FakeCircuitHost.Circuit circuit = host.open(DOCUMENT);
    private final EditService service = new EditService(host, new CopyOnWriteArrayList<>(), Duration.ofSeconds(5),
            Runnable::run);
    private int nextOperationId = 1;

    /** An 8-bit splitter at (20, 10) with groups {@code groups}; returns its id. */
    private String splitter(String groups, EditOperation... more) {
        List<EditOperation> operations = new ArrayList<>();
        operations.add(new PlacePart(Optional.of(SPLITTER), StandardComponentTypes.BUS_SPLITTER, 20, 10, Rotation.R0,
                Map.of(StandardComponentTypes.WIDTH, 8, StandardComponentTypes.GROUPS, groups), ""));
        operations.addAll(List.of(more));
        return service.edit(request(operations.toArray(EditOperation[]::new))).aliases().get(SPLITTER);
    }

    /** A NOT gate per bit, each wired straight to its one-bit pin (the review's layout). */
    private EditOperation[] readerOnEveryBit() {
        List<EditOperation> operations = new ArrayList<>();
        for (int bit = 0; bit < 8; bit++) {
            int x = 40 + 8 * bit;
            operations.add(new PlacePart(Optional.empty(), StandardComponentTypes.BUFFER, x, 9 + bit, Rotation.R0,
                    Map.of(StandardComponentTypes.NEGATE, true), ""));
            operations.add(new AddWire(List.of(new GridPoint(22, 11 + bit), new GridPoint(x, 11 + bit))));
        }
        return operations.toArray(EditOperation[]::new);
    }

    private Component part(String id) {
        return circuit.document().components().stream().filter(component -> component.id().value().equals(id))
                .findFirst().orElseThrow();
    }

    private Object parameter(String id, String name) {
        return part(id).parameters().get(name);
    }

    @Test
    void groupsThatPutAPinOnAnotherBitsWireAreRefusedNamingThePin() {
        String id = splitter("", readerOnEveryBit());

        assertThatThrownBy(() -> service.edit(request(new SetParameters(id,
                Map.of(StandardComponentTypes.GROUPS, "0-3|4|5|6|7")))))
                .isInstanceOfSatisfying(AutomationException.class, refusal -> {
                    assertThat(refusal.error()).isEqualTo(AutomationError.INVALID_PARAMETER);
                    assertThat(refusal.getMessage()).contains("b4").contains("same width");
                });
        assertThat(parameter(id, StandardComponentTypes.GROUPS)).isEqualTo("");
    }

    @Test
    void groupsWhosePinsOnlyTouchWiresOfAnotherWidthAreAllowed() {
        String id = splitter("", readerOnEveryBit());

        service.edit(request(new SetParameters(id, Map.of(StandardComponentTypes.GROUPS, "0-3|4-7"))));

        assertThat(parameter(id, StandardComponentTypes.GROUPS)).isEqualTo("0-3|4-7");
    }

    @Test
    void aNarrowerBusWhosePinKeepsItsWireIsAllowed() {
        String id = splitter("0-3|4-7",
                new AddWire(List.of(new GridPoint(23, 11), new GridPoint(30, 11))),
                new AddWire(List.of(new GridPoint(23, 13), new GridPoint(30, 13))));

        service.edit(request(new SetParameters(id, Map.of(StandardComponentTypes.WIDTH, 6,
                StandardComponentTypes.GROUPS, "0-3|4-5"))));

        assertThat(parameter(id, StandardComponentTypes.GROUPS)).isEqualTo("0-3|4-5");
    }

    @Test
    void groupsWithABitBeyondTheBusAreRefusedInWordsNamingTheBusWidth() {
        String id = splitter("");

        assertThatThrownBy(() -> service.edit(request(new SetParameters(id,
                Map.of(StandardComponentTypes.GROUPS, "0-3|4-40")))))
                .isInstanceOfSatisfying(AutomationException.class, refusal -> {
                    assertThat(refusal.error()).isEqualTo(AutomationError.INVALID_PARAMETER);
                    assertThat(refusal.getMessage()).contains("bit 40").contains("8 bits")
                            .doesNotContain("groups.bitOutOfRange");
                });
    }

    @Test
    void aPlacedPartWithGroupsInTwoPinsIsRefusedInWords() {
        assertThatThrownBy(() -> splitter("0-4|4-7"))
                .isInstanceOfSatisfying(AutomationException.class, refusal ->
                        assertThat(refusal.getMessage()).contains("bit 4").contains("two pins")
                                .doesNotContain("groups.bitInTwoPins"));
    }

    @Test
    void aWiderBusGivenOnlyItsWidthKeepsEqualPinsEqual() {
        String id = splitter("0-3|4-7");

        service.edit(request(new SetParameters(id, Map.of(StandardComponentTypes.WIDTH, 16))));

        assertThat(parameter(id, StandardComponentTypes.GROUPS)).isEqualTo("0-3|4-7|8-11|12-15");
    }

    @Test
    void aNarrowerBusGivenOnlyItsWidthDropsTheBitsItNoLongerHas() {
        String id = splitter("0-3|4-7");

        service.edit(request(new SetParameters(id, Map.of(StandardComponentTypes.WIDTH, 4))));

        assertThat(parameter(id, StandardComponentTypes.GROUPS)).isEqualTo("0-3");
        assertThat(parameter(id, StandardComponentTypes.WIDTH)).isEqualTo(4);
    }

    @Test
    void aWidthGivenWithGroupsKeepsTheGroupsGiven() {
        String id = splitter("0-3|4-7");

        service.edit(request(new SetParameters(id, Map.of(StandardComponentTypes.WIDTH, 16,
                StandardComponentTypes.GROUPS, "0-3|4-7"))));

        assertThat(parameter(id, StandardComponentTypes.GROUPS)).isEqualTo("0-3|4-7");
    }

    @Test
    void groupsOfOneBitPerPinAreStoredAsTheEmptyText() {
        String id = splitter("0-3|4-7");

        service.edit(request(new SetParameters(id, Map.of(StandardComponentTypes.GROUPS, "0|1|2|3|4|5|6|7"))));

        assertThat(parameter(id, StandardComponentTypes.GROUPS)).isEqualTo("");
    }

    @Test
    void aPlacedPartWithOneBitPerPinStoresTheEmptyText() {
        String id = splitter("0|1|2|3|4|5|6|7");

        assertThat(parameter(id, StandardComponentTypes.GROUPS)).isEqualTo("");
    }

    private EditRequest request(EditOperation... operations) {
        return new EditRequest(new DocumentId(DOCUMENT), circuit.version(), "op-" + nextOperationId++,
                Optional.empty(), List.of(operations), false);
    }
}
