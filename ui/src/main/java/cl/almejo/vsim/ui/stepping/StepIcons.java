package cl.almejo.vsim.ui.stepping;

import java.util.List;

/** The icons of «Paso a paso» and its advances (M6.1-B), by file name under {@code icons/}. */
public final class StepIcons {

    public static final String MODE = "step-mode";
    public static final String ADVANCE = "step-advance";
    public static final String CLOCK_EDGE = "step-clock-edge";
    /** «Avanzar un ciclo de reloj»: a whole period of a clock, with an arrow under it. */
    public static final String CLOCK_CYCLE = "step-clock-cycle";
    /** The arrow of the fixed step's split button (16 px, no action). */
    public static final String SIZE_MENU = "step-size";

    private StepIcons() {
    }

    /** Every step icon, once. */
    public static List<String> names() {
        return List.of(MODE, ADVANCE, CLOCK_EDGE, CLOCK_CYCLE, SIZE_MENU);
    }
}
