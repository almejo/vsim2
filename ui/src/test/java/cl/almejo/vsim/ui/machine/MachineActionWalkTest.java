package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
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
import cl.almejo.vsim.ui.shortcuts.ShortcutsExtension;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JMenuItem;
import java.awt.Component;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.itemsDeep;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Walks every action of a machine's tab (the menus, the toolbar and the shortcut catalog) and fails on an enabled one
 * that this list does not know to work there: a new circuit action is then disabled by {@link MachineActionPolicy},
 * or someone decides, here, that it applies. An action that overrides {@code isEnabled()} ignores the policy's hold,
 * so it fails too unless it is in the list.
 */
class MachineActionWalkTest {

    /** The actions that work on a machine's tab (the shortcut catalog's id, or the label of an action without one). */
    private static final Set<String> WORK_ON_A_MACHINE = Set.of(
            "file.new", "file.open", "file.save", "file.saveAs", "file.exit", "file.preferences",
            "file.exportWindowImage", "edit.undo", "edit.redo", "edit.delete", "edit.cut", "edit.copy", "edit.paste",
            "edit.duplicate", "edit.selectAll",
            "view.circuitTree", "view.palette", "view.properties", "view.problems", "view.notes", "view.memoryMap",
            "view.selectTool", "view.zoomIn", "view.zoomOut", "view.zoomReset", "view.zoomToFit", "view.zoomField",
            "view.centerView", "view.stickyNoteTool", "view.nextProblem", "view.previousProblem",
            "view.presentationMode",
            "view.aiPresent", "view.showUsedShortcuts", "tools.karnaughPractice", "tools.aiPanel",
            "help.findAction", "waveforms.toggle",
            "simulation.start", "simulation.pause", "simulation.startOrPause", "simulation.restart",
            "simulation.step", "simulation.stepMode", "simulation.stepSize",
            "window.newView", "window.newWindow", "window.nextTab", "window.previousTab", "window.closeTab",
            "window.closeComponents", "window.closeWindow", "window.moveGroup", "window.foldGroup");

    /** The menu items without a catalog id that work on a machine's tab: their menu and their label's bundle key. */
    private static final Map<MenuId, List<String>> MENU_ITEMS_WITHOUT_ID = Map.of(
            MenuId.SIMULATION, List.of("action.pauseOnShortCircuit"),
            MenuId.HELP, List.of("action.about", "action.shortcutSheet", "action.tipOfTheDay", "action.whatsNew",
                    "action.reportProblem"));

    @TempDir
    Path directory;

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final AppPreferences preferences = AppPreferences.inMemory();
    private LibraryService service;
    private OpenDocument document;
    private OpenDocument place;
    private EditorView view;

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
    }

    @AfterEach
    void close() {
        view.dispose();
        place.close();
        document.close();
        service.close();
    }

    /** Every action the tab offers, by the name the list knows it by. */
    private Map<String, Action> walk() {
        Map<Action, String> idByAction = new IdentityHashMap<>();
        ShortcutsExtension.actionsOf(view).forEach((id, action) -> idByAction.putIfAbsent(action, id));
        Map<String, Action> found = new LinkedHashMap<>();
        ShortcutsExtension.actionsOf(view).forEach(found::putIfAbsent);
        for (MenuId menuId : MenuId.values()) {
            for (JMenuItem item : itemsDeep(view.menu(menuId))) {
                Action action = item.getAction();
                if (action != null) {
                    found.putIfAbsent(idByAction.getOrDefault(action, "menu " + menuId + ": " + item.getText()),
                            action);
                }
            }
        }
        for (Component component : view.toolBar().getComponents()) {
            if (component instanceof AbstractButton button && button.getAction() != null) {
                Action action = button.getAction();
                found.putIfAbsent(idByAction.getOrDefault(action, "toolbar: " + button.getToolTipText()), action);
            }
        }
        return found;
    }

    /**
     * The names of the actions of {@code walked} that are enabled, or override {@code isEnabled()} (so the policy's
     * hold cannot disable them), without being known to work on a machine.
     */
    private static List<String> strays(Map<String, Action> walked) {
        List<String> found = new ArrayList<>();
        walked.forEach((name, action) -> {
            if (isKnownToWork(name)) {
                return;
            }
            if (action.isEnabled() || overridesIsEnabled(action)) {
                found.add(name);
            }
        });
        return found;
    }

    private static boolean isKnownToWork(String name) {
        return WORK_ON_A_MACHINE.contains(name) || machineItems().contains(name)
                || menuItemsWithoutId().contains(name);
    }

    /** {@link #MENU_ITEMS_WITHOUT_ID} named as the walk names them, by menu and text in the current language. */
    private static Set<String> menuItemsWithoutId() {
        Set<String> names = new TreeSet<>();
        MENU_ITEMS_WITHOUT_ID.forEach((menuId, keys) -> keys.forEach(key ->
                names.add("menu " + menuId + ": " + Messages.text(key))));
        return names;
    }

    /** The machine's own Editar items: no id in the catalog, so the walk names them by menu and text. */
    private static Set<String> machineItems() {
        return Set.of("menu EDIT: " + Messages.text("machine.action.newState"),
                "menu EDIT: " + Messages.text("machine.action.newTransition"),
                "menu EDIT: " + Messages.text("machine.action.editText"));
    }

    private static boolean overridesIsEnabled(Action action) {
        try {
            return action.getClass().getMethod("isEnabled").getDeclaringClass() != AbstractAction.class;
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Test
    void everyActionOfAMachineTabIsDisabledWithItsReasonOrKnownToWork() {
        assertThat(strays(walk())).isEmpty();
    }

    @Test
    void theWalkTurnsRedForAnEnabledActionNobodyDecidedOn() {
        Map<String, Action> walked = walk();
        walked.put("fake.enabled", new AbstractAction("Fake") {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
            }
        });
        walked.put("fake.override", new AbstractAction("Override") {
            @Override
            public boolean isEnabled() {
                return false;
            }

            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
            }
        });

        assertThat(strays(walked)).containsExactlyInAnyOrder("fake.enabled", "fake.override");
    }
}
