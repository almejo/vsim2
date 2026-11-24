package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.ui.canvas.Selection;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** What a press on an element leaves for a release without moving: narrow the selection, take it out, or nothing. */
class SelectionOnReleaseTest {

    private static final ElementId PRESSED = new ElementId("pressed");
    private static final ElementId OTHER = new ElementId("other");

    private final Selection selection = new Selection();
    private final SelectionOnRelease onRelease = new SelectionOnRelease();

    @Test
    void aPlainPressOnAnUnselectedElementSelectsItAtOnceAndLeavesNothingForTheRelease() {
        selection.select(OTHER);
        onRelease.selectOnPress(selection, PRESSED);
        assertThat(selection.ids()).containsExactly(PRESSED);
        selection.add(OTHER);
        onRelease.released(selection, PRESSED);
        assertThat(selection.ids()).containsExactlyInAnyOrder(PRESSED, OTHER);
    }

    @Test
    void aPlainPressInAMultiSelectionKeepsItAndTheReleaseNarrowsIt() {
        selection.select(PRESSED);
        selection.add(OTHER);
        onRelease.selectOnPress(selection, PRESSED);
        assertThat(selection.ids()).containsExactlyInAnyOrder(PRESSED, OTHER);
        onRelease.released(selection, PRESSED);
        assertThat(selection.ids()).containsExactly(PRESSED);
    }

    @Test
    void aToggleOnAnUnselectedElementAddsItAtOnce() {
        selection.select(OTHER);
        onRelease.toggleOnPress(selection, PRESSED);
        assertThat(selection.ids()).containsExactlyInAnyOrder(PRESSED, OTHER);
        onRelease.released(selection, PRESSED);
        assertThat(selection.ids()).containsExactlyInAnyOrder(PRESSED, OTHER);
    }

    @Test
    void aToggleOnASelectedElementTakesItOutAtTheRelease() {
        selection.select(PRESSED);
        selection.add(OTHER);
        onRelease.toggleOnPress(selection, PRESSED);
        assertThat(selection.ids()).containsExactlyInAnyOrder(PRESSED, OTHER);
        onRelease.released(selection, PRESSED);
        assertThat(selection.ids()).containsExactly(OTHER);
    }

    @Test
    void resetForgetsWhatTheReleaseWouldDo() {
        selection.select(PRESSED);
        selection.add(OTHER);
        onRelease.toggleOnPress(selection, PRESSED);
        onRelease.reset();
        onRelease.released(selection, PRESSED);
        assertThat(selection.ids()).containsExactlyInAnyOrder(PRESSED, OTHER);
    }
}
