package cl.almejo.vsim.ui.busvalues;

import java.util.List;

/** The icon of the bus values toggle, by file name under {@code icons/} (installed with {@code Icons.install}). */
public final class BusValuesIcons {

    public static final String BUS_VALUES = "bus-values";

    private BusValuesIcons() {
    }

    /** Every icon of the bus values toggle, once. */
    public static List<String> names() {
        return List.of(BUS_VALUES);
    }
}
