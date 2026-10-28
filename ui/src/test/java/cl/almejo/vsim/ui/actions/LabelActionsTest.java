package cl.almejo.vsim.ui.actions;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.AddWireCommand;
import cl.almejo.vsim.core.command.SetLabelCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.command.CreateFrameCommand;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.EditorFixture;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.Selection;
import cl.almejo.vsim.ui.inline.InlineTextEditor;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

class LabelActionsTest {

    private OpenDocument openDocument;
    private EditorFixture fixture;
    private EditorView view;
    private Selection selection;
    private InlineTextEditor editor;
    private LabelActions actions;

    @BeforeEach
    void create() {
        fixture = EditorFixture.create();
        openDocument = fixture.openDocument();
        view = fixture.view();
        view.canvas().setSize(800, 600);
        selection = view.canvas().selection();
        editor = view.inlineEditor();
        actions = view.labelFeatures().actions();
    }

    @AfterEach
    void close() {
        fixture.close();
    }

    /** Types {@code text} into the open inline editor and confirms it with Enter. */
    private void answer(String text) {
        assertThat(editor.isOpen()).isTrue();
        editor.textComponent().setText(text);
        editor.confirm();
    }

    private void cancel() {
        assertThat(editor.isOpen()).isTrue();
        editor.cancel();
    }


    private Component gate() {
        return place(openDocument, StandardComponentTypes.BUFFER, 0, 0, Map.of(StandardComponentTypes.NEGATE, true));
    }

    private Component labelOf(Component component) {
        return openDocument.document().component(component.id()).orElseThrow();
    }

    private TextLabel note(String text) {
        TextLabel label = new TextLabel(openDocument.document().newLabelId(), new GridPoint(10, 10), text);
        openDocument.execute(new AddElementsCommand(List.of(label)));
        return label;
    }

    private Wire wire(int startX, int startY, int endX, int endY) {
        Wire wire = new Wire(openDocument.document().newWireId(), new GridPoint(startX, startY),
                new GridPoint(endX, endY));
        openDocument.execute(new AddWireCommand(wire));
        return wire;
    }

    @Test
    void editLabelSetsTheComponentLabelAsOneUndoStep() {
        Component gate = gate();
        actions.editLabel(gate);
        assertThat(editor.field().getText()).isEmpty();
        answer(" U3 ");
        assertThat(labelOf(gate).label()).isEqualTo("U3");
        assertThat(openDocument.history().undoDescription()).contains("command.label");
        openDocument.history().undo();
        assertThat(labelOf(gate).label()).isEmpty();
    }

    @Test
    void blankAnswerRemovesAComponentLabel() {
        Component gate = gate();
        openDocument.execute(new SetLabelCommand(gate.id(), "U1"));
        actions.editLabel(gate);
        assertThat(editor.field().getText()).isEqualTo("U1");
        assertThat(editor.field().getSelectedText()).isEqualTo("U1");
        answer("   ");
        assertThat(labelOf(gate).label()).isEmpty();
    }

    @Test
    void cancelOrSameTextAddsNoStep() {
        Component gate = gate();
        actions.editLabel(gate);
        cancel();
        actions.editLabel(gate);
        answer("");
        assertThat(openDocument.history().undoDescription()).contains("command.add");
    }

    @Test
    void editLabelOfAWireAsksNothing() {
        actions.editLabel(wire(0, 0, 5, 0));
        assertThat(editor.isOpen()).isFalse();
    }

    @Test
    void blankAnswerKeepsTheFreeText() {
        TextLabel label = note("nota");
        actions.editLabel(label);
        answer("  ");
        assertThat(openDocument.document().label(label.id()).orElseThrow().text()).isEqualTo("nota");
        actions.editLabel(label);
        answer("otra");
        assertThat(openDocument.document().label(label.id()).orElseThrow().text()).isEqualTo("otra");
    }

