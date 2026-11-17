package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.machine.DiagramPoint;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.Application;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.library.LibraryService;
import cl.almejo.vsim.ui.library.SwingLibraryDialogs;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.sticky.StickyNoteTool;
import cl.almejo.vsim.ui.tools.PanTool;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.perform;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

/**
 * The toolbar's «Mover vista» (the hand) in a machine's tab (hand test of hito 3, item 5): it is enabled there, a drag
 * with it pans the diagram exactly as on a circuit and never moves or selects what it starts on, its button stays
 * pressed while it is chosen, and Esc or a palette tool goes back to Selección.
 */
class MachinePanToolTest {

    @TempDir
    Path directory;

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final AppPreferences preferences = AppPreferences.inMemory();
    private final List<EditorView> views = new ArrayList<>();
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
        document = MachineFixtures.circuitWithGeneratedMachines(2);
        place = document.openBody(MachineFixtures.DEFINITION_ID,
                Optional.of(MachineFixtures.copies(document).get(1)));
        view = new EditorView(place, services, NO_WINDOW);
        view.setSize(1200, 800);
        view.doLayout();
        views.add(view);
        mode = MachineMode.of(view).orElseThrow();
        canvas = mode.canvas();
        canvas.setBounds(0, 0, 900, 600);
    }

    @AfterEach
    void close() {
        views.forEach(EditorView::dispose);
        place.close();
        document.close();
        service.close();
    }

    private StateMachine machine() {
        return canvas.machine().orElseThrow();
    }

    private void mouse(int id, Point2D point, int modifiers) {
        canvas.dispatchEvent(new MouseEvent(canvas, id, 0, modifiers, (int) Math.round(point.getX()),
                (int) Math.round(point.getY()), 1, false, MouseEvent.BUTTON1));
    }

    private void drag(Point2D from, Point2D to) {
        mouse(MouseEvent.MOUSE_MOVED, from, 0);
        mouse(MouseEvent.MOUSE_PRESSED, from, InputEvent.BUTTON1_DOWN_MASK);
        mouse(MouseEvent.MOUSE_DRAGGED, to, InputEvent.BUTTON1_DOWN_MASK);
        mouse(MouseEvent.MOUSE_RELEASED, to, 0);
    }

    private AbstractButton toolbarButtonOf(Action action) {
        for (java.awt.Component component : view.toolBar().getComponents()) {
            if (component instanceof AbstractButton button && button.getAction() == action) {
                return button;
            }
        }
        throw new AssertionError("no toolbar button for " + action.getValue(Action.NAME));
    }

    private void pressEscape() {
        Object key = canvas.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
        canvas.getActionMap().get(key).actionPerformed(new ActionEvent(canvas, ActionEvent.ACTION_PERFORMED, ""));
    }

    @Test
    void theHandIsEnabledInAMachineTab() {
        assertThat(view.actions().panTool().isEnabled()).isTrue();
        assertThat(toolbarButtonOf(view.actions().panTool()).isEnabled()).isTrue();
    }

    @Test
    void aDragWithTheHandPansTheDiagramAndLeavesTheStateWhereItWas() {
        DiagramPoint stateCentre = machine().states().get(0).position();
        Point2D from = canvas.toScreen(stateCentre);
        Point2D to = new Point2D.Double(from.getX() + 60, from.getY() + 30);

        perform(view.actions().panTool());
        drag(from, to);

        assertThat(machine().states().get(0).position()).isEqualTo(stateCentre);
        assertThat(mode.selection().stateIds()).isEmpty();
        Point2D now = canvas.toScreen(stateCentre);
        assertThat(now.getX()).isCloseTo(to.getX(), offset(0.5));
        assertThat(now.getY()).isCloseTo(to.getY(), offset(0.5));
        assertThat(canvas.getCursor().getType()).isEqualTo(Cursor.MOVE_CURSOR);
    }

    @Test
    void theHandsButtonStaysPressedWhileItIsChosenAndEscGoesBackToSelection() {
        perform(view.actions().panTool());
        assertThat(toolbarButtonOf(view.actions().panTool()).isSelected()).isTrue();

        pressEscape();

        assertThat(view.canvas().tool()).isNotInstanceOf(PanTool.class);
        assertThat(toolbarButtonOf(view.actions().selectTool()).isSelected()).isTrue();
        DiagramPoint stateCentre = machine().states().get(0).position();
        drag(canvas.toScreen(stateCentre), new Point2D.Double(canvas.toScreen(stateCentre).getX() + 60,
                canvas.toScreen(stateCentre).getY()));
        assertThat(machine().states().get(0).position()).isNotEqualTo(stateCentre); // drawing again
    }

    @Test
    void choosingAPaletteToolPutsTheHandDownAndTheHandPutsThePaletteToolDown() {
        perform(view.actions().panTool());
        mode.palette().stateButton().doClick();

        assertThat(view.canvas().tool()).isNotInstanceOf(PanTool.class);
        assertThat(mode.palette().stateButton().isSelected()).isTrue();

        perform(view.actions().panTool());

        assertThat(mode.palette().stateButton().isSelected()).isFalse();
        assertThat(view.canvas().tool()).isInstanceOf(PanTool.class);
    }

    @Test
    void choosingThePalettesNoteWhileTheHandIsChosenPressesTheToolbarsNote() {
        perform(view.actions().panTool());
        mode.palette().noteButton().doClick();

        assertThat(canvas.isPanning()).isFalse();
        assertThat(view.toolButton(StickyNoteTool.class).orElseThrow().isSelected()).isTrue();
    }
}
