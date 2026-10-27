package cl.almejo.vsim.ui.window.panes;

import cl.almejo.vsim.core.command.AddWireCommand;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.simulation.ShortCircuit;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.ViewState;
import cl.almejo.vsim.ui.export.ExportChoice;
import cl.almejo.vsim.ui.export.ExportImageExtension;
import cl.almejo.vsim.ui.export.ExportImageFeatures;
import cl.almejo.vsim.ui.karnaugh.KarnaughExtension;
import cl.almejo.vsim.ui.library.LibraryService;
import cl.almejo.vsim.ui.library.SwingLibraryDialogs;
import cl.almejo.vsim.ui.library.TemplatesExtension;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.print.ExportPdfExtension;
import cl.almejo.vsim.ui.search.FindExtension;
import cl.almejo.vsim.ui.search.SearchField;
import cl.almejo.vsim.ui.stepping.StepModeExtension;
import cl.almejo.vsim.ui.tree.ComponentTreeExtension;
import cl.almejo.vsim.ui.truthtable.TruthTableExtension;
import cl.almejo.vsim.ui.waveform.WaveformsExtension;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.panes.CanvasPanes.Orientation;
import cl.almejo.vsim.ui.wiring.TidyWiresExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.imageio.ImageIO;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.awt.Container;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.perform;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Split views, audit part B (find, problems, stepping, waveforms, truth table, Karnaugh, library and templates,
 * component tree, tidy wires, exports): what these features paint or listen to is in every pane, including the panes
 * made after them, a removed pane keeps none of it and disposing takes it back from all; travel and exports act on the
 * active pane, with the window's one selection.
 */
class EveryViewFeatureInEveryPaneTest {

    private static final KeyStroke F8 = KeyStroke.getKeyStroke(KeyEvent.VK_F8, 0);

    @TempDir
    Path directory;

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final AppPreferences preferences = AppPreferences.inMemory();
    private final ComponentLibrary library = new ComponentLibrary();
    private LibraryService service;
    private OpenDocument openDocument;
    private EditorView view;

