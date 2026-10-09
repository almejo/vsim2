package cl.almejo.vsim.ui.paint;

import java.awt.Color;

public final class AppearanceTestSupport {

    private AppearanceTestSupport() {
    }

    /** {@code base} with another background, for tests that need a second scheme. */
    public static ColorScheme withBackground(ColorScheme base, Color background) {
        return new ColorScheme(background, base.grid(), base.wireZero(), base.wireOne(), base.wireHighImpedance(),
                base.wireConflict(), base.wireUnknown(), base.componentStroke(), base.componentFill(),
                base.componentShade(), base.shadow(), base.text(), base.selection(), base.preview(),
                base.displayBackground(), base.segmentOn(), base.segmentOff(), base.diagramBackground(),
                base.diagramGrid(), base.diagramTrace());
    }
}
