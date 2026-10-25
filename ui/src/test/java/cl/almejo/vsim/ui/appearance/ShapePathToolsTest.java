package cl.almejo.vsim.ui.appearance;

import cl.almejo.vsim.core.component.appearance.ComponentAppearance;
import cl.almejo.vsim.core.component.appearance.SymbolDraft;
import cl.almejo.vsim.core.component.appearance.SymbolNode;
import cl.almejo.vsim.core.component.appearance.SymbolPath;
import cl.almejo.vsim.core.component.appearance.SymbolPoint;
import cl.almejo.vsim.core.component.appearance.SymbolTemplates;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static cl.almejo.vsim.ui.appearance.ShapeNodeToolsTest.canvas;
import static cl.almejo.vsim.ui.appearance.ShapeNodeToolsTest.click;
import static cl.almejo.vsim.ui.appearance.ShapeNodeToolsTest.move;
import static cl.almejo.vsim.ui.appearance.ShapeNodeToolsTest.paint;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Multilínea (K, open) and Polígono (P, always closed): a click per node, double-click / Enter / Esc finish, a click
 * on the first node closes, Ctrl+Z while drawing takes back the last node; «Cerrar / Abrir figura» converts a chosen
 * figure. The box is 6 × 4 units at zoom 1 with its corner at (70, 70), so a screen point (70 + 8u, 70 + 8v) is the grid
 * point (u, v) and the drawing point (8u, 8v) in eighths.
 */
class ShapePathToolsTest {

    @Test
    void aMultilineAddsANodePerClickAndADoubleClickFinishesItOpen() {
        AtomicReference<SymbolDraft> draft = blank();
        ShapeCanvas canvas = canvas(draft);
        canvas.setTool(ShapeTool.MULTILINE);

        click(canvas, 78, 78, 0);
        click(canvas, 94, 86, 0);
        click(canvas, 110, 78, 0);
        click(canvas, 110, 78, 0, 2);

        assertThat(onlyPath(draft).closed()).isFalse();
        assertThat(points(onlyPath(draft))).containsExactly(new SymbolPoint(8, 8), new SymbolPoint(24, 16),
                new SymbolPoint(40, 8));
        assertThat(canvas.operationActive()).isFalse();
    }

    @Test
    void enterFinishesAMultilineOpen() {
        AtomicReference<SymbolDraft> draft = blank();
        ShapeCanvas canvas = canvas(draft);
        canvas.setTool(ShapeTool.MULTILINE);
        click(canvas, 78, 78, 0);
        click(canvas, 94, 86, 0);

        canvas.finishOperation();

        assertThat(onlyPath(draft).closed()).isFalse();
        assertThat(onlyPath(draft).nodes()).hasSize(2);
    }

    @Test
    void escapeDropsOnlyTheRubberBandAndKeepsThePlacedNodes() {
        AtomicReference<SymbolDraft> draft = blank();
        ShapeCanvas canvas = canvas(draft);
        canvas.setTool(ShapeTool.MULTILINE);
        click(canvas, 78, 78, 0);
        click(canvas, 94, 86, 0);
        move(canvas, 110, 94, 0);

        canvas.escapeOperation();

        assertThat(points(onlyPath(draft))).containsExactly(new SymbolPoint(8, 8), new SymbolPoint(24, 16));
    }

    @Test
    void escapeWithASingleNodeDrawsNothing() {
        AtomicReference<SymbolDraft> draft = blank();
        SymbolDraft original = draft.get();
        ShapeCanvas canvas = canvas(draft);
        canvas.setTool(ShapeTool.MULTILINE);
        click(canvas, 78, 78, 0);

        canvas.escapeOperation();

        assertThat(draft.get()).isSameAs(original);
        assertThat(canvas.operationActive()).isFalse();
    }

    @Test
    void aClickOnTheFirstNodeClosesAMultilineIntoAPolygon() {
        AtomicReference<SymbolDraft> draft = blank();
        ShapeCanvas canvas = canvas(draft);
        canvas.setTool(ShapeTool.MULTILINE);
        click(canvas, 78, 78, 0);
        click(canvas, 110, 78, 0);
        click(canvas, 94, 94, 0);

        click(canvas, 79, 79, 0);

        assertThat(onlyPath(draft).closed()).isTrue();
        assertThat(onlyPath(draft).nodes()).hasSize(3);
    }

    @Test
    void aPolygonIsAlwaysClosedByEnterEscapeOrADoubleClick() {
        assertThat(polygonFinishedBy(ShapeCanvas::finishOperation).closed()).isTrue();
        assertThat(polygonFinishedBy(ShapeCanvas::escapeOperation).closed()).isTrue();
        assertThat(polygonFinishedBy(canvas -> click(canvas, 94, 94, 0, 2)).closed()).isTrue();
    }

    @Test
    void aPolygonWithTwoNodesIsNotClosedByEscape() {
        AtomicReference<SymbolDraft> draft = blank();
        SymbolDraft original = draft.get();
        ShapeCanvas canvas = canvas(draft);
        canvas.setTool(ShapeTool.POLYGON);
        click(canvas, 78, 78, 0);
        click(canvas, 110, 78, 0);

        canvas.escapeOperation();

        assertThat(draft.get()).isSameAs(original);
    }

