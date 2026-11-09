package cl.almejo.vsim.ui.quickinsert;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.shortcuts.KeyBindings;
import cl.almejo.vsim.ui.tools.CanvasMouseEvent;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import cl.almejo.vsim.ui.window.panes.CanvasPanes.Orientation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.perform;
import static org.assertj.core.api.Assertions.assertThat;

/** The two tabs of the command palette, its keys, its actions and its notices, without showing a window. */
class CommandPaletteTest {

    private final List<Runnable> later = new ArrayList<>();
    private final List<String> notices = new ArrayList<>();
    private final RecentActions recents = new RecentActions();
    private OpenDocument document;
    private EditorView view;
    private QuickInsertFeatures features;
    private QuickInsertPanel shown;
    private int opened;
    private int closed;
    private int counted;

    @BeforeEach
    void create() {
        document = OpenDocument.createEmpty(StandardComponentTypes.registry());
        view = new EditorView(document, PainterRegistry.standard(), ColorScheme.LIGHT, NO_WINDOW);
        view.canvas().setSize(800, 600);
        features = newFeatures(view);
        view.menu(MenuId.TOOLS).add(new JMenuItem(Actions.of("Contar", () -> counted++)));
    }

    private QuickInsertFeatures newFeatures(EditorView target) {
        return new QuickInsertFeatures(target, recents, (canvas, point, panel) -> {
            shown = panel;
            opened++;
            return () -> closed++;
        }, later::add, notices::add);
    }

    @AfterEach
    void close() {
        features.dispose();
        view.dispose();
        document.close();
    }

    private CanvasMouseEvent doubleClickAt(int x, int y) {
        return new CanvasMouseEvent(new Point2D.Double(x, y), new GridPoint(x, y),
                view.canvas().viewport().gridToScreen(x, y), CanvasMouseEvent.PRIMARY, false, false, 2);
    }

    private void findAction() {
        perform(view.shortcutActions().get("help.findAction"));
    }

    private void insert() {
        perform(view.shortcutActions().get("edit.quickInsert"));
    }

    private void press(JTextField field, KeyStroke stroke) {
        Object name = field.getInputMap().get(stroke);
        assertThat(name).as("a binding for " + stroke).isNotNull();
        field.getActionMap().get(name).actionPerformed(new ActionEvent(field, ActionEvent.ACTION_PERFORMED, ""));
    }

    private void flushLater() {
        List<Runnable> pending = List.copyOf(later);
        later.clear();
        pending.forEach(Runnable::run);
    }

    @Test
    void aDoubleClickAndInsertOpenOnComponentesAndTheShortcutAndTheHelpItemOnAcciones() {
        view.canvas().tool().mousePressed(view.canvas(), doubleClickAt(10, 20));
        assertThat(shown.tab()).isEqualTo(PaletteTab.COMPONENTS);
        insert();
        assertThat(shown.tab()).isEqualTo(PaletteTab.COMPONENTS);
        findAction();
        assertThat(shown.tab()).isEqualTo(PaletteTab.ACTIONS);
        shown.cancel();

        JMenuItem help = (JMenuItem) view.menu(MenuId.HELP).getMenuComponent(0);
        assertThat(help.getText()).isEqualTo("Buscar una acción…");
        help.doClick(0);

        assertThat(shown.tab()).isEqualTo(PaletteTab.ACTIONS);
        assertThat(opened).isEqualTo(3);
    }

    @Test
    void tabSwitchesTabsKeepingTheTextAndTheFieldKeepsTheTabKey() {
        findAction();
        JTextField field = shown.searchField();
        shown.setQuery("xor");

        press(field, KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0));

