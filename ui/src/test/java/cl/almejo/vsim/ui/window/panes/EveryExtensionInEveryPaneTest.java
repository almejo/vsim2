package cl.almejo.vsim.ui.window.panes;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Guide;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.AppPreferences.GridStyle;
import cl.almejo.vsim.ui.app.Application;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.frames.FrameGestures;
import cl.almejo.vsim.ui.frames.FrameTool;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.library.LibraryService;
import cl.almejo.vsim.ui.library.SwingLibraryDialogs;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.shortcuts.KeyBindings;
import cl.almejo.vsim.ui.snap.DefaultSnapper;
import cl.almejo.vsim.ui.tools.FloatingSelectionTool;
import cl.almejo.vsim.ui.tools.PanTool;
import cl.almejo.vsim.ui.tools.SelectTool;
import cl.almejo.vsim.ui.tools.WireTool;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.WindowCommands;
import cl.almejo.vsim.ui.window.panes.CanvasPanes.Orientation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Split views (Review Focus 1–3): every production extension ({@code Application.editorExtensions}) and the editor's
 * own per-pane features are installed on every pane of a window split into four, on the panes created later, and gone
 * from a pane when it is removed and from all of them when the view is disposed; the window's actions act on the
 * active pane and on the one shared selection. Part A (canvas, window, actions, shortcuts, labels, frames, tunnels,
 * guides, snapping, grid); the other extensions add their cases in their own class.
 */
class EveryExtensionInEveryPaneTest {

    private static final WindowCommands NO_WINDOW = new WindowCommands() {
        @Override public void newDocument() { }
        @Override public void open() { }
        @Override public void save() { }
        @Override public void saveAs() { }
        @Override public void closeWindow() { }
        @Override public void exit() { }
    };
    private static final int CHOSEN_SPACING = 5;

    @TempDir
    Path directory;

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final AppPreferences preferences = AppPreferences.inMemory();
    private final ComponentLibrary library = new ComponentLibrary();
    private LibraryService service;
    private OpenDocument openDocument;
    private EditorView view;
    private boolean disposed;

