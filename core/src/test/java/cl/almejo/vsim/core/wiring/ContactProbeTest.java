package cl.almejo.vsim.core.wiring;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static cl.almejo.vsim.core.DocumentFixtures.wire;
import static org.assertj.core.api.Assertions.assertThat;

/** Alignment guides spec §3.7: the contacts a move, a copy or a ghost would make. */
class ContactProbeTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitDocument document = new CircuitDocument();

    /** A NOT gate: input pin at (x, y + 2), output pin at (x + 4, y + 2). */
    private Component gate(int x, int y) {
        return component(StandardComponentTypes.NOT, x, y);
    }

    /** An LED: its only pin at (x + 1, y + 3). */
    private Component led(int x, int y) {
        return component(StandardComponentTypes.LED, x, y);
    }

    private Component component(String typeId, int x, int y) {
        Component component = new Component(document.newComponentId(), typeId, new GridPoint(x, y), Rotation.R0,
                Map.of(), "");
        document.add(component);
        return component;
    }

    private List<NewContact> moving(int deltaX, int deltaY, DocumentElement... elements) {
        Set<ElementId> ids = new HashSet<>();
        for (DocumentElement element : elements) {
            ids.add(element.id());
        }
        return ContactProbe.newContacts(document, registry, List.of(elements), deltaX, deltaY, ids);
    }

    private static String pinName(Component component, int pinIndex) {
        return StandardComponentTypes.registry().typeOf(component).pin(pinIndex).name();
    }

    @Test
    void aPinDroppedOnAnotherNetsWireIsOneContact() {
        Component gate = gate(0, 0);
        wire(document, 10, 0, 10, 10);
        assertThat(moving(6, 0, gate)).containsExactly(
                new NewContact(new GridPoint(10, 2), Optional.of(gate.id()), Optional.of(pinName(gate, 1)),
                        Optional.of(pinName(gate, 1)), Optional.empty()));
    }

    @Test
    void aPinDroppedOnAnotherPartsPinNamesBothTheMovingPinAndThePinItMeets() {
        Component gate = gate(0, 0);
        Component lamp = led(9, -1); // its pin at (10, 2)
        assertThat(moving(6, 0, gate)).containsExactly(
                new NewContact(new GridPoint(10, 2), Optional.of(lamp.id()), Optional.of(pinName(lamp, 0)),
                        Optional.of(pinName(gate, 1)), Optional.of(pinName(lamp, 0))));
    }

    @Test
    void aPinDroppedOnItsOwnNetIsNoContact() {
        Component gate = gate(0, 0);
        wire(document, 4, 2, 20, 2);
        assertThat(moving(2, 0, gate)).isEmpty();
    }

    @Test
    void aPinDroppedOnAWireJoinedToItFarAwayIsNoContact() {
        Component gate = gate(0, 0);
        wire(document, 4, 2, 4, 100);
        wire(document, 4, 100, 30, 100);
        wire(document, 30, 0, 30, 100);
        assertThat(moving(26, 0, gate)).isEmpty();
    }

    @Test
    void aWireEndDroppedOnAPinNamesThePinThatStays() {
        Component lamp = led(0, 0);
        Wire moved = wire(document, 5, 3, 8, 3);
        assertThat(moving(-4, 0, moved)).containsExactly(
                new NewContact(new GridPoint(1, 3), Optional.of(lamp.id()), Optional.of(pinName(lamp, 0)),
                        Optional.empty(), Optional.of(pinName(lamp, 0))));
    }

    @Test
    void aMovedWiresInteriorOverAWireEndIsAContactButACrossingIsNot() {
        Wire moved = wire(document, 0, 0, 0, 10);
        wire(document, 5, 4, 9, 4);
        wire(document, 3, 8, 8, 8);
        assertThat(moving(5, 0, moved)).containsExactly(
                new NewContact(new GridPoint(5, 4), Optional.empty(),
                        Optional.empty(), Optional.empty(), Optional.empty()));
    }

    @Test
    void aCopyTouchesItsOriginals() {
        Wire original = wire(document, 0, 0, 4, 0);
        List<NewContact> copy = ContactProbe.newContacts(document, registry, List.of(original), 2, 0, Set.of());
        assertThat(copy).extracting(NewContact::point).containsExactly(new GridPoint(2, 0), new GridPoint(4, 0));
        assertThat(moving(2, 0, original)).isEmpty();
    }

    @Test
    void aGhostNotInTheDocumentIsProbedEvenWhenItsIdsCollide() {
        Wire existing = wire(document, 10, 0, 10, 10);
        Component ghost = new Component(existing.id(), StandardComponentTypes.NOT, new GridPoint(0, 0), Rotation.R0,
                Map.of(), "");
        assertThat(ContactProbe.newContacts(document, registry, List.of(ghost), 6, 0, Set.of())).containsExactly(
                new NewContact(new GridPoint(10, 2), Optional.of(existing.id()), Optional.of(pinName(ghost, 1)),
                        Optional.of(pinName(ghost, 1)), Optional.empty()));
    }

    @Test
    void aGhostsOwnPiecesNeverTouchEachOther() {
        Component ghostGate = new Component(new ElementId("ghost-gate"), StandardComponentTypes.NOT,
                new GridPoint(0, 0), Rotation.R0, Map.of(), "");
        Wire ghostWire = new Wire(new ElementId("ghost-wire"), new GridPoint(4, 2), new GridPoint(8, 2));
        assertThat(ContactProbe.newContacts(document, registry, List.of(ghostGate, ghostWire), 3, 3, Set.of()))
                .isEmpty();
    }

    @Test
    void aMoveThatWiresDoNotFollowAgreesWithWireFollow() {
        Component gate = gate(10, 10);
        wire(document, 0, 20, 30, 20);
        led(20, 9);
        FollowPlan plan = WireFollow.plan(document, registry, Set.of(gate.id()), 0, 8);
        assertThat(moving(0, 8, gate)).isEqualTo(plan.newContacts()).hasSize(2);
    }

    @Test
    void theVisitedElementsStayBoundedByTheMovedBox() {
        for (int index = 0; index < 1000; index++) {
            int column = index % 40;
            int row = index / 40;
            gate(column * 20, row * 20);
            wire(document, column * 20 + 4, row * 20 + 2, column * 20 + 10, row * 20 + 2);
        }
        Component moved = document.components().get(0);
        ContactProbe probe = ContactProbe.of(document, registry);
        List<NewContact> contacts = probe.newContacts(List.of(moved), 6, 0, Set.of(moved.id()));
        assertThat(contacts).extracting(NewContact::point).containsExactly(new GridPoint(6, 2));
        assertThat(probe.lastVisitedCount()).isPositive().isLessThanOrEqualTo(4);
    }

    @Test
    void labelsAndAZeroDeltaMoveMakeNoContact() {
        Component gate = gate(0, 0);
        wire(document, 4, 2, 20, 2);
        assertThat(moving(0, 0, gate)).isEmpty();
        assertThat(ContactProbe.newContacts(document, registry, List.of(), 3, 0, Set.of())).isEmpty();
    }
}
