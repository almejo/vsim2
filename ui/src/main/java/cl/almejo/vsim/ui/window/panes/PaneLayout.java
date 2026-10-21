package cl.almejo.vsim.ui.window.panes;

import cl.almejo.vsim.ui.canvas.ViewState;

import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * How a window's canvas area is split: the horizontal divider (top and bottom) and the vertical one (side by side)
 * as fractions 0..1 of the area, empty when absent, and the view state of each pane in reading order (left to right,
 * then top to bottom): 1 pane without dividers, 2 with one, 4 with both.
 */
public record PaneLayout(OptionalDouble horizontalDivider, OptionalDouble verticalDivider, List<ViewState> panes) {

    public PaneLayout {
        Objects.requireNonNull(horizontalDivider, "horizontalDivider");
        Objects.requireNonNull(verticalDivider, "verticalDivider");
        panes = List.copyOf(panes);
        checkFraction(horizontalDivider);
        checkFraction(verticalDivider);
        int dividers = (horizontalDivider.isPresent() ? 1 : 0) + (verticalDivider.isPresent() ? 1 : 0);
        int expected = dividers == 0 ? 1 : dividers == 1 ? 2 : 4;
        if (panes.size() != expected) {
            throw new IllegalArgumentException(
                    "a layout with " + dividers + " dividers has " + expected + " panes, not " + panes.size());
        }
    }

    /** One pane showing {@code view}. */
    public static PaneLayout single(ViewState view) {
        return new PaneLayout(OptionalDouble.empty(), OptionalDouble.empty(), List.of(view));
    }

    public boolean isSplit() {
        return panes.size() > 1;
    }

    private static void checkFraction(OptionalDouble fraction) {
        if (fraction.isPresent() && !(fraction.getAsDouble() > 0 && fraction.getAsDouble() < 1)) {
            throw new IllegalArgumentException("a divider sits strictly inside the area: " + fraction.getAsDouble());
        }
    }
}
