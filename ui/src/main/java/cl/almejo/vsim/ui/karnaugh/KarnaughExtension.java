package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import java.awt.event.ActionEvent;

/**
 * Adds Herramientas ▸ «Circuito desde tabla de verdad…» (and its shortcut id) to every editor view: each click opens a
 * fresh Karnaugh wizard with an empty 3-input table.
 */
public final class KarnaughExtension implements EditorExtension {

    static final String SHORTCUT_ID = "tools.circuitFromTruthTable";

    private final KarnaughWizard.Opener opener;
    private final KarnaughCost cost;

    /**
     * The real dialog. The step-4 cost is {@link PlaceholderCost} until Task 4's {@code CircuitGenerator.cost} is
     * merged: then pass a {@link KarnaughCost} that calls it (mapping {@link WizardOptions} to the generator's options
     * one to one) and delete {@code PlaceholderCost} and its test.
     */
    public KarnaughExtension() {
        this(KarnaughWizard.Opener.dialog(), PlaceholderCost.INSTANCE);
    }

    public KarnaughExtension(KarnaughWizard.Opener opener, KarnaughCost cost) {
        this.opener = opener;
        this.cost = cost;
    }

    @Override
    public Runnable install(EditorView view) {
        Action action = new AbstractAction(Messages.text("action.circuitFromTruthTable")) {
            @Override
            public void actionPerformed(ActionEvent event) {
                opener.open(view, new KarnaughWizard(new KarnaughModel(), cost));
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
