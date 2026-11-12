package cl.almejo.vsim.ui.lens;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * When the accessibility lens shows, and where: it is on while its key is held or «Lupa fija» keeps it, and it shows
 * on the pane under the pointer, at the pointer. Pure state (the panes are of type {@code P}, so it is tested without
 * Swing); EDT only. Neither held nor fixed is ever saved: every view starts with the lens off.
 *
 * @param <P> what a pane is (the circuit canvas in the editor)
 */
public final class LensState<P> {

    /** The magnifications the toolbar's ▾ offers. */
    public static final List<Double> FACTORS = List.of(2.0, 3.0, 4.0);
    /** The magnification of a lens nobody chose one for (the approved mockup's). */
    public static final double DEFAULT_FACTOR = 3;
    /** The lens's radius in logical pixels: medium, fixed until Preferencias gets a size (2026-11-11). */
    public static final int RADIUS = 100;

    private boolean held;
    private boolean fixed;
    private double factor = DEFAULT_FACTOR;
    private Optional<P> pointerPane = Optional.empty();
    private Optional<Point2D> pointer = Optional.empty();

    /** The lens's key went down (again, when it repeats). */
    public void press() {
        held = true;
    }

    /** The lens's key or one of its modifiers went up, or the window lost the focus. */
    public void release() {
        held = false;
    }

    public boolean isHeld() {
        return held;
    }

    public void setFixed(boolean fixed) {
        this.fixed = fixed;
    }

    public boolean isFixed() {
        return fixed;
    }

    /** Held or fixed: it shows wherever the pointer is over a pane. */
    public boolean isOn() {
        return held || fixed;
    }

    /** One of {@link #FACTORS}. */
    public void setFactor(double factor) {
        if (!FACTORS.contains(factor)) {
            throw new IllegalArgumentException("the lens offers " + FACTORS + ", not " + factor);
        }
        this.factor = factor;
    }

    public double factor() {
        return factor;
    }

    /** The pointer is at {@code point} (logical pixels) of {@code pane}. */
    public void pointerAt(P pane, Point2D point) {
        pointerPane = Optional.of(Objects.requireNonNull(pane, "pane"));
        pointer = Optional.of(new Point2D.Double(point.getX(), point.getY()));
    }

    /** The pointer left {@code pane}; a late exit of a pane it has already left for another one changes nothing. */
    public void pointerLeft(P pane) {
        if (pointerPane.filter(pane::equals).isPresent()) {
            pointerPane = Optional.empty();
            pointer = Optional.empty();
        }
    }

    /** The pane under the pointer, if any. */
    public Optional<P> pointerPane() {
        return pointerPane;
    }

    /** Where the pointer is on {@link #pointerPane()}, if over one. */
    public Optional<Point2D> pointer() {
        return pointer.map(point -> new Point2D.Double(point.getX(), point.getY()));
    }

    /** On, with the pointer over a pane. */
    public boolean isShowing() {
        return isOn() && pointerPane.isPresent();
    }

    /** Showing on {@code pane}. */
    public boolean isShowingOn(P pane) {
        return isOn() && pointerPane.filter(pane::equals).isPresent();
    }
}
