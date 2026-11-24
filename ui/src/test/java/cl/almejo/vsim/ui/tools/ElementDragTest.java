package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.command.CreateFrameCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.geometry.GridPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The drag of one grabbed element on its own (what frames and sticky notes delegate to it): a release without moving
 * adjusts the selection, a drag a locked frame's wall stopped is no click, and a drag moves what the selection acts
 * on. Two NOT gates (4 x 4) are selected; the drag grabs {@link #first}.
 */
class ElementDragTest {

    private final TestToolContext context = TestToolContext.withNewDocument();
    private final ElementDrag drag = new ElementDrag();
    private Component first;
    private Component second;

    @AfterEach
    void close() {
        context.close();
    }

    private Component gate(int x, int y) {
        return place(context.openDocument(), StandardComponentTypes.BUFFER, x, y,
                Map.of(StandardComponentTypes.NEGATE, true));
    }

    private void selectBoth(int firstX, int secondX) {
        first = gate(firstX, 4);
        second = gate(secondX, 4);
        context.selection.select(first.id());
        context.selection.add(second.id());
    }

    private Component current(Component component) {
        return context.openDocument().document().component(component.id()).orElseThrow();
    }

    /** The grab of {@link #first} at ({@code x}, 6), a drag to each of {@code toX} (y 6) and the release there. */
    private void grabAndRelease(int x, boolean shiftDown, int... toX) {
        drag.start(TestToolContext.at(x, 6), false);
        if (shiftDown) {
            drag.toggleOnPress(context.selection, first.id());
        } else {
            drag.selectOnPress(context.selection, first.id());
        }
        for (int pointerX : toX) {
            drag.dragged(context, TestToolContext.at(pointerX, 6));
        }
        drag.releaseMove(context, first.id());
    }

    @Test
    void aReleaseWithoutMovingNarrowsTheSelectionToTheGrabbedElement() {
        selectBoth(4, 30);
        grabAndRelease(6, false, 6);
        assertThat(context.selection.ids()).containsExactly(first.id());
        assertThat(current(first)).isEqualTo(first);
    }

    @Test
    void aShiftReleaseWithoutMovingTakesTheGrabbedElementOut() {
        selectBoth(4, 30);
        grabAndRelease(6, true, 6);
        assertThat(context.selection.ids()).containsExactly(second.id());
    }

    @Test
    void aReleaseWithoutMovingIsAClick() {
        selectBoth(4, 30);
        drag.start(TestToolContext.at(6, 6), false);
        drag.dragged(context, TestToolContext.at(6, 6));
        assertThat(drag.isClick()).isTrue();
    }

    @Test
    void aDragStoppedByALockedFramesWallMovesNothingAndIsNoClick() {
        Frame wall = new Frame(context.openDocument().document().newFrameId(), new GridPoint(0, 0), 20, 12,
                "Muro", FrameTint.BLUE, true);
        context.openDocument().execute(new CreateFrameCommand(wall));
        selectBoth(16, 4); // the first gate's right side is on the wall (x 20)
        drag.start(TestToolContext.at(18, 6), false);
        drag.selectOnPress(context.selection, first.id());
        drag.dragged(context, TestToolContext.at(26, 6));
        assertThat(drag.displaced()).isFalse();
        assertThat(drag.isClick()).isFalse();
        drag.releaseMove(context, first.id());
        assertThat(current(first)).isEqualTo(first);
        assertThat(current(second)).isEqualTo(second);
        assertThat(context.selection.ids()).containsExactlyInAnyOrder(first.id(), second.id());
    }

    @Test
    void aDragBackToThePressPointIsNoClickEither() {
        selectBoth(4, 30);
        grabAndRelease(6, false, 10, 6);
        assertThat(context.selection.ids()).containsExactlyInAnyOrder(first.id(), second.id());
    }

    @Test
    void aDragMovesTheSelectionAsOneUndoStep() {
        selectBoth(4, 30);
        grabAndRelease(6, false, 16);
        assertThat(current(first).position()).isEqualTo(new GridPoint(14, 4));
        assertThat(current(second).position()).isEqualTo(new GridPoint(40, 4));
        assertThat(context.selection.ids()).containsExactlyInAnyOrder(first.id(), second.id());
        context.openDocument().history().undo();
        assertThat(current(first)).isEqualTo(first);
        assertThat(current(second)).isEqualTo(second);
    }
}
