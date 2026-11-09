package cl.almejo.vsim.ui.quickinsert;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.preferences.PreferencesFeature;
import cl.almejo.vsim.ui.shortcuts.KeyBindings;
import cl.almejo.vsim.ui.shortcuts.ShortcutsExtension;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.perform;
import static org.assertj.core.api.Assertions.assertThat;

class ActionRowsTest {

    private OpenDocument document;
    private EditorView view;

    @BeforeEach
    void create() {
        document = OpenDocument.createEmpty(StandardComponentTypes.registry());
        EditorServices services = EditorServices.of(PainterRegistry.standard(), ColorScheme.LIGHT)
                .withExtensions(List.of(new PreferencesFeature(), new ShortcutsExtension()));
        view = new EditorView(document, services, NO_WINDOW);
    }

    @AfterEach
    void close() {
        view.dispose();
        document.close();
    }

    private ActionRow row(String name) {
        return ActionRows.of(view).stream().filter(row -> row.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void anItemInsideASubmenuCarriesItsWholeMenuPath() {
        JMenu export = new JMenu("Exportar");
        export.add(new JMenuItem(Actions.of("Imagen de la ventana…", () -> { })));
        view.menu(MenuId.FILE).add(export);

        ActionRow row = row("Imagen de la ventana…");

        assertThat(row.menuPath()).isEqualTo("Archivo ▸ Exportar");
        assertThat(row.key()).isEqualTo("Archivo ▸ Exportar ▸ Imagen de la ventana…");
    }

    @Test
    void theShortcutIsTheCurrentOneAfterTheUserRebindsIt() {
        assertThat(row("Guardar como…").shortcut()).contains("Ctrl+Shift+S");

        new KeyBindings(view.services().preferences()).assign("file.saveAs",
                KeyStroke.getKeyStroke(KeyEvent.VK_J, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK));

        assertThat(row("Guardar como…").shortcut()).contains("Ctrl+Alt+J");
    }

    @Test
    void aCanvasKeyShowsBesideItsItemThoughTheMenuHasNoAccelerator() {
        assertThat(row("Borrar").shortcut()).contains("Delete");
    }

    @Test
    void aDisabledActionSaysItsDescriptionOrThatItIsNotAvailableNow() {
        Action withReason = Actions.of("Imprimir…", () -> { });
        withReason.putValue(Action.SHORT_DESCRIPTION, "No hay impresoras instaladas");
        withReason.setEnabled(false);
        Action withoutReason = Actions.of("Avanzar al próximo evento", () -> { });
        withoutReason.setEnabled(false);
        view.menu(MenuId.SIMULATION).add(new JMenuItem(withReason));
        view.menu(MenuId.SIMULATION).add(new JMenuItem(withoutReason));

        assertThat(row("Imprimir…").enabled()).isFalse();
        assertThat(row("Imprimir…").disabledReason()).contains("No hay impresoras instaladas");
        assertThat(row("Avanzar al próximo evento").disabledReason()).contains("No disponible ahora");
        assertThat(row("Nuevo").enabled()).isTrue();
        assertThat(row("Nuevo").disabledReason()).isEmpty();
    }

    @Test
    void aCheckItemTellsWhetherItIsOnAndPlainItemsDoNot() {
        JCheckBoxMenuItem guides = new JCheckBoxMenuItem(Actions.of("Mostrar guías", () -> { }));
        guides.setSelected(true);
        view.menu(MenuId.VIEW).add(guides);

        assertThat(row("Mostrar guías").selected()).contains(true);
        assertThat(row("Nuevo").selected()).isEmpty();
    }

    @Test
    void runningACheckRowClicksItsItemSoItsStateFlips() {
        JCheckBoxMenuItem guides = new JCheckBoxMenuItem(Actions.of("Mostrar guías", () -> { }));
        view.menu(MenuId.VIEW).add(guides);

        perform(row("Mostrar guías").action());

        assertThat(guides.isSelected()).isTrue();
    }

    @Test
    void anItemWithoutAnActionIsListedAndRunsItsListeners() {
        JMenu examples = new JMenu("Ejemplos");
        JMenuItem adder = new JMenuItem("Sumador completo");
        int[] opened = {0};
        adder.addActionListener(event -> opened[0]++);
        examples.add(adder);
        view.menu(MenuId.FILE).add(examples);

        perform(row("Sumador completo").action());

        assertThat(opened[0]).isEqualTo(1);
    }

    @Test
    void hiddenItemsAreLeftOutAndAnActionInTwoMenusIsListedOnce() {
        JMenuItem hidden = new JMenuItem(Actions.of("Oculta", () -> { }));
        hidden.setVisible(false);
        view.menu(MenuId.EDIT).add(hidden);

        List<ActionRow> rows = ActionRows.of(view);

        assertThat(rows).extracting(ActionRow::name).doesNotContain("Oculta");
        assertThat(rows).filteredOn(row -> row.name().equals("Cerrar pestaña")).hasSize(1);
    }

    @Test
    void preferencesOffersOneEntryPerTab() {
        assertThat(ActionRows.of(view)).extracting(ActionRow::name)
                .contains("Preferencias…", "Preferencias ▸ General", "Preferencias ▸ Atajos de teclado",
                        "Preferencias ▸ IA");
        assertThat(row("Preferencias ▸ Atajos de teclado").menuPath()).isEqualTo("Archivo");
    }

    @Test
    void wordsMatchTheNameOrThePathInAnyOrderIgnoringCaseAccentsAndEllipsis() {
        List<ActionRow> rows = ActionRows.of(view);

        assertThat(ActionMatcher.filter(rows, "SIMULACION reiniciar")).extracting(ActionRow::name)
                .containsExactly("Reiniciar");
        assertThat(ActionMatcher.filter(rows, "guardar")).extracting(ActionRow::name)
                .startsWith("Guardar", "Guardar como…");
        assertThat(ActionMatcher.filter(rows, "abrir…")).extracting(ActionRow::name).contains("Abrir…");
        assertThat(ActionMatcher.filter(rows, "zzz")).isEmpty();
    }

    @Test
    void rowsWhoseNameHasEveryWordComeBeforeRowsFoundByTheirMenu() {
        List<ActionRow> rows = ActionRows.of(view);

        List<ActionRow> found = ActionMatcher.filter(rows, "ventana");

        assertThat(found.getFirst().name()).isEqualTo("Cerrar ventana");
        assertThat(found).extracting(ActionRow::menuPath).last().isEqualTo("Ventana");
    }

    @Test
    void aLongWordFindsItsOtherFormsSoPresentarFindsBothPresentationActions() {
        view.menu(MenuId.VIEW).add(new JCheckBoxMenuItem(Actions.of("Presentar lo que hace la IA", () -> { })));
        view.menu(MenuId.VIEW).add(new JCheckBoxMenuItem(
                Actions.of("Modo presentación (pantalla completa)", () -> { })));

        assertThat(ActionMatcher.filter(ActionRows.of(view), "presentar")).extracting(ActionRow::name)
                .containsExactly("Presentar lo que hace la IA", "Modo presentación (pantalla completa)");
    }

    @Test
    void recentActionsKeepTheLastFiveMostRecentFirstAndTellTheirListeners() {
        RecentActions recents = new RecentActions();
        int[] changes = {0};
        Runnable remover = recents.addListener(() -> changes[0]++);
        for (String key : List.of("a", "b", "c", "d", "e", "f", "c")) {
            recents.used(key);
        }

        assertThat(recents.keys()).containsExactly("c", "f", "e", "d", "b");
        assertThat(changes[0]).isEqualTo(7);
        recents.used("c");
        assertThat(changes[0]).isEqualTo(7);
        remover.run();
        recents.used("a");
        assertThat(changes[0]).isEqualTo(7);
    }

    @Test
    void theRecentRowsAreThoseOfThisViewInTheOrderTheyWereUsed() {
        RecentActions recents = new RecentActions();
        recents.used("Archivo ▸ Abrir…");
        recents.used("No existe ▸ Nada");
        recents.used("Archivo ▸ Nuevo");

        assertThat(recents.rowsIn(ActionRows.of(view))).extracting(ActionRow::name)
                .containsExactly("Nuevo", "Abrir…");
        assertThat(Optional.of(recents.keys().size())).contains(3);
    }
}
