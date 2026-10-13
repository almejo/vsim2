package cl.almejo.vsim.ui.stepping;

import java.awt.Component;
import java.util.OptionalLong;

/** Asks for the size of the fixed step (M6.1-B «Otro…», «Tamaño del avance…»); a fake in tests. */
@FunctionalInterface
public interface StepSizeChooser {

    /** The chosen number of time units (1 to 1 000 000 000), or empty when the user cancels. */
    OptionalLong choose(Component parent, long current);
}