    /** What a pane holds for the features: what an extension adds there must be in every pane alike. */
    private record Footprint(int overlays, int snapshotListeners, int edgeHandles, int pressInterceptors,
                             int toolTipOverrides, int incomingFragmentListeners, int placementListeners,
                             int selectToolDelegates, int toolTipProviders,
                             int focusedKeys, int actions) {
        static Footprint of(CircuitCanvas pane) {
            CircuitCanvas.PaneHooks hooks = pane.paneHooks();
            return new Footprint(pane.overlayCount(), pane.snapshotListenerCount(), pane.edgeHandles().size(),
                    hooks.pressInterceptors(), hooks.toolTipOverrides(), hooks.incomingFragmentListeners(), hooks.placementListeners(), hooks.selectToolDelegates(),
                    hooks.toolTipProvider() ? 1 : 0, sizeOf(pane.getInputMap(JComponent.WHEN_FOCUSED).keys()),
                    sizeOf(pane.getActionMap().keys()));
        }

        /** What this pane holds beyond {@code before}. */
        Footprint minus(Footprint before) {
            return new Footprint(overlays - before.overlays, snapshotListeners - before.snapshotListeners,
                    edgeHandles - before.edgeHandles, pressInterceptors - before.pressInterceptors,
                    toolTipOverrides - before.toolTipOverrides,
                    incomingFragmentListeners - before.incomingFragmentListeners,
                    placementListeners - before.placementListeners,
                    selectToolDelegates - before.selectToolDelegates,
                    toolTipProviders - before.toolTipProviders, focusedKeys - before.focusedKeys,
                    actions - before.actions);
        }

        boolean isEmpty() {
            return equals(new Footprint(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        }

        private static int sizeOf(Object[] keys) {
            return keys == null ? 0 : keys.length;
        }
    }

    @BeforeEach
    void create() {
        preferences.setLibraryDirectory(Optional.of(directory.resolve("library")));
        preferences.setAnimateViewMoves(false);
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
        view.dispose();
        openDocument.close();
        service.close();
    }

    /** Each extension of part B and whether it paints or listens in the panes (else it must simply leave them alone). */
    static Stream<Arguments> extensions() {
        return Stream.of(
                Arguments.of("Buscar", (Function<LibraryService, EditorExtension>) libraryService ->
                        new FindExtension(), true),
                Arguments.of("Formas de onda", (Function<LibraryService, EditorExtension>) libraryService ->
                        new WaveformsExtension(), true),
                Arguments.of("Modo paso a paso", (Function<LibraryService, EditorExtension>) libraryService ->
                        new StepModeExtension(), true),
                Arguments.of("Componentes (árbol)", (Function<LibraryService, EditorExtension>) libraryService ->
                        new ComponentTreeExtension(), true),
                Arguments.of("Plantillas y biblioteca", (Function<LibraryService, EditorExtension>) libraryService ->
                        new TemplatesExtension(libraryService, SwingLibraryDialogs.INSTANCE), true),
                Arguments.of("Tabla de verdad", (Function<LibraryService, EditorExtension>) libraryService ->
                        new TruthTableExtension(), false),
                Arguments.of("Karnaugh", (Function<LibraryService, EditorExtension>) libraryService ->
                        new KarnaughExtension(libraryService, () -> Path.of("."), path -> { }), false),
                Arguments.of("Ordenar cables", (Function<LibraryService, EditorExtension>) libraryService ->
                        new TidyWiresExtension(), false),
                Arguments.of("Exportar imagen", (Function<LibraryService, EditorExtension>) libraryService ->
                        new ExportImageExtension(), false),
                Arguments.of("Exportar a PDF", (Function<LibraryService, EditorExtension>) libraryService ->
                        new ExportPdfExtension(), false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("extensions")
    void anExtensionIsInEveryPaneEvenLaterOnesAndLeavesNothingInRemovedOnes(
            String name, Function<LibraryService, EditorExtension> extension, boolean touchesPanes) {
        // what the window itself puts in its first pane and in the panes it makes later (part A may differ there)
        CircuitCanvas first = view.canvas();
        Footprint firstBare = Footprint.of(first);
        view.canvasPanes().split(Orientation.HORIZONTAL, 0.5);
        CircuitCanvas probe = view.panes().get(1);
        Footprint laterBare = Footprint.of(probe);
        view.canvasPanes().removeDivider(Orientation.HORIZONTAL);
        Footprint removedBare = Footprint.of(probe);
        Function<CircuitCanvas, Footprint> bareOf = pane -> pane == first ? firstBare : laterBare;

        view.canvasPanes().split(Orientation.HORIZONTAL, 0.5);
        Runnable dispose = extension.apply(service).install(view);
        Footprint added = Footprint.of(first).minus(firstBare);
        assertThat(added.isEmpty()).as("what %s adds to a pane", name).isEqualTo(!touchesPanes);
        view.canvasPanes().split(Orientation.VERTICAL, 0.5);
        assertThat(view.panes()).hasSize(4).allSatisfy(pane ->
                assertThat(Footprint.of(pane).minus(bareOf.apply(pane))).as("added to a pane").isEqualTo(added));

        List<CircuitCanvas> closing = List.of(view.panes().get(1), view.panes().get(3));
        view.canvasPanes().removeDivider(Orientation.VERTICAL);
        assertThat(view.panes()).hasSize(2).doesNotContainAnyElementsOf(closing);
        closing.forEach(pane -> assertThat(Footprint.of(pane)).as("a removed pane").isEqualTo(removedBare));
        view.panes().forEach(pane ->
                assertThat(Footprint.of(pane).minus(bareOf.apply(pane))).as("a kept pane").isEqualTo(added));

        dispose.run();
        view.panes().forEach(pane ->
                assertThat(Footprint.of(pane)).as("after dispose").isEqualTo(bareOf.apply(pane)));
    }

    @Test
    void theProblemsGlowTooltipAndF8AreInEveryPaneAndGoneFromARemovedOne() {
        view.canvasPanes().split(Orientation.HORIZONTAL, 0.5);
        view.canvasPanes().split(Orientation.VERTICAL, 0.5);
        Action next = view.problems().nextProblem();
        assertThat(view.panes()).hasSize(4).allSatisfy(pane -> {
            assertThat(pane.getInputMap(JComponent.WHEN_FOCUSED).get(F8)).isEqualTo("nextProblem");
            assertThat(pane.getActionMap().get("nextProblem")).isSameAs(next);
            assertThat(pane.paneHooks().toolTipProvider()).isTrue();
        });
        int overlays = view.panes().getFirst().overlayCount();
        assertThat(view.panes()).allSatisfy(pane -> assertThat(pane.overlayCount()).isEqualTo(overlays));

        CircuitCanvas closing = view.panes().get(3);
        view.canvasPanes().removeDivider(Orientation.VERTICAL);
        assertThat(closing.getInputMap(JComponent.WHEN_FOCUSED).get(F8)).isNull();
        assertThat(closing.paneHooks().toolTipProvider()).isFalse();

        view.problems().dispose();
        assertThat(view.panes()).allSatisfy(pane -> {
            assertThat(pane.getInputMap(JComponent.WHEN_FOCUSED).get(F8)).isNull();
            assertThat(pane.paneHooks().toolTipProvider()).isFalse();
        });
    }

    @Test
    void findingTravelsInTheActivePaneOnlyAndSelectsInTheSharedSelection() {
        Runnable dispose = new FindExtension().install(view);
        Component tunnel = place(openDocument, StandardComponentTypes.TUNNEL_ENTRANCE, 900, 700,
                Map.of("name", "LEJANO"));
        view.canvasPanes().split(Orientation.VERTICAL, 0.5);
        CircuitCanvas left = view.panes().get(0);
        CircuitCanvas right = view.panes().get(1);
        press(right);
        ViewState leftBefore = left.viewState();
        ViewState rightBefore = right.viewState();

        perform(view.shortcutActions().get("edit.find"));
        searchField().setText("LEJANO");

        assertThat(view.selection().ids()).containsExactly(tunnel.id());
        assertThat(right.viewState()).isNotEqualTo(rightBefore);
        assertThat(left.viewState()).isEqualTo(leftBefore);
        dispose.run();
    }

    @Test
    void f8TravelsInTheActivePaneOnly() {
        place(openDocument, StandardComponentTypes.SWITCH, 900, 700, Map.of("value", "1"));
        place(openDocument, StandardComponentTypes.SWITCH, 900, 706, Map.of("value", "0"));
        Wire wire = new Wire(openDocument.document().newWireId(), new GridPoint(904, 701), new GridPoint(904, 707));
        openDocument.execute(new AddWireCommand(wire));
        String netId = openDocument.netlist().netForWire(wire.id()).orElseThrow().id().value();
        view.canvasPanes().split(Orientation.HORIZONTAL, 0.5);
        CircuitCanvas top = view.panes().get(0);
        CircuitCanvas bottom = view.panes().get(1);
        press(bottom);
        ViewState topBefore = top.viewState();
        ViewState bottomBefore = bottom.viewState();

        view.problems().showSnapshot(new SimulationSnapshot(1, openDocument.netlistToken(), 10, false, Map.of(),
                Map.of(), List.of(new ShortCircuit(netId, List.of(), 2)), Optional.empty()));
        perform(view.problems().nextProblem());

        assertThat(view.selection().ids()).containsExactly(wire.id());
        assertThat(bottom.viewState()).isNotEqualTo(bottomBefore);
        assertThat(top.viewState()).isEqualTo(topBefore);
    }

    @Test
    void exportingAnImageFromAnotherPaneUsesTheSharedSelectionAndNoPaneChrome() throws Exception {
        List<java.awt.Component> parents = new ArrayList<>();
        List<Path> answers = new ArrayList<>(List.of(directory.resolve("split.png"), directory.resolve("whole.png"),
                directory.resolve("single.png")));
        ExportImageFeatures features = new ExportImageFeatures(view, (parent, start, name, scale, background, stickyNotes) -> {
            parents.add(parent);
            return Optional.of(new ExportChoice(answers.removeFirst(), ExportChoice.Format.PNG, 1, true));
        }, (parent, message) -> { });
        Component left = place(openDocument, StandardComponentTypes.SWITCH, 10, 10, Map.of());
        place(openDocument, StandardComponentTypes.LED, 80, 10, Map.of());
        view.canvasPanes().split(Orientation.HORIZONTAL, 0.5);
        view.canvasPanes().split(Orientation.VERTICAL, 0.5);
        CircuitCanvas bottomRight = view.panes().get(3);
        press(bottomRight);
        bottomRight.selection().set(Set.of(left.id()));

        perform(features.action());
        view.selection().clear();
        perform(features.action());
        view.canvasPanes().removeAll();
        perform(features.action());

        BufferedImage split = ImageIO.read(directory.resolve("split.png").toFile());
        BufferedImage whole = ImageIO.read(directory.resolve("whole.png").toFile());
        assertThat(split.getWidth()).as("only the selection").isLessThan(whole.getWidth());
        assertThat(Files.readAllBytes(directory.resolve("whole.png")))
                .as("four panes export what one pane exports: no pane border nor divider")
                .isEqualTo(Files.readAllBytes(directory.resolve("single.png")));
        assertThat(parents).containsOnly(view);
        features.dispose();
    }

    private SearchField searchField() {
        return find(view, SearchField.class).orElseThrow();
    }

    private static <T> Optional<T> find(Container container, Class<T> type) {
        for (java.awt.Component child : container.getComponents()) {
            if (type.isInstance(child)) {
                return Optional.of(type.cast(child));
            }
            if (child instanceof Container inner) {
                Optional<T> found = find(inner, type);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }

    /** A press on {@code pane} makes it the active one (the tool sees an empty spot). */
    private static void press(CircuitCanvas pane) {
        MouseEvent event = new MouseEvent(pane, MouseEvent.MOUSE_PRESSED, 0, 0, 20, 20, 1, false,
                MouseEvent.BUTTON1);
        for (var listener : pane.getMouseListeners()) {
            listener.mousePressed(event);
        }
        MouseEvent release = new MouseEvent(pane, MouseEvent.MOUSE_RELEASED, 0, 0, 20, 20, 1, false,
                MouseEvent.BUTTON1);
        for (var listener : pane.getMouseListeners()) {
            listener.mouseReleased(release);
        }
    }
}
