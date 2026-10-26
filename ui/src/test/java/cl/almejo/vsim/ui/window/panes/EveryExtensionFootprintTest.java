package cl.almejo.vsim.ui.window.panes;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.Application;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.library.LibraryService;
import cl.almejo.vsim.ui.library.SwingLibraryDialogs;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.panes.CanvasPanes.Orientation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JComponent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Split views, Review Focus 1 over the whole production list: installed one after the other in
 * {@code Application.editorExtensions} order on a window split into four, every extension adds exactly the same to
 * every pane (overlays, snapshot listeners, edge handles, press interceptors, tooltips, incoming-fragment listeners,
 * key bindings and actions, mouse and key listeners); a pane made afterwards gets all of it, a removed pane keeps none
 * of it, and disposing the extensions (then the view) leaves every pane as bare as it started.
 */
class EveryExtensionFootprintTest {

    @TempDir
    Path directory;

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final AppPreferences preferences = AppPreferences.inMemory();
    private final ComponentLibrary library = new ComponentLibrary();
    private LibraryService service;
    private OpenDocument openDocument;
    private EditorView view;
    private boolean viewDisposed;

    /** What a pane holds that an extension may add to. */
    private record Footprint(int overlays, int snapshotListeners, int edgeHandles, int pressInterceptors,
                             int toolTipOverrides, int incomingFragmentListeners, int placementListeners,
                             int selectToolDelegates, int toolTipProviders,
                             int focusedKeys, int actions, int mouseListeners, int mouseMotionListeners,
                             int mouseWheelListeners, int keyListeners) {
        static Footprint of(CircuitCanvas pane) {
            CircuitCanvas.PaneHooks hooks = pane.paneHooks();
            return new Footprint(pane.overlayCount(), pane.snapshotListenerCount(), pane.edgeHandles().size(),
                    hooks.pressInterceptors(), hooks.toolTipOverrides(), hooks.incomingFragmentListeners(), hooks.placementListeners(), hooks.selectToolDelegates(),
                    hooks.toolTipProvider() ? 1 : 0, sizeOf(pane.getInputMap(JComponent.WHEN_FOCUSED).keys()),
                    sizeOf(pane.getActionMap().keys()), pane.getMouseListeners().length,
                    pane.getMouseMotionListeners().length, pane.getMouseWheelListeners().length,
                    pane.getKeyListeners().length);
        }

        Footprint minus(Footprint before) {
            return new Footprint(overlays - before.overlays, snapshotListeners - before.snapshotListeners,
                    edgeHandles - before.edgeHandles, pressInterceptors - before.pressInterceptors,
                    toolTipOverrides - before.toolTipOverrides,
                    incomingFragmentListeners - before.incomingFragmentListeners,
                    placementListeners - before.placementListeners,
                    selectToolDelegates - before.selectToolDelegates,
                    toolTipProviders - before.toolTipProviders, focusedKeys - before.focusedKeys,
                    actions - before.actions, mouseListeners - before.mouseListeners,
                    mouseMotionListeners - before.mouseMotionListeners,
                    mouseWheelListeners - before.mouseWheelListeners, keyListeners - before.keyListeners);
        }

        private static int sizeOf(Object[] keys) {
            return keys == null ? 0 : keys.length;
        }
    }

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        preferences.setLibraryDirectory(Optional.of(directory.resolve("library")));
        preferences.setShowGuides(true);
        service = new LibraryService(preferences, library, registry, Runnable::run);
        EditorServices services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                preferences, library, List.of());
        openDocument = OpenDocument.createEmpty(registry);
        view = new EditorView(openDocument, services, NO_WINDOW);
        view.setSize(1200, 800);
        view.doLayout();
        view.canvasPanes().setSize(800, 600);
        view.canvasPanes().doLayout();
    }

    @AfterEach
    void close() {
        if (!viewDisposed) {
            view.dispose();
        }
        openDocument.close();
        service.close();
    }

    @Test
    void everyExtensionLeavesTheSameFootprintOnEveryPaneAndTakesItAllBack() {
        // the bare window: its first pane, a pane it makes later, and such a pane once removed
        CircuitCanvas first = view.canvas();
        Footprint firstBare = Footprint.of(first);
        view.canvasPanes().split(Orientation.HORIZONTAL, 0.5);
        CircuitCanvas probe = view.panes().get(1);
        Footprint laterBare = Footprint.of(probe);
        view.canvasPanes().removeDivider(Orientation.HORIZONTAL);
        Footprint removedBare = Footprint.of(probe);

        view.canvasPanes().split(Orientation.HORIZONTAL, 0.5);
        view.canvasPanes().split(Orientation.VERTICAL, 0.5);
        assertThat(view.panes()).hasSize(4);
        List<EditorExtension> extensions = Application.editorExtensions(service, SwingLibraryDialogs.INSTANCE);
        List<Runnable> disposers = new ArrayList<>();
        for (EditorExtension extension : extensions) {
            String name = extension.getClass().getSimpleName();
            Map<CircuitCanvas, Footprint> before = footprints();
            disposers.add(extension.install(view));
            assertThat(view.panes()).as("%s keeps the four panes", name).hasSize(4);
            Footprint added = Footprint.of(first).minus(before.get(first));
            for (CircuitCanvas pane : view.panes()) {
                assertThat(Footprint.of(pane).minus(before.get(pane))).as("what %s adds to a pane", name)
                        .isEqualTo(added);
            }
        }
        Footprint everything = Footprint.of(first).minus(firstBare);
        view.panes().stream().filter(pane -> pane != first).forEach(pane ->
                assertThat(Footprint.of(pane).minus(laterBare)).as("all the extensions on a pane")
                        .isEqualTo(everything));

        List<CircuitCanvas> closing = List.of(view.panes().get(1), view.panes().get(3));
        view.canvasPanes().removeDivider(Orientation.VERTICAL);
        assertThat(view.panes()).hasSize(2).doesNotContainAnyElementsOf(closing);
        closing.forEach(pane -> assertThat(Footprint.of(pane)).as("a removed pane").isEqualTo(removedBare));

        view.canvasPanes().split(Orientation.VERTICAL, 0.5);
        view.panes().stream().filter(pane -> pane != first).forEach(pane ->
                assertThat(Footprint.of(pane).minus(laterBare)).as("a pane made after the extensions")
                        .isEqualTo(everything));

        for (int index = disposers.size() - 1; index >= 0; index--) {
            disposers.get(index).run();
        }
        view.panes().forEach(pane -> assertThat(Footprint.of(pane)).as("after the extensions were disposed")
                .isEqualTo(pane == first ? firstBare : laterBare));

        List<CircuitCanvas> last = view.panes();
        viewDisposed = true;
        view.dispose();
        last.stream().filter(pane -> pane != first).forEach(pane ->
                assertThat(Footprint.of(pane)).as("after the view was disposed").isEqualTo(removedBare));
    }

    private Map<CircuitCanvas, Footprint> footprints() {
        Map<CircuitCanvas, Footprint> footprints = new IdentityHashMap<>();
        view.panes().forEach(pane -> footprints.put(pane, Footprint.of(pane)));
        return footprints;
    }
}
