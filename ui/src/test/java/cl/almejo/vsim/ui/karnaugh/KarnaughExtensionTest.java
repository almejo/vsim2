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
import cl.almejo.vsim.ui.window.ImportMenu;
import cl.almejo.vsim.ui.window.MenuId;
import cl.almejo.vsim.ui.window.WindowCommands;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.Action;
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
    private final List<ExpressionsPanel> expressions = new ArrayList<>();
    private OpenDocument openDocument;
    private EditorView view;
    private Optional<String> clipboard = Optional.empty();

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        KarnaughExtension extension = new KarnaughExtension((parent, wizard) -> {
            parents.add(parent);
            opened.add(wizard);
        }, (parent, panel) -> imports.add(panel), (parent, panel) -> {
            parents.add(parent);
            expressions.add(panel);
        }, new TableSources(parent -> Optional.empty(), () -> clipboard), KarnaughCost.GENERATOR,
                view -> new FakeCreationTarget(), Background.inline());
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
        assertThat(tools.getItemCount()).isEqualTo(3);
        assertThat(tools.getItem(0).getText()).isEqualTo("Circuito desde tabla de verdad…");
        assertThat(view.shortcutActions().get("tools.circuitFromTruthTable")).isSameAs(tools.getItem(0).getAction());

        tools.getItem(0).doClick();
        tools.getItem(0).doClick();

        assertThat(opened).hasSize(2);
        assertThat(opened.get(0)).isNotSameAs(opened.get(1));
        assertThat(opened.get(0).step()).isEqualTo(KarnaughWizard.Step.TABLE);
        assertThat(opened.get(0).mode()).isEqualTo(KarnaughWizard.Mode.SOLUTION);
        assertThat(opened.get(0).model().practice().practising()).isFalse();
        assertThat(opened.get(0).dialogTitle()).isEqualTo("Circuito desde tabla de verdad");
        assertThat(parents.getFirst()).isSameAs(view);
    }

    @Test
    void thePracticeItemComesRightAfterTheWizardAndOpensAFreshWizardInPracticeMode() {
        JMenu tools = view.menu(MenuId.TOOLS);
        assertThat(tools.getItem(1).getText()).isEqualTo("Practicar Karnaugh…");
        assertThat(view.shortcutActions().get("tools.karnaughPractice")).isSameAs(tools.getItem(1).getAction());

        tools.getItem(1).doClick();
        tools.getItem(1).doClick();

        assertThat(opened).hasSize(2);
        assertThat(opened.get(0)).isNotSameAs(opened.get(1));
        assertThat(opened.get(0).model()).isNotSameAs(opened.get(1).model());
        assertThat(opened.get(0).step()).isEqualTo(KarnaughWizard.Step.TABLE);
        assertThat(opened.get(0).mode()).isEqualTo(KarnaughWizard.Mode.PRACTICE);
        assertThat(opened.get(0).model().practice().practising()).isTrue();
        assertThat(opened.get(0).dialogTitle()).isEqualTo("Práctica de Karnaugh");
        assertThat(parents).containsExactly(view, view);
    }

    @Test
    void theToolsMenuOpensAFreshExpressionsDialogRightAfterTheWizard() {
        JMenu tools = view.menu(MenuId.TOOLS);
        assertThat(tools.getItem(2).getText()).isEqualTo("Circuito desde expresiones…");
        assertThat(view.shortcutActions().get("tools.circuitFromExpressions"))
                .isSameAs(tools.getItem(2).getAction());

        tools.getItem(2).doClick();
        tools.getItem(2).doClick();

        assertThat(expressions).hasSize(2);
        assertThat(expressions.get(0)).isNotSameAs(expressions.get(1));
        assertThat(expressions.get(0).lineField(0).getText()).isEqualTo("F = ");
        assertThat(parents).containsExactly(view, view);
        assertThat(opened).isEmpty();
    }

    @Test
    void seeInKarnaughOpensTheWizardOnTheGroupsWithTheTable() {
        view.menu(MenuId.TOOLS).getItem(2).doClick();
        ExpressionsPanel panel = expressions.getFirst();
        panel.titleField().setText("Mayoría");
        panel.lineField(0).setText("M = A B + A C + B C");

        panel.wizardLink().doClick();

        assertThat(opened).singleElement().satisfies(wizard -> {
            assertThat(wizard.step()).isEqualTo(KarnaughWizard.Step.GROUPS);
            assertThat(wizard.mode()).isEqualTo(KarnaughWizard.Mode.SOLUTION);
            assertThat(wizard.model().title()).isEqualTo("Mayoría");
            assertThat(wizard.model().inputNames()).containsExactly("A", "B", "C");
            assertThat(wizard.model().outputNames()).containsExactly("M");
            assertThat(wizard.model().spec()).isEqualTo(panel.model().spec().orElseThrow());
            assertThat(wizard.closeAction().getValue(Action.NAME)).isEqualTo("Volver a las expresiones");
            assertThat(wizard.tablePanel().titleField().isEditable()).isFalse();
        });
        assertThat(parents).as("the wizard opens over the expressions dialog").containsExactly(view, panel);
    }

    @Test
    void creatingFromTheWizardOverTheExpressionsClosesBoth() {
        view.menu(MenuId.TOOLS).getItem(2).doClick();
        ExpressionsPanel panel = expressions.getFirst();
        int[] expressionsClosed = {0};
        int[] wizardClosed = {0};
        panel.setOnClose(() -> expressionsClosed[0]++);
        panel.lineField(0).setText("M = A B + A C + B C");
        panel.wizardLink().doClick();
        KarnaughWizard wizard = opened.getFirst();
        wizard.setOnClose(() -> wizardClosed[0]++);

        wizard.requestClose();
        assertThat(wizardClosed[0]).isEqualTo(1);
        assertThat(expressionsClosed[0]).as("going back keeps the expressions").isZero();

        wizard.goTo(KarnaughWizard.Step.CREATE);
        wizard.createButton().doClick();
        assertThat(wizardClosed[0]).isEqualTo(2);
        assertThat(expressionsClosed[0]).as("the work is done").isEqualTo(1);
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
    void anImportedTableOpensInTheWizardOnTheMapOverTheView() {
        clipboard = Optional.of("A\tB\tC\tY\n0\t0\t0\t1\n1\t1\t1\t0\n0\t1\t0\t1\n");
        importMenu(view.menu(MenuId.FILE)).getItem(0).doClick();
        TableImportPanel panel = imports.getFirst();
        int[] importClosed = {0};
        panel.setOnClose(() -> importClosed[0]++);
        panel.pasteButton().doClick();
        panel.nextButton().doClick();
        panel.titleField().setText("Mi tabla");
        panel.gateButton(WizardOptions.GateChoice.NOR_ONLY).doClick();

        panel.wizardButton().doClick();

        assertThat(importClosed[0]).isEqualTo(1);
        assertThat(opened).singleElement().satisfies(wizard -> {
            assertThat(wizard.step()).isEqualTo(KarnaughWizard.Step.MAP);
            assertThat(wizard.mode()).isEqualTo(KarnaughWizard.Mode.SOLUTION);
            assertThat(wizard.model().title()).isEqualTo("Mi tabla");
            assertThat(wizard.model().inputNames()).containsExactly("A", "B", "C");
            assertThat(wizard.model().outputNames()).containsExactly("Y");
            assertThat(wizard.model().spec()).isEqualTo(panel.model().spec().orElseThrow());
            assertThat(wizard.model().options().gates()).isEqualTo(WizardOptions.GateChoice.NOR_ONLY);
            assertThat(wizard.tablePanel().titleField().isEditable()).as("the normal, editable wizard").isTrue();
            assertThat(wizard.edited()).as("closing it untouched does not ask").isFalse();
        });
        assertThat(parents).as("the wizard opens over the view: the import dialog is gone").containsExactly(view);
    }

    @Test
    void theTruthTableWindowCanOpenAWizardOnItsTable() {
        assertThat(view.tableWizard()).isPresent();
        view.tableWizard().orElseThrow().accept(KarnaughModelTest.bcd());
        assertThat(opened).singleElement().satisfies(wizard -> {
            assertThat(wizard.model().inputNames()).containsExactly("A", "B", "C", "D");
            assertThat(wizard.model().outputNames()).containsExactly("P", "M");
            assertThat(wizard.step()).isEqualTo(KarnaughWizard.Step.TABLE);
            assertThat(wizard.mode()).isEqualTo(KarnaughWizard.Mode.SOLUTION);
        });
        assertThat(parents).containsExactly(view);
    }

    @Test
    void disposingTheViewTakesTheItemsAndTheShortcutsBack() {
        JMenu tools = view.menu(MenuId.TOOLS);
        JMenu file = view.menu(MenuId.FILE);
        int fileItems = file.getMenuComponentCount();
        view.dispose();
        assertThat(tools.getItemCount()).isZero();
        assertThat(file.getMenuComponentCount()).as("the submenu and its separator").isEqualTo(fileItems - 2);
        assertThat(ImportMenu.find(file)).isEmpty();
        assertThat(view.shortcutActions()).doesNotContainKeys("tools.circuitFromTruthTable",
                "tools.karnaughPractice", "tools.circuitFromExpressions", "file.importTruthTable");
        assertThat(view.tableWizard()).isEmpty();
    }

    private static JMenu importMenu(JMenu file) {
        return ImportMenu.find(file).orElseThrow(() -> new AssertionError("no Importar submenu"));
    }
}
