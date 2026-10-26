package cl.almejo.vsim.core.document;

import cl.almejo.vsim.core.command.ChangeFrameCommand;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static cl.almejo.vsim.core.DocumentFixtures.frame;
import static cl.almejo.vsim.core.DocumentFixtures.gate;
import static cl.almejo.vsim.core.DocumentFixtures.wire;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FrameTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitDocument document = new CircuitDocument();

    @Test
    void aFrameSmallerThanTheMinimumIsRejected() {
        ElementId id = new ElementId("f1");
        assertThatThrownBy(() -> new Frame(id, GridPoint.ORIGIN, Frame.MINIMUM_WIDTH - 1, Frame.MINIMUM_HEIGHT,
                "", FrameTint.NONE, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Frame(id, GridPoint.ORIGIN, Frame.MINIMUM_WIDTH, Frame.MINIMUM_HEIGHT - 1,
                "", FrameTint.NONE, false)).isInstanceOf(IllegalArgumentException.class);
        Frame minimal = new Frame(id, GridPoint.ORIGIN, Frame.MINIMUM_WIDTH, Frame.MINIMUM_HEIGHT, null, null, true);
        assertThat(minimal.title()).isEmpty();
        assertThat(minimal.tint()).isEqualTo(FrameTint.NONE);
        assertThatThrownBy(() -> minimal.withRectangle(GridPoint.ORIGIN, 3, 3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void geometryHelpersIncludeTheBorder() {
        Frame frame = new Frame(new ElementId("f1"), new GridPoint(2, 3), 10, 6, "", FrameTint.NONE, false);
        assertThat(frame.contains(new GridPoint(2, 3))).isTrue();
        assertThat(frame.contains(new GridPoint(12, 9))).isTrue();
        assertThat(frame.contains(new GridPoint(13, 9))).isFalse();
        assertThat(frame.encloses(2, 3, 12, 9)).isTrue();
        assertThat(frame.encloses(2, 3, 13, 9)).isFalse();
        assertThat(frame.translate(1, -1).topLeft()).isEqualTo(new GridPoint(3, 2));
    }

    @Test
    void theDocumentKeepsFramesAndReplacesThemInPlace() {
        Frame frame = frame(document, 0, 0, 10, 10);
        assertThat(document.frames()).containsExactly(frame);
        assertThat(document.frame(frame.id())).contains(frame);
        assertThat(document.elements()).contains(frame);
        long order = document.orderOf(frame.id());
        document.replace(frame.withTitle("Other"));
        assertThat(document.frame(frame.id()).orElseThrow().title()).isEqualTo("Other");
        assertThat(document.orderOf(frame.id())).isEqualTo(order);
        document.remove(frame.id());
        assertThat(document.frames()).isEmpty();
        document.restore(frame, order);
        assertThat(document.frame(frame.id())).contains(frame);
    }

    @Test
    void contentsAreWhatLiesCompletelyInside() {
        Component inside = gate(registry, document, 2, 2);
        Component half = gate(registry, document, 18, 2);
        Component outside = gate(registry, document, 40, 40);
        Wire wireInside = wire(document, 2, 8, 8, 8);
        Wire wireCrossing = wire(document, 10, 8, 30, 8);
        TextLabel labelInside = new TextLabel(document.newLabelId(), new GridPoint(5, 9), "x");
        TextLabel labelOutside = new TextLabel(document.newLabelId(), new GridPoint(25, 9), "y");
        document.add(labelInside);
        document.add(labelOutside);
        Frame frame = frame(document, 0, 0, 20, 12);
        assertThat(FrameContents.of(document, registry, frame)).containsExactlyInAnyOrder(
                inside.id(), wireInside.id(), labelInside.id());
        assertThat(FrameContents.of(document, registry, frame)).doesNotContain(
                half.id(), outside.id(), wireCrossing.id(), labelOutside.id(), frame.id());
        assertThat(FrameContents.block(document, registry, frame)).contains(frame.id(), inside.id());
    }

    @Test
    void aLabelAnchorOnTheBorderCountsAsInside() {
        TextLabel label = new TextLabel(document.newLabelId(), new GridPoint(20, 12), "edge");
        document.add(label);
        Frame frame = frame(document, 0, 0, 20, 12);
        assertThat(FrameContents.of(document, registry, frame)).containsExactly(label.id());
    }

    @Test
    void nestedFramesAndTheirContentsAreCountedOnce() {
        Component deep = gate(registry, document, 4, 4);
        Frame inner = frame(document, 2, 2, 10, 10);
        Frame outer = frame(document, 0, 0, 30, 30);
        Frame sibling = frame(document, 40, 40, 10, 10);
        assertThat(FrameContents.of(document, registry, outer)).containsExactlyInAnyOrder(inner.id(), deep.id());
        assertThat(FrameContents.of(document, registry, inner)).containsExactly(deep.id());
        assertThat(FrameContents.of(document, registry, sibling)).isEmpty();
    }

    @Test
    void lockedBlocksAddTheContentsOfEveryLockedFrameOnceAndLeaveUnlockedFramesAlone() {
        Component deep = gate(registry, document, 4, 4);
        Frame inner = locked(frame(document, 2, 2, 10, 10));
        Frame outer = locked(frame(document, 0, 0, 30, 30));
        Component inUnlocked = gate(registry, document, 44, 44);
        Frame unlocked = frame(document, 40, 40, 15, 15);
        Component loose = gate(registry, document, 70, 70);
        assertThat(FrameContents.withLockedBlocks(document, registry, Set.of(outer.id(), inner.id(), loose.id())))
                .containsExactlyInAnyOrder(outer.id(), inner.id(), deep.id(), loose.id());
        assertThat(FrameContents.withLockedBlocks(document, registry, Set.of(unlocked.id())))
                .containsExactly(unlocked.id());
        assertThat(FrameContents.withLockedBlocks(document, registry, Set.of(inUnlocked.id())))
                .containsExactly(inUnlocked.id());
    }

    private Frame locked(Frame frame) {
        Frame locked = frame.withLocked(true);
        new ChangeFrameCommand(frame, locked).apply(document);
        return locked;
    }

    @Test
    void framesWithTheSameRectangleDoNotLoopForever() {
        Frame first = frame(document, 0, 0, 10, 10);
        Frame second = frame(document, 0, 0, 10, 10);
        assertThat(FrameContents.of(document, registry, first)).containsExactly(second.id());
    }

    @Test
    void changeFrameCommandAppliesAndRevertsExactly() {
        Frame frame = frame(document, 0, 0, 10, 10);
        Wire after = wire(document, 1, 1, 3, 1);
        Frame moved = frame.withRectangle(new GridPoint(5, 5), 12, 8);
        ChangeFrameCommand command = new ChangeFrameCommand(frame, moved);
        command.apply(document);
        assertThat(document.frame(frame.id())).contains(moved);
        command.revert(document);
        assertThat(document.frame(frame.id())).contains(frame);
        assertThat(document.wires()).containsExactly(after);
    }

    @Test
    void changeFrameCommandDescribesWhatChanged() {
        Frame frame = new Frame(new ElementId("f1"), GridPoint.ORIGIN, 10, 10, "A", FrameTint.NONE, false);
        assertThat(new ChangeFrameCommand(frame, frame.withTitle("B")).description()).isEqualTo("command.renameFrame");
        assertThat(new ChangeFrameCommand(frame, frame.withTint(FrameTint.RED)).description())
                .isEqualTo("command.tintFrame");
        assertThat(new ChangeFrameCommand(frame, frame.withLocked(true)).description()).isEqualTo("command.lockFrame");
        assertThat(new ChangeFrameCommand(frame.withLocked(true), frame).description())
                .isEqualTo("command.unlockFrame");
        assertThat(new ChangeFrameCommand(frame, frame.withRectangle(GridPoint.ORIGIN, 20, 10)).description())
                .isEqualTo("command.resizeFrame");
        Frame other = new Frame(new ElementId("f2"), GridPoint.ORIGIN, 10, 10, "A", FrameTint.NONE, false);
        assertThatThrownBy(() -> new ChangeFrameCommand(frame, other)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theNetlistIgnoresFrames() {
        Component first = gate(registry, document, 0, 0);
        gate(registry, document, 10, 0);
        wire(document, 4, 2, 10, 2);
        NetlistCompiler compiler = new NetlistCompiler(registry);
        Netlist without = compiler.compile(document);
        frame(document, -2, -2, 30, 12);
        Netlist with = compiler.compile(document);
        assertThat(with).isEqualTo(without);
        assertThat(with.nets()).isNotEmpty();
        assertThat(document.component(first.id())).isPresent();
        assertThat(Set.copyOf(document.frames())).hasSize(1);
    }
}