    @Test
    void addTextAtCreatesASelectedFreeText() {
        actions.addTextAt(new GridPoint(3, 4));
        assertThat(editor.textComponent().getText()).isEmpty();
        answer("hola");
        assertThat(openDocument.document().labels()).singleElement().satisfies(label -> {
            assertThat(label.text()).isEqualTo("hola");
            assertThat(label.position()).isEqualTo(new GridPoint(3, 4));
            assertThat(selection.ids()).containsExactly(label.id());
        });
        openDocument.history().undo();
        assertThat(openDocument.document().labels()).isEmpty();
    }

    @Test
    void blankTextAddsNoLabel() {
        actions.addTextAt(GridPoint.ORIGIN);
        answer("  ");
        actions.addTextAt(GridPoint.ORIGIN);
        cancel();
        assertThat(openDocument.document().labels()).isEmpty();
        assertThat(openDocument.history().canUndo()).isFalse();
    }

    @Test
    void removeWireSegmentCutsTheWireAsOneStep() {
        Wire main = wire(0, 0, 10, 0);
        wire(5, 0, 5, 4);
        actions.removeWireSegment(main, new GridPoint(8, 0));
        assertThat(openDocument.document().wire(main.id()).orElseThrow().end()).isEqualTo(new GridPoint(5, 0));
        assertThat(openDocument.history().undoDescription()).contains("command.removeSegment");
    }

    @Test
    void editLabelActionIsEnabledForOneComponentOrFreeText() {
        Component gate = gate();
        TextLabel label = note("nota");
        Wire wire = wire(20, 20, 25, 20);
        assertThat(actions.editLabel().isEnabled()).isFalse();
        selection.select(gate.id());
        assertThat(actions.editLabel().isEnabled()).isTrue();
        selection.select(label.id());
        assertThat(actions.editLabel().isEnabled()).isTrue();
        selection.select(wire.id());
        assertThat(actions.editLabel().isEnabled()).isFalse();
        selection.set(List.of(gate.id(), label.id()));
        assertThat(actions.editLabel().isEnabled()).isFalse();
    }

    @Test
    void editLabelOnAFrameChangesItsTitleAsOneUndoStepAndAnEmptyTitleIsAllowed() {
        Frame frame = new Frame(openDocument.document().newFrameId(), new GridPoint(0, 0), 20, 10, "Bloque",
                FrameTint.NONE, false);
        openDocument.execute(new CreateFrameCommand(frame));
        actions.editLabel(frame);
        assertThat(editor.field().getText()).isEqualTo("Bloque");
        answer(" Sumador ");
        assertThat(openDocument.document().frame(frame.id()).orElseThrow().title()).isEqualTo("Sumador");
        assertThat(openDocument.history().undoDescription()).contains("command.renameFrame");

        actions.editLabel(openDocument.document().frame(frame.id()).orElseThrow());
        answer("");
        assertThat(openDocument.document().frame(frame.id()).orElseThrow().title()).isEmpty();

        openDocument.history().undo();
        assertThat(openDocument.document().frame(frame.id()).orElseThrow().title()).isEqualTo("Sumador");
        openDocument.history().undo();
        assertThat(openDocument.document().frame(frame.id()).orElseThrow().title()).isEqualTo("Bloque");
    }

    @Test
    void f2IsEnabledWithASingleSelectedFrame() {
        Frame frame = new Frame(openDocument.document().newFrameId(), new GridPoint(0, 0), 20, 10, "Bloque",
                FrameTint.NONE, false);
        openDocument.execute(new CreateFrameCommand(frame));
        selection.select(frame.id());
        assertThat(actions.editLabel().isEnabled()).isTrue();
        actions.editLabel().actionPerformed(null);
        answer("Lógica");
        assertThat(openDocument.document().frame(frame.id()).orElseThrow().title()).isEqualTo("Lógica");
    }

