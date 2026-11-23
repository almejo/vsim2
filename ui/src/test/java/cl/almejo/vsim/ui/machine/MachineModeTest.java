package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.Application;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.library.LibraryService;
import cl.almejo.vsim.ui.library.SwingLibraryDialogs;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.panels.PalettePanel;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.SwingUtilities;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.perform;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The machine tab (plan 2026-11-14 task 4, review focus 4): a component tab of a machine shows its diagram and its
 * palette instead of the pins-only body, every circuit action is disabled there with its reason and never changes the
 * body, a circuit tab keeps every action, and the active state is the one of the copy entered through.
 */
class MachineModeTest {

    @TempDir
    Path directory;

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final AppPreferences preferences = AppPreferences.inMemory();
    private final Map<String, ActiveMachineState> statesByCopy = new HashMap<>();
    private final MachineStateSource fakeSource = (snapshot, instancePath) ->
            Optional.ofNullable(statesByCopy.get(instancePath));
    private final List<EditorView> views = new ArrayList<>();
    private final List<OpenDocument> places = new ArrayList<>();
    private LibraryService service;
    private EditorServices services;
    private OpenDocument document;

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
        extensions.add(new MachineModeExtension(fakeSource));
        services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT), preferences,
                library, extensions);
        document = MachineFixtures.circuitWithMachines(2);
    }

    @AfterEach
    void close() {
        views.forEach(EditorView::dispose);
        places.forEach(OpenDocument::close);
        document.close();
        service.close();
    }

    private EditorView viewOf(OpenDocument shown) {
        EditorView view = new EditorView(shown, services, NO_WINDOW);
        view.setSize(1200, 800);
        view.doLayout();
        views.add(view);
        return view;
    }

    private OpenDocument machinePlace(int copy) {
        OpenDocument place = document.openBody(MachineFixtures.DEFINITION_ID,
                Optional.of(MachineFixtures.copies(document).get(copy)));
        places.add(place);
        return place;
    }

    private static String reason(String whyKey) {
        return Messages.format("machine.disabled.reason", Messages.text(whyKey));
    }

    @Test
    void aMachineTabShowsTheDiagramAndTheMachinePaletteInsteadOfTheBody() {
        EditorView view = viewOf(machinePlace(1));

        MachineMode mode = MachineMode.of(view).orElseThrow();

        assertThat(SwingUtilities.isDescendingFrom(mode.canvas(), view)).isTrue();
        assertThat(SwingUtilities.isDescendingFrom(mode.palette(), view)).isTrue();
        assertThat(SwingUtilities.isDescendingFrom(view.canvas(), view)).isFalse();
        assertThat(SwingUtilities.isDescendingFrom(view.palette(), view)).isFalse();
        assertThat(mode.canvas().definitionId()).isEqualTo(MachineFixtures.DEFINITION_ID);
    }

    @Test
    void theMachinePaletteOffersTheDrawingToolsOnly() {
        MachinePalette palette = MachineMode.of(viewOf(machinePlace(0))).orElseThrow().palette();

        assertThat(palette.stateButton().getText()).isEqualTo(Messages.text("machine.palette.state"));
        assertThat(palette.transitionButton().getText()).isEqualTo(Messages.text("machine.palette.transition"));
        assertThat(palette.noteButton().getText()).isEqualTo(Messages.text("machine.palette.note"));
        assertThat(palette.textButton().getText()).isEqualTo(Messages.text("machine.palette.text"));
    }

    @Test
    void aCircuitTabIsNotInMachineMode() {
        EditorView view = viewOf(document);

        assertThat(MachineMode.of(view)).isEmpty();
        assertThat(SwingUtilities.isDescendingFrom(view.canvas(), view)).isTrue();
        assertThat(SwingUtilities.isDescendingFrom(view.palette(), view)).isTrue();
    }

    @Test
    void circuitActionsAreDisabledThereWithTheirReasonAndStaySo() {
        EditorView view = viewOf(machinePlace(0));
        Map<String, Action> byId = view.shortcutActions();

        view.actions().refresh(); // what every history or selection change runs
        view.actions().rotateClockwise().setEnabled(true); // a feature re-enabling one is held back

        assertThat(byId.get("edit.tidyWires").isEnabled()).isFalse();
        assertThat(byId.get("edit.tidyWires").getValue(Action.SHORT_DESCRIPTION))
                .isEqualTo(reason("machine.disabled.noWiresToTidy"));
        assertThat(byId.get("view.busValues").getValue(Action.SHORT_DESCRIPTION))
                .isEqualTo(reason("machine.disabled.noBuses"));
        assertThat(byId.get("view.showSheets").getValue(Action.SHORT_DESCRIPTION))
                .isEqualTo(reason("machine.disabled.noSheets"));
        List<Action> circuitActions = List.of(view.actions().rotateClockwise(),
                view.actions().rotateCounterClockwise(), view.actions().configure(),
                byId.get("edit.quickInsert"),
                byId.get("view.frameTool"), // «Nota adhesiva» and «Texto» write on the diagram
                byId.get("tools.circuitFromExpressions"), byId.get("tools.circuitFromTruthTable"),
                byId.get("view.addHorizontalGuide"), byId.get("window.splitHorizontal"));
        for (Action action : circuitActions) {
            assertThat(action.isEnabled()).as(String.valueOf(action.getValue(Action.NAME))).isFalse();
            assertThat(String.valueOf(action.getValue(Action.SHORT_DESCRIPTION)))
                    .as(String.valueOf(action.getValue(Action.NAME)))
                    .isIn(reason("machine.disabled.circuitOnly"), Messages.text("machine.comesLater"));
        }
    }

    @Test
    void theToolbarShowsTheSameButtonsWithTheCircuitOnesDisabledAndTheirReason() {
        EditorView machineView = viewOf(machinePlace(0));
        EditorView circuitView = viewOf(document);

        assertThat(machineView.toolBar().getComponentCount()).isEqualTo(circuitView.toolBar().getComponentCount());
        Action tidy = machineView.shortcutActions().get("edit.tidyWires");
        AbstractButton tidyButton = buttonOf(machineView, tidy);
        assertThat(tidyButton.isEnabled()).isFalse();
        assertThat(tidyButton.getToolTipText()).startsWith(reason("machine.disabled.noWiresToTidy")); // + its key
    }

    private static AbstractButton buttonOf(EditorView view, Action action) {
        for (java.awt.Component component : view.toolBar().getComponents()) {
            if (component instanceof AbstractButton button && button.getAction() == action) {
                return button;
            }
        }
        throw new AssertionError("no toolbar button for " + action.getValue(Action.NAME));
    }

    @Test
    void whatEveryTabNeedsWorksThereAsInACircuitTab() {
        EditorView machineView = viewOf(machinePlace(0));
        EditorView circuitView = viewOf(document);

        for (String id : List.of("waveforms.toggle", "simulation.stepMode", "simulation.advanceToClockEdge",
                "help.findAction", "view.presentationMode", "file.preferences", "view.showUsedShortcuts")) {
            assertThat(machineView.shortcutActions().get(id).isEnabled()).as(id)
                    .isEqualTo(circuitView.shortcutActions().get(id).isEnabled());
            assertThat(machineView.shortcutActions().get(id).getValue(Action.SHORT_DESCRIPTION)).as(id)
                    .isEqualTo(circuitView.shortcutActions().get(id).getValue(Action.SHORT_DESCRIPTION));
        }
        for (Action kept : List.of(machineView.actions().save(), machineView.actions().open(),
                machineView.actions().restart(), machineView.actions().zoomIn(), machineView.actions().zoomOut(),
                machineView.actions().zoomToFit(), machineView.actions().nextTab(), machineView.actions().about(),
                machineView.actions().selectTool(), machineView.zoomControl().typeZoomAction())) {
            assertThat(kept.isEnabled()).as(String.valueOf(kept.getValue(Action.NAME))).isTrue();
        }
    }

    @Test
    void aCircuitEditNeverChangesTheMachineBody() {
        OpenDocument place = machinePlace(0);
        viewOf(place);
        Optional<Command> lastStep = document.history().undoCommand();
        Component lamp = registry.require(StandardComponentTypes.LED).newComponent(new ElementId("c99"),
                new GridPoint(4, 4), Rotation.R0, Map.of(), "");

        place.execute(new AddElementsCommand(List.of(lamp))); // a paste, a dropped file, the AI: all go through here

        assertThat(place.document().components()).isEmpty();
        assertThat(document.history().undoCommand()).isEqualTo(lastStep);
    }

    @Test
    void aCircuitTabOfTheSameWindowKeepsEveryAction() {
        viewOf(machinePlace(0));
        EditorView circuitView = viewOf(document);
        Component copy = document.document().components().get(0);

        circuitView.selection().set(Set.of(copy.id()));

        assertThat(MachineMode.of(circuitView)).isEmpty();
        assertThat(circuitView.actions().delete().isEnabled()).isTrue();
        assertThat(circuitView.actions().rotateClockwise().isEnabled()).isTrue();
        assertThat(circuitView.selectionActions().selectAll().isEnabled()).isTrue();
        assertThat(circuitView.shortcutActions().get("edit.tidyWires").isEnabled()).isTrue();
        assertThat(circuitView.shortcutActions().get("edit.tidyWires").getValue(Action.SHORT_DESCRIPTION))
                .isNotEqualTo(reason("machine.disabled.noWiresToTidy"));
        place(document, StandardComponentTypes.LED, 40, 40, Map.of());
        assertThat(document.document().components()).hasSize(3);
    }

    @Test
    void theActiveStateIsTheOneOfTheCopyEnteredThrough() {
        List<String> copies = MachineFixtures.copies(document);
        statesByCopy.put(copies.get(0), new ActiveMachineState("s0", Optional.empty(), Optional.empty()));
        statesByCopy.put(copies.get(1), new ActiveMachineState("s2", Optional.of("s0"), Optional.of("t5")));
        OpenDocument place = machinePlace(1);
        MachineCanvas canvas = MachineMode.of(viewOf(place)).orElseThrow().canvas();

        canvas.showLatestState();
        assertThat(canvas.activeState()).contains(statesByCopy.get(copies.get(1)));

        place.setContextPath(Optional.of(copies.get(0)));
        canvas.showLatestState();
        assertThat(canvas.activeState()).contains(statesByCopy.get(copies.get(0)));

        place.setContextPath(Optional.empty()); // «Sin valores»
        canvas.showLatestState();
        assertThat(canvas.activeState()).isEmpty();
    }

    @Test
    void theZoomActionsZoomTheDiagramNotTheHiddenBody() {
        EditorView view = viewOf(machinePlace(0));
        MachineCanvas canvas = MachineMode.of(view).orElseThrow().canvas();

        perform(view.actions().zoomIn());

        assertThat(canvas.viewport().zoom()).isGreaterThan(1.0);
        assertThat(view.canvas().viewport().zoom()).isEqualTo(1.0);
    }

    @Test
    void uninstallingPutsTheBodyThePaletteAndEveryActionBack() {
        EditorServices withoutMachines = services.withExtensions(services.extensions().stream()
                .filter(extension -> !(extension instanceof MachineModeExtension)).toList());
        OpenDocument place = machinePlace(0);
        EditorView view = new EditorView(place, withoutMachines, NO_WINDOW);
        views.add(view);
        Action tidy = view.shortcutActions().get("edit.tidyWires");
        Object tidyTooltip = tidy.getValue(Action.SHORT_DESCRIPTION);

        Runnable uninstall = MachineMode.install(view, fakeSource);
        assertThat(MachineMode.of(view)).isPresent();
        uninstall.run();

        assertThat(MachineMode.of(view)).isEmpty();
        assertThat(SwingUtilities.isDescendingFrom(view.canvas(), view)).isTrue();
        assertThat(SwingUtilities.isDescendingFrom(view.palette(), view)).isTrue();
        assertThat(view.palette()).isInstanceOf(PalettePanel.class);
        assertThat(tidy.isEnabled()).isTrue();
        assertThat(tidy.getValue(Action.SHORT_DESCRIPTION)).isEqualTo(tidyTooltip);
        assertThat(view.actions().selectTool().isEnabled()).isTrue();
    }

    @Test
    void installingOnACircuitTabDoesNothing() {
        EditorServices withoutMachines = services.withExtensions(List.of());
        EditorView view = new EditorView(document, withoutMachines, NO_WINDOW);
        views.add(view);

        Runnable uninstall = MachineMode.install(view, fakeSource);

        assertThat(MachineMode.of(view)).isEmpty();
        assertThat(SwingUtilities.isDescendingFrom(view.canvas(), view)).isTrue();
        uninstall.run();
    }
}
