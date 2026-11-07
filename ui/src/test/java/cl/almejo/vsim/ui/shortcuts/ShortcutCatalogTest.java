package cl.almejo.vsim.ui.shortcuts;

import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.MenuId;
import org.junit.jupiter.api.Test;

import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ShortcutCatalogTest {

    /** Track C's entries in menu order; later tracks may add entries (A adds waveforms.toggle under Ver). */
    static final List<String> TRACK_C_IDS = List.of("file.new", "file.open", "file.save", "file.saveAs",
            "file.preferences", "file.exit", "edit.undo", "edit.redo", "edit.delete",
            "edit.rotateClockwise", "edit.rotateCounterClockwise", "edit.configure", "edit.cut", "edit.copy",
            "edit.paste", "edit.duplicate", "edit.selectAll", "edit.label", "view.circuitTree", "view.palette",
            "view.properties", "view.problems", "view.notes", "view.memoryMap",
            "view.selectTool", "view.wireTool",
            "view.panTool", "view.zoomIn", "view.zoomOut", "view.zoomReset", "view.zoomToFit", "view.zoomToSelection",
            "view.zoomField", "view.centerView", "view.nextProblem",
            "view.previousProblem", "simulation.start", "simulation.pause", "simulation.step", "window.newView",
            "window.newWindow", "window.moveGroup", "window.foldGroup", "window.nextTab", "window.previousTab",
            "window.closeTab", "window.closeComponents", "window.closeWindow");

    private static List<String> ids() {
        return ShortcutCatalog.entries().stream().map(ShortcutEntry::id).toList();
    }

    @Test
    void idsAreUniqueAndFollowTheMenus() {
        assertThat(ids()).doesNotHaveDuplicates().containsSubsequence(TRACK_C_IDS);
        List<Integer> groups = ShortcutCatalog.entries().stream().map(entry -> entry.group().ordinal()).toList();
        assertThat(groups).isSorted();
        assertThat(ShortcutCatalog.entries()).allSatisfy(entry -> assertThat(entry.group()).isNotEqualTo(MenuId.HELP));
    }

    @Test
    void noTwoEntriesShareADefaultStrokeAndFiftyNineHaveOne() {
        List<KeyStroke> defaults = ShortcutCatalog.entries().stream()
                .map(ShortcutEntry::defaultStroke).flatMap(Optional::stream).toList();
        assertThat(defaults).doesNotHaveDuplicates().hasSize(59);
        assertThat(defaults).noneMatch(KeyBindings::isReserved);
    }

    @Test
    void snapIsMenuMayusMUnderVerAndWhatItSnapsToHasNoDefault() {
        assertThat(ShortcutCatalog.find("view.snap")).contains(new ShortcutEntry("view.snap", "action.snap",
                MenuId.VIEW, Scope.WINDOW,
                Optional.of(KeyStroke.getKeyStroke(KeyEvent.VK_M, ShortcutCatalog.menuMask() | InputEvent.SHIFT_DOWN_MASK))));
        for (String id : List.of("view.snapToParts", "view.snapToGuides", "view.snapToGridLines")) {
            assertThat(ShortcutCatalog.find(id).orElseThrow().defaultStroke()).as(id).isEmpty();
            assertThat(ShortcutCatalog.find(id).orElseThrow().scope()).as(id).isEqualTo(Scope.WINDOW);
        }
        assertThat(ids()).containsSubsequence("view.cycleGrid", "view.snap", "view.snapToParts",
                "view.snapToGuides", "view.snapToGridLines", "waveforms.toggle");
    }

    @Test
    void presentarIsTheLastOfVerAndHasNoDefaultStroke() {
        List<String> view = ShortcutCatalog.entries().stream()
                .filter(entry -> entry.group() == MenuId.VIEW).map(ShortcutEntry::id).toList();
        assertThat(view).endsWith("waveforms.toggle", "view.aiPresent");
        assertThat(ShortcutCatalog.find("view.aiPresent")).contains(new ShortcutEntry("view.aiPresent",
                "action.aiPresent", MenuId.VIEW, Scope.WINDOW, Optional.empty()));
    }

    @Test
    void theArrowsMoveTheSelectionAndMayusMakesItFiveUnits() {
        int[] keys = {KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT, KeyEvent.VK_UP, KeyEvent.VK_DOWN};
        String[] directions = {"Left", "Right", "Up", "Down"};
        for (int index = 0; index < keys.length; index++) {
            ShortcutEntry near = ShortcutCatalog.find("edit.move" + directions[index]).orElseThrow();
            ShortcutEntry far = ShortcutCatalog.find("edit.move" + directions[index] + "Far").orElseThrow();
            assertThat(near.scope()).isEqualTo(Scope.CANVAS);
            assertThat(far.scope()).isEqualTo(Scope.CANVAS);
            assertThat(near.defaultStroke()).contains(KeyStroke.getKeyStroke(keys[index], 0));
            assertThat(far.defaultStroke())
                    .contains(KeyStroke.getKeyStroke(keys[index], InputEvent.SHIFT_DOWN_MASK));
        }
    }

    @Test
    void defaultsUseThePlatformMenuMaskAndCanvasEntriesKeepTheirKeys() {
        int control = InputEvent.CTRL_DOWN_MASK; // the menu mask when headless
        assertThat(ShortcutCatalog.menuMask()).isEqualTo(control);
        assertThat(ShortcutCatalog.find("file.save")).hasValueSatisfying(entry -> {
            assertThat(entry.scope()).isEqualTo(Scope.WINDOW);
            assertThat(entry.defaultStroke()).contains(KeyStroke.getKeyStroke(KeyEvent.VK_S, control));
            assertThat(entry.nameKey()).isEqualTo("action.save");
        });
        assertThat(ShortcutCatalog.find("edit.redo").orElseThrow().defaultStroke())
                .contains(KeyStroke.getKeyStroke(KeyEvent.VK_Z, control | InputEvent.SHIFT_DOWN_MASK));
        assertThat(ShortcutCatalog.find("edit.delete").orElseThrow())
                .isEqualTo(new ShortcutEntry("edit.delete", "action.delete", MenuId.EDIT, Scope.CANVAS,
                        Optional.of(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0))));
        assertThat(ShortcutCatalog.find("edit.rotateCounterClockwise").orElseThrow().defaultStroke())
                .contains(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.SHIFT_DOWN_MASK));
        assertThat(ShortcutCatalog.find("edit.label").orElseThrow().defaultStroke())
                .contains(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0));
        assertThat(ShortcutCatalog.find("view.previousProblem").orElseThrow().defaultStroke())
                .contains(KeyStroke.getKeyStroke(KeyEvent.VK_F8, InputEvent.SHIFT_DOWN_MASK));
        assertThat(ShortcutCatalog.find("window.newView")).hasValueSatisfying(entry -> {
            assertThat(entry.scope()).isEqualTo(Scope.WINDOW);
            assertThat(entry.defaultStroke()).isEmpty();
        });
        assertThat(ShortcutCatalog.find("help.about")).isEmpty();
    }

    @Test
    void theLeftColumnPanelsFoldWithAltOneAndAltTwoUnderVer() {
        int alt = InputEvent.ALT_DOWN_MASK;
        assertThat(ShortcutCatalog.find("view.circuitTree")).contains(new ShortcutEntry("view.circuitTree",
                "action.circuitTree", MenuId.VIEW, Scope.WINDOW, Optional.of(KeyStroke.getKeyStroke(KeyEvent.VK_1,
                alt))));
        assertThat(ShortcutCatalog.find("view.palette")).contains(new ShortcutEntry("view.palette",
                "action.palette", MenuId.VIEW, Scope.WINDOW, Optional.of(KeyStroke.getKeyStroke(KeyEvent.VK_2,
                alt))));
    }

    @Test
    void theRightPanelSectionsOpenWithAltThreeToAltSixUnderVer() {
        int alt = InputEvent.ALT_DOWN_MASK;
        String[] ids = {"view.properties", "view.problems", "view.notes", "view.memoryMap"};
        String[] names = {"inspector.properties", "problems.tab", "stickyNotes.tab", "memory.map.tab"};
        int[] digits = {KeyEvent.VK_3, KeyEvent.VK_4, KeyEvent.VK_5, KeyEvent.VK_6};
        for (int index = 0; index < ids.length; index++) {
            assertThat(ShortcutCatalog.find(ids[index])).contains(new ShortcutEntry(ids[index], names[index],
                    MenuId.VIEW, Scope.WINDOW, Optional.of(KeyStroke.getKeyStroke(digits[index], alt))));
        }
    }

    @Test
    void theTabEntriesHaveTheSpecsDefaultsUnderVentana() {
        int control = InputEvent.CTRL_DOWN_MASK;
        int shift = InputEvent.SHIFT_DOWN_MASK;
        assertThat(ShortcutCatalog.find("file.close")).as("Ctrl+W moved to «Cerrar pestaña»").isEmpty();
        Map<String, Optional<KeyStroke>> expected = new LinkedHashMap<>();
        expected.put("window.newWindow", Optional.of(KeyStroke.getKeyStroke(KeyEvent.VK_N, control | shift)));
        expected.put("window.moveGroup", Optional.empty());
        expected.put("window.foldGroup", Optional.empty());
        expected.put("window.nextTab", Optional.of(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, control)));
        expected.put("window.previousTab", Optional.of(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, control | shift)));
        expected.put("window.closeTab", Optional.of(KeyStroke.getKeyStroke(KeyEvent.VK_W, control)));
        expected.put("window.closeComponents", Optional.empty());
        expected.put("window.closeWindow", Optional.of(KeyStroke.getKeyStroke(KeyEvent.VK_W, control | shift)));
        expected.forEach((id, stroke) -> {
            ShortcutEntry entry = ShortcutCatalog.find(id).orElseThrow();
            assertThat(entry.group()).as(id).isEqualTo(MenuId.WINDOW);
            assertThat(entry.scope()).as(id).isEqualTo(Scope.WINDOW);
            assertThat(entry.defaultStroke()).as(id).isEqualTo(stroke);
        });
        assertThat(ids()).containsSubsequence("window.removeSplit", "window.nextPane");
    }

    @Test
    void theSplitViewItemsFollowNewViewUnderVentanaWithoutDefaults() {
        List<String> splitIds = List.of("window.splitHorizontal", "window.splitVertical", "window.removeSplit",
                "window.nextPane");
        assertThat(ids()).containsSubsequence("window.newView", "window.splitHorizontal", "window.splitVertical",
                "window.removeSplit", "window.nextPane");
        for (String id : splitIds) {
            ShortcutEntry entry = ShortcutCatalog.find(id).orElseThrow();
            assertThat(entry.group()).as(id).isEqualTo(MenuId.WINDOW);
            assertThat(entry.scope()).as(id).isEqualTo(Scope.WINDOW);
            assertThat(entry.defaultStroke()).as(id).isEmpty();
        }
        assertThat(Messages.bundle(Messages.SPANISH)
                .getString(ShortcutCatalog.find("window.splitHorizontal").orElseThrow().nameKey()))
                .isEqualTo("Dividir arriba y abajo");
    }

    @Test
    void everyNameKeyIsInBothBundles() {
        for (ShortcutEntry entry : ShortcutCatalog.entries()) {
            assertThat(Messages.bundle(Messages.SPANISH).containsKey(entry.nameKey())).as(entry.id()).isTrue();
            assertThat(Messages.bundle(java.util.Locale.ENGLISH).containsKey(entry.nameKey())).as(entry.id()).isTrue();
        }
    }

    @Test
    void everyDefaultPassesTheAssignmentRules() {
        for (ShortcutEntry entry : ShortcutCatalog.entries()) {
            entry.defaultStroke().ifPresent(stroke -> assertThat(KeyBindings.isAllowedFor(entry, stroke))
                    .as("%s = %s", entry.id(), stroke).isTrue());
        }
    }

    @Test
    void everySimulationEntryIsVisibleAndTheStepsAndRestartHaveTheirDefaults() {
        List<String> simulation = ShortcutCatalog.entries().stream()
                .filter(entry -> entry.group() == MenuId.SIMULATION).map(ShortcutEntry::id).toList();
        assertThat(simulation).containsExactly("simulation.start", "simulation.pause", "simulation.startOrPause",
                "simulation.restart",
                "simulation.step", "simulation.stepSize", "simulation.stepMode", "simulation.advance",
                "simulation.advanceToClockEdge", "simulation.advanceClockCycle", "simulation.truthTable");
        assertThat(ShortcutCatalog.find("simulation.step").orElseThrow().defaultStroke())
                .contains(KeyStroke.getKeyStroke(KeyEvent.VK_F6, 0));
        assertThat(ShortcutCatalog.find("simulation.step").orElseThrow().nameKey())
                .as("the dialog must not say «10 t» when the step size is another").isEqualTo("shortcut.fixedStep");
        assertThat(ShortcutCatalog.find("simulation.stepSize").orElseThrow().defaultStroke()).isEmpty();
        assertThat(ShortcutCatalog.find("simulation.restart").orElseThrow().defaultStroke())
                .contains(KeyStroke.getKeyStroke(KeyEvent.VK_F5, InputEvent.CTRL_DOWN_MASK));
        assertThat(ShortcutCatalog.find("simulation.advance").orElseThrow().defaultStroke())
                .contains(KeyStroke.getKeyStroke(KeyEvent.VK_F7, 0));
        assertThat(ShortcutCatalog.find("simulation.advanceToClockEdge").orElseThrow().defaultStroke())
                .contains(KeyStroke.getKeyStroke(KeyEvent.VK_F7, InputEvent.SHIFT_DOWN_MASK));
        assertThat(ShortcutCatalog.find("simulation.advanceClockCycle").orElseThrow().defaultStroke())
                .contains(KeyStroke.getKeyStroke(KeyEvent.VK_F7, ShortcutCatalog.menuMask()));
        assertThat(ShortcutCatalog.find("simulation.truthTable")).contains(new ShortcutEntry("simulation.truthTable",
                "action.truthTable", MenuId.SIMULATION, Scope.WINDOW, Optional.empty()));
    }

    @Test
    void theToolsMenuHasTheKarnaughWizardTheExpressionsAndTheAiPanelWithoutADefaultStroke() {
        List<String> tools = ShortcutCatalog.entries().stream()
                .filter(entry -> entry.group() == MenuId.TOOLS).map(ShortcutEntry::id).toList();
        assertThat(tools).containsExactly("tools.circuitFromTruthTable", "tools.karnaughPractice",
                "tools.circuitFromExpressions", "tools.aiPanel");
        assertThat(ShortcutCatalog.find("tools.aiPanel")).contains(new ShortcutEntry("tools.aiPanel",
                "action.aiPanel", MenuId.TOOLS, Scope.WINDOW, Optional.empty()));
        assertThat(ShortcutCatalog.find("tools.circuitFromTruthTable")).contains(new ShortcutEntry(
                "tools.circuitFromTruthTable", "action.circuitFromTruthTable", MenuId.TOOLS, Scope.WINDOW,
                Optional.empty()));
        assertThat(ShortcutCatalog.find("tools.karnaughPractice")).contains(new ShortcutEntry(
                "tools.karnaughPractice", "action.karnaughPractice", MenuId.TOOLS, Scope.WINDOW,
                Optional.empty()));
        assertThat(ShortcutCatalog.find("tools.circuitFromExpressions")).contains(new ShortcutEntry(
                "tools.circuitFromExpressions", "action.circuitFromExpressions", MenuId.TOOLS, Scope.WINDOW,
                Optional.empty()));
    }

    @Test
    void exportPdfFollowsExportImageWithNoDefaultStroke() {
        assertThat(ids()).containsSubsequence("file.exportImage", "file.exportPdf");
        assertThat(ids().indexOf("file.exportPdf")).isEqualTo(ids().indexOf("file.exportImage") + 1);
        ShortcutEntry entry = ShortcutCatalog.find("file.exportPdf").orElseThrow();
        assertThat(entry.scope()).isEqualTo(Scope.WINDOW);
        assertThat(entry.group()).isEqualTo(MenuId.FILE);
        assertThat(entry.defaultStroke()).isEmpty();
    }

    @Test
    void printFollowsSaveAsAndIsTheOnlyOneOnCtrlP() {
        assertThat(ids().indexOf("file.print")).isEqualTo(ids().indexOf("file.saveAs") + 1);
        KeyStroke print = KeyStroke.getKeyStroke(KeyEvent.VK_P, ShortcutCatalog.menuMask());
        assertThat(ShortcutCatalog.find("file.print")).contains(new ShortcutEntry("file.print", "action.print",
                MenuId.FILE, Scope.WINDOW, Optional.of(print)));
        assertThat(ShortcutCatalog.entries()).filteredOn(each -> each.defaultStroke().equals(Optional.of(print)))
                .extracting(ShortcutEntry::id).containsExactly("file.print");
    }
}
