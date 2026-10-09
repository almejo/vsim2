package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CircuitFragmentTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitDocument document = new CircuitDocument();
    private Component gate;
    private Wire wire;
    private TextLabel label;

    @BeforeEach
    void createDocument() {
        gate = registry.require(StandardComponentTypes.NOT).newComponent(document.newComponentId(),
                new GridPoint(10, 6), Rotation.R0, Map.of(), "U1");
        wire = new Wire(document.newWireId(), new GridPoint(14, 8), new GridPoint(20, 8));
        label = new TextLabel(document.newLabelId(), new GridPoint(12, 4), "nota");
        Component outsider = registry.require(StandardComponentTypes.NOT).newComponent(document.newComponentId(),
                new GridPoint(40, 40), Rotation.R0, Map.of(), "");
        document.add(gate);
        document.add(wire);
        document.add(label);
        document.add(outsider);
    }

    @Test
    void takesOnlyTheListedElementsMovedToTheOrigin() {
        CircuitFragment fragment = CircuitFragment.of(document,
                List.of(label.id(), wire.id(), gate.id(), new ElementId("c99")), registry);
        assertThat(fragment.components()).containsExactly(gate.translate(-10, -4));
        assertThat(fragment.wires()).containsExactly(wire.translate(-10, -4));
        assertThat(fragment.labels()).containsExactly(label.translate(-10, -4));
        assertThat(fragment.bounds(registry)).isEqualTo(new Bounds(0, 0, 10, 6));
        assertThat(fragment.isEmpty()).isFalse();
        assertThat(fragment.elements()).hasSize(3);
    }

    @Test
    void noIdsGiveAnEmptyFragment() {
        CircuitFragment fragment = CircuitFragment.of(document, List.of(), registry);
        assertThat(fragment.isEmpty()).isTrue();
        assertThat(fragment.bounds(registry)).isEqualTo(new Bounds(0, 0, 0, 0));
    }

    @Test
    void translateMovesEveryElement() {
        CircuitFragment fragment = CircuitFragment.of(document, List.of(gate.id(), label.id()), registry)
                .translate(3, 5);
        assertThat(fragment.bounds(registry)).isEqualTo(new Bounds(3, 5, 4, 6));
    }
}
