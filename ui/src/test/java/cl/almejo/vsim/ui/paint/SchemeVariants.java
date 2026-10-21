package cl.almejo.vsim.ui.paint;

import java.awt.Color;

/** Copies of a scheme with other display or diagram colors, so tests can tell the painter reads the scheme. */
final class SchemeVariants {

    private SchemeVariants() {
    }

    static ColorScheme withDisplay(ColorScheme base, Color displayBackground, Color segmentOn, Color segmentOff) {
        return new ColorScheme(base.background(), base.grid(), base.gridLine(), base.wireZero(), base.wireOne(),
                base.wireHighImpedance(), base.wireConflict(), base.wireUnknown(), base.componentStroke(),
                base.componentFill(), base.componentShade(), base.shadow(), base.text(), base.selection(),
                base.preview(), displayBackground, segmentOn, segmentOff, base.diagramBackground(),
                base.diagramGrid(), base.diagramTrace());
    }

    static ColorScheme withDiagram(ColorScheme base, Color diagramBackground, Color diagramGrid, Color diagramTrace) {
        return new ColorScheme(base.background(), base.grid(), base.gridLine(), base.wireZero(), base.wireOne(),
                base.wireHighImpedance(), base.wireConflict(), base.wireUnknown(), base.componentStroke(),
                base.componentFill(), base.componentShade(), base.shadow(), base.text(), base.selection(),
                base.preview(), base.displayBackground(), base.segmentOn(), base.segmentOff(), diagramBackground,
                diagramGrid, diagramTrace);
    }
}
