package cl.almejo.vsim.ui.shortcuts;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.icons.ActionIcons;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.preferences.PreferencesFeature;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

class ShortcutsExtensionTest {

    private static final int CONTROL = InputEvent.CTRL_DOWN_MASK;
    private final List<EditorView> views = new ArrayList<>();
    private OpenDocument openDocument;
    private EditorServices services;
    private AppPreferences preferences;
    private KeyBindings bindings;
    private EditorView view;

    @BeforeEach
    void create() {
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        services = EditorServices.of(PainterRegistry.standard(), ColorScheme.LIGHT)
                .withExtensions(List.of(new PreferencesFeature(), new ShortcutsExtension()));
        preferences = services.preferences();
        bindings = new KeyBindings(preferences);
        view = newView();
    }

    @AfterEach
    void close() {
        views.forEach(EditorView::dispose);
        openDocument.close();
    }

    private EditorView newView() {
        EditorView created = new EditorView(openDocument, services, NO_WINDOW);
        views.add(created);
        return created;
    }

    private static KeyStroke control(int keyCode) {
        return KeyStroke.getKeyStroke(keyCode, CONTROL);
    }

    private static KeyStroke plain(int keyCode) {
        return KeyStroke.getKeyStroke(keyCode, 0);
    }

    private InputMap canvasKeys(EditorView target) {
        return target.canvas().getInputMap(JComponent.WHEN_FOCUSED);
    }

    private static JMenuItem itemOf(JMenu menu, Action action) {
        for (int index = 0; index < menu.getItemCount(); index++) {
            JMenuItem item = menu.getItem(index);
            if (item != null && item.getAction() == action) {
                return item;
            }
        }
        throw new AssertionError("no menu item for " + action.getValue(Action.NAME));
    }

    @Test
    void everyTrackCActionHasItsActionInAHeadlessView() {
        Map<String, Action> actions = ShortcutsExtension.actionsOf(view);
        assertThat(actions.keySet()).containsAll(ShortcutCatalogTest.TRACK_C_IDS);
        assertThat(actions.get("file.save")).isSameAs(view.actions().save());
        assertThat(actions.get("edit.cut")).isSameAs(view.selectionActions().cut());
        assertThat(actions.get("edit.label")).isSameAs(view.labelFeatures().actions().editLabel());
        assertThat(actions.get("view.nextProblem")).isSameAs(view.problems().nextProblem());
        assertThat(actions.get("window.newView")).isSameAs(view.actions().newView());
        assertThat(actions.get("file.preferences")).isSameAs(view.shortcutActions().get("file.preferences"));
    }

    @Test
    void defaultsKeepTodaysAcceleratorsAndCanvasKeysUnderTheExtensionsNames() {
        assertThat(view.actions().save().getValue(Action.ACCELERATOR_KEY)).isEqualTo(control(KeyEvent.VK_S));
        assertThat(view.actions().start().getValue(Action.ACCELERATOR_KEY)).isNull();
        assertThat(view.actions().delete().getValue(Action.ACCELERATOR_KEY)).isNull();
        assertThat(canvasKeys(view).get(plain(KeyEvent.VK_DELETE))).isEqualTo("shortcut.edit.delete");
        assertThat(canvasKeys(view).get(plain(KeyEvent.VK_F2))).isEqualTo("shortcut.edit.label");
        assertThat(canvasKeys(view).get(plain(KeyEvent.VK_F8))).isEqualTo("shortcut.view.nextProblem");
        assertThat(view.canvas().getActionMap().get("shortcut.edit.rotateClockwise"))
                .isSameAs(view.actions().rotateClockwise());
    }

    @Test
    void appliesAChangedAcceleratorToTheMenuActionAtOnce() {
        bindings.assign("file.save", control(KeyEvent.VK_G));
        assertThat(view.actions().save().getValue(Action.ACCELERATOR_KEY)).isEqualTo(control(KeyEvent.VK_G));
        assertThat(itemOf(view.menu(MenuId.FILE), view.actions().save()).getAccelerator())
                .isEqualTo(control(KeyEvent.VK_G));
        assertThat(view.actions().undo().getValue(Action.ACCELERATOR_KEY)).isEqualTo(control(KeyEvent.VK_Z));
        bindings.assign("simulation.start", plain(KeyEvent.VK_F5));
        assertThat(view.actions().start().getValue(Action.ACCELERATOR_KEY)).isEqualTo(plain(KeyEvent.VK_F5));
    }

