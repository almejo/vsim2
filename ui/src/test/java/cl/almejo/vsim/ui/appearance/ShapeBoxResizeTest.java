package cl.almejo.vsim.ui.appearance;

import cl.almejo.vsim.core.component.appearance.ComponentAppearance;
import cl.almejo.vsim.core.component.appearance.PinPlacement;
import cl.almejo.vsim.core.component.appearance.SymbolDraft;
import cl.almejo.vsim.core.component.appearance.SymbolPath;
import cl.almejo.vsim.core.component.appearance.SymbolPoint;
import cl.almejo.vsim.core.component.appearance.SymbolTemplates;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The box's spinners resize it like its handle: the drawing stays centred and the pins stay on the grid. */
class ShapeBoxResizeTest {

    @Test
    void theSpinnersKeepTheDrawingCentredForEvenAndOddChanges() {
        ShapeEditorPanel panel = panel();
        panel.apply(draft(panel).applyTemplate(SymbolTemplates.Template.RECTANGLE));

        panel.widthSpinner().setValue(7);

        assertThat(panel.appearance().size()).isEqualTo(new GridSize(7, 4));
        assertThat(firstNode(panel)).isEqualTo(new SymbolPoint(4, 0));
        assertThat(panel.appearance().pins()).containsExactly(new PinPlacement(
                ShapeEditorPanelTest.definition().pinout().getFirst().source(), new GridPoint(0, 2)));

        panel.heightSpinner().setValue(6);

        assertThat(firstNode(panel)).isEqualTo(new SymbolPoint(4, 8));
        assertThat(panel.appearance().pins().getFirst().offset()).isEqualTo(new GridPoint(0, 3));
    }

    private static SymbolPoint firstNode(ShapeEditorPanel panel) {
        return ((SymbolPath) panel.appearance().drawing().orElseThrow().elements().getFirst()).nodes().getFirst()
                .point();
    }

    private static ShapeEditorPanel panel() {
        ComponentDefinition definition = ShapeEditorPanelTest.definition();
        return new ShapeEditorPanel(new ShapeEditRequest(definition, ComponentAppearance.of(definition),
                ShapeEditScope.INSTANCE, true, false, "", 1, "U1", false, false), AppPreferences.inMemory(),
                () -> ColorSchemes.LIGHT);
    }

    private static SymbolDraft draft(ShapeEditorPanel panel) {
        return SymbolDraft.of(panel.appearance(), ShapeEditorPanelTest.definition().pinout(), false);
    }
}
