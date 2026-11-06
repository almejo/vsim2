package cl.almejo.vsim.ui.magnifier;

import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;

import java.awt.geom.Point2D;

/** Where the magnifier's picture shows; a seam so the hover rules are tested without a window. */
interface MagnifierPresenter {

    /** Shows what is inside {@code instance} (a user-component instance placed in {@code pane}) near {@code screenPoint}. */
    void show(CircuitCanvas pane, Component instance, Point2D screenPoint);

    /** Takes the picture away (the real panel shrinks back to the piece first); harmless when none shows. */
    void hide();

    /** Takes the picture away at once, without animation; harmless when none shows. */
    default void close() {
        hide();
    }
}
