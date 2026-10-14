package cl.almejo.vsim.ui.export;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;

import java.util.Optional;
import java.util.Set;

/** The expected size of the export of an open document, worked out through {@link CircuitImage}. */
record CircuitImageFixture(CircuitImage image) {

    static CircuitImageFixture of(OpenDocument source, Optional<Set<ElementId>> only) {
        return new CircuitImageFixture(CircuitImage.of(source.document(), source.netlist(), source.netlistToken(),
                source.latestSnapshot(), source.registry(), PainterRegistry.standard(), ColorScheme.LIGHT, only)
                .orElseThrow());
    }

    int width(double scale) {
        return image.width(scale);
    }

    int height(double scale) {
        return image.height(scale);
    }
}
