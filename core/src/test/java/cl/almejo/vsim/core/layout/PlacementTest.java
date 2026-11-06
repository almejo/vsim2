package cl.almejo.vsim.core.layout;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.wiring.Connectivity;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import static cl.almejo.vsim.core.DocumentFixtures.gate;
import static cl.almejo.vsim.core.DocumentFixtures.wire;
import static org.assertj.core.api.Assertions.assertThat;

class PlacementTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();

    private static Set<ElementId> everything(CircuitDocument document) {
        Set<ElementId> ids = new LinkedHashSet<>();
        document.elements().forEach(element -> ids.add(element.id()));
        return ids;
    }

    private Placement placementOn(CircuitDocument document) {
        LayoutItems items = LayoutItems.of(document, registry, everything(document), TextBoxes.NONE);
        return new Placement(document, registry, items, nets(document));
    }

    private Set<Set<PinRef>> nets(CircuitDocument document) {
        return Connectivity.of(document.wires(), document, registry).pinNets();
    }

    private static Set<DocumentElement> elements(CircuitDocument document) {
        return Set.copyOf(document.elements());
    }

    @Test
    void aMoveToAFreePlaceCarriesTheWiresAndIsRemembered() {
        CircuitDocument document = new CircuitDocument();
        Component driver = gate(registry, document, 0, 0);
        Component receiver = gate(registry, document, 10, 0);
        wire(document, 4, 2, 10, 2);
        Placement placement = placementOn(document);
        LayoutItems.Item item = placement.items().item(receiver.id()).orElseThrow();

        boolean moved = placement.tryMove(item, new GridPoint(0, 3));

        assertThat(moved).isTrue();
        assertThat(document.component(receiver.id()).orElseThrow().position()).isEqualTo(new GridPoint(10, 3));
        assertThat(placement.current(item)).isEqualTo(new Bounds(10, 3, 4, 4));
        assertThat(nets(document)).contains(Set.of(new PinRef(driver.id(), 1), new PinRef(receiver.id(), 0)));
        assertThat(document.component(driver.id()).orElseThrow()).isEqualTo(driver);
    }

    @Test
    void aMoveOntoANeighbourIsRefusedAndChangesNothing() {
        CircuitDocument document = new CircuitDocument();
        gate(registry, document, 0, 0);
        Component mover = gate(registry, document, 0, 6);
        Set<DocumentElement> before = elements(document);
        Placement placement = placementOn(document);

        boolean moved = placement.tryMove(placement.items().item(mover.id()).orElseThrow(), new GridPoint(0, -5));

        assertThat(moved).isFalse();
        assertThat(elements(document)).isEqualTo(before);
    }

    @Test
    void aMoveThatJoinsAForeignWireIsUndone() {
        CircuitDocument document = new CircuitDocument();
        Component mover = gate(registry, document, 0, 0);
        wire(document, 6, 5, 20, 5);
        gate(registry, document, 20, 3);
        Set<DocumentElement> before = elements(document);
        Placement placement = placementOn(document);

        boolean moved = placement.tryMove(placement.items().item(mover.id()).orElseThrow(), new GridPoint(2, 3));

        assertThat(moved).isFalse();
        assertThat(elements(document)).isEqualTo(before);
    }

    @Test
    void jumpingOverTheNeighbourBelowBreaksTheColumnOrder() {
        CircuitDocument document = new CircuitDocument();
        Component mover = gate(registry, document, 0, 0);
        gate(registry, document, 1, 6);
        Placement placement = placementOn(document);
        LayoutItems.Item item = placement.items().item(mover.id()).orElseThrow();

        assertThat(placement.keepsOrder(item, new Bounds(0, 12, 4, 4))).isFalse();
        assertThat(placement.keepsOrder(item, new Bounds(0, 1, 4, 4))).isTrue();
    }

    @Test
    void passingTheNeighbourOnTheRightBreaksTheRowOrder() {
        CircuitDocument document = new CircuitDocument();
        Component mover = gate(registry, document, 0, 0);
        gate(registry, document, 6, 1);
        Placement placement = placementOn(document);
        LayoutItems.Item item = placement.items().item(mover.id()).orElseThrow();

        assertThat(placement.keepsOrder(item, new Bounds(12, 0, 4, 4))).isFalse();
        assertThat(placement.keepsOrder(item, new Bounds(1, 0, 4, 4))).isTrue();
    }

    @Test
    void aMoveThatBendsAStraightWireIsUndoneWhenCornersMayNotGrow() {
        CircuitDocument document = new CircuitDocument();
        gate(registry, document, 0, 0);
        Component receiver = gate(registry, document, 10, 0);
        wire(document, 4, 2, 10, 2);
        Set<DocumentElement> before = elements(document);
        Placement placement = placementOn(document);
        LayoutItems.Item item = placement.items().item(receiver.id()).orElseThrow();

        boolean moved = placement.tryTidyingMove(item, new GridPoint(0, 3));

        assertThat(moved).isFalse();
        assertThat(elements(document)).isEqualTo(before);
        assertThat(placement.current(item)).isEqualTo(item.bounds());
    }

    /** Free texts six units wide and two high at their position; parts have no label. */
    private static final TextBoxes SIX_BY_TWO_TEXTS = new TextBoxes() {
        @Override
        public Optional<Bounds> componentLabel(Component component) {
            return Optional.empty();
        }

        @Override
        public Bounds freeText(TextLabel label) {
            return new Bounds(label.position().x(), label.position().y(), 6, 2);
        }
    };

    @Test
    void aTidyingMoveNeverPutsABodyOverAText() {
        CircuitDocument document = new CircuitDocument();
        Component mover = gate(registry, document, 0, 0);
        document.add(new TextLabel(document.newLabelId(), new GridPoint(0, 6), "nota"));
        Set<DocumentElement> before = elements(document);
        LayoutItems items = LayoutItems.of(document, registry, everything(document), SIX_BY_TWO_TEXTS);
        Placement placement = new Placement(document, registry, items, nets(document));

        boolean moved = placement.tryTidyingMove(items.item(mover.id()).orElseThrow(), new GridPoint(0, 3));

        assertThat(moved).isFalse();
        assertThat(elements(document)).isEqualTo(before);
    }
}
