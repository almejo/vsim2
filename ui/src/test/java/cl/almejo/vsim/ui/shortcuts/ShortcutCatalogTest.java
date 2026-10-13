package cl.almejo.vsim.ui.shortcuts;

import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.MenuId;
import org.junit.jupiter.api.Test;

import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ShortcutCatalogTest {

    /** Track C's entries in menu order; later tracks may add entries (A adds waveforms.toggle under Ver). */
    static final List<String> TRACK_C_IDS = List.of("file.new", "file.open", "file.save", "file.saveAs",
            "file.preferences", "file.close", "file.exit", "edit.undo", "edit.redo", "edit.delete",
            "edit.rotateClockwise", "edit.rotateCounterClockwise", "edit.configure", "edit.cut", "edit.copy",
            "edit.paste", "edit.duplicate", "edit.selectAll", "edit.label", "view.selectTool", "view.wireTool",
            "view.panTool", "view.zoomIn", "view.zoomOut", "view.zoomReset", "view.centerView", "view.nextProblem",
            "view.previousProblem", "simulation.start", "simulation.pause", "simulation.step", "window.newView");

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
    void noTwoEntriesShareADefaultStrokeAndTwentyThreeHaveOne() {
        List<KeyStroke> defaults = ShortcutCatalog.entries().stream()
                .map(ShortcutEntry::defaultStroke).flatMap(Optional::stream).toList();
        assertThat(defaults).doesNotHaveDuplicates().hasSize(23);
        assertThat(defaults).noneMatch(KeyBindings::isReserved);
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
    void everySimulationEntryIsVisibleAndAdvanceHasF7() {
        List<String> simulation = ShortcutCatalog.entries().stream()
                .filter(entry -> entry.group() == MenuId.SIMULATION).map(ShortcutEntry::id).toList();
        assertThat(simulation).containsExactly("simulation.start", "simulation.pause", "simulation.step",
                "simulation.stepMode", "simulation.advance", "simulation.truthTable");
        assertThat(ShortcutCatalog.find("simulation.advance").orElseThrow().defaultStroke())
                .contains(KeyStroke.getKeyStroke(KeyEvent.VK_F7, 0));
        assertThat(ShortcutCatalog.find("simulation.truthTable")).contains(new ShortcutEntry("simulation.truthTable",
                "action.truthTable", MenuId.SIMULATION, Scope.WINDOW, Optional.empty()));
    }
}
