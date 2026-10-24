package cl.almejo.vsim.ui.appearance;

import cl.almejo.vsim.core.component.appearance.ComponentAppearance;
import cl.almejo.vsim.core.component.appearance.SymbolDraft;
import cl.almejo.vsim.core.component.appearance.SymbolPath;
import cl.almejo.vsim.core.component.appearance.SymbolPoint;
import cl.almejo.vsim.core.component.appearance.SymbolTemplates;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ShapeCanvasTest {
    @Test
    void addingANodeHighlightsThePointedSegmentAndInsertsAtTheClickWithoutAPriorSelection() {
        ComponentDefinition definition = ShapeEditorPanelTest.definition();
        AtomicReference<SymbolDraft> draft = new AtomicReference<>(SymbolDraft.of(ComponentAppearance.of(definition),
                definition.pinout(), false).applyTemplate(SymbolTemplates.Template.RECTANGLE));
        ShapeCanvas canvas = canvas(definition, draft);
        canvas.setTool(ShapeTool.ADD_NODE);
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_MOVED, 0, 0, 83, 70, 0, false));
        BufferedImage image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try { canvas.paint(graphics); } finally { graphics.dispose(); }
        assertThat(image.getRGB(100, 70)).isEqualTo(ColorSchemes.LIGHT.selection().getRGB());
        assertThat(((SymbolPath) draft.get().elements().getFirst()).nodes()).hasSize(4);

        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_CLICKED, 0, 0,
                83, 70, 1, false, MouseEvent.BUTTON1));

        SymbolPath path = (SymbolPath) draft.get().elements().getFirst();
        assertThat(path.nodes()).hasSize(5);
        assertThat(path.nodes().get(1).point()).isEqualTo(new SymbolPoint(13, 0));
        canvas.deleteSelection();
        assertThat(((SymbolPath) draft.get().elements().getFirst()).nodes()).hasSize(4);
    }

    @Test
    void theHandToolPansWithTheLeftButtonWithoutEditingTheDrawing() {
        ComponentDefinition definition = ShapeEditorPanelTest.definition();
        SymbolDraft original = SymbolDraft.of(ComponentAppearance.of(definition), definition.pinout(), false)
                .applyTemplate(SymbolTemplates.Template.RECTANGLE);
        AtomicReference<SymbolDraft> draft = new AtomicReference<>(original);
        ShapeCanvas canvas = canvas(definition, draft);
        canvas.setTool(ShapeTool.PAN);

        drag(canvas, new Point(200, 180), new Point(240, 210));

        assertThat(draft.get().appearance()).isEqualTo(original.appearance());
        canvas.setTool(ShapeTool.NODES);
        drag(canvas, new Point(110, 100), new Point(118, 108));
        assertThat(((SymbolPath) draft.get().elements().getFirst()).nodes().getFirst().point())
                .isEqualTo(new SymbolPoint(8, 8));
    }

    @Test
    void cancellingADefaultNodeDragRestoresTheOriginalDefaultAppearance() {
        ComponentDefinition definition = ShapeEditorPanelTest.definition();
        SymbolDraft original = SymbolDraft.of(ComponentAppearance.of(definition), definition.pinout(), false);
        AtomicReference<SymbolDraft> draft = new AtomicReference<>(original);
        ShapeCanvas canvas = canvas(definition, draft);
        canvas.setTool(ShapeTool.NODES);
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 0, MouseEvent.BUTTON1_DOWN_MASK,
                70, 70, 1, false, MouseEvent.BUTTON1));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_DRAGGED, 0, MouseEvent.BUTTON1_DOWN_MASK,
                78, 78, 0, false, MouseEvent.NOBUTTON));
        assertThat(draft.get().appearance().drawing()).isPresent();

        canvas.cancelOperation();

        assertThat(draft.get().appearance()).isEqualTo(original.appearance());
    }

    @Test
    void nodesAreVisibleAndDraggableWithoutSelectingTheFigureFirst() {
        ComponentDefinition definition = ShapeEditorPanelTest.definition();
        AtomicReference<SymbolDraft> draft = new AtomicReference<>(SymbolDraft.of(ComponentAppearance.of(definition),
                definition.pinout(), false).applyTemplate(SymbolTemplates.Template.RECTANGLE));
        ShapeCanvas canvas = canvas(definition, draft);
        canvas.setTool(ShapeTool.NODES);
        BufferedImage image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try { canvas.paint(graphics); } finally { graphics.dispose(); }
        assertThat(image.getRGB(70, 70)).isEqualTo(ColorSchemes.LIGHT.background().getRGB());

        drag(canvas, new Point(70, 70), new Point(78, 78));

        assertThat(((SymbolPath) draft.get().elements().getFirst()).nodes().getFirst().point())
                .isEqualTo(new SymbolPoint(8, 8));
    }

    @Test
    void defaultBoxNodesCanBeEditedWithoutChangingTheDraftJustByActivatingTheTool() {
        ComponentDefinition definition = ShapeEditorPanelTest.definition();
        SymbolDraft original = SymbolDraft.of(ComponentAppearance.of(definition), definition.pinout(), false);
        AtomicReference<SymbolDraft> draft = new AtomicReference<>(original);
        ShapeCanvas canvas = canvas(definition, draft);
        canvas.setTool(ShapeTool.NODES);
        assertThat(draft.get().appearance()).isEqualTo(original.appearance());

        drag(canvas, new Point(70, 70), new Point(78, 78));

        assertThat(draft.get().appearance().drawing()).isPresent();
        assertThat(((SymbolPath) draft.get().elements().getFirst()).nodes().getFirst().point())
                .isEqualTo(new SymbolPoint(8, 8));
    }

    @Test
    void theSeparateBoxHandleCanEnlargeAndThenShrinkBackToTheDrawing() {
        ComponentDefinition definition = ShapeEditorPanelTest.definition();
        AtomicReference<SymbolDraft> draft = new AtomicReference<>(SymbolDraft.of(ComponentAppearance.of(definition),
                definition.pinout(), false).applyTemplate(SymbolTemplates.Template.RECTANGLE).select(Set.of(0)));
        ShapeCanvas canvas = canvas(definition, draft);

        drag(canvas, new Point(136, 120), new Point(152, 136));
        assertThat(draft.get().appearance().size()).isEqualTo(new GridSize(8, 6));
        assertThat(((SymbolPath) draft.get().elements().getFirst()).nodes().get(2).point())
                .isEqualTo(new SymbolPoint(48, 32));
        drag(canvas, new Point(152, 136), new Point(136, 120));
        assertThat(draft.get().appearance().size()).isEqualTo(new GridSize(6, 4));
    }

    @Test
    void theFigureCornerCanShrinkWithoutBeingCapturedByTheBoxHandle() {
        ComponentDefinition definition = ShapeEditorPanelTest.definition();
        AtomicReference<SymbolDraft> draft = new AtomicReference<>(SymbolDraft.of(ComponentAppearance.of(definition),
                definition.pinout(), false).applyTemplate(SymbolTemplates.Template.RECTANGLE).select(Set.of(0)));
        ShapeCanvas canvas = canvas(definition, draft);

        drag(canvas, new Point(118, 102), new Point(94, 86));

        assertThat(draft.get().appearance().size()).isEqualTo(new GridSize(6, 4));
        SymbolPath figure = (SymbolPath) draft.get().elements().getFirst();
        assertThat(figure.nodes().get(2).point()).isEqualTo(new SymbolPoint(24, 16));
    }

    private static ShapeCanvas canvas(ComponentDefinition definition, AtomicReference<SymbolDraft> draft) {
        ShapeCanvas canvas = new ShapeCanvas(definition, draft::get, draft::set, draft::set,
                () -> ColorSchemes.LIGHT, () -> { });
        canvas.setZoom(1);
        canvas.setSize(400, 300);
        return canvas;
    }

    private static void drag(ShapeCanvas canvas, Point start, Point end) {
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 0, MouseEvent.BUTTON1_DOWN_MASK,
                start.x, start.y, 1, false, MouseEvent.BUTTON1));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_DRAGGED, 0, MouseEvent.BUTTON1_DOWN_MASK,
                end.x, end.y, 0, false, MouseEvent.NOBUTTON));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, 0, 0,
                end.x, end.y, 1, false, MouseEvent.BUTTON1));
    }
}
