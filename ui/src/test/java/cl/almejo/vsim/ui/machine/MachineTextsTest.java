package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.NoteColor;
import cl.almejo.vsim.core.document.NoteStyle;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.io.CircuitFormat;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.io.CircuitReader;
import cl.almejo.vsim.core.io.FragmentFormat;
import cl.almejo.vsim.core.io.MachineFragmentFormat;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.machine.DiagramPoint;
import cl.almejo.vsim.core.machine.MachineText;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.Application;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.clipboard.FragmentClipboard;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.inline.InlineTextEditor;
import cl.almejo.vsim.ui.labels.TextTool;
import cl.almejo.vsim.ui.library.LibraryService;
import cl.almejo.vsim.ui.library.SwingLibraryDialogs;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.shortcuts.ShortcutsExtension;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JToggleButton;
import java.awt.Graphics2D;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
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
 * Free texts on a machine's diagram, as the circuit's «Texto»: the palette's «Texto» (or the toolbar's) and a click open
 * the editor, a confirmed text is added, selected, one undo step; it moves, is selected with the rest, deleted, copied
 * (also to and from a circuit), styled in Propiedades, and saved with the machine.
 */
class MachineTextsTest {

    private static final NoteStyle YELLOW_BOX = new NoteStyle(NoteColor.YELLOW, NoteColor.NONE, NoteColor.NONE);

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
        throughSource(canvas);
    }

    /** What a subclass does to the diagram's canvas before each test (runs the gestures through a machine source). */
    void throughSource(MachineCanvas shownCanvas) {
        // the canvas's own source
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

    private void shiftClick(Point2D point) {
        mouse(MouseEvent.MOUSE_PRESSED, point, InputEvent.BUTTON1_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK, 1);
        mouse(MouseEvent.MOUSE_RELEASED, point, InputEvent.SHIFT_DOWN_MASK, 1);
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

    /** A text {@code t1} with its top-left corner at (40, 150), below the states. */
    private MachineText writeOne(String text) {
        MachineText written = new MachineText("t1", new DiagramPoint(40, 150), text);
        canvas.edit(current -> current.withTexts(List.of(written)));
        return written;
    }

    private MachineText onlyText() {
        assertThat(machine().texts()).hasSize(1);
        return machine().texts().getFirst();
    }

    /** The middle of the box of {@code text} in screen pixels: a click there is on the text. */
    private Point2D onTheText(MachineText text) {
        Rectangle2D box = MachineTextPainting.box(text);
        return canvas.toScreen(new DiagramPoint(box.getCenterX(), box.getCenterY()));
    }

    @Test
    void thePalettesTextWritesAFreeTextWhereClickedAndEachStepIsOneUndoStep() {
        JToggleButton text = mode.palette().textButton();
        assertThat(text.getText()).isEqualTo(Messages.text("machine.palette.text"));
        text.doClick();

        click(at(40, 150), 1);

        assertThat(machine().texts()).as("nothing is added until it is confirmed").isEmpty();
        assertThat(inline().isOpen()).isTrue();
        assertThat(text.isSelected()).as("the tool is used once").isFalse();
        inline().textComponent().setText("Espera dos 1\nseguidos");
        inline().confirm();

        MachineText written = onlyText();
        assertThat(written.text()).isEqualTo("Espera dos 1\nseguidos");
        assertThat(written.position().x()).isCloseTo(40, offset(1.0));
        assertThat(written.position().y()).isCloseTo(150, offset(1.0));
        assertThat(written.style()).isEqualTo(NoteStyle.PLAIN);
        assertThat(mode.selection().textIds()).containsExactly(written.id());
        perform(view.actions().undo());
        assertThat(machine().texts()).isEmpty();
        assertThat(machine().states()).hasSize(3);
        perform(view.actions().redo());
        assertThat(onlyText().text()).isEqualTo("Espera dos 1\nseguidos");
    }

    @Test
    void aBlankTextAddsNothing() {
        StateMachine before = machine();
        mode.palette().textButton().doClick();
        click(at(40, 150), 1);

        inline().textComponent().setText("   ");
        inline().confirm();

        assertThat(machine().texts()).isEmpty();
        assertThat(machine()).isEqualTo(before);
    }

    @Test
    void theToolbarsTextToolIsEnabledInAMachineTabAndArmsTheDiagramsText() {
        assertThat(ShortcutsExtension.actionsOf(view).get("view.textTool").isEnabled()).isTrue();
        JToggleButton toolbarButton = view.toolButton(TextTool.class).orElseThrow();

        toolbarButton.doClick();

        assertThat(mode.palette().textButton().isSelected()).isTrue();
        assertThat(view.panes()).allSatisfy(pane -> assertThat(pane.tool()).isNotInstanceOf(TextTool.class));
        click(at(40, 150), 1);
        inline().textComponent().setText("hola");
        inline().confirm();
        assertThat(machine().texts()).hasSize(1);
        assertThat(toolbarButton.isSelected()).as("back to Selección once used").isFalse();
    }

    @Test
    void escDropsTheChosenTextTool() {
        mode.palette().textButton().doClick();

        canvas.getActionMap().get("machine-cancel").actionPerformed(null);

        assertThat(mode.palette().textButton().isSelected()).isFalse();
    }

    @Test
    void aDoubleClickOnATextEditsItAndF2DoesToo() {
        MachineText written = writeOne("Hola");

        doubleClick(onTheText(written));

        assertThat(inline().isOpen()).isTrue();
        assertThat(inline().textComponent().getText()).isEqualTo("Hola");
        inline().textComponent().setText("Chao\nmundo");
        inline().confirm();
        assertThat(onlyText().text()).isEqualTo("Chao\nmundo");
        assertThat(machine().states()).hasSize(3);
        perform(view.actions().undo());
        assertThat(onlyText().text()).isEqualTo("Hola");

        mode.selection().selectText("t1");
        canvas.getActionMap().get("machine-edit-text").actionPerformed(null);
        assertThat(inline().isOpen()).isTrue();
    }

    @Test
    void aBlankEditKeepsTheText() {
        MachineText written = writeOne("Hola");
        doubleClick(onTheText(written));

        inline().textComponent().setText("");
        inline().confirm();

        assertThat(onlyText().text()).isEqualTo("Hola");
    }

    @Test
    void aClickSelectsATextShiftClickAddsAndTheRubberBandTakesTheTextsItEncloses() {
        MachineText written = writeOne("Hola");

        click(onTheText(written), 1);
        assertThat(mode.selection().textIds()).containsExactly("t1");
        assertThat(mode.selection().stateIds()).isEmpty();

        shiftClick(at(160, 0));
        assertThat(mode.selection().textIds()).containsExactly("t1");
        assertThat(mode.selection().stateIds()).containsExactly("s1");

        click(at(600, 400), 1);
        assertThat(mode.selection().isEmpty()).isTrue();

        drag(at(-80, -80), at(100, 100), at(500, 300));
        assertThat(mode.selection().textIds()).containsExactly("t1");
        assertThat(mode.selection().stateIds()).containsExactlyInAnyOrder("s0", "s1", "s2");
    }

    @Test
    void aDragMovesATextAndASelectedStateTogetherInOneUndoStep() {
        MachineText written = writeOne("Hola");
        mode.selection().set(Set.of("s1"), Set.of(), Set.of(), Set.of("t1"));

        drag(onTheText(written), at(130, 230), at(150, 250));

        assertThat(onlyText().position().x()).isGreaterThan(written.position().x() + 30);
        assertThat(machine().state("s1").orElseThrow().position().x()).isGreaterThan(160 + 30);
        perform(view.actions().undo());
        assertThat(onlyText().position()).isEqualTo(new DiagramPoint(40, 150));
        assertThat(machine().state("s1").orElseThrow().position()).isEqualTo(new DiagramPoint(160, 0));
    }

    @Test
    void borrarDeletesATextWithTheRestOfTheSelectionInOneUndoStep() {
        writeOne("Hola");
        mode.selection().set(Set.of("s2"), Set.of(), Set.of(), Set.of("t1"));

        perform(view.actions().delete());

        assertThat(machine().texts()).isEmpty();
        assertThat(machine().state("s2")).isEmpty();
        assertThat(mode.selection().isEmpty()).isTrue();
        perform(view.actions().undo());
        assertThat(machine().texts()).hasSize(1);
        assertThat(machine().state("s2")).isPresent();
    }

    @Test
    void selectAllTakesTheTextsAndATextAloneCanBeCopied() {
        writeOne("Hola");

        perform(view.selectionActions().selectAll());

        assertThat(mode.selection().textIds()).containsExactly("t1");
        mode.selection().set(new LinkedHashSet<>(), new LinkedHashSet<>(), Set.of(), Set.of("t1"));
        assertThat(view.selectionActions().copy().isEnabled()).isTrue();
    }

    @Test
    void duplicateAndPasteAddTheTextSelectedAwayFromTheOriginal() {
        writeOne("Hola");
        mode.selection().selectText("t1");

        perform(view.selectionActions().duplicate());

        assertThat(machine().texts()).hasSize(2);
        MachineText copy = machine().texts().get(1);
        assertThat(copy.text()).isEqualTo("Hola");
        assertThat(copy.position()).isNotEqualTo(machine().texts().get(0).position());
        assertThat(mode.selection().textIds()).containsExactly(copy.id());
        perform(view.actions().undo());
        assertThat(machine().texts()).hasSize(1);
    }

    @Test
    void aCopiedTextTravelsWithItsStyleInTheMachinePieceFormat() {
        MachineText styled = new MachineText("t1", new DiagramPoint(40, 150), "Hola", YELLOW_BOX);
        canvas.edit(current -> current.withTexts(List.of(styled)));
        mode.selection().selectText("t1");

        perform(view.selectionActions().copy());
        FragmentClipboard clipboard = FragmentClipboard.system();
        String copied = clipboard.read().orElseThrow();

        assertThat(MachineFragmentFormat.read(copied).orElseThrow().texts()).extracting(MachineText::style)
                .containsExactly(YELLOW_BOX);
    }

    @Test
    void aCircuitsFreeTextPastesIntoTheDiagramAsATextWithItsStyle() {
        TextLabel label = new TextLabel(new ElementId("l1"), new GridPoint(3, 4), "del circuito", YELLOW_BOX);
        String json = new FragmentFormat(registry).write(new CircuitFragment(List.of(), List.of(), List.of(label)));
        FragmentClipboard.system().put(json, GridPoint.ORIGIN, null);

        perform(view.selectionActions().paste());

        MachineText pasted = onlyText();
        assertThat(pasted.text()).isEqualTo("del circuito");
        assertThat(pasted.style()).isEqualTo(YELLOW_BOX);
        assertThat(mode.selection().textIds()).containsExactly(pasted.id());
        perform(view.actions().undo());
        assertThat(machine().texts()).isEmpty();
    }

    @Test
    void thePropertiesOfATextChangeItsColoursAndItsWordsEachInOneUndoStep() {
        writeOne("Hola");
        mode.selection().selectText("t1");
        MachineInspectorRows rows = mode.inspectorRows();

        assertThat(rows.textArea().orElseThrow().getText()).isEqualTo("Hola");
        assertThat(rows.textStyle(NoteStyle.Role.BACKGROUND).orElseThrow().selectedValue()).isEqualTo(NoteColor.NONE);
        rows.textStyle(NoteStyle.Role.BACKGROUND).orElseThrow().swatch(NoteColor.YELLOW.ordinal()).doClick();

        assertThat(onlyText().style().background()).isEqualTo(NoteColor.YELLOW);
        perform(view.actions().undo());
        assertThat(onlyText().style()).isEqualTo(NoteStyle.PLAIN);

        mode.selection().selectText("t1");
        rows = mode.inspectorRows();
        rows.textArea().orElseThrow().setText("otro");
        rows.textArea().orElseThrow().confirm();
        assertThat(onlyText().text()).isEqualTo("otro");
        perform(view.actions().undo());
        assertThat(onlyText().text()).isEqualTo("Hola");
    }

    @Test
    void theTextsAreSavedWithTheMachineAndReadBack() throws Exception {
        MachineText styled = new MachineText("t1", new DiagramPoint(40, 150), "Recordatorio\ndos", YELLOW_BOX);
        canvas.edit(current -> current.withTexts(List.of(styled)));

        CircuitFormat format = new CircuitFormat(document.registry());
        CircuitDocument read = new CircuitReader(document.registry()).read(format.write(document.document()));

        StateMachine reread = read.definition(MachineFixtures.DEFINITION_ID).flatMap(ComponentDefinition::machine)
                .orElseThrow();
        assertThat(reread.texts()).containsExactly(styled);
    }

    @Test
    void theDiagramPaintsATextAndItsBoxAndSelectionAnts() {
        MachineText styled = new MachineText("t1", new DiagramPoint(40, 150), "Hola mundo", YELLOW_BOX);
        canvas.edit(current -> current.withTexts(List.of(styled)));
        BufferedImage plain = painted();

        Rectangle2D box = MachineTextPainting.box(styled);
        Point2D corner = canvas.toScreen(new DiagramPoint(box.getMinX() + 1, box.getMinY() + 1));
        assertThat(rgbAt(plain, corner)).as("the box's background")
                .isNotEqualTo(ColorScheme.LIGHT.background().getRGB() & 0xFFFFFF);

        mode.selection().selectText("t1");
        BufferedImage selected = painted();
        assertThat(different(plain, selected)).as("the selection's ants").isTrue();
    }

    @Test
    void verTodoTakesTheTextsIn() {
        MachineText far = new MachineText("t1", new DiagramPoint(2000, 2000), "lejos");
        canvas.edit(current -> current.withTexts(List.of(far)));

        canvas.zoomToFit();

        Rectangle2D box = MachineTextPainting.box(far);
        Point2D corner = canvas.toScreen(new DiagramPoint(box.getMaxX(), box.getMaxY()));
        assertThat(corner.getX()).isBetween(0.0, 900.0);
        assertThat(corner.getY()).isBetween(0.0, 600.0);
    }

    @Test
    void aTextIsNeverOnThePiecesFaceNorInProblems() {
        writeOne("Hola");

        assertThat(canvas.problems()).isEmpty();
    }

    private BufferedImage painted() {
        BufferedImage image = new BufferedImage(900, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        canvas.paint(graphics);
        graphics.dispose();
        return image;
    }

    private int rgbAt(BufferedImage image, Point2D point) {
        return image.getRGB((int) Math.round(point.getX()), (int) Math.round(point.getY())) & 0xFFFFFF;
    }

    private static boolean different(BufferedImage first, BufferedImage second) {
        for (int x = 0; x < first.getWidth(); x++) {
            for (int y = 0; y < first.getHeight(); y++) {
                if (first.getRGB(x, y) != second.getRGB(x, y)) {
                    return true;
                }
            }
        }
        return false;
    }
}