    @Test
    void reassigningLeavesTheOtherActionWithoutAShortcut() {
        bindings.assign("edit.selectAll", control(KeyEvent.VK_S));
        assertThat(view.selectionActions().selectAll().getValue(Action.ACCELERATOR_KEY))
                .isEqualTo(control(KeyEvent.VK_S));
        assertThat(view.actions().save().getValue(Action.ACCELERATOR_KEY)).isNull();
    }

    @Test
    void movesACanvasBindingWithoutLeavingTheOldStrokeBound() {
        bindings.assign("edit.rotateClockwise", plain(KeyEvent.VK_T));
        assertThat(canvasKeys(view).get(plain(KeyEvent.VK_T))).isEqualTo("shortcut.edit.rotateClockwise");
        assertThat(canvasKeys(view).get(plain(KeyEvent.VK_R))).isNull();
        bindings.assign("edit.rotateClockwise", control(KeyEvent.VK_T));
        assertThat(canvasKeys(view).get(plain(KeyEvent.VK_T))).isNull();
        bindings.assign("view.wireTool", control(KeyEvent.VK_T)); // a window action takes Ctrl+T from the canvas
        assertThat(canvasKeys(view).get(control(KeyEvent.VK_T))).isNull();
        assertThat(view.actions().wireTool().getValue(Action.ACCELERATOR_KEY)).isEqualTo(control(KeyEvent.VK_T));
        bindings.reset("edit.rotateClockwise");
        assertThat(canvasKeys(view).get(plain(KeyEvent.VK_R))).isEqualTo("shortcut.edit.rotateClockwise");
    }

    @Test
    void deleteKeepsItsFixedBackspaceAliasWhenItsShortcutMoves() {
        bindings.assign("edit.delete", control(KeyEvent.VK_DELETE));
        assertThat(canvasKeys(view).get(plain(KeyEvent.VK_DELETE))).isNull();
        assertThat(canvasKeys(view).get(control(KeyEvent.VK_DELETE))).isEqualTo("shortcut.edit.delete");
        assertThat(canvasKeys(view).get(plain(KeyEvent.VK_BACK_SPACE))).isEqualTo("delete");
        assertThat(view.canvas().getActionMap().get("delete")).isSameAs(view.actions().delete());
        bindings.clear("edit.delete");
        assertThat(canvasKeys(view).get(control(KeyEvent.VK_DELETE))).isNull();
        assertThat(canvasKeys(view).get(plain(KeyEvent.VK_BACK_SPACE))).isEqualTo("delete");
    }

    @Test
    void storedOverridesApplyWhenTheViewOpensAndInEveryView() {
        preferences.shortcutsNode().put("file.open", "ctrl pressed K");
        EditorView later = newView();
        assertThat(later.actions().open().getValue(Action.ACCELERATOR_KEY)).isEqualTo(control(KeyEvent.VK_K));
        bindings.assign("view.nextProblem", plain(KeyEvent.VK_F9));
        for (EditorView each : List.of(view, later)) {
            assertThat(canvasKeys(each).get(plain(KeyEvent.VK_F9))).isEqualTo("shortcut.view.nextProblem");
            assertThat(canvasKeys(each).get(plain(KeyEvent.VK_F8))).isNull();
        }
    }

    @Test
    void disposingRemovesThePreferencesListener() {
        int withOneView = preferences.listenerCount();
        EditorView second = newView();
        assertThat(preferences.listenerCount()).isEqualTo(withOneView + 1);
        views.remove(second);
        second.dispose();
        assertThat(preferences.listenerCount()).isEqualTo(withOneView);
    }

    @Test
    void releasingRemovesOnlyTheStrokeTheEditorBoundAndKeepsItsAliases() {
        // the default strokes now answer to the extension's names, not the editor's own
        assertThat(canvasKeys(view).get(plain(KeyEvent.VK_R))).isEqualTo("shortcut.edit.rotateClockwise");
        assertThat(canvasKeys(view).get(plain(KeyEvent.VK_BACK_SPACE))).isEqualTo("delete");
        assertThat(canvasKeys(view).get(plain(KeyEvent.VK_DELETE))).isEqualTo("shortcut.edit.delete");
        assertThat(canvasKeys(view).get(plain(KeyEvent.VK_F8))).isEqualTo("shortcut.view.nextProblem");
        assertThat(canvasKeys(view).get(KeyStroke.getKeyStroke(KeyEvent.VK_F8, InputEvent.SHIFT_DOWN_MASK)))
                .isEqualTo("shortcut.view.previousProblem");
    }

