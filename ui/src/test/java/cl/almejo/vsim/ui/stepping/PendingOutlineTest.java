package cl.almejo.vsim.ui.stepping;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PendingOutlineTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitDocument document = new CircuitDocument();

    private ElementId placeNot(String id) {
        ElementId elementId = new ElementId(id);
        document.add(registry.require(StandardComponentTypes.NOT).newComponent(elementId, new GridPoint(0, 0),
                Rotation.R0, Map.of(), ""));
        return elementId;
    }

    @Test
    void aNestedComponentOutlinesItsTopLevelInstanceAndUnknownIdsAreSkipped() {
        ElementId gate = placeNot("c1");
        ElementId instance = placeNot("c4");

        PendingOutline outline = PendingOutline.of(Set.of("c1", "c4/c2", "c4/c7/c1", "c9", "c9/c1"), document, 5);

        assertThat(outline.components()).containsExactlyInAnyOrder(gate, instance);
        assertThat(PendingOutline.of(Set.of(), document, 5).components()).isEmpty();
    }

    @Test
    void itBelongsToTheNetlistOfItsSnapshot() {
        placeNot("c1");
        PendingOutline outline = PendingOutline.of(Set.of("c1"), document, 5);

        assertThat(outline.matches(5)).isTrue();
        assertThat(outline.matches(6)).isFalse();
        assertThat(PendingOutline.NONE.components()).isEmpty();
    }
}