    @Test
    void undoWhileDrawingRemovesTheLastNodeAndLeavesTheDrawingAlone() {
        ShapeEditorPanel panel = panel();
        SymbolDraft before = draftOf(panel);
        ShapeCanvas canvas = panel.canvas();
        canvas.setZoom(1);
        canvas.setSize(400, 300);
        canvas.setTool(ShapeTool.MULTILINE);
        click(canvas, 78, 78, 0);
        click(canvas, 94, 86, 0);
        click(canvas, 110, 78, 0);

        panel.undo();

        assertThat(panel.appearance()).isEqualTo(before.appearance());
        assertThat(canvas.placedNodes()).containsExactly(new SymbolPoint(8, 8), new SymbolPoint(24, 16));
        panel.undo();
        panel.undo();
        assertThat(canvas.operationActive()).isFalse();
    }

    @Test
    void theRubberBandFollowsThePointerFromTheLastNode() {
        AtomicReference<SymbolDraft> draft = blank();
        ShapeCanvas canvas = canvas(draft);
        canvas.setTool(ShapeTool.MULTILINE);
        click(canvas, 78, 78, 0);

        move(canvas, 110, 78, 0);
        BufferedImage image = paint(canvas);

        int drawn = 0;
        for (int x = 84; x <= 106; x++) {
            if (image.getRGB(x, 78) == ColorSchemes.LIGHT.selection().getRGB()) {
                drawn++;
            }
        }
        assertThat(drawn).isGreaterThan(8);
    }

    @Test
    void closeOrOpenFigureConvertsTheChosenFigure() {
        ShapeEditorPanel panel = panel();
        SymbolPath triangle = SymbolTemplates.polygon(List.of(new SymbolPoint(8, 8), new SymbolPoint(40, 8),
                new SymbolPoint(24, 24)), true);
        panel.apply(draftOf(panel).addFigure(triangle));
        AbstractButton button = closeOrOpen(panel);
        assertThat(button.isEnabled()).isTrue();

        button.doClick(0);
        SymbolPath opened = (SymbolPath) draftOf(panel).elements().getLast();
        assertThat(opened.closed()).isFalse();
        assertThat(opened.filled()).isFalse();
        button.doClick(0);
        assertThat(((SymbolPath) draftOf(panel).elements().getLast()).closed()).isTrue();
    }

    @Test
    void aTwoNodeLineCannotBeClosedAndTheButtonSaysWhy() {
        ShapeEditorPanel panel = panel();
        panel.apply(draftOf(panel).addFigure(SymbolTemplates.line(new SymbolPoint(8, 8), new SymbolPoint(40, 8),
                false)));

        AbstractButton button = closeOrOpen(panel);

        assertThat(button.isEnabled()).isFalse();
        assertThat(button.getToolTipText()).contains(" — ");
    }

    private static SymbolPath polygonFinishedBy(Consumer<ShapeCanvas> finish) {
        AtomicReference<SymbolDraft> draft = blank();
        ShapeCanvas canvas = canvas(draft);
        canvas.setTool(ShapeTool.POLYGON);
        click(canvas, 78, 78, 0);
        click(canvas, 110, 78, 0);
        click(canvas, 94, 94, 0);
        finish.accept(canvas);
        return onlyPath(draft);
    }

    private static AtomicReference<SymbolDraft> blank() {
        ComponentDefinition definition = ShapeEditorPanelTest.definition();
        SymbolDraft empty = SymbolDraft.of(ComponentAppearance.of(definition), definition.pinout(), false)
                .addFigure(SymbolTemplates.line(new SymbolPoint(0, 0), new SymbolPoint(8, 0), false))
                .select(Set.of(0)).deleteSelection();
        return new AtomicReference<>(empty);
    }

    private static SymbolPath onlyPath(AtomicReference<SymbolDraft> draft) {
        assertThat(draft.get().elements()).hasSize(1);
        return (SymbolPath) draft.get().elements().getFirst();
    }

    private static List<SymbolPoint> points(SymbolPath path) {
        return path.nodes().stream().map(SymbolNode::point).toList();
    }

    private static ShapeEditorPanel panel() {
        ComponentDefinition definition = ShapeEditorPanelTest.definition();
        return new ShapeEditorPanel(new ShapeEditRequest(definition, ComponentAppearance.of(definition),
                ShapeEditScope.INSTANCE, true, false, "", 1, "U1", false, false), AppPreferences.inMemory(),
                () -> ColorSchemes.LIGHT);
    }

    private static SymbolDraft draftOf(ShapeEditorPanel panel) {
        return SymbolDraft.of(panel.appearance(), ShapeEditorPanelTest.definition().pinout(), false);
    }

    private static AbstractButton closeOrOpen(ShapeEditorPanel panel) {
        for (java.awt.Component child : panel.toolbar().getComponents()) {
            if (child instanceof AbstractButton button && button.getAccessibleContext().getAccessibleName()
                    .equals(Messages.text("shape.closePath"))) {
                return button;
            }
        }
        throw new AssertionError("no «Cerrar / Abrir figura» button");
    }
}