    @Test
    void twoActionsSharingAStoredStrokeLeaveItToTheFirstInCatalogOrder() {
        preferences.shortcutsNode().put("edit.rotateCounterClockwise", "pressed R"); // rotateClockwise's default
        preferences.shortcutsNode().put("edit.cut", "ctrl pressed S"); // file.save's default
        preferences.shortcutsChanged();
        assertThat(canvasKeys(view).get(plain(KeyEvent.VK_R))).isEqualTo("shortcut.edit.rotateClockwise");
        assertThat(canvasKeys(view).get(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.SHIFT_DOWN_MASK))).isNull();
        assertThat(view.actions().save().getValue(Action.ACCELERATOR_KEY)).isEqualTo(control(KeyEvent.VK_S));
        assertThat(view.selectionActions().cut().getValue(Action.ACCELERATOR_KEY)).isNull();
    }

    private static AbstractButton toolbarButton(EditorView target, Action action) {
        return Arrays.stream(target.toolBar().getComponents())
                .filter(AbstractButton.class::isInstance)
                .map(AbstractButton.class::cast)
                .filter(button -> button.getAction() == action)
                .findFirst().orElseThrow();
    }

    private static String withShortcut(String text, KeyStroke stroke) {
        return text + " (" + KeyBindings.describe(stroke) + ")";
    }

    private EditorView iconView() {
        EditorView created = new EditorView(openDocument,
                services.withExtensions(List.of(new ActionIcons(), new PreferencesFeature(), new ShortcutsExtension())),
                NO_WINDOW);
        views.add(created);
        return created;
    }

    @Test
    void toolbarTooltipsEndWithTheCurrentShortcutAndFollowRebindings() {
        EditorView icons = iconView();
        AbstractButton save = toolbarButton(icons, icons.actions().save());
        AbstractButton center = toolbarButton(icons, icons.actions().start());
        assertThat(save.getToolTipText()).isEqualTo(withShortcut("Guardar", control(KeyEvent.VK_S)));
        assertThat(center.getToolTipText()).as("no shortcut, no parentheses").isEqualTo("Iniciar");

        bindings.assign("simulation.start", control(KeyEvent.VK_J));
        assertThat(center.getToolTipText()).isEqualTo(withShortcut("Iniciar", control(KeyEvent.VK_J)));
        bindings.clear("file.save");
        assertThat(save.getToolTipText()).isEqualTo("Guardar");
        bindings.reset("file.save");
        assertThat(save.getToolTipText()).isEqualTo(withShortcut("Guardar", control(KeyEvent.VK_S)));
    }

    @Test
    void theUndoTooltipFollowsItsNameAndADescriptionWinsOverTheName() {
        EditorView icons = iconView();
        AbstractButton undo = toolbarButton(icons, icons.actions().undo());
        assertThat(undo.getToolTipText()).isEqualTo(withShortcut("Deshacer", control(KeyEvent.VK_Z)));
        place(openDocument, StandardComponentTypes.NOT, 0, 0, Map.of());
        String name = (String) icons.actions().undo().getValue(Action.NAME);
        assertThat(name).startsWith("Deshacer ").isNotEqualTo("Deshacer");
        assertThat(undo.getToolTipText()).isEqualTo(withShortcut(name, control(KeyEvent.VK_Z)));

        icons.actions().start().putValue(Action.SHORT_DESCRIPTION, "Lleva el circuito al centro");
        bindings.assign("simulation.start", control(KeyEvent.VK_J));
        assertThat(toolbarButton(icons, icons.actions().start()).getToolTipText())
                .isEqualTo(withShortcut("Lleva el circuito al centro", control(KeyEvent.VK_J)));
    }

    @Test
    void disposingStopsFollowingTheToolbar() {
        EditorView plain = new EditorView(openDocument,
                EditorServices.of(PainterRegistry.standard(), ColorScheme.LIGHT), NO_WINDOW);
        views.add(plain);
        Runnable removal = new ShortcutsExtension().install(plain);
        AbstractButton center = toolbarButton(plain, plain.actions().start());
        removal.run();
        plain.actions().start().putValue(Action.SHORT_DESCRIPTION, "otra cosa");
        assertThat(center.getToolTipText()).as("Swing shows the new description, nothing adds a shortcut")
                .isEqualTo("otra cosa");
        AbstractButton undo = toolbarButton(plain, plain.actions().undo());
        String before = undo.getToolTipText();
        place(openDocument, StandardComponentTypes.NOT, 0, 0, Map.of());
        assertThat(undo.getToolTipText()).isEqualTo(before);
    }
}