        assertThat(shown.tab()).isEqualTo(PaletteTab.COMPONENTS);
        assertThat(field.getText()).isEqualTo("xor");
        assertThat(shown.results()).extracting(entry -> entry.id()).contains("xor");
        assertThat(field.getFocusTraversalKeysEnabled()).isFalse();
        press(field, KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK));
        assertThat(shown.tab()).isEqualTo(PaletteTab.ACTIONS);
        assertThat(field.getText()).isEqualTo("xor");
    }

    @Test
    void whenThisTabFindsNothingTheFooterCountsWhatTheOtherTabFinds() {
        insert();
        shown.setQuery("xor");
        int components = shown.results().size();
        shown.switchTab();

        assertThat(shown.actionResults()).isEmpty();
        assertThat(shown.footer()).isEqualTo(Messages.format("commandPalette.otherTab.components", components, "xor"));
        shown.setQuery("contar");
        shown.switchTab();
        assertThat(shown.results()).isEmpty();
        assertThat(shown.footer()).isEqualTo(Messages.format("commandPalette.otherTab.actions", 1, "contar"));
    }

    @Test
    void choosingAnActionClosesThePaletteAndThenRunsItOnceAndSaysItIsDone() {
        findAction();
        shown.setQuery("contar");
        assertThat(shown.actionResults()).extracting(ActionRow::name).containsExactly("Contar");

        shown.acceptSelection();

        assertThat(closed).isEqualTo(1);
        assertThat(counted).as("not before the palette is gone").isZero();
        flushLater();
        assertThat(counted).isEqualTo(1);
        assertThat(notices).containsExactly("Hecho: Contar");
        assertThat(recents.keys()).containsExactly("Herramientas ▸ Contar");
    }

    @Test
    void aDisabledActionIsShownButChoosingItDoesNothing() {
        Action disabled = Actions.of("Imprimir…", () -> counted++);
        disabled.setEnabled(false);
        view.menu(MenuId.FILE).add(new JMenuItem(disabled));
        findAction();
        shown.setQuery("imprimir");
        assertThat(shown.actionResults()).singleElement().satisfies(row -> {
            assertThat(row.enabled()).isFalse();
            assertThat(row.disabledReason()).contains("No disponible ahora");
        });

        shown.acceptSelection();
        flushLater();

        assertThat(closed).isZero();
        assertThat(counted).isZero();
        assertThat(notices).isEmpty();
    }

    @Test
    void withAnEmptySearchActionesShowsTheLastUsedOnesUnderTheirHeading() {
        findAction();
        assertThat(shown.actionResults()).isEmpty();
        assertThat(shown.footer()).isEqualTo(Messages.text("commandPalette.actions.empty"));
        shown.setQuery("contar");
        shown.acceptSelection();
        flushLater();

        findAction();

        assertThat(shown.groupTitles()).containsExactly("Usadas hace poco");
        assertThat(shown.actionResults()).extracting(ActionRow::name).containsExactly("Contar");
    }

    @Test
    void thePalettesOwnItemIsNotAnAction() {
        findAction();
        shown.setQuery("buscar una accion");

        assertThat(shown.actionResults()).isEmpty();
    }

    @Test
    void theShortcutInsideThePaletteGoesToAccionesAndThenClosesIt() {
        insert();
        JTextField field = shown.searchField();
        KeyStroke findAction = KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK);

        press(field, findAction);
        assertThat(shown.tab()).isEqualTo(PaletteTab.ACTIONS);
        assertThat(closed).isZero();
        press(field, findAction);
        assertThat(closed).isEqualTo(1);
    }

    @Test
    void theShortcutOfTheViewTogglesAnOpenPaletteTheSameWay() {
        insert();
        findAction();
        assertThat(shown.tab()).isEqualTo(PaletteTab.ACTIONS);
        assertThat(opened).isEqualTo(1);
        findAction();
        assertThat(closed).isEqualTo(1);
    }

    @Test
    void aReboundShortcutIsTheOneTheTabsShowAndThePaletteAnswers() {
        KeyStroke rebound = KeyStroke.getKeyStroke(KeyEvent.VK_J, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK);
        new KeyBindings(view.services().preferences()).assign("help.findAction", rebound);
        insert();

        assertThat(shown.tabTitles()).containsExactly("Componentes · Insert", "Acciones · Ctrl+Alt+J");
        press(shown.searchField(), rebound);
        assertThat(shown.tab()).isEqualTo(PaletteTab.ACTIONS);
        assertThat(shown.searchField().getInputMap()
                .get(KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK))).isNull();
    }

    @Test
    void aDoubleClickPlacementSaysWhereItWentAndInsertSaysNothing() {
        view.canvas().tool().mousePressed(view.canvas(), doubleClickAt(10, 20));
        shown.setQuery("xor");
        shown.acceptSelection();

        assertThat(document.document().components()).extracting(Component::typeId).containsExactly("xor");
        assertThat(notices).containsExactly("XOR colocado donde hiciste doble clic");
        insert();
        shown.setQuery("xor");
        shown.acceptSelection();
        assertThat(notices).hasSize(1);
    }

    @Test
    void anActionRunsOnTheViewThatOpenedThePalette() {
        EditorView other = new EditorView(document, PainterRegistry.standard(), ColorScheme.LIGHT, NO_WINDOW);
        int[] otherCount = {0};
        other.menu(MenuId.TOOLS).add(new JMenuItem(Actions.of("Contar", () -> otherCount[0]++)));
        QuickInsertFeatures otherFeatures = newFeatures(other);
        try {
            perform(other.shortcutActions().get("help.findAction"));
            shown.setQuery("contar");
            shown.acceptSelection();
            flushLater();

            assertThat(otherCount[0]).isEqualTo(1);
            assertThat(counted).isZero();
        } finally {
            otherFeatures.dispose();
            other.dispose();
        }
    }

    @Test
    void disposingTakesTheHelpItemAndTheShortcutBack() {
        JMenu help = view.menu(MenuId.HELP);
        int items = help.getMenuComponentCount();

        features.dispose();

        assertThat(help.getMenuComponentCount()).isEqualTo(items - 2);
        assertThat(view.shortcutActions()).doesNotContainKeys("help.findAction", "edit.quickInsert");
    }

    @Test
    void theEmptyCanvasNamesTheCurrentKeyOfFindAnActionOnEveryPane() {
        assertThat(view.canvas().emptyHintLines().get(2)).contains("Ctrl+K: todas las acciones");
        new KeyBindings(view.services().preferences()).assign("help.findAction",
                KeyStroke.getKeyStroke(KeyEvent.VK_J, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK));
        view.canvasPanes().split(Orientation.VERTICAL, 0.5);

        assertThat(view.panes()).allSatisfy(pane ->
                assertThat(pane.emptyHintLines().get(2)).contains("Ctrl+Alt+J: todas las acciones"));
        new KeyBindings(view.services().preferences()).clear("help.findAction");
        assertThat(view.canvas().emptyHintLines().get(2)).endsWith("· Ayuda ▸ Buscar una acción…");
    }

    @Test
    void disposingLeavesTheEmptyCanvasWithoutTheKey() {
        features.dispose();

        assertThat(view.canvas().emptyHintLines().get(2)).doesNotContain("Ctrl+K");
    }
}