    /** One thing installed per pane, named for the test report. */
    record PaneCase(String name, BiPredicate<EditorView, CircuitCanvas> installed) {
        @Override
        public String toString() {
            return name;
        }
    }

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        preferences.setLibraryDirectory(Optional.of(directory.resolve("library")));
        preferences.setShowGuides(true);
        preferences.setGridStyle(GridStyle.LINES);
        preferences.setGridLineSpacing(CHOSEN_SPACING);
        service = new LibraryService(preferences, library, registry, Runnable::run);
        EditorServices services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                preferences, library, Application.editorExtensions(service, SwingLibraryDialogs.INSTANCE));
        openDocument = OpenDocument.createEmpty(registry);
        view = new EditorView(openDocument, services, NO_WINDOW);
        view.setSize(1200, 800);
        view.doLayout();
        view.canvasPanes().setSize(800, 600);
        view.canvasPanes().doLayout();
    }

    @AfterEach
    void close() {
        if (!disposed) {
            view.dispose();
        }
        openDocument.close();
        service.close();
    }

    static Stream<PaneCase> cases() {
        return Stream.of(
                new PaneCase("editor: Delete, Backspace, R and Shift+R", (view, pane) ->
                        actionOf(pane, stroke(KeyEvent.VK_DELETE, 0)) == view.actions().delete()
                                && actionOf(pane, stroke(KeyEvent.VK_BACK_SPACE, 0)) == view.actions().delete()
                                && actionOf(pane, stroke(KeyEvent.VK_R, 0)) == view.actions().rotateClockwise()
                                && actionOf(pane, stroke(KeyEvent.VK_R, InputEvent.SHIFT_DOWN_MASK))
                                == view.actions().rotateCounterClockwise()),
                new PaneCase("labels: F2", (view, pane) ->
                        actionOf(pane, stroke(KeyEvent.VK_F2, 0)) == view.labelFeatures().actions().editLabel()),
                new PaneCase("shortcuts: the catalogue's canvas keys", (view, pane) ->
                        actionOf(pane, stroke(KeyEvent.VK_LEFT, 0)) == view.actions().moveActions().get("edit.moveLeft")
                                && actionOf(pane, stroke(KeyEvent.VK_DOWN, InputEvent.SHIFT_DOWN_MASK))
                                == view.actions().moveActions().get("edit.moveDownFar")),
                new PaneCase("frames: the padlock overlay", (view, pane) ->
                        hasOverlayFrom(pane, "cl.almejo.vsim.ui.frames.")),
                new PaneCase("frames: the select tool's frame gestures", (view, pane) ->
                        pane.defaultTool() instanceof SelectTool select
                                && select.delegates().stream().anyMatch(FrameGestures.class::isInstance)),
                new PaneCase("tunnels: the hover outline and its pointer", (view, pane) ->
                        hasOverlayFrom(pane, "cl.almejo.vsim.ui.tunnel.")
                                && Arrays.stream(pane.getMouseMotionListeners())
                                .map(MouseMotionListener::getClass).map(Class::getName)
                                .anyMatch(name -> name.startsWith("cl.almejo.vsim.ui.tunnel."))),
                new PaneCase("guides: the rulers, the corner, the grab and the lines", (view, pane) ->
                        pane.edgeHandles().stream()
                                .filter(handle -> handle.getClass().getName().startsWith("cl.almejo.vsim.ui.guides."))
                                .count() == 4
                                && hasOverlayFrom(pane, "cl.almejo.vsim.ui.guides.")),
                new PaneCase("snap: the magnet", (view, pane) -> pane.snapper() instanceof DefaultSnapper),
                new PaneCase("grid: the chosen style and spacing", (view, pane) ->
                        pane.gridStyle() == GridStyle.LINES && pane.gridLineSpacing() == CHOSEN_SPACING));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void isOnEveryPaneAndGoesWithItsPaneAndWithTheView(PaneCase paneCase) {
        splitIntoFour();
        assertThat(view.panes()).hasSize(4)
                .allSatisfy(pane -> assertThat(paneCase.installed().test(view, pane)).as("on a pane").isTrue());

        List<CircuitCanvas> before = view.panes();
        view.canvasPanes().removeDivider(Orientation.VERTICAL);
        List<CircuitCanvas> removed = new ArrayList<>(before);
        removed.removeAll(view.panes());
        assertThat(removed).hasSize(2)
                .allSatisfy(pane -> assertThat(paneCase.installed().test(view, pane)).as("on a removed pane")
                        .isFalse());

        view.canvasPanes().split(Orientation.VERTICAL, 0.5);
        assertThat(view.panes()).hasSize(4)
                .allSatisfy(pane -> assertThat(paneCase.installed().test(view, pane)).as("on a new pane").isTrue());

        List<CircuitCanvas> last = view.panes();
        disposeTheView();
        assertThat(last).allSatisfy(pane -> assertThat(paneCase.installed().test(view, pane))
                .as("after the view was disposed").isFalse());
    }

    @Test
    void deleteAndRotateFromAnotherPaneActOnTheSharedSelection() {
        Component gate = place(openDocument, StandardComponentTypes.NOT, 5, 5, Map.of());
        splitIntoFour();
        CircuitCanvas first = view.panes().get(0);
        CircuitCanvas other = view.panes().get(3);
        press(other); // a click on an empty spot: the pane becomes active (and the selection is cleared)
        first.selection().select(gate.id());

        perform(actionOf(other, stroke(KeyEvent.VK_R, 0)));
        assertThat(openDocument.document().component(gate.id()).orElseThrow().rotation())
                .isNotEqualTo(gate.rotation());
        perform(actionOf(other, stroke(KeyEvent.VK_DELETE, 0)));
        assertThat(openDocument.document().component(gate.id())).isEmpty();
    }

    @Test
    void aCanvasShortcutChangedInThePreferencesMovesOnEveryPane() {
        splitIntoFour();
        KeyStroke chosen = stroke(KeyEvent.VK_X, 0);

        new KeyBindings(preferences).assign("edit.rotateClockwise", chosen);

        assertThat(view.panes()).allSatisfy(pane -> {
            assertThat(actionOf(pane, chosen)).isSameAs(view.actions().rotateClockwise());
            assertThat(actionOf(pane, stroke(KeyEvent.VK_R, 0))).isNull();
        });
    }

    @Test
    void theZoomAndCentreActionsActOnTheActivePaneOnly() {
        view.canvasPanes().split(Orientation.VERTICAL, 0.5);
        CircuitCanvas left = view.panes().get(0);
        CircuitCanvas right = view.panes().get(1);
        double zoom = left.viewport().zoom();
        press(right);

        perform(view.actions().zoomIn());
        assertThat(right.viewport().zoom()).isGreaterThan(zoom);
        assertThat(left.viewport().zoom()).isEqualTo(zoom);

        press(left);
        perform(view.actions().zoomOut());
        assertThat(left.viewport().zoom()).isLessThan(zoom);
        assertThat(right.viewport().zoom()).isGreaterThan(zoom);
    }

    @Test
    void theToolActionsReachEveryPaneEachWithItsOwnTool() {
        splitIntoFour();
        perform(view.actions().wireTool());
        assertThat(view.panes()).allSatisfy(pane -> assertThat(pane.tool()).isInstanceOf(WireTool.class));
        assertThat(view.panes().stream().map(CircuitCanvas::tool).distinct()).hasSize(4);

        perform(view.actions().panTool());
        assertThat(view.panes()).allSatisfy(pane -> assertThat(pane.tool()).isInstanceOf(PanTool.class));

        perform(view.toolButton(FrameTool.class).orElseThrow().getAction());
        assertThat(view.panes()).allSatisfy(pane -> assertThat(pane.tool()).isInstanceOf(FrameTool.class));

        perform(view.actions().selectTool());
        assertThat(view.panes()).allSatisfy(pane -> assertThat(pane.tool()).isSameAs(pane.defaultTool()));
    }

    @Test
    void aDuplicateFloatsInTheActivePaneOnly() {
        Component gate = place(openDocument, StandardComponentTypes.NOT, 5, 5, Map.of());
        view.canvasPanes().split(Orientation.HORIZONTAL, 0.5);
        CircuitCanvas top = view.panes().get(0);
        CircuitCanvas bottom = view.panes().get(1);
        press(bottom);
        view.selection().select(gate.id());

        perform(view.selectionActions().duplicate());

        assertThat(bottom.tool()).isInstanceOf(FloatingSelectionTool.class);
        assertThat(top.tool()).isSameAs(top.defaultTool());
    }

    @Test
    void anInlineEditOpensInTheActivePaneAndClosesWhenThatPaneGoes() {
        Component gate = place(openDocument, StandardComponentTypes.NOT, 5, 5, Map.of());
        view.canvasPanes().split(Orientation.VERTICAL, 0.5);
        CircuitCanvas right = view.panes().get(1);
        press(right);

        view.labelFeatures().actions().editLabel(gate);
        assertThat(view.inlineEditor().openIn()).contains(right);
        assertThat(view.inlineEditor().field().getParent().getParent()).isSameAs(right);

        view.canvasPanes().removeDivider(Orientation.VERTICAL);
        assertThat(view.inlineEditor().isOpen()).isFalse();
        assertThat(Arrays.asList(right.getComponents())).isEmpty();
    }

    @Test
    void aNewGuideFromTheMenuGoesInTheMiddleOfTheActivePane() {
        view.canvasPanes().split(Orientation.HORIZONTAL, 0.5);
        CircuitCanvas top = view.panes().get(0);
        CircuitCanvas bottom = view.panes().get(1);
        bottom.centerOn(new Bounds(200, 300, 2, 2));
        press(bottom);
        // the pointer leaves the pane, so «Agregar guía» uses the middle of the active pane
        for (var listener : bottom.getMouseListeners()) {
            listener.mouseExited(new MouseEvent(bottom, MouseEvent.MOUSE_EXITED, 0, 0, 1000, 1000, 0, false));
        }
        GridPoint middleOfBottom = bottom.viewport().snap(bottom.getWidth() / 2.0, bottom.getHeight() / 2.0);
        GridPoint middleOfTop = top.viewport().snap(top.getWidth() / 2.0, top.getHeight() / 2.0);
        assertThat(middleOfBottom.y()).isNotEqualTo(middleOfTop.y());

        perform(view.shortcutActions().get("view.addHorizontalGuide"));

        assertThat(openDocument.document().guides())
                .containsExactly(new Guide(Guide.Orientation.HORIZONTAL, middleOfBottom.y()));
    }

    private void splitIntoFour() {
        view.canvasPanes().split(Orientation.HORIZONTAL, 0.5);
        view.canvasPanes().split(Orientation.VERTICAL, 0.5);
    }

    private void disposeTheView() {
        disposed = true;
        view.dispose();
    }

    private static KeyStroke stroke(int keyCode, int modifiers) {
        return KeyStroke.getKeyStroke(keyCode, modifiers);
    }

    /** The action {@code stroke} runs on {@code pane} while it has the focus; null when none. */
    private static Action actionOf(CircuitCanvas pane, KeyStroke stroke) {
        Object name = pane.getInputMap(JComponent.WHEN_FOCUSED).get(stroke);
        return name == null ? null : pane.getActionMap().get(name);
    }

    private static boolean hasOverlayFrom(CircuitCanvas pane, String packagePrefix) {
        return pane.overlays().stream().anyMatch(overlay -> overlay.getClass().getName().startsWith(packagePrefix));
    }

    /** A click on an empty spot of {@code pane}: it becomes the active pane. */
    private static void press(CircuitCanvas pane) {
        MouseEvent pressed = new MouseEvent(pane, MouseEvent.MOUSE_PRESSED, 0, InputEvent.BUTTON1_DOWN_MASK, 30, 30,
                1, false, MouseEvent.BUTTON1);
        MouseEvent released = new MouseEvent(pane, MouseEvent.MOUSE_RELEASED, 0, 0, 30, 30, 1, false,
                MouseEvent.BUTTON1);
        for (var listener : pane.getMouseListeners()) {
            listener.mousePressed(pressed);
        }
        for (var listener : pane.getMouseListeners()) {
            listener.mouseReleased(released);
        }
    }

    private static void perform(Action action) {
        assertThat(action).isNotNull();
        action.actionPerformed(new ActionEvent(EveryExtensionInEveryPaneTest.class, ActionEvent.ACTION_PERFORMED, ""));
    }
}
