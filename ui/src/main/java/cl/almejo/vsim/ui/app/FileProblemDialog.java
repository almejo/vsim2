package cl.almejo.vsim.ui.app;

import java.awt.Component;

/** The seam through which the user is told why a file did not open; tests record instead of showing a dialog. */
@FunctionalInterface
public interface FileProblemDialog {

    /** Shows {@code message}; {@code details} (technical, may be empty) goes in a «Detalles» part kept closed. */
    void show(Component parent, String title, String message, String details);
}
