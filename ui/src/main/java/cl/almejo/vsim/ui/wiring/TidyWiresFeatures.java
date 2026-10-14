package cl.almejo.vsim.ui.wiring;

import cl.almejo.vsim.core.command.TidyWiresCommand;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.wiring.TidyOptions;
import cl.almejo.vsim.core.wiring.TidyPlan;
import cl.almejo.vsim.core.wiring.WireTidy;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JToolBar;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * The wire tidying of one editor view: two items at the end of Editar (after a separator), their shortcut
 * registrations and the notice. {@link #dispose()} takes all of it back.
 */
public final class TidyWiresFeatures {

    private final EditorView view;
    private final TidyChooser chooser;
    private final Action tidy;
    private final Action tidyWithOptions;
    private final JMenu editMenu;
    private final JPopupMenu.Separator separator = new JPopupMenu.Separator();
    private final JMenuItem tidyItem;
    private final JMenuItem optionsItem;
    private final JToolBar toolBar;
    private final JButton toolBarButton;
    private final Runnable tidyShortcutRemover;
    private final Runnable optionsShortcutRemover;
    private boolean disposed;

    public TidyWiresFeatures(EditorView view, TidyChooser chooser) {
        this.view = view;
        this.chooser = chooser;
        this.tidy = new AbstractAction(Messages.text("action.tidyWires")) {
            @Override
            public void actionPerformed(ActionEvent event) {
                tidyNow();
            }
        };
        this.tidyWithOptions = new AbstractAction(Messages.text("action.tidyWiresOptions")) {
            @Override
            public void actionPerformed(ActionEvent event) {
                tidyWithDialog();
            }
        };
        // 16 px in the menu, 24 px in the toolbar; ActionIcons (installed after this extension) makes the button
        // icon-only with the action name as tooltip, and the shortcuts extension appends the shortcut
        Icons.install(tidy, TidyIcons.TIDY_WIRES);
        this.editMenu = view.menu(MenuId.EDIT);
        this.tidyItem = new JMenuItem(tidy);
        this.optionsItem = new JMenuItem(tidyWithOptions);
        editMenu.add(separator);
        editMenu.add(tidyItem);
        editMenu.add(optionsItem);
        this.toolBar = view.toolBar();
        this.toolBarButton = new JButton(tidy);
        toolBar.add(toolBarButton, indexAfterRedo());
        this.tidyShortcutRemover = view.registerShortcutAction("edit.tidyWires", tidy);
        this.optionsShortcutRemover = view.registerShortcutAction("edit.tidyWiresOptions", tidyWithOptions);
    }

    /** Right after the «Rehacer» button (the end of the toolbar if it is not there). */
    private int indexAfterRedo() {
        Component[] components = toolBar.getComponents();
        for (int index = 0; index < components.length; index++) {
            if (components[index] instanceof AbstractButton button && button.getAction() == view.actions().redo()) {
                return index + 1;
            }
        }
        return components.length;
    }

    public Action tidyAction() {
        return tidy;
    }

    public Action tidyWithOptionsAction() {
        return tidyWithOptions;
    }

    /** The wires of the selection, or none (then the whole circuit is tidied). */
    private Set<ElementId> selectedWires() {
        Set<ElementId> wires = new LinkedHashSet<>();
        for (ElementId id : view.canvas().selection().ids()) {
            if (view.openDocument().document().wire(id).isPresent()) {
                wires.add(id);
            }
        }
        return wires;
    }

    private Set<ElementId> allWires() {
        Set<ElementId> wires = new LinkedHashSet<>();
        for (Wire wire : view.openDocument().document().wires()) {
            wires.add(wire.id());
        }
        return wires;
    }

    private TidyPlan plan(TidyOptions options, boolean selectionOnly) {
        OpenDocument openDocument = view.openDocument();
        Set<ElementId> scope = selectionOnly ? selectedWires() : allWires();
        return WireTidy.plan(openDocument.document(), openDocument.registry(), scope, options);
    }

    /** Applies the saved options at once, on the selection's wires if there are any, else on everything. */
    void tidyNow() {
        if (disposed) {
            return;
        }
        run(view.services().preferences().tidyOptions(), !selectedWires().isEmpty());
    }

    void tidyWithDialog() {
        if (disposed) {
            return;
        }
        Optional<TidyChoice> choice = chooser.choose(view, !selectedWires().isEmpty(),
                view.services().preferences().tidyOptions(), this::plan);
        if (choice.isEmpty()) {
            return;
        }
        view.services().preferences().setTidyOptions(choice.get().options());
        run(choice.get().options(), choice.get().selectionOnly());
    }

    private void run(TidyOptions options, boolean selectionOnly) {
        TidyPlan plan = plan(options, selectionOnly);
        if (!plan.isEmpty()) {
            view.openDocument().execute(new TidyWiresCommand(plan));
        }
        view.statusBar().showNotice(TidySummary.notice(plan));
    }

    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        tidyShortcutRemover.run();
        optionsShortcutRemover.run();
        toolBar.remove(toolBarButton);
        editMenu.remove(tidyItem);
        editMenu.remove(optionsItem);
        editMenu.remove(separator);
    }
}
