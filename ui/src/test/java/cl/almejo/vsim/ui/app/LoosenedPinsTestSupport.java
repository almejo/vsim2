package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.core.netlist.PinRef;

import java.util.Set;

/** Lets tests outside this package set the pins a shape change left loose. */
public final class LoosenedPinsTestSupport {

    private LoosenedPinsTestSupport() {
    }

    public static void set(OpenDocument openDocument, Set<PinRef> pins) {
        openDocument.setLoosenedPins(pins);
    }
}