    @Test
    void f2EditsTheTitleOfASingleSelectedLockedFrame() {
        Frame frame = new Frame(openDocument.document().newFrameId(), new GridPoint(0, 0), 20, 10, "Bloque",
                FrameTint.NONE, true);
        openDocument.execute(new CreateFrameCommand(frame));
        selection.select(frame.id());
        assertThat(actions.editLabel().isEnabled()).isTrue();
        actions.editLabel().actionPerformed(null);
        answer("Banderas");
        assertThat(openDocument.document().frame(frame.id()).orElseThrow().title()).isEqualTo("Banderas");
    }

    @Test
    void theEditorOpensOverTheFreeTextAsPainted() {
        TextLabel label = note("nota");
        actions.editLabel(label);
        java.awt.Rectangle area = textArea();
        java.awt.geom.Rectangle2D painted = view.canvas().viewport().documentTransform()
                .createTransformedShape(cl.almejo.vsim.ui.canvas.LabelLayout.freeText(label).bounds()).getBounds2D();
        assertThat((double) area.x).isBetween(painted.getX() - 1, painted.getX() + 1);
        assertThat((double) area.y).isBetween(painted.getY() - 1, painted.getY() + 1);
        cancel();
    }

    @Test
    void theComponentLabelEditorSitsWhereAMissingLabelWouldGo() {
        Component gate = gate();
        actions.editLabel(gate);
        java.awt.Rectangle empty = textArea();
        cancel();
        openDocument.execute(new SetLabelCommand(gate.id(), "U1"));
        actions.editLabel(labelOf(gate));
        java.awt.Rectangle placed = textArea();
        cancel();
        // both are centred above the body
        assertThat(empty.getCenterX()).isBetween(placed.getCenterX() - 2, placed.getCenterX() + 2);
        assertThat(empty.y).isBetween(placed.y - 3, placed.y + 3);
    }

    @Test
    void aSidewaysLabelEditorGrowsToTheLeftAwayFromTheBody() {
        Component gate = new Component(openDocument.document().newComponentId(), StandardComponentTypes.BUFFER,
                new GridPoint(10, 10), cl.almejo.vsim.core.geometry.Rotation.R90, Map.of(), "U1");
        openDocument.execute(new AddElementsCommand(List.of(gate)));
        actions.editLabel(gate);
        java.awt.geom.Rectangle2D painted = view.canvas().viewport().documentTransform().createTransformedShape(
                cl.almejo.vsim.ui.canvas.LabelLayout.componentLabelPlace(
                        openDocument.registry().typeOf(gate), gate, "U1").bounds()).getBounds2D();
        java.awt.Rectangle before = textArea();
        assertThat(before.getMaxX()).isBetween(painted.getMaxX() - 1, painted.getMaxX() + 1);
        editor.field().setText("una etiqueta bastante larga");
        java.awt.Rectangle after = textArea();
        assertThat(after.getMaxX()).isEqualTo(before.getMaxX());
        assertThat(after.x).isLessThan(before.x);
        cancel();
    }

    /** M6.8: a moved label is drawn centred on its place, so its editor sits over it and grows both ways. */
    @Test
    void aMovedLabelEditorSitsOverTheLabelAndStaysCentred() {
        Component gate = new Component(openDocument.document().newComponentId(), StandardComponentTypes.BUFFER,
                new GridPoint(10, 10), cl.almejo.vsim.core.geometry.Rotation.R0, Map.of(), "U1",
                java.util.Optional.of(new cl.almejo.vsim.core.document.LabelOffset(2, 9)));
        openDocument.execute(new AddElementsCommand(List.of(gate)));
        actions.editLabel(gate);
        java.awt.geom.Rectangle2D painted = view.canvas().viewport().documentTransform().createTransformedShape(
                cl.almejo.vsim.ui.canvas.LabelLayout.componentLabelPlace(
                        openDocument.registry().typeOf(gate), gate, "U1").bounds()).getBounds2D();
        java.awt.Rectangle before = textArea();
        assertThat(before.getCenterX()).isBetween(painted.getCenterX() - 1, painted.getCenterX() + 1);
        assertThat(before.getCenterY()).isBetween(painted.getCenterY() - 2, painted.getCenterY() + 2);
        editor.field().setText("una etiqueta bastante larga");
        java.awt.Rectangle after = textArea();
        assertThat(after.getCenterX()).isBetween(before.getCenterX() - 1, before.getCenterX() + 1);
        assertThat(after.x).isLessThan(before.x);
        cancel();
    }

