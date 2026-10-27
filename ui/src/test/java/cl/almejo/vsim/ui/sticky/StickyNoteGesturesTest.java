package cl.almejo.vsim.ui.sticky;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.CreateFrameCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.document.StickyChecklist;
import cl.almejo.vsim.core.document.StickyColor;
import cl.almejo.vsim.core.document.StickyContent;
import cl.almejo.vsim.core.document.StickyItem;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.core.document.StickyText;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.frames.FrameGestures;
import cl.almejo.vsim.ui.frames.FramesFeatures;
import cl.almejo.vsim.ui.tools.CanvasCursor;
import cl.almejo.vsim.ui.tools.CanvasMouseEvent;
import cl.almejo.vsim.ui.tools.SelectTool;
import cl.almejo.vsim.ui.tools.TestToolContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The select tool's sticky note gestures (spec §3): a note at (10, 10), 16 x 10 grid units, big enough for three
 * checklist items. Points on the controls come from the same layout the painter uses.
 */
class StickyNoteGesturesTest {

    private final TestToolContext context = TestToolContext.withNewDocument();
    private final SelectTool tool = new SelectTool();
    private final List<String> asked = new ArrayList<>();
    private boolean answer = true;
    private final StickyNoteGestures gestures = new StickyNoteGestures(() -> null, (parent, firstLine) -> {
        asked.add(firstLine);
        return answer;
    });

    StickyNoteGesturesTest() {
        tool.addDelegate(gestures);
        tool.addDelegate(new FrameGestures());
        context.selection.addExpansion(FramesFeatures.lockedBlocks(context.openDocument()));
    }

    @AfterEach
    void close() {
        context.close();
    }

    private StickyNote note(StickyContent content) {
        StickyNote note = new StickyNote(context.openDocument().document().newStickyNoteId(), new GridPoint(10, 10),
                16, 10, StickyColor.YELLOW, content, false);
        context.openDocument().execute(new AddElementsCommand(List.of(note)));
        context.openDocument().history().clear();
        return note;
    }

    private StickyNote checklist() {
        return note(new StickyChecklist(List.of(new StickyItem("Comprar LEDs", false),
                new StickyItem("Probar el sumador", false), new StickyItem("Entregar", true))));
    }

    private Frame lockedFrameAround() {
        Frame frame = new Frame(context.openDocument().document().newFrameId(), new GridPoint(5, 5), 30, 20,
                "Bloque", FrameTint.NONE, true);
        context.openDocument().execute(new CreateFrameCommand(frame));
        context.openDocument().history().clear();
        return frame;
    }

    private StickyNote current(StickyNote note) {
        return context.openDocument().document().stickyNote(note.id()).orElseThrow();
    }

    private static StickyNoteLayout layout(StickyNote note) {
        return StickyNoteLayout.of(note, StickyNoteHitTester.measuringMetrics(), 8);
    }

    private static Point2D gridCentre(Rectangle2D documentPixels) {
        return new Point2D.Double(documentPixels.getCenterX() / GridPoint.PIXELS_PER_UNIT,
                documentPixels.getCenterY() / GridPoint.PIXELS_PER_UNIT);
    }

    private void click(Point2D point) {
        context.click(tool, point.getX(), point.getY());
    }

    private void doubleClick(double gridX, double gridY) {
        tool.mousePressed(context, TestToolContext.event(gridX, gridY, CanvasMouseEvent.PRIMARY, 1, false));
        tool.mouseReleased(context, TestToolContext.event(gridX, gridY, CanvasMouseEvent.PRIMARY, 1, false));
        tool.mousePressed(context, TestToolContext.event(gridX, gridY, CanvasMouseEvent.PRIMARY, 2, false));
        tool.mouseReleased(context, TestToolContext.event(gridX, gridY, CanvasMouseEvent.PRIMARY, 2, false));
    }

    // --- the hit tester

    @Test
    void theHitTesterFindsEveryPartAndTheTopmostNote() {
        StickyNote below = checklist();
        StickyNote above = note(new StickyText("encima"));
        StickyNoteLayout layout = layout(above);
        Optional<StickyNoteHit> close = StickyNoteHitTester.hit(context.openDocument().document(),
                gridCentre(layout.closeBox()), 8, StickyNoteHitTester.measuringMetrics());
        assertThat(close).contains(new StickyNoteHit(above.id(), StickyPart.CLOSE, StickyNoteHit.NO_ITEM));
        assertThat(StickyNoteHitTester.hit(context.openDocument().document(), new Point2D.Double(12, 14), 8,
                StickyNoteHitTester.measuringMetrics()).orElseThrow().note()).isEqualTo(above.id());
        assertThat(StickyNoteHitTester.hit(context.openDocument().document(), new Point2D.Double(40, 40), 8,
                StickyNoteHitTester.measuringMetrics())).isEmpty();
        assertThat(below.id()).isNotEqualTo(above.id());
    }

