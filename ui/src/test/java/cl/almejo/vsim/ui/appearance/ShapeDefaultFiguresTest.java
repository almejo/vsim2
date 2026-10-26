package cl.almejo.vsim.ui.appearance;

import cl.almejo.vsim.core.component.appearance.ComponentAppearance;
import cl.almejo.vsim.core.component.appearance.SnapMode;
import cl.almejo.vsim.core.component.appearance.SymbolDraft;
import cl.almejo.vsim.core.component.appearance.SymbolPath;
import cl.almejo.vsim.core.component.appearance.SymbolPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import org.junit.jupiter.api.Test;

import java.awt.geom.Rectangle2D;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shape exists from the start: a component without its own drawing opens with the default shape as real figures
 * (the rectangle and the name), selectable at once, and leaves the editor as no own drawing when it is still the
 * default shape for the final box.
 */
class ShapeDefaultFiguresTest {

    @Test
    void theDefaultShapeOpensAsFiguresSelectableWithTheFirstClick() {
        ShapeEditorPanel panel = panel();
        ShapeCanvas canvas = panel.canvas();
        canvas.setZoom(1);
        canvas.setSize(400, 300);
        Rectangle2D box = canvas.boxOnScreen();

        assertThat(panel.appearance().drawing().orElseThrow().elements()).hasSize(2);
        assertThat(panel.hasChanges()).isFalse();
        ShapeNodeToolsTest.click(canvas, (int) (box.getX() + box.getWidth() / 4), (int) box.getY(), 0);

        assertThat(canvas.hasSelection()).isTrue();
        assertThat(panel.hintText()).startsWith("1 figura elegida");
    }

    @Test
    void anUnchangedDefaultShapeIsSavedAsTheDefaultEvenAfterAResize() {
        ShapeEditorPanel panel = panel();

        panel.widthSpinner().setValue(8);

        assertThat(panel.hasChanges()).isTrue();
        assertThat(rectangle(panel).nodes().get(2).point()).isEqualTo(new SymbolPoint(64, 32));
        assertThat(panel.result().appearance().size()).isEqualTo(new GridSize(8, 4));
        assertThat(panel.result().appearance().drawing()).isEmpty();
    }

    @Test
    void anEditedDefaultShapeIsSavedAsADrawing() {
        ShapeEditorPanel panel = panel();
        SymbolDraft draft = SymbolDraft.of(panel.appearance(), ShapeEditorPanelTest.definition().pinout(), false);

        panel.apply(draft.moveNode(0, 2, new SymbolPoint(40, 32), SnapMode.FREE));

        assertThat(panel.result().appearance().drawing()).isPresent();
    }

    @Test
    void oneHintForTheShapeAndOneForTheBoxEdgeRule() {
        ShapeEditorPanel panel = panel();
        ShapeCanvas canvas = panel.canvas();
        canvas.setZoom(1);
        canvas.setSize(400, 300);
        Rectangle2D box = canvas.boxOnScreen();

        assertThat(panel.hintText()).isEqualTo(Messages.text("shape.hint.select"));
        ShapeNodeToolsTest.move(canvas, (int) box.getMaxX() + 18, (int) box.getMaxY() + 18, 0);
        assertThat(panel.hintText()).isEqualTo(Messages.text("shape.hint.box"));
        assertThat(List.of("shape.hint.selectDrawing", "shape.hint.boxDrawing", "shape.hint.boxDefault"))
                .allSatisfy(key -> assertThat(Messages.text(key)).as(key).contains("!"));
    }

    private static SymbolPath rectangle(ShapeEditorPanel panel) {
        return (SymbolPath) panel.appearance().drawing().orElseThrow().elements().getFirst();
    }

    private static ShapeEditorPanel panel() {
        ComponentDefinition definition = ShapeEditorPanelTest.definition();
        return new ShapeEditorPanel(new ShapeEditRequest(definition, ComponentAppearance.of(definition),
                ShapeEditScope.INSTANCE, true, false, "", 1, "U1", false, false), AppPreferences.inMemory(),
                () -> ColorSchemes.LIGHT);
    }
}