    /** The automatic label of an unrotated component is centred above the body, so its editor grows both ways. */
    @Test
    void anAutomaticLabelAboveTheBodyKeepsItsEditorCentred() {
        Component gate = new Component(openDocument.document().newComponentId(), StandardComponentTypes.BUFFER,
                new GridPoint(10, 10), cl.almejo.vsim.core.geometry.Rotation.R0, Map.of(), "U1");
        openDocument.execute(new AddElementsCommand(List.of(gate)));
        actions.editLabel(gate);
        java.awt.geom.Rectangle2D painted = view.canvas().viewport().documentTransform().createTransformedShape(
                cl.almejo.vsim.ui.canvas.LabelLayout.componentLabelPlace(
                        openDocument.registry().typeOf(gate), gate, "U1").bounds()).getBounds2D();
        java.awt.Rectangle before = textArea();
        assertThat(before.getCenterX()).isBetween(painted.getCenterX() - 1, painted.getCenterX() + 1);
        editor.field().setText("una etiqueta bastante larga");
        java.awt.Rectangle after = textArea();
        assertThat(after.getCenterX()).isBetween(before.getCenterX() - 1, before.getCenterX() + 1);
        assertThat(after.x).isLessThan(before.x);
        cancel();
    }

    @Test
    void finishingTheEditBeforeClosingAppliesAValidHalfTypedLabel() {
        Component gate = gate();
        openDocument.history().markSaved();
        assertThat(openDocument.isModified()).isFalse();
        actions.editLabel(gate);
        editor.field().setText("U7");
        editor.confirmOrCancel();
        assertThat(editor.isOpen()).isFalse();
        assertThat(labelOf(gate).label()).isEqualTo("U7");
        assertThat(openDocument.isModified()).isTrue();
    }

    @Test
    void finishingTheEditBeforeClosingDropsAnInvalidConstantValue() {
        Component constant = place(openDocument, StandardComponentTypes.CONSTANT, 0, 0,
                Map.of("width", 4, "value", "5"));
        actions.editConstantValue(constant);
        editor.field().setText("0x1F");
        editor.confirmOrCancel();
        assertThat(editor.isOpen()).isFalse();
        assertThat(openDocument.document().component(constant.id()).orElseThrow().parameters().get("value"))
                .isEqualTo("5");
        assertThat(openDocument.history().undoDescription()).contains("command.add");
    }

    @Test
    void theInlineEditorsUseTheCurrentTextFont() {
        cl.almejo.vsim.ui.paint.TextFonts.set(new cl.almejo.vsim.ui.paint.TextFont(java.awt.Font.SERIF, 14));
        try {
            actions.editLabel(note("nota"));
            assertThat(editor.textComponent().getFont().getFamily()).isEqualTo(java.awt.Font.SERIF);
            cancel();
            Component gate = gate();
            actions.editLabel(gate);
            assertThat(editor.textComponent().getFont().getFamily()).isEqualTo(java.awt.Font.SERIF);
            cancel();
            Frame frame = new Frame(openDocument.document().newFrameId(), new GridPoint(2, 2), 20, 10, "Bloque",
                    FrameTint.NONE, false);
            openDocument.execute(new CreateFrameCommand(frame));
            actions.editLabel(frame);
            assertThat(editor.textComponent().getFont().getFamily()).isEqualTo(java.awt.Font.SERIF);
            assertThat(editor.textComponent().getFont().isBold()).isTrue();
            cancel();
            actions.addTextAt(new GridPoint(30, 30));
            assertThat(editor.textComponent().getFont().getFamily()).isEqualTo(java.awt.Font.SERIF);
            cancel();
        } finally {
            cl.almejo.vsim.ui.paint.TextFonts.set(cl.almejo.vsim.ui.paint.TextFont.DEFAULT);
        }
    }

