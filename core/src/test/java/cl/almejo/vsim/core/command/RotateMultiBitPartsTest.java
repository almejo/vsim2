package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.MultiBitLayout;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.PinRef;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cl.almejo.vsim.core.DocumentFixtures.gate;
import static org.assertj.core.api.Assertions.assertThat;

/** A quarter turn of a multi-bit Switch or LED moves its pin to the next side; the body never turns. */
class RotateMultiBitPartsTest {

    private static final int WIDTH = 8;

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitDocument document = new CircuitDocument();

    private Component place(String typeId, int x, int y, Map<String, Object> parameters) {
        Component component = registry.require(typeId).newComponent(document.newComponentId(), new GridPoint(x, y),
                Rotation.R0, parameters, "");
        document.add(component);
        return component;
    }

    private Component current(ElementId id) {
        return document.component(id).orElseThrow();
    }

    private String sideOf(ElementId id) {
        Component component = current(id);
        return MultiBitLayout.sideOf(registry.require(component.typeId()), component.parameters());
    }

    private GridPoint pin(ElementId id) {
        Component component = current(id);
        return registry.require(component.typeId()).pinLocation(component, 0);
    }

    /** Where the upright body's top-left corner is on the grid. */
    private GridPoint bodyCorner(ElementId id) {
        return current(id).position().plus(MultiBitLayout.bodyOrigin(WIDTH, sideOf(id)));
    }

    private void rotate(boolean clockwise, ElementId... ids) {
        new RotateElementsCommand(List.of(ids), clockwise, registry).apply(document);
    }

    @Test
    void aLoneMultiBitSwitchMovesItsPinToTheNextSideClockwiseAndItsBodyStaysPut() {
        Component wide = place(StandardComponentTypes.SWITCH, 10, 10, Map.of("width", WIDTH));
        GridPoint corner = bodyCorner(wide.id());

        List<String> sides = new ArrayList<>();
        for (int turn = 0; turn < 4; turn++) {
            rotate(true, wide.id());
            sides.add(sideOf(wide.id()));
            assertThat(current(wide.id()).rotation()).isEqualTo(Rotation.R0);
            assertThat(bodyCorner(wide.id())).isEqualTo(corner);
        }

        assertThat(sides).containsExactly("bottom", "left", "top", "right");
        assertThat(current(wide.id())).isEqualTo(wide);
    }

    @Test
    void aLoneMultiBitLedTurnsTheSameWayFromItsLeftSide() {
        Component wide = place(StandardComponentTypes.LED, 10, 10, Map.of("width", WIDTH));

        List<String> sides = new ArrayList<>();
        for (int turn = 0; turn < 4; turn++) {
            rotate(true, wide.id());
            sides.add(sideOf(wide.id()));
        }

        assertThat(sides).containsExactly("top", "right", "bottom", "left");
        assertThat(current(wide.id())).isEqualTo(wide);
    }

    @Test
    void counterClockwiseMovesThePinTheOtherWayAndFourTurnsComeBack() {
        Component wide = place(StandardComponentTypes.SWITCH, 3, 5, Map.of("width", WIDTH));

        rotate(false, wide.id());
        assertThat(sideOf(wide.id())).isEqualTo("top");
        for (int turn = 1; turn < 4; turn++) {
            rotate(false, wide.id());
        }

        assertThat(current(wide.id())).isEqualTo(wide);
    }

    @Test
    void aOneBitSwitchAndLedStillTurnTheWholePart() {
        Component narrowSwitch = place(StandardComponentTypes.SWITCH, 0, 0, Map.of());
        Component narrowLed = place(StandardComponentTypes.LED, 20, 0, Map.of());

        rotate(true, narrowSwitch.id());
        rotate(true, narrowLed.id());

        assertThat(current(narrowSwitch.id()).rotation()).isEqualTo(Rotation.R90);
        assertThat(sideOf(narrowSwitch.id())).isEqualTo("right");
        assertThat(current(narrowLed.id()).rotation()).isEqualTo(Rotation.R90);
        assertThat(sideOf(narrowLed.id())).isEqualTo("left");
    }

    @Test
    void inAGroupTheBodyStaysUprightAndThePinFollowsTheTurnSoTheWireStaysOnIt() {
        Component wide = place(StandardComponentTypes.SWITCH, 0, 0, Map.of("width", WIDTH));
        GridPoint start = pin(wide.id());
        Wire wire = new Wire(document.newWireId(), start, start.translate(4, 0));
        document.add(wire);
        Component not = gate(registry, document, start.x() + 4, start.y() - 2);

        rotate(true, wide.id(), wire.id(), not.id());

        Component turnedSwitch = current(wide.id());
        assertThat(turnedSwitch.rotation()).isEqualTo(Rotation.R0);
        assertThat(sideOf(wide.id())).isEqualTo("bottom");
        assertThat(current(not.id()).rotation()).isEqualTo(Rotation.R90);
        Wire turnedWire = document.wire(wire.id()).orElseThrow();
        assertThat(List.of(turnedWire.start(), turnedWire.end())).contains(pin(wide.id()));
        assertThat(turnedWire.start().x()).isEqualTo(turnedWire.end().x());
        assertThat(new NetlistCompiler(registry).compile(document).netForWire(wire.id()).orElseThrow().pins())
                .containsExactlyInAnyOrder(new PinRef(wide.id(), 0), new PinRef(not.id(), 0));
    }

    @Test
    void undoGivesBackTheSideAndThePlaceAndRedoTurnsAgain() {
        Component wide = place(StandardComponentTypes.SWITCH, 0, 0, Map.of("width", WIDTH));
        Component not = gate(registry, document, 30, 0);
        Set<DocumentElement> before = Set.copyOf(document.elements());
        History history = new History(document);

        history.execute(new RotateElementsCommand(List.of(wide.id(), not.id()), true, registry));
        Set<DocumentElement> after = Set.copyOf(document.elements());
        history.undo();
        assertThat(Set.copyOf(document.elements())).isEqualTo(before);
        history.redo();

        assertThat(Set.copyOf(document.elements())).isEqualTo(after);
        assertThat(sideOf(wide.id())).isEqualTo("bottom");
    }

    @Test
    void aMultiBitPartThatStillCarriesARotationComesOutUprightAfterATurn() {
        Component stale = registry.require(StandardComponentTypes.SWITCH).newComponent(document.newComponentId(),
                new GridPoint(10, 10), Rotation.R90, Map.of("width", WIDTH), "");
        document.add(stale);

        rotate(true, stale.id());

        assertThat(current(stale.id()).rotation()).isEqualTo(Rotation.R0);
        assertThat(sideOf(stale.id())).isEqualTo("left");
    }
}