    @Test
    void theHitTesterFindsACheckboxByItsItemAndTheResizeCorner() {
        StickyNote note = checklist();
        StickyNoteLayout layout = layout(note);
        assertThat(StickyNoteHitTester.hit(context.openDocument().document(), gridCentre(layout.checkboxes().get(1)),
                8, StickyNoteHitTester.measuringMetrics()))
                .contains(new StickyNoteHit(note.id(), StickyPart.CHECKBOX, 1));
        assertThat(StickyNoteHitTester.hit(context.openDocument().document(), gridCentre(layout.resizeHandle()),
                8, StickyNoteHitTester.measuringMetrics()).orElseThrow().part()).isEqualTo(StickyPart.RESIZE);
    }

    @Test
    void zoomedFarOutTheCloseButtonIsNotThereToHit() {
        StickyNote note = note(new StickyText("lejos"));
        Point2D closePoint = gridCentre(layout(note).closeBox());
        assertThat(StickyNoteHitTester.hit(context.openDocument().document(), closePoint, 2,
                StickyNoteHitTester.measuringMetrics()).orElseThrow().part()).isEqualTo(StickyPart.BODY);
    }

    // --- close

    @Test
    void theCloseButtonAsksAndOnDespegarRemovesTheNoteAsOneUndoStep() {
        StickyNote note = note(new StickyText("Comprar LEDs\notra cosa"));
        click(gridCentre(layout(note).closeBox()));

        assertThat(asked).containsExactly("Comprar LEDs");
        assertThat(context.openDocument().document().stickyNotes()).isEmpty();
        context.openDocument().history().undo();
        assertThat(current(note)).isEqualTo(note);
        assertThat(context.openDocument().history().canUndo()).isFalse();
    }

    @Test
    void cancellingTheCloseLeavesDocumentHistoryAndSelectionUntouched() {
        StickyNote note = note(new StickyText("Comprar LEDs"));
        answer = false;
        Point2D closePoint = gridCentre(layout(note).closeBox());
        tool.mousePressed(context, TestToolContext.at(closePoint.getX(), closePoint.getY()));
        tool.mouseDragged(context, TestToolContext.at(closePoint.getX() + 5, closePoint.getY() + 5));
        tool.mouseReleased(context, TestToolContext.at(closePoint.getX() + 5, closePoint.getY() + 5));

        assertThat(asked).hasSize(1);
        assertThat(current(note)).isEqualTo(note);
        assertThat(context.openDocument().history().canUndo()).isFalse();
        assertThat(context.selection.ids()).isEmpty();
    }

    // --- checkboxes

    @Test
    void aCheckboxPressTogglesThatItemOnlyWithoutSelectingOrMoving() {
        StickyNote note = checklist();
        Point2D box = gridCentre(layout(note).checkboxes().get(1));
        tool.mousePressed(context, TestToolContext.at(box.getX(), box.getY()));
        tool.mouseDragged(context, TestToolContext.at(box.getX() + 4, box.getY() + 3));
        tool.mouseReleased(context, TestToolContext.at(box.getX() + 4, box.getY() + 3));

        StickyChecklist items = (StickyChecklist) current(note).content();
        assertThat(items.items()).extracting(StickyItem::done).containsExactly(false, true, true);
        assertThat(current(note).position()).isEqualTo(note.position());
        assertThat(context.selection.ids()).isEmpty();
        assertThat(context.openDocument().history().undoDescription()).contains("command.tickStickyItem");
        context.openDocument().history().undo();
        assertThat(context.openDocument().history().canUndo()).isFalse();
    }

    @Test
    void aSecondCheckboxClickUnticks() {
        StickyNote note = checklist();
        click(gridCentre(layout(note).checkboxes().get(2)));
        assertThat(((StickyChecklist) current(note).content()).items().get(2).done()).isFalse();
        assertThat(context.openDocument().history().undoDescription()).contains("command.untickStickyItem");
    }

    // --- resize

    @Test
    void draggingTheFoldedCornerResizesAsOneCommand() {
        StickyNote note = note(new StickyText("crece"));
        Point2D corner = gridCentre(layout(note).resizeHandle());
        context.drag(tool, corner.getX(), corner.getY(), corner.getX() + 4, corner.getY() + 2);

        assertThat(current(note).width()).isEqualTo(20);
        assertThat(current(note).height()).isEqualTo(12);
        assertThat(current(note).position()).isEqualTo(note.position());
        assertThat(context.openDocument().history().undoDescription()).contains("command.resizeStickyNote");
        context.openDocument().history().undo();
        assertThat(context.openDocument().history().canUndo()).isFalse();
    }

