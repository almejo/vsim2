package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import cl.almejo.vsim.ui.window.WindowCommands;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JMenu;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class KarnaughExtensionTest {

    private static final WindowCommands NO_WINDOW = new WindowCommands() {
        @Override public void newDocument() { }
        @Override public void open() { }
        @Override public void save() { }
        @Override public void saveAs() { }
        @Override public void closeWindow() { }
        @Override public void exit() { }
    };

    private final List<KarnaughWizard> opened = new ArrayList<>();
    private final List<Component> parents = new ArrayList<>();
    private final List<TableImportPanel> imports = new ArrayList<>();
    private OpenDocument openDocument;
    private EditorView view;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        KarnaughExtension extension = new KarnaughExtension((parent, wizard) -> {
            parents.add(parent);
            opened.add(wizard);
        }, (parent, panel) -> imports.add(panel), new TableSources(parent -> Optional.empty(), Optional::empty),
                KarnaughCost.GENERATOR, view -> new FakeCreationTarget(), Background.inline());
        EditorServices services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                AppPreferences.inMemory(), new ComponentLibrary(), List.of(extension));
        view = new EditorView(openDocument, services, NO_WINDOW);
    }

    @AfterEach
    void close() {
        view.dispose();
        openDocument.close();
    }

    @Test
    void theToolsMenuItemOpensAFreshWizardOnTheTable() {
        JMenu tools = view.menu(MenuId.TOOLS);
        assertThat(tools.getItemCount()).isEqualTo(1);
        assertThat(tools.getItem(0).getText()).isEqualTo("Circuito desde tabla de verdad…");
        assertThat(view.shortcutActions().get("tools.circuitFromTruthTable")).isSameAs(tools.getItem(0).getAction());

        tools.getItem(0).doClick();
        tools.getItem(0).doClick();

        assertThat(opened).hasSize(2);
        assertThat(opened.get(0)).isNotSameAs(opened.get(1));
        assertThat(opened.get(0).step()).isEqualTo(KarnaughWizard.Step.TABLE);
        assertThat(parents.getFirst()).isSameAs(view);
    }

    @Test
    void theFileMenuImportsATruthTableFromItsImportSubmenu() {
        JMenu file = view.menu(MenuId.FILE);
        JMenu importMenu = importMenu(file);
        assertThat(importMenu.getText()).isEqualTo("Importar");
        assertThat(importMenu.getItemCount()).isEqualTo(1);
        assertThat(importMenu.getItem(0).getText()).isEqualTo("Tabla de verdad desde Excel/CSV…");
        assertThat(view.shortcutActions().get("file.importTruthTable")).isSameAs(importMenu.getItem(0).getAction());

        importMenu.getItem(0).doClick();
        importMenu.getItem(0).doClick();

        assertThat(imports).hasSize(2);
        assertThat(imports.get(0)).isNotSameAs(imports.get(1));
        assertThat(imports.get(0).model().hasTable()).isFalse();
    }

    @Test
    void disposingTheViewTakesTheItemsAndTheShortcutsBack() {
        JMenu tools = view.menu(MenuId.TOOLS);
        JMenu file = view.menu(MenuId.FILE);
        int fileItems = file.getMenuComponentCount();
        view.dispose();
        assertThat(tools.getItemCount()).isZero();
        assertThat(file.getMenuComponentCount()).isEqualTo(fileItems - 1);
        assertThat(view.shortcutActions()).doesNotContainKeys("tools.circuitFromTruthTable", "file.importTruthTable");
    }

    private static JMenu importMenu(JMenu file) {
        for (java.awt.Component child : file.getMenuComponents()) {
            if (child instanceof JMenu menu && menu.getText().equals("Importar")) {
                return menu;
            }
        }
        throw new AssertionError("no Importar submenu");
    }
}
