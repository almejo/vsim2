package cl.almejo.vsim.core.document;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FrameContentsTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitDocument document = new CircuitDocument();

    /** NOT gate: 4x4. */
    private Component gate(int x, int y) {
        Component component = registry.require(StandardComponentTypes.NOT).newComponent(document.newComponentId(),
                new GridPoint(x, y), Rotation.R0, Map.of(), "");
        document.add(component);
        return component;
    }

    private Frame frame(int x, int y, int width, int height) {
        Frame frame = new Frame(document.newFrameId(), new GridPoint(x, y), width, height, "Block",
                FrameTint.NONE, false);
        document.add(frame);
        return frame;
    }

    @Test
    void eachElementGetsItsInnermostFrame() {
        Frame outer = frame(0, 0, 40, 30);
        Frame inner = frame(2, 2, 20, 20);
        Component insideInner = gate(4, 4);
        Component insideOuterOnly = gate(30, 4);

        assertThat(FrameContents.parents(document, registry)).containsExactlyInAnyOrderEntriesOf(Map.of(
                inner.id(), outer.id(),
                insideInner.id(), inner.id(),
                insideOuterOnly.id(), outer.id()));
    }

    @Test
    void aComponentStraddlingABorderAndOneOutsideHaveNoParent() {
        frame(0, 0, 10, 10);
        Component straddling = gate(8, 2);
        Component outside = gate(50, 50);

        assertThat(FrameContents.parents(document, registry))
                .doesNotContainKey(straddling.id()).doesNotContainKey(outside.id());
    }

    @Test
    void disjointFramesEachHoldTheirOwnComponents() {
        Frame left = frame(0, 0, 10, 10);
        Frame right = frame(20, 0, 10, 10);
        Component inLeft = gate(2, 2);
        Component inRight = gate(22, 2);

        assertThat(FrameContents.parents(document, registry)).containsExactlyInAnyOrderEntriesOf(Map.of(
                inLeft.id(), left.id(),
                inRight.id(), right.id()));
    }

    @Test
    void identicalFramesNestByDocumentOrderWithoutCycles() {
        Frame first = frame(0, 0, 10, 10);
        Frame second = frame(0, 0, 10, 10);
        Component gate = gate(2, 2);

        assertThat(FrameContents.parents(document, registry)).containsExactlyInAnyOrderEntriesOf(Map.of(
                second.id(), first.id(),
                gate.id(), second.id()));
    }

    @Test
    void aComponentOfAnUnknownTypeHasNoParent() {
        frame(0, 0, 10, 10);
        Component unknown = new Component(document.newComponentId(), "subcircuit:missing", new GridPoint(2, 2),
                Rotation.R0, Map.of(), "");
        document.add(unknown);

        assertThat(FrameContents.parents(document, registry)).doesNotContainKey(unknown.id());
    }
}
