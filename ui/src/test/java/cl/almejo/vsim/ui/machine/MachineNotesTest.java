package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.StickyColor;
import cl.almejo.vsim.core.io.CircuitFormat;
import cl.almejo.vsim.core.io.CircuitReader;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.machine.DiagramPoint;
import cl.almejo.vsim.core.machine.MachineNote;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.Application;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.inline.InlineTextEditor;
import cl.almejo.vsim.ui.library.LibraryService;
import cl.almejo.vsim.ui.library.SwingLibraryDialogs;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.shortcuts.ShortcutsExtension;
import cl.almejo.vsim.ui.sticky.StickyNoteTool;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.Action;
import javax.swing.JToggleButton;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.perform;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

/**
 * Sticky notes on a machine's diagram (plan 2026-11-14, hito 3, task 8): the palette's «Nota» (or the toolbar's
 * «Nota adhesiva») sticks a yellow note with a click and opens its text; a double click edits it; a drag moves it, its
 * corner resizes it; Supr deletes it with what else is selected; each step is one undo step, and the notes are saved
 * with the machine.
 */
class MachineNotesTest {

    @TempDir
    Path directory;

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final AppPreferences preferences = AppPreferences.inMemory();
    private LibraryService service;
    private OpenDocument document;
    private OpenDocument place;
    private EditorView view;
    private MachineMode mode;
    private MachineCanvas canvas;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        preferences.setLibraryDirectory(Optional.of(directory.resolve("library")));
        ComponentLibrary library = new ComponentLibrary();
        service = new LibraryService(preferences, library, registry, Runnable::run);
        List<EditorExtension> extensions = new ArrayList<>(
                Application.editorExtensions(service, SwingLibraryDialogs.INSTANCE).stream()
                        .filter(extension -> !(extension instanceof MachineModeExtension))
                        .toList());
        extensions.add(new MachineModeExtension((snapshot, instancePath) -> Optional.empty()));
        EditorServices services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                preferences, library, extensions);
        document = MachineFixtures.circuitWithGeneratedMachines(1);
        place = document.openBody(MachineFixtures.DEFINITION_ID,
                Optional.of(MachineFixtures.copies(document).getFirst()));
        view = new EditorView(place, services, NO_WINDOW);
        view.setSize(1200, 800);
        view.doLayout();
        mode = MachineMode.of(view).orElseThrow();
        canvas = mode.canvas();
        canvas.setBounds(0, 0, 900, 600);
    }

    @AfterEach
    void close() {
        view.dispose();
        place.close();
        document.close();
        service.close();
    }

    private StateMachine machine() {
        return canvas.machine().orElseThrow();
    }

    private Point2D at(double x, double y) {
        return canvas.toScreen(new DiagramPoint(x, y));
    }

    private void mouse(int id, Point2D point, int modifiers, int clicks) {
        canvas.dispatchEvent(new MouseEvent(canvas, id, 0, modifiers, (int) Math.round(point.getX()),
                (int) Math.round(point.getY()), clicks, false, MouseEvent.BUTTON1));
    }

    private void click(Point2D point, int clicks) {
        mouse(MouseEvent.MOUSE_PRESSED, point, InputEvent.BUTTON1_DOWN_MASK, clicks);
        mouse(MouseEvent.MOUSE_RELEASED, point, 0, clicks);
        mouse(MouseEvent.MOUSE_CLICKED, point, 0, clicks);
    }

    private void doubleClick(Point2D point) {
        click(point, 1);
        click(point, 2);
    }

    private void drag(Point2D from, Point2D... through) {
        mouse(MouseEvent.MOUSE_MOVED, from, 0, 0);
        mouse(MouseEvent.MOUSE_PRESSED, from, InputEvent.BUTTON1_DOWN_MASK, 1);
        Point2D last = from;
        for (Point2D point : through) {
            mouse(MouseEvent.MOUSE_DRAGGED, point, InputEvent.BUTTON1_DOWN_MASK, 0);
            last = point;
        }
        mouse(MouseEvent.MOUSE_RELEASED, last, 0, 1);
    }

    private InlineTextEditor inline() {
        return mode.inlineEditor().editor();
    }

    /** A note {@code n1} of the default size with its top-left corner at (40, 150), below the states. */
    private MachineNote stickOne(String text) {
        MachineNote note = new MachineNote("n1", new DiagramPoint(40, 150), MachineNote.DEFAULT_WIDTH,
                MachineNote.DEFAULT_HEIGHT, StickyColor.YELLOW, text);
        canvas.edit(current -> current.withNotes(List.of(note)));
        return note;
    }

    private MachineNote onlyNote() {
        assertThat(machine().notes()).hasSize(1);
        return machine().notes().getFirst();
    }

    @Test
    void thePalettesNoteSticksAYellowNoteWhereClickedOpensItsTextAndEachIsOneUndoStep() {
        JToggleButton note = mode.palette().noteButton();
        assertThat(note.getText()).isEqualTo(Messages.text("machine.palette.note"));
        note.doClick();

        click(at(40, 150), 1);

        MachineNote stuck = onlyNote();
        assertThat(stuck.position().x()).isCloseTo(40, offset(1.0));
        assertThat(stuck.position().y()).isCloseTo(150, offset(1.0));
        assertThat(stuck.color()).isEqualTo(StickyColor.YELLOW);
        assertThat(stuck.width()).isEqualTo(MachineNote.DEFAULT_WIDTH);
        assertThat(stuck.text()).isEmpty();
        assertThat(mode.selection().noteIds()).containsExactly(stuck.id());
        assertThat(note.isSelected()).as("the tool is used once").isFalse();
        assertThat(inline().isOpen()).isTrue();

        inline().textComponent().setText("Espera dos 1\nseguidos");
        inline().confirm();
        assertThat(onlyNote().text()).isEqualTo("Espera dos 1\nseguidos");

        perform(view.actions().undo());
        assertThat(onlyNote().text()).isEmpty();
        perform(view.actions().undo());
        assertThat(machine().notes()).isEmpty();
        assertThat(machine().states()).hasSize(3);
    }

    @Test
    void theToolbarsStickyNoteIsEnabledInAMachineTabAndArmsTheDiagramsNote() {
        Action sticky = ShortcutsExtension.actionsOf(view).get("view.stickyNoteTool");
        assertThat(sticky.isEnabled()).isTrue();
        JToggleButton toolbarButton = view.toolButton(StickyNoteTool.class).orElseThrow();

        toolbarButton.doClick();

        assertThat(mode.palette().noteButton().isSelected()).isTrue();
        assertThat(view.panes()).allSatisfy(pane -> assertThat(pane.tool()).isNotInstanceOf(StickyNoteTool.class));
        click(at(40, 150), 1);
        assertThat(machine().notes()).hasSize(1);
        assertThat(toolbarButton.isSelected()).as("back to Selección once used").isFalse();
    }

    @Test
    void aDoubleClickOnANoteEditsItsTextAndOnEmptySpaceStillMakesAState() {
        stickOne("Hola");

        doubleClick(at(100, 200));

        assertThat(inline().isOpen()).isTrue();
        assertThat(inline().textComponent().getText()).isEqualTo("Hola");
        inline().textComponent().setText("Chao");
        inline().confirm();
        assertThat(onlyNote().text()).isEqualTo("Chao");
        assertThat(machine().states()).hasSize(3);

        doubleClick(at(500, 300));
        assertThat(machine().states()).hasSize(4);
        assertThat(machine().notes()).hasSize(1);
    }

    @Test
    void aDragMovesANoteInOneUndoStep() {
        stickOne("");

        drag(at(100, 200), at(120, 220), at(160, 260));

        assertThat(onlyNote().position().x()).isCloseTo(100, offset(1.0));
        assertThat(onlyNote().position().y()).isCloseTo(210, offset(1.0));
        assertThat(mode.selection().noteIds()).containsExactly("n1");
        perform(view.actions().undo());
        assertThat(onlyNote().position()).isEqualTo(new DiagramPoint(40, 150));
    }

    @Test
    void aDragOfASelectedNoteAndStateMovesThemTogether() {
        stickOne("");
        mode.selection().set(Set.of("s1"), Set.of(), Set.of("n1"));

        drag(at(100, 200), at(130, 230), at(150, 250));

        assertThat(onlyNote().position().x()).isCloseTo(90, offset(1.0));
        assertThat(machine().state("s1").orElseThrow().position().x()).isCloseTo(210, offset(1.0));
        perform(view.actions().undo());
        assertThat(onlyNote().position()).isEqualTo(new DiagramPoint(40, 150));
        assertThat(machine().state("s1").orElseThrow().position()).isEqualTo(new DiagramPoint(160, 0));
    }

    @Test
    void aDragFromTheCornerResizesTheNoteInWholeStepsInOneUndoStep() {
        stickOne("");
        Point2D corner = at(40 + MachineNote.DEFAULT_WIDTH - 3, 150 + MachineNote.DEFAULT_HEIGHT - 3);

        drag(corner, at(250, 330), at(258, 335));

        MachineNote resized = onlyNote();
        assertThat(resized.position()).isEqualTo(new DiagramPoint(40, 150));
        assertThat(resized.width()).isCloseTo(224, offset(0.01)); // 160 + 61, to whole 8s
        assertThat(resized.height()).isCloseTo(192, offset(0.01)); // 120 + 68, to whole 8s
        perform(view.actions().undo());
        assertThat(onlyNote().width()).isEqualTo(MachineNote.DEFAULT_WIDTH);
    }

    @Test
    void resizingNeverGoesBelowTheSmallestNote() {
        stickOne("");
        Point2D corner = at(40 + MachineNote.DEFAULT_WIDTH - 3, 150 + MachineNote.DEFAULT_HEIGHT - 3);

        drag(corner, at(60, 160), at(41, 151));

        assertThat(onlyNote().width()).isEqualTo(MachineNote.MINIMUM_WIDTH);
        assertThat(onlyNote().height()).isEqualTo(MachineNote.MINIMUM_HEIGHT);
    }

    @Test
    void borrarDeletesAMixedSelectionInOneUndoStep() {
        stickOne("Hola");
        mode.selection().set(Set.of("s2"), Set.of(), Set.of("n1"));

        perform(view.actions().delete());

        assertThat(machine().notes()).isEmpty();
        assertThat(machine().state("s2")).isEmpty();
        assertThat(mode.selection().isEmpty()).isTrue();
        perform(view.actions().undo());
        assertThat(machine().notes()).hasSize(1);
        assertThat(machine().state("s2")).isPresent();
    }

    @Test
    void aClickSelectsANoteAndTheRubberBandTakesTheNotesItEncloses() {
        stickOne("");

        click(at(100, 200), 1);
        assertThat(mode.selection().noteIds()).containsExactly("n1");
        assertThat(mode.selection().stateIds()).isEmpty();

        click(at(600, 400), 1);
        assertThat(mode.selection().isEmpty()).isTrue();

        drag(at(-80, -80), at(100, 100), at(500, 300));
        assertThat(mode.selection().noteIds()).containsExactly("n1");
        assertThat(mode.selection().stateIds()).containsExactlyInAnyOrder("s0", "s1", "s2");
    }

    @Test
    void thePropertiesOfANoteChangeItsColourInOneUndoStepAndOfferToEditItsText() {
        stickOne("Hola");
        mode.selection().selectNote("n1");
        MachineInspectorRows rows = mode.inspectorRows();

        assertThat(rows.noteColor().orElseThrow().values()).containsExactly(StickyColor.values());
        assertThat(rows.noteColor().orElseThrow().selectedValue()).isEqualTo(StickyColor.YELLOW);
        rows.noteColor().orElseThrow().swatch(StickyColor.PINK.ordinal()).doClick();

        assertThat(onlyNote().color()).isEqualTo(StickyColor.PINK);
        perform(view.actions().undo());
        assertThat(onlyNote().color()).isEqualTo(StickyColor.YELLOW);

        rows.editNoteTextButton().orElseThrow().doClick();
        assertThat(inline().isOpen()).isTrue();
        assertThat(inline().textComponent().getText()).isEqualTo("Hola");
    }

    @Test
    void theNotesAreSavedWithTheMachineAndReadBack() throws Exception {
        mode.palette().noteButton().doClick();
        click(at(40, 150), 1);
        inline().textComponent().setText("Recordatorio");
        inline().confirm();
        canvas.edit(current -> current.withNote(onlyNote().id(), found -> found.withColor(StickyColor.BLUE)));

        CircuitFormat format = new CircuitFormat(document.registry());
        CircuitDocument read = new CircuitReader(document.registry()).read(format.write(document.document()));

        StateMachine reread = read.definition(MachineFixtures.DEFINITION_ID).flatMap(ComponentDefinition::machine)
                .orElseThrow();
        assertThat(reread.notes()).containsExactlyElementsOf(machine().notes());
        assertThat(reread.notes().getFirst().text()).isEqualTo("Recordatorio");
    }

    @Test
    void theDiagramPaintsTheNoteAsAPieceOfPaperUnderTheStates() {
        stickOne("");
        mode.selection().clear();
        BufferedImage image = new BufferedImage(900, 600, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        canvas.paint(graphics);
        graphics.dispose();

        Point2D inside = at(100, 220);
        assertThat(image.getRGB((int) inside.getX(), (int) inside.getY()) & 0xFFFFFF)
                .isEqualTo(ColorScheme.LIGHT.stickyPaper(StickyColor.YELLOW).getRGB() & 0xFFFFFF);
    }

    @Test
    void verTodoTakesTheNotesIn() {
        MachineNote far = new MachineNote("n1", new DiagramPoint(2000, 2000), MachineNote.DEFAULT_WIDTH,
                MachineNote.DEFAULT_HEIGHT, StickyColor.YELLOW, "");
        canvas.edit(current -> current.withNotes(List.of(far)));

        canvas.zoomToFit();

        Point2D corner = canvas.toScreen(new DiagramPoint(2000 + MachineNote.DEFAULT_WIDTH,
                2000 + MachineNote.DEFAULT_HEIGHT));
        assertThat(corner.getX()).isBetween(0.0, 900.0);
        assertThat(corner.getY()).isBetween(0.0, 600.0);
    }

    @Test
    void selectAllTakesTheNotesToo() {
        stickOne("");

        perform(view.selectionActions().selectAll());

        assertThat(mode.selection().noteIds()).containsExactly("n1");
        mode.selection().set(new LinkedHashSet<>(), new LinkedHashSet<>(), Set.of("n1"));
        assertThat(view.selectionActions().copy().isEnabled()).as("a note alone can be copied").isTrue();
    }

    // hand test of hito 3, item 7: the circuit note's ✕ and «–», the same look and behaviour on the diagram.
    // n1 is at (40, 150), 160 x 120: its ✕ is at (187..197, 153..163), its «–» at (43..53, 153..163); folded, it is a
    // 16 x 16 paper at (40, 150).

    private static final DiagramPoint CROSS = new DiagramPoint(192, 158);
    private static final DiagramPoint FOLD_BUTTON = new DiagramPoint(48, 158);

    private Point2D at(DiagramPoint point) {
        return canvas.toScreen(point);
    }

    private BufferedImage painted() {
        BufferedImage image = new BufferedImage(900, 600, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        canvas.paint(graphics);
        graphics.dispose();
        return image;
    }

    private int rgbAt(BufferedImage image, Point2D point) {
        return image.getRGB((int) Math.round(point.getX()), (int) Math.round(point.getY())) & 0xFFFFFF;
    }

    /** Whether some pixel of the square of {@code half} diagram units around {@code centre} is not paper. */
    private boolean somethingIsDrawnOnThePaperAround(BufferedImage image, DiagramPoint centre, double half) {
        int paper = ColorScheme.LIGHT.stickyPaper(StickyColor.YELLOW).getRGB() & 0xFFFFFF;
        Point2D from = at(new DiagramPoint(centre.x() - half, centre.y() - half));
        Point2D to = at(new DiagramPoint(centre.x() + half, centre.y() + half));
        for (int x = (int) Math.ceil(from.getX()); x <= (int) Math.floor(to.getX()); x++) {
            for (int y = (int) Math.ceil(from.getY()); y <= (int) Math.floor(to.getY()); y++) {
                if ((image.getRGB(x, y) & 0xFFFFFF) != paper) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    void aNoteShowsTheCircuitNotesCrossAndFoldButton() {
        stickOne("");
        mode.selection().clear();

        BufferedImage image = painted();

        assertThat(somethingIsDrawnOnThePaperAround(image, CROSS, 3)).as("the ✕").isTrue();
        assertThat(somethingIsDrawnOnThePaperAround(image, FOLD_BUTTON, 3)).as("the «–»").isTrue();
        assertThat(somethingIsDrawnOnThePaperAround(image, new DiagramPoint(120, 158), 3)).as("between them")
                .isFalse();
    }

    @Test
    void theCrossAsksAsOnTheCircuitAndDeletesTheNoteInOneUndoStep() {
        List<String> asked = new ArrayList<>();
        mode.tool().useCloseConfirmation((parent, firstLine) -> {
            asked.add(firstLine);
            return true;
        });
        stickOne("Ojo con S2");

        mouse(MouseEvent.MOUSE_MOVED, at(CROSS), 0, 0);
        assertThat(canvas.getCursor().getType()).isEqualTo(java.awt.Cursor.HAND_CURSOR);
        click(at(CROSS), 1);

        assertThat(asked).containsExactly("Ojo con S2");
        assertThat(machine().notes()).isEmpty();
        assertThat(mode.selection().noteIds()).isEmpty();
        perform(view.actions().undo());
        assertThat(onlyNote().text()).isEqualTo("Ojo con S2");
    }

    @Test
    void cancellingTheCrossesQuestionKeepsTheNote() {
        mode.tool().useCloseConfirmation((parent, firstLine) -> false);
        stickOne("Hola");

        click(at(CROSS), 1);

        assertThat(onlyNote().text()).isEqualTo("Hola");
    }

    @Test
    void theFoldButtonFoldsTheNoteIntoASmallPaperInOneUndoStepAndAClickUnfoldsIt() {
        stickOne("Hola");

        click(at(FOLD_BUTTON), 1);

        assertThat(onlyNote().folded()).isTrue();
        assertThat(onlyNote().width()).isEqualTo(MachineNote.DEFAULT_WIDTH); // kept for when it unfolds
        assertThat(MachineNotePainting.paperOf(onlyNote()).getWidth()).isEqualTo(16.0);
        assertThat(MachineNotePainting.noteAt(machine(), new Point2D.Double(100, 220))).isEmpty();
        BufferedImage folded = painted();
        assertThat(rgbAt(folded, at(100, 220)))
                .isNotEqualTo(ColorScheme.LIGHT.stickyPaper(StickyColor.YELLOW).getRGB() & 0xFFFFFF);
        perform(view.actions().undo());
        assertThat(onlyNote().folded()).isFalse();
        perform(view.actions().redo());
        assertThat(onlyNote().folded()).isTrue();

        click(at(new DiagramPoint(46, 154)), 1); // anywhere on the small paper

        assertThat(onlyNote().folded()).isFalse();
        perform(view.actions().undo());
        assertThat(onlyNote().folded()).isTrue();
    }

    @Test
    void aDoubleClickOnAFoldedNoteOnlyUnfoldsItAndADragMovesItFolded() {
        stickOne("Hola");
        click(at(FOLD_BUTTON), 1);

        drag(at(new DiagramPoint(46, 154)), at(new DiagramPoint(86, 194)));

        assertThat(onlyNote().folded()).isTrue();
        assertThat(onlyNote().position().x()).isCloseTo(80, offset(1.0));
        assertThat(onlyNote().position().y()).isCloseTo(190, offset(1.0));

        doubleClick(at(new DiagramPoint(86, 194)));

        assertThat(onlyNote().folded()).isFalse();
        assertThat(inline().isOpen()).isFalse();
    }

    @Test
    void aFoldedNoteHasNoCrossNoFoldButtonAndNoResizeCorner() {
        mode.tool().useCloseConfirmation((parent, firstLine) -> true);
        stickOne("Hola");
        click(at(FOLD_BUTTON), 1);

        drag(at(new DiagramPoint(55, 165)), at(new DiagramPoint(155, 265))); // its corner, were it a full note

        assertThat(onlyNote().folded()).isTrue();
        assertThat(onlyNote().width()).isEqualTo(MachineNote.DEFAULT_WIDTH);
        click(at(CROSS), 1);
        assertThat(machine().notes()).hasSize(1);
    }

    @Test
    void aFoldedNoteIsSavedFolded() throws Exception {
        stickOne("Hola");
        click(at(FOLD_BUTTON), 1);

        String json = new CircuitFormat(document.registry()).write(document.document());

        assertThat(json).contains("\"folded\" : true");
    }

    @Test
    void hoveringAFoldedNoteShowsItsTextAsOnTheCircuit() {
        stickOne("Ojo con S2\nsi x = 1");
        Point2D onTheSmallPaper = at(new DiagramPoint(46, 154));
        MouseEvent hover = new MouseEvent(canvas, MouseEvent.MOUSE_MOVED, 0, 0,
                (int) Math.round(onTheSmallPaper.getX()), (int) Math.round(onTheSmallPaper.getY()), 0, false,
                MouseEvent.NOBUTTON);
        assertThat(canvas.getToolTipText(hover)).isNull(); // unfolded, its text is on the paper

        click(at(FOLD_BUTTON), 1);

        assertThat(canvas.getToolTipText(hover)).isEqualTo("<html>Ojo con S2<br>si x = 1</html>");
    }
}
