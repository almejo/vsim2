package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.library.LibraryService;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import java.awt.event.ActionEvent;
import java.util.function.Function;

/**
 * Adds Herramientas ▸ «Circuito desde tabla de verdad…» (and its shortcut id) to every editor view: each click opens a
 * fresh Karnaugh wizard with an empty 3-input table, whose «Crear» puts the circuit in a new window or over this view.
 */
public final class KarnaughExtension implements EditorExtension {

    static final String SHORTCUT_ID = "tools.circuitFromTruthTable";

    private final KarnaughWizard.Opener opener;
    private final KarnaughCost cost;
    private final Function<EditorView, CreationTarget> targets;
    private final Background background;

    /** The real dialog, the generator's cost, the library of {@code service}, circuits built off the EDT. */
    public KarnaughExtension(LibraryService service) {
        this(KarnaughWizard.Opener.dialog(), KarnaughCost.GENERATOR, view -> new ViewCreationTarget(view, service),
                Background.application());
    }

    /**
     * @param targets    where a view's wizard creates circuits
     * @param background where circuits are built
     */
    public KarnaughExtension(KarnaughWizard.Opener opener, KarnaughCost cost,
                             Function<EditorView, CreationTarget> targets, Background background) {
        this.opener = opener;
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
        return () -> {
            shortcutRemover.run();
            tools.remove(item);
        };
    }
}
