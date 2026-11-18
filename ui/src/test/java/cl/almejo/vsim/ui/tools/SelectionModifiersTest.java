package cl.almejo.vsim.ui.tools;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one rule of how Shift and Ctrl change a selection, shared by the circuit's select tool and a machine's diagram
 * (hand test of the machines, 2026-11-17: «la manera de agregar y quitar selección debe ser igual en circuito que en
 * máquina»).
 */
class SelectionModifiersTest {

    @Test
    void shiftOrCtrlChangeTheSelectionAndNoKeyDoesNot() {
        assertThat(SelectionModifiers.changesSelection(false, false)).isFalse();
        assertThat(SelectionModifiers.changesSelection(true, false)).isTrue();
        assertThat(SelectionModifiers.changesSelection(false, true)).isTrue();
        assertThat(SelectionModifiers.changesSelection(true, true)).isTrue();
    }

    @Test
    void shiftTogglesOrDragsTheSelectionAndCtrlOnlyToggles() {
        assertThat(SelectionModifiers.elementPress(false, false)).isEqualTo(SelectionModifiers.ElementPress.PLAIN);
        assertThat(SelectionModifiers.elementPress(true, false))
                .isEqualTo(SelectionModifiers.ElementPress.TOGGLE_OR_DRAG);
        assertThat(SelectionModifiers.elementPress(false, true)).isEqualTo(SelectionModifiers.ElementPress.TOGGLE);
        assertThat(SelectionModifiers.elementPress(true, true)).isEqualTo(SelectionModifiers.ElementPress.TOGGLE);
    }

    @Test
    void aBandReplacesAddsWithOneKeyAndTakesAwayWithBoth() {
        assertThat(SelectionModifiers.bandMode(false, false)).isEqualTo(SelectionModifiers.BandMode.REPLACE);
        assertThat(SelectionModifiers.bandMode(true, false)).isEqualTo(SelectionModifiers.BandMode.ADD);
        assertThat(SelectionModifiers.bandMode(false, true)).isEqualTo(SelectionModifiers.BandMode.ADD);
        assertThat(SelectionModifiers.bandMode(true, true)).isEqualTo(SelectionModifiers.BandMode.REMOVE);
    }

    @Test
    void theBandsResultReplacesAddsOrTakesAway() {
        Set<String> current = Set.of("a", "b");
        Set<String> inside = Set.of("b", "c");

        assertThat(SelectionModifiers.combine(SelectionModifiers.BandMode.REPLACE, current, inside))
                .containsExactlyInAnyOrder("b", "c");
        assertThat(SelectionModifiers.combine(SelectionModifiers.BandMode.ADD, current, inside))
                .containsExactlyInAnyOrder("a", "b", "c");
        assertThat(SelectionModifiers.combine(SelectionModifiers.BandMode.REMOVE, current, inside))
                .containsExactly("a");
    }

    @Test
    void theCursorWithAKeyShowsWhatAPressWouldDo() {
        assertThat(SelectionModifiers.hoverCursor(true, false, Optional.of(false)))
                .isEqualTo(CanvasCursor.ADD_TO_SELECTION);
        assertThat(SelectionModifiers.hoverCursor(false, true, Optional.of(true)))
                .isEqualTo(CanvasCursor.REMOVE_FROM_SELECTION);
        assertThat(SelectionModifiers.hoverCursor(true, false, Optional.empty()))
                .isEqualTo(CanvasCursor.ADD_TO_SELECTION);
        assertThat(SelectionModifiers.hoverCursor(true, true, Optional.empty()))
                .isEqualTo(CanvasCursor.REMOVE_FROM_SELECTION);
    }

    @Test
    void aBandsCursorFollowsItsMode() {
        assertThat(SelectionModifiers.bandCursor(SelectionModifiers.BandMode.REPLACE)).isEqualTo(CanvasCursor.DEFAULT);
        assertThat(SelectionModifiers.bandCursor(SelectionModifiers.BandMode.ADD))
                .isEqualTo(CanvasCursor.ADD_TO_SELECTION);
        assertThat(SelectionModifiers.bandCursor(SelectionModifiers.BandMode.REMOVE))
                .isEqualTo(CanvasCursor.REMOVE_FROM_SELECTION);
    }
}
