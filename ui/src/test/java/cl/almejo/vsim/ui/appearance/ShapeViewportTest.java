package cl.almejo.vsim.ui.appearance;

import cl.almejo.vsim.core.component.appearance.ComponentAppearance;
import cl.almejo.vsim.core.component.appearance.SymbolDraft;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.ui.canvas.Viewport;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** The shape editor's zoom follows the main editor's {@link Viewport}: its range, its step and where it zooms. */
class ShapeViewportTest {

    @Test
    void theZoomStopsAtTheMainEditorsRange() {
        ShapeCanvas canvas = canvas();

        canvas.setZoom(10);
        assertThat(canvas.zoom()).isEqualTo(Viewport.MAXIMUM_ZOOM);

        canvas.setZoom(0.01);
        assertThat(canvas.zoom()).isEqualTo(Viewport.MINIMUM_ZOOM);
    }

    private static ShapeCanvas canvas() {
        ComponentDefinition definition = ShapeEditorPanelTest.definition();
        AtomicReference<SymbolDraft> draft = new AtomicReference<>(SymbolDraft.of(ComponentAppearance.of(definition),
                definition.pinout(), false));
        ShapeCanvas canvas = new ShapeCanvas(definition, draft::get, draft::set, draft::set,
                () -> ColorSchemes.LIGHT, () -> { });
        canvas.setZoom(1);
        canvas.setSize(400, 300);
        return canvas;
    }
}