    @Test
    void theFrameTitleEditorSitsInTheTitleBand() {
        Frame frame = new Frame(openDocument.document().newFrameId(), new GridPoint(2, 2), 20, 10, "Bloque",
                FrameTint.NONE, false);
        openDocument.execute(new CreateFrameCommand(frame));
        actions.editLabel(frame);
        java.awt.geom.Rectangle2D band = view.canvas().viewport().documentTransform().createTransformedShape(
                cl.almejo.vsim.ui.frames.FramePainter.titleBarBounds(frame)).getBounds2D();
        java.awt.Rectangle area = textArea();
        assertThat(area.x).isGreaterThan((int) band.getX());
        assertThat(area.getCenterY()).isBetween(band.getCenterY() - 3, band.getCenterY() + 3);
        assertThat(area.getMaxX()).isLessThanOrEqualTo(band.getMaxX());
        cancel();
    }

    @Test
    void undoRemovingTheEditedElementClosesTheEditorWithoutApplying() {
        Component gate = gate();
        actions.editLabel(gate);
        editor.field().setText("U9");
        openDocument.history().undo();
        assertThat(editor.isOpen()).isFalse();
        assertThat(openDocument.document().component(gate.id())).isEmpty();
    }

    @Test
    void anInvalidConstantValueNeverReachesTheDocumentByAnyRoute() {
        Component constant = place(openDocument, StandardComponentTypes.CONSTANT, 0, 0,
                Map.of("width", 4, "value", "5"));
        actions.editConstantValue(constant);
        assertThat(editor.field().getText()).isEqualTo("5");
        editor.field().setText("0x1F");
        assertThat(editor.problemText().getText()).isEqualTo("No cabe en 4 bits");
        editor.confirm();
        editor.confirmButton().doClick();
        assertThat(editor.isOpen()).isTrue();
        assertThat(openDocument.document().component(constant.id()).orElseThrow().parameters().get("value"))
                .isEqualTo("5");
        pressOutside();
        assertThat(editor.isOpen()).isFalse();
        assertThat(openDocument.document().component(constant.id()).orElseThrow().parameters().get("value"))
                .isEqualTo("5");
        assertThat(openDocument.history().undoDescription()).contains("command.add");
    }

    @Test
    void aConstantValueIsStoredNormalizedInOneUndoStep() {
        Component constant = place(openDocument, StandardComponentTypes.CONSTANT, 0, 0,
                Map.of("width", 8, "value", "5"));
        actions.editConstantValue(constant);
        answer("0x00ff");
        assertThat(openDocument.document().component(constant.id()).orElseThrow().parameters().get("value"))
                .isEqualTo("0xFF");
        assertThat(openDocument.history().undoDescription()).contains("command.configure");
        openDocument.history().undo();
        assertThat(openDocument.document().component(constant.id()).orElseThrow().parameters().get("value"))
                .isEqualTo("5");
    }

    @Test
    void confirmingTheSameConstantValueAddsNoStep() {
        Component constant = place(openDocument, StandardComponentTypes.CONSTANT, 0, 0,
                Map.of("width", 8, "value", "5"));
        actions.editConstantValue(constant);
        answer("5");
        assertThat(openDocument.history().undoDescription()).contains("command.add");
    }

