package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.ParameterSpec;

import java.util.List;

/** Parameters several parts share, declared once so they read and save the same everywhere. */
public final class StandardParameters {

    /** «Flanco» value: the part acts when its clock goes from 0 to 1 (Logisim's default). */
    public static final String RISING = "rising";
    /** «Flanco» value: the part acts when its clock goes from 1 to 0 (VSim's flip-flops). */
    public static final String FALLING = "falling";

    /** «Flanco»: the clock edge a part acts on, «Subida» or «Bajada»; see {@link ClockEdgeDetector}. */
    public static final ParameterSpec EDGE = ParameterSpec.choice("edge", RISING, List.of(RISING, FALLING));

    private StandardParameters() {
    }
}
