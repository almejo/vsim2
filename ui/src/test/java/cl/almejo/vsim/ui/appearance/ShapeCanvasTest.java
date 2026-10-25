package cl.almejo.vsim.ui.appearance;

import cl.almejo.vsim.core.component.appearance.ComponentAppearance;
import cl.almejo.vsim.core.component.appearance.SymbolDraft;
import cl.almejo.vsim.core.component.appearance.SymbolPath;
import cl.almejo.vsim.core.component.appearance.SymbolPoint;
import cl.almejo.vsim.core.component.appearance.SymbolTemplates;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.appearance.SymbolText;
import cl.almejo.vsim.core.component.appearance.TextAlignment;
import cl.almejo.vsim.core.component.appearance.TextSize;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.DefinitionPin;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.awt.event.MouseEvent;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

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

    @Test
    void theBoxIsFittedAndCentredWhenTheEditorOpensWhateverItsProportions() {
        for (GridSize size : new GridSize[]{new GridSize(6, 8), new GridSize(20, 4)}) {
            ComponentDefinition definition = ShapeEditorPanelTest.definition();
            AtomicReference<SymbolDraft> draft = new AtomicReference<>(SymbolDraft.of(ComponentAppearance.of(definition),
                    definition.pinout(), false).resizeBox(size));
            ShapeCanvas canvas = new ShapeCanvas(definition, draft::get, draft::set, draft::set,
                    () -> ColorSchemes.LIGHT, () -> { });
            canvas.setSize(700, 500);
            paintInto(canvas);

            Rectangle2D box = canvas.boxOnScreen();
            assertThat(box.getCenterX()).isCloseTo(350, within(1.5));
            assertThat(box.getCenterY()).isCloseTo(250, within(1.5));
            assertThat(box.getMinX()).isGreaterThanOrEqualTo(40);
            assertThat(box.getMaxX()).isLessThanOrEqualTo(660);
            assertThat(box.getMinY()).isGreaterThanOrEqualTo(40);
            assertThat(box.getMaxY()).isLessThanOrEqualTo(460);
            assertThat(box.getWidth() > box.getHeight()).isEqualTo(size.width() > size.height());
            assertThat(canvas.zoom()).isGreaterThan(2);
        }
    }

    @Test
    void aZoomChosenByTheUserIsNotOverriddenByTheFit() {
        ComponentDefinition definition = ShapeEditorPanelTest.definition();
        AtomicReference<SymbolDraft> draft = new AtomicReference<>(SymbolDraft.of(ComponentAppearance.of(definition),
                definition.pinout(), false));
        ShapeCanvas canvas = canvas(definition, draft);
        paintInto(canvas);
        assertThat(canvas.zoom()).isEqualTo(1);
    }

    @Test
    void theGridHasOneDotPerGridUnit() {
        ComponentDefinition definition = ShapeEditorPanelTest.definition();
        AtomicReference<SymbolDraft> draft = new AtomicReference<>(SymbolDraft.of(ComponentAppearance.of(definition),
                definition.pinout(), false));
        ShapeCanvas canvas = canvas(definition, draft);
        canvas.setZoom(3);
        BufferedImage image = paintInto(canvas);
        // Far from the box: dots at the origin (70) plus multiples of one unit (8 px * zoom 3 = 24 px).
        int onUnit = image.getRGB(70 + 24 * 8, 70 + 24 * 6);
        int halfUnit = image.getRGB(70 + 24 * 8 + 12, 70 + 24 * 6);
        assertThat(onUnit).isNotEqualTo(ColorSchemes.LIGHT.background().getRGB());
        assertThat(halfUnit).isEqualTo(ColorSchemes.LIGHT.background().getRGB());
    }

    @Test
    void aPinDroppedOnAnotherIsMarkedWithTheNameOfTheOneAlreadyThere() {
        ComponentDefinition definition = twoPinDefinition();
        AtomicReference<SymbolDraft> draft = new AtomicReference<>(SymbolDraft.of(ComponentAppearance.of(definition),
                definition.pinout(), false));
        ShapeCanvas canvas = canvas(definition, draft);
        assertThat(canvas.coincidences()).isEmpty();

        // B is at (0, 3) -> y 94; A at (0, 2) -> y 86. Pressed and dragged, not yet released.
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 0, MouseEvent.BUTTON1_DOWN_MASK,
                70, 94, 1, false, MouseEvent.BUTTON1));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_DRAGGED, 0, MouseEvent.BUTTON1_DOWN_MASK,
                70, 86, 0, false, MouseEvent.NOBUTTON));

        assertThat(canvas.coincidences()).hasSize(2);
        assertThat(canvas.coincidenceCallout()).hasValue(Messages.format("shape.pin.coincides", "A"));
        BufferedImage image = paintInto(canvas);
        assertThat(image.getRGB(70, 86)).isEqualTo(ColorSchemes.LIGHT.wireConflict().getRGB());

        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_DRAGGED, 0, MouseEvent.BUTTON1_DOWN_MASK,
                70, 102, 0, false, MouseEvent.NOBUTTON));
        assertThat(canvas.coincidences()).isEmpty();
        assertThat(canvas.coincidenceCallout()).isEmpty();
    }

    @Test
    void theSelectedPinShowsItsNameDirectionAndWidthInACallout() {
        ComponentDefinition definition = twoPinDefinition();
        AtomicReference<SymbolDraft> draft = new AtomicReference<>(SymbolDraft.of(ComponentAppearance.of(definition),
                definition.pinout(), false));
        ShapeCanvas canvas = canvas(definition, draft);
        assertThat(canvas.pinCallout()).isEmpty();

        canvas.selectPin(definition.pinout().getFirst().source());

        assertThat(canvas.pinCallout()).hasValue(Messages.format("shape.pin.row", "A",
                Messages.text("shape.direction.input"), 1));
    }

    @Test
    void draggingTheBoxOrAFigureHandleShowsItsSizeInUnits() {
        ComponentDefinition definition = ShapeEditorPanelTest.definition();
        AtomicReference<SymbolDraft> draft = new AtomicReference<>(SymbolDraft.of(ComponentAppearance.of(definition),
                definition.pinout(), false).applyTemplate(SymbolTemplates.Template.RECTANGLE).select(Set.of(0)));
        ShapeCanvas canvas = canvas(definition, draft);
        assertThat(canvas.dragTip()).isEmpty();

        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 0, MouseEvent.BUTTON1_DOWN_MASK,
                136, 120, 1, false, MouseEvent.BUTTON1));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_DRAGGED, 0, MouseEvent.BUTTON1_DOWN_MASK,
                152, 136, 0, false, MouseEvent.NOBUTTON));
        assertThat(canvas.dragTip()).hasValue("8 × 6");
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, 0, 0, 152, 136, 1, false, MouseEvent.BUTTON1));
        assertThat(canvas.dragTip()).isEmpty();

        // The figure's own corner handle: the figure (6 x 4 units) shrinks from its bottom right corner.
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 0, MouseEvent.BUTTON1_DOWN_MASK,
                118, 102, 1, false, MouseEvent.BUTTON1));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_DRAGGED, 0, MouseEvent.BUTTON1_DOWN_MASK,
                102, 86, 0, false, MouseEvent.NOBUTTON));
        assertThat(canvas.dragTip().orElseThrow()).startsWith("4 × 2").contains(Messages.text("shape.drag.shift"));
    }

    @Test
    void aSelectedNodeShowsItsCoordinatesInGridUnits() {
        ComponentDefinition definition = ShapeEditorPanelTest.definition();
        AtomicReference<SymbolDraft> draft = new AtomicReference<>(SymbolDraft.of(ComponentAppearance.of(definition),
                definition.pinout(), false).applyTemplate(SymbolTemplates.Template.RECTANGLE));
        ShapeCanvas canvas = canvas(definition, draft);
        canvas.setTool(ShapeTool.NODES);
        assertThat(canvas.nodeLabel()).isEmpty();

        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 0, MouseEvent.BUTTON1_DOWN_MASK,
                70, 70, 1, false, MouseEvent.BUTTON1));
        assertThat(canvas.nodeLabel()).hasValue("(0, 0)");
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_DRAGGED, 0, MouseEvent.BUTTON1_DOWN_MASK,
                78, 74, 0, false, MouseEvent.NOBUTTON));
        assertThat(canvas.nodeLabel().orElseThrow()).startsWith("(1, ");
    }

    @Test
    void undoRedoAndDuplicateInsideTheInPlaceTextFieldActOnTheFieldNotOnTheDraft() {
        ComponentDefinition definition = ShapeEditorPanelTest.definition();
        SymbolDraft original = SymbolDraft.of(ComponentAppearance.of(definition), definition.pinout(), false)
                .applyTemplate(SymbolTemplates.Template.RECTANGLE)
                .addText(new SymbolText(new SymbolPoint(8, 8), Optional.of("uno"), TextSize.MEDIUM, TextAlignment.LEFT));
        AtomicReference<SymbolDraft> draft = new AtomicReference<>(original);
        ShapeCanvas canvas = canvas(definition, draft);
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_CLICKED, 0, 0, 78, 78, 2, false, MouseEvent.BUTTON1));
        JTextField field = (JTextField) canvas.getComponent(0);
        field.setText("dos");

        for (String key : new String[]{"ctrl Z", "ctrl Y", "ctrl D", "meta Z", "meta Y", "meta D", "ctrl shift Z"}) {
            Object binding = field.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(key));
            assertThat(binding).as(key).isNotNull();
            field.getActionMap().get(binding).actionPerformed(new ActionEvent(field, ActionEvent.ACTION_PERFORMED, key));
        }

        assertThat(draft.get()).isSameAs(original);
        assertThat(canvas.getComponentCount()).isEqualTo(1);
        Object undo = field.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke("ctrl Z"));
        field.setText("tres");
        field.getActionMap().get(undo).actionPerformed(new ActionEvent(field, ActionEvent.ACTION_PERFORMED, "undo"));
        assertThat(field.getText()).isNotEqualTo("tres");
    }

    @Test
    void aDraftChangeUnderAnOpenTextFieldClosesTheFieldInsteadOfCorruptingTheSession() {
        ComponentDefinition definition = ShapeEditorPanelTest.definition();
        SymbolDraft original = SymbolDraft.of(ComponentAppearance.of(definition), definition.pinout(), false)
                .applyTemplate(SymbolTemplates.Template.RECTANGLE)
                .addText(new SymbolText(new SymbolPoint(8, 8), Optional.of("uno"), TextSize.MEDIUM, TextAlignment.LEFT));
        AtomicReference<SymbolDraft> draft = new AtomicReference<>(original);
        ShapeCanvas canvas = canvas(definition, draft);
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_CLICKED, 0, 0, 78, 78, 2, false, MouseEvent.BUTTON1));
        assertThat(canvas.operationActive()).isTrue();

        draft.set(SymbolDraft.of(ComponentAppearance.of(definition), definition.pinout(), false));
        canvas.draftChanged();

        assertThat(canvas.operationActive()).isFalse();
        canvas.finishOperation();
        assertThat(draft.get().elements()).isEmpty();
    }

    private static BufferedImage paintInto(ShapeCanvas canvas) {
        BufferedImage image = new BufferedImage(canvas.getWidth(), canvas.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try { canvas.paint(graphics); } finally { graphics.dispose(); }
        return image;
    }

    private static ComponentDefinition twoPinDefinition() {
        ElementId first = new ElementId("first");
        ElementId second = new ElementId("second");
        Component input = new Component(first, InterfacePins.INPUT_PIN, new GridPoint(0, 0), Rotation.R0, Map.of(), "A");
        Component other = new Component(second, InterfacePins.INPUT_PIN, new GridPoint(0, 4), Rotation.R0, Map.of(), "B");
        return new ComponentDefinition("test", "Prueba", "", new GridSize(6, 4),
                List.of(new DefinitionPin(PinSpec.input("A", 0, 2, 1), first),
                        new DefinitionPin(PinSpec.input("B", 0, 3, 1), second)),
                new CircuitFragment(List.of(input, other), List.of(), List.of()));
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
