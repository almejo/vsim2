package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.library.LibraryService;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.ImportMenu;
import cl.almejo.vsim.ui.window.MenuId;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import java.awt.event.ActionEvent;
import java.util.function.Function;

/**
 * Adds Herramientas ▸ «Circuito desde tabla de verdad…» and «Circuito desde expresiones…», and Archivo ▸ Importar ▸
 * «Tabla de verdad desde Excel/CSV…» (and their shortcut ids) to every editor view. Each click opens a fresh Karnaugh
 * wizard with an empty 3-input table, a fresh expressions dialog or a fresh import dialog; their «Crear» puts the
 * circuit in a new window or over this view. The view's truth-table windows ({@link EditorView#setTableWizard}) and
 * the expressions dialog's «Ver en Karnaugh…» can also open a wizard on their table.
 */
public final class KarnaughExtension implements EditorExtension {

    static final String SHORTCUT_ID = "tools.circuitFromTruthTable";
    static final String IMPORT_SHORTCUT_ID = "file.importTruthTable";
    static final String EXPRESSIONS_SHORTCUT_ID = "tools.circuitFromExpressions";

    private final KarnaughWizard.Opener opener;
    private final TableImportPanel.Opener importOpener;
    private final ExpressionsPanel.Opener expressionsOpener;
    private final TableSources sources;
    private final KarnaughCost cost;
    private final Function<EditorView, CreationTarget> targets;
    private final Background background;

    /** The real dialog, the generator's cost, the library of {@code service}, circuits built off the EDT. */
    public KarnaughExtension(LibraryService service) {
        this(KarnaughWizard.Opener.dialog(), TableImportPanel.Opener.dialog(), ExpressionsPanel.Opener.dialog(),
                TableSources.system(), KarnaughCost.GENERATOR, view -> new ViewCreationTarget(view, service),
                Background.application());
    }

    /**
     * @param sources    where the import dialog reads tables
     * @param targets    where a view's wizard and import dialog create circuits
     * @param background where tables are read and circuits are built
     */
    public KarnaughExtension(KarnaughWizard.Opener opener, TableImportPanel.Opener importOpener,
                             ExpressionsPanel.Opener expressionsOpener, TableSources sources, KarnaughCost cost,
                             Function<EditorView, CreationTarget> targets, Background background) {
        this.opener = opener;
        this.importOpener = importOpener;
        this.expressionsOpener = expressionsOpener;
        this.sources = sources;
        this.cost = cost;
        this.targets = targets;
        this.background = background;
    }

    @Override
    public Runnable install(EditorView view) {
        CreationTarget target = targets.apply(view);
        Action action = new AbstractAction(Messages.text("action.circuitFromTruthTable")) {
            @Override
            public void actionPerformed(ActionEvent event) {
                opener.open(view, new KarnaughWizard(new KarnaughModel(), cost, target, background));
            }
        };
        JMenu tools = view.menu(MenuId.TOOLS);
        JMenuItem item = new JMenuItem(action);
        tools.add(item);
        Runnable shortcutRemover = view.registerShortcutAction(SHORTCUT_ID, action);
        // The truth-table window's «Abrir en el asistente de Karnaugh» opens a wizard on its table.
        view.setTableWizard(spec -> {
            KarnaughModel model = new KarnaughModel();
            model.load(spec);
            opener.open(view, new KarnaughWizard(model, cost, target, background));
        });

        // «Ver en Karnaugh…» of the expressions dialog: a wizard over it on the expressions' table (read-only there),
        // showing the groups (step 3); going back leaves the expressions as they were, creating closes both.
        ExpressionsPanel.WizardOpener openInWizard = (owner, spec) -> {
            KarnaughModel model = new KarnaughModel();
            model.load(spec);
            KarnaughWizard wizard = new KarnaughWizard(model, cost, target, background);
            wizard.showTableFromExpressions(owner::closeAfterCreating);
            wizard.goTo(KarnaughWizard.Step.GROUPS);
            opener.open(owner, wizard);
        };
        Action expressionsAction = new AbstractAction(Messages.text("action.circuitFromExpressions")) {
            @Override
            public void actionPerformed(ActionEvent event) {
                expressionsOpener.open(view, new ExpressionsPanel(target, background,
                        view.services().preferences(), cost, openInWizard));
            }
        };
        JMenuItem expressionsItem = new JMenuItem(expressionsAction);
        tools.add(expressionsItem);
        Runnable expressionsShortcutRemover = view.registerShortcutAction(EXPRESSIONS_SHORTCUT_ID,
                expressionsAction);

        // «Abrir en el asistente» of the import dialog: that dialog is closed, the normal (editable) wizard opens over
        // the view on the map, with the table and the dialog's options.
        TableImportPanel.WizardOpener importInWizard = (spec, options) -> {
            KarnaughModel model = new KarnaughModel();
            model.load(spec);
            model.setOptions(options);
            KarnaughWizard wizard = new KarnaughWizard(model, cost, target, background);
            wizard.goTo(KarnaughWizard.Step.MAP);
            opener.open(view, wizard);
        };
        Action importAction = new AbstractAction(Messages.text("action.importTruthTable")) {
            @Override
            public void actionPerformed(ActionEvent event) {
                importOpener.open(view, new TableImportPanel(sources, target, background, importInWizard));
            }
        };
        Runnable importItemRemover = ImportMenu.add(view.menu(MenuId.FILE), new JMenuItem(importAction));
        Runnable importShortcutRemover = view.registerShortcutAction(IMPORT_SHORTCUT_ID, importAction);
        return () -> {
            view.setTableWizard(null);
            importShortcutRemover.run();
            importItemRemover.run();
            expressionsShortcutRemover.run();
            tools.remove(expressionsItem);
            shortcutRemover.run();
            tools.remove(item);
        };
    }
}