    @Test
    void aResizeNeverGoesBelowTheMinimum() {
        StickyNote note = note(new StickyText("chica"));
        Point2D corner = gridCentre(layout(note).resizeHandle());
        context.drag(tool, corner.getX(), corner.getY(), corner.getX() - 20, corner.getY() - 20);

        assertThat(current(note).width()).isEqualTo(StickyNote.MINIMUM_WIDTH);
        assertThat(current(note).height()).isEqualTo(StickyNote.MINIMUM_HEIGHT);
    }

    // --- body

    @Test
    void aClickOnTheBodySelectsTheNoteEvenOverAPart() {
        place(context.openDocument(), StandardComponentTypes.NOT, 11, 11, Map.of());
        StickyNote note = note(new StickyText("encima"));
        context.click(tool, 12.5, 13.5);
        assertThat(context.selection.ids()).containsExactly(note.id());
    }

    @Test
    void draggingTheBodyMovesItSnappedAsOneUndoStep() {
        StickyNote note = note(new StickyText("muévete"));
        context.drag(tool, 12.4, 13.2, 15.6, 15.1);

        assertThat(current(note).position()).isEqualTo(new GridPoint(14, 12));
        assertThat(context.openDocument().history().undoDescription()).contains("command.move");
        context.openDocument().history().undo();
        assertThat(current(note)).isEqualTo(note);
        assertThat(context.openDocument().history().canUndo()).isFalse();
    }

    @Test
    void aRubberBandSelectsTheNote() {
        StickyNote note = note(new StickyText("dentro"));
        context.drag(tool, 5, 5, 30, 30);
        assertThat(context.selection.ids()).containsExactly(note.id());
    }

    @Test
    void aDoubleClickOnTheBodyOpensTheEditor() {
        StickyNote note = note(new StickyText("edítame"));
        doubleClick(12, 14);
        assertThat(context.editedLabel).isEqualTo(note);
    }

    @Test
    void aDoubleClickOnAFoldedNoteUnfoldsIt() {
        StickyNote note = note(new StickyText("plegada"));
        context.openDocument().execute(new cl.almejo.vsim.core.command.ChangeStickyNoteCommand(note,
                note.withFolded(true)));
        context.openDocument().history().clear();
        doubleClick(10.5, 10.5);

        assertThat(current(note).folded()).isFalse();
        assertThat(context.editedLabel).isNull();
        assertThat(context.openDocument().history().undoDescription()).contains("command.unfoldStickyNote");
    }

    @Test
    void aRightClickSelectsTheNoteAndOpensItsMenu() {
        StickyNote note = note(new StickyText("menú"));
        tool.mousePressed(context, TestToolContext.event(12, 14, CanvasMouseEvent.SECONDARY, 1, false));
        assertThat(context.selection.ids()).containsExactly(note.id());
        assertThat(context.contextMenuTarget).contains(current(note));
    }

    @Test
    void theCursorTellsWhatAPressWouldDo() {
        StickyNote note = checklist();
        StickyNoteLayout layout = layout(note);
        Point2D close = gridCentre(layout.closeBox());
        Point2D corner = gridCentre(layout.resizeHandle());
        assertThat(gestures.moved(context, TestToolContext.at(close.getX(), close.getY())))
                .contains(CanvasCursor.HAND);
        assertThat(gestures.moved(context, TestToolContext.at(corner.getX(), corner.getY())))
                .contains(CanvasCursor.RESIZE_SOUTH_EAST);
        assertThat(gestures.moved(context, TestToolContext.at(40, 40))).isEmpty();
    }

    // --- inside a locked frame

    @Test
    void insideALockedFrameTheControlsAndTheEditorAreRefused() {
        StickyNote note = checklist();
        lockedFrameAround();
        StickyNoteLayout layout = layout(note);

        click(gridCentre(layout.closeBox()));
        click(gridCentre(layout.checkboxes().get(0)));
        Point2D corner = gridCentre(layout.resizeHandle());
        context.drag(tool, corner.getX(), corner.getY(), corner.getX() + 4, corner.getY() + 2);
        doubleClick(12, 14);

        assertThat(asked).isEmpty();
        assertThat(current(note).content()).isEqualTo(note.content());
        assertThat(current(note).width()).isEqualTo(note.width());
        assertThat(current(note).height()).isEqualTo(note.height());
        assertThat(context.editedLabel).isNull();
    }

    @Test
    void movingTheLockedFrameMovesTheNoteWithIt() {
        StickyNote note = note(new StickyText("viaja"));
        Frame frame = lockedFrameAround();
        context.drag(tool, 30, 6, 33, 8);

        assertThat(context.openDocument().document().frame(frame.id()).orElseThrow().topLeft())
                .isEqualTo(new GridPoint(8, 7));
        assertThat(current(note).position()).isEqualTo(new GridPoint(13, 12));
    }
}
