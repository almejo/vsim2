package cl.almejo.vsim.core.document;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static cl.almejo.vsim.core.DocumentFixtures.frame;
import static cl.almejo.vsim.core.DocumentFixtures.gate;
import static org.assertj.core.api.Assertions.assertThat;

class FrameContentsTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitDocument document = new CircuitDocument();

    @Test
    void eachElementGetsItsInnermostFrame() {
        Frame outer = frame(document, 0, 0, 40, 30);
        Frame inner = frame(document, 2, 2, 20, 20);
        Component insideInner = gate(registry, document, 4, 4);
        Component insideOuterOnly = gate(registry, document, 30, 4);

        assertThat(FrameContents.parents(document, registry)).containsExactlyInAnyOrderEntriesOf(Map.of(
                inner.id(), outer.id(),
                insideInner.id(), inner.id(),
                insideOuterOnly.id(), outer.id()));
    }

    @Test
    void aComponentStraddlingABorderAndOneOutsideHaveNoParent() {
        frame(document, 0, 0, 10, 10);
        Component straddling = gate(registry, document, 8, 2);
        Component outside = gate(registry, document, 50, 50);

        assertThat(FrameContents.parents(document, registry))
                .doesNotContainKey(straddling.id()).doesNotContainKey(outside.id());
    }

    @Test
    void disjointFramesEachHoldTheirOwnComponents() {
        Frame left = frame(document, 0, 0, 10, 10);
        Frame right = frame(document, 20, 0, 10, 10);
        Component inLeft = gate(registry, document, 2, 2);
        Component inRight = gate(registry, document, 22, 2);

        assertThat(FrameContents.parents(document, registry)).containsExactlyInAnyOrderEntriesOf(Map.of(
                inLeft.id(), left.id(),
                inRight.id(), right.id()));
    }

    @Test
    void identicalFramesNestByDocumentOrderWithoutCycles() {
        Frame first = frame(document, 0, 0, 10, 10);
        Frame second = frame(document, 0, 0, 10, 10);
        Component gate = gate(registry, document, 2, 2);

        assertThat(FrameContents.parents(document, registry)).containsExactlyInAnyOrderEntriesOf(Map.of(
                second.id(), first.id(),
                gate.id(), second.id()));
    }

    @Test
    void aComponentOfAnUnknownTypeHasNoParent() {
        frame(document, 0, 0, 10, 10);
        Component unknown = new Component(document.newComponentId(), "subcircuit:missing", new GridPoint(2, 2),
                Rotation.R0, Map.of(), "");
        document.add(unknown);

        assertThat(FrameContents.parents(document, registry)).doesNotContainKey(unknown.id());
    }
}