    @Test
    void aWidthChangedMeanwhileMakesTheTypedValueAProblemInsteadOfAnException() {
        Component constant = place(openDocument, StandardComponentTypes.CONSTANT, 0, 0,
                Map.of("width", 8, "value", "5"));
        actions.editConstantValue(constant);
        editor.field().setText("0xFF");
        assertThat(editor.problemText().isVisible()).isFalse();
        cl.almejo.vsim.core.component.ComponentType type = openDocument.registry().typeOf(constant);
        openDocument.execute(new cl.almejo.vsim.core.command.ChangeParametersCommand(constant.id(), type,
                Map.of("width", 4, "value", "5", "format", "auto")));

        assertThat(editor.problemText().isVisible()).isTrue();
        editor.confirm();
        editor.confirmButton().doClick();
        assertThat(editor.isOpen()).isTrue();
        assertThat(editor.problemText().getText()).isEqualTo("No cabe en 4 bits");
        assertThat(openDocument.document().component(constant.id()).orElseThrow().parameters().get("value"))
                .isEqualTo("5");
    }

    @Test
    void theConstantEditorCentersItsText() {
        Component constant = place(openDocument, StandardComponentTypes.CONSTANT, 0, 0,
                Map.of("width", 8, "value", "5"));
        actions.editConstantValue(constant);
        assertThat(editor.field().getHorizontalAlignment()).isEqualTo(javax.swing.SwingConstants.CENTER);
    }

    private void pressOutside() {
        java.awt.Component target = view.canvas();
        target.dispatchEvent(new java.awt.event.MouseEvent(target, java.awt.event.MouseEvent.MOUSE_PRESSED, 0,
                java.awt.event.InputEvent.BUTTON1_DOWN_MASK, 700, 500, 1, false, java.awt.event.MouseEvent.BUTTON1));
    }

    /** The field's text area in canvas pixels. */
    private java.awt.Rectangle textArea() {
        javax.swing.text.JTextComponent text = editor.textComponent();
        java.awt.Rectangle bounds = javax.swing.SwingUtilities.convertRectangle(text.getParent(),
                text.getBounds(), view.canvas());
        java.awt.Insets insets = text.getInsets();
        return new java.awt.Rectangle(bounds.x + insets.left, bounds.y + insets.top,
                bounds.width - insets.left - insets.right, bounds.height - insets.top - insets.bottom);
    }

    @Test
    void aNoteIsEditedOverItsLinesAndKeepsThem() {
        TextLabel label = note("Prueba:\ncambia A");
        actions.editLabel(label);
        assertThat(editor.textComponent()).isSameAs(editor.area());
        assertThat(editor.area().getText()).isEqualTo("Prueba:\ncambia A");
        java.awt.Rectangle area = textArea();
        java.awt.geom.Rectangle2D painted = view.canvas().viewport().documentTransform()
                .createTransformedShape(cl.almejo.vsim.ui.canvas.LabelLayout.freeText(label).bounds()).getBounds2D();
        assertThat((double) area.y).isBetween(painted.getY() - 1, painted.getY() + 1);
        assertThat((double) area.height).isGreaterThanOrEqualTo(painted.getHeight() - 1);
        answer("Prueba:\ncambia A\nmira la salida\n");
        assertThat(openDocument.document().label(label.id()).orElseThrow().text())
                .isEqualTo("Prueba:\ncambia A\nmira la salida");
        openDocument.history().undo();
        assertThat(openDocument.document().label(label.id()).orElseThrow().text()).isEqualTo("Prueba:\ncambia A");
    }

    @Test
    void aNewNoteMayHaveSeveralLinesButAPartLabelStaysOneLine() {
        actions.addTextAt(new GridPoint(3, 4));
        assertThat(editor.textComponent()).isSameAs(editor.area());
        answer("uno\ndos");
        assertThat(openDocument.document().labels()).singleElement()
                .satisfies(label -> assertThat(label.text()).isEqualTo("uno\ndos"));

        actions.editLabel(gate());
        assertThat(editor.textComponent()).isSameAs(editor.field());
        cancel();
    }
}
