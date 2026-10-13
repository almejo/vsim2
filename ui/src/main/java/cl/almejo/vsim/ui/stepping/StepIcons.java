package cl.almejo.vsim.ui.stepping;

import java.util.List;

/** The icons of «Paso a paso» and «Avanzar» (M6.1-B), by file name under {@code icons/}. */
public final class StepIcons {

    public static final String MODE = "step-mode";
    public static final String ADVANCE = "step-advance";

    private StepIcons() {
    }

    /** Every step icon, once. */
    public static List<String> names() {
        return List.of(MODE, ADVANCE);
    }
}
