package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.ui.canvas.ViewState;

import java.awt.Point;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Where Ventana ▸ Nueva vista puts a new view of a document (spec §5.1): {@value #OFFSET} px down-right of its
 * source window, showing the source's zoom and center instead of re-centering the circuit.
 */
public record NewViewPlacement(Point location, ViewState viewState) {

    public static final int OFFSET = 30;

    public NewViewPlacement {
        location = new Point(Objects.requireNonNull(location, "location"));
        Objects.requireNonNull(viewState, "viewState");
    }

    public static NewViewPlacement after(Point sourceLocation, ViewState sourceViewState) {
        return new NewViewPlacement(new Point(sourceLocation.x + OFFSET, sourceLocation.y + OFFSET), sourceViewState);
    }

    /**
     * Hands the placement to a new window: what {@code Application.newWindow} does to it before showing it. Kept apart
     * from {@code CircuitWindow} so it can be exercised without a real frame.
     */
    public void applyTo(Consumer<ViewState> initialViewState, Consumer<Point> newLocation) {
        initialViewState.accept(viewState);
        newLocation.accept(location());
    }

    /** A copy: {@link Point} is mutable. */
    @Override
    public Point location() {
        return new Point(location);
    }
}
