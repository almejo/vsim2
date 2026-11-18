package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.command.CreateMachineCommand;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.actions.ContextMenuContributor;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.library.PlaceDefinitionTool;
import cl.almejo.vsim.ui.panels.PalettePanel;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;

import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Where «Duplicar como otra máquina» is offered ({@link MachineDuplication}): in Editar, next to «Crear componente» and
 * «Actualizar componente» (so «Acciones» finds it; enabled when one machine's piece is chosen, else it says so), on
 * the right-click menu of a machine's piece, and on a machine's entry of the palette. From a piece the duplicate is
 * put to its right and chosen; from the palette there is no piece to stand by, so the duplicate is placed where the
 * user clicks, as any component of the palette (a definition no piece uses is not listed in the palette and is not
 * saved).
 */
final class MachineDuplicationActions {

    private MachineDuplicationActions() {
    }

    /**
     * Puts the three ways in {@code view}. Call before the machine mode is installed, so a machine's tab disables
     * the Editar item with the circuit's other actions. Returns what takes them away.
     */
    static Runnable install(EditorView view) {
        Action action = Actions.of(Messages.text("machine.duplicate"),
                () -> selectedMachinePiece(view).ifPresent(piece -> duplicate(view, piece)));
        JMenuItem item = new JMenuItem(action);
        item.putClientProperty(Actions.SEARCH_TERMS, Messages.text("machine.duplicate.searchTerms"));
        JMenu edit = view.menu(MenuId.EDIT);
        edit.insert(item, indexAfterTheComponentItems(view, edit));
        Runnable refresh = () -> refresh(view, action);
        refresh.run();
        view.selection().addListener(refresh);
        view.openDocument().addChangeListener(refresh);
        List<Runnable> removals = new ArrayList<>();
        removals.add(view.addContextMenuContributor(pieceContributor(view)));
        removals.add(view.palette().addUserMenuContributor(paletteContributor(view)));
        return () -> {
            removals.forEach(Runnable::run);
            view.openDocument().removeChangeListener(refresh);
            view.selection().removeListener(refresh);
            edit.remove(item);
        };
    }

    /** Right after «Actualizar componente», where the circuit's own components are made; at the end without it. */
    private static int indexAfterTheComponentItems(EditorView view, JMenu edit) {
        Optional<Action> update = view.templateActions().map(actions -> actions.updateComponent());
        for (int index = 0; index < edit.getItemCount(); index++) {
            JMenuItem entry = edit.getItem(index);
            if (entry != null && update.isPresent() && entry.getAction() == update.get()) {
                return index + 1;
            }
        }
        return edit.getItemCount();
    }

    private static void refresh(EditorView view, Action action) {
        boolean one = selectedMachinePiece(view).isPresent();
        action.setEnabled(one);
        action.putValue(Action.SHORT_DESCRIPTION, one ? null : Messages.text("machine.duplicate.choosePiece"));
    }

    /** The one part chosen in {@code view}, when it is a machine's piece. */
    private static Optional<Component> selectedMachinePiece(EditorView view) {
        if (view.selection().size() != 1) {
            return Optional.empty();
        }
        return view.openDocument().document().component(view.selection().ids().iterator().next())
                .filter(piece -> isMachinePiece(view.openDocument(), piece));
    }

    static boolean isMachinePiece(OpenDocument place, Component piece) {
        return place.registry().findTypeOf(piece).flatMap(SubcircuitTypes::definitionOf)
                .filter(ComponentDefinition::isMachine).isPresent();
    }

    /** The duplicate of {@code piece}'s machine next to it, chosen; one undo step. */
    private static void duplicate(EditorView view, Component piece) {
        CreateMachineCommand command = MachineDuplication.duplicate(view.openDocument(), piece,
                view.services().library());
        view.openDocument().execute(command);
        command.instanceId().ifPresent(view.selection()::select);
    }

    private static ContextMenuContributor pieceContributor(EditorView view) {
        return (menu, target, selection, gridPosition) -> {
            Optional<DocumentElement> element = target.filter(found -> selection.size() == 1
                    && selection.contains(found.id()));
            if (element.isPresent() && element.get() instanceof Component piece
                    && isMachinePiece(view.openDocument(), piece)) {
                menu.add(new JMenuItem(Actions.of(Messages.text("machine.duplicate"),
                        () -> duplicate(view, piece))));
            }
        };
    }

    /** On a machine's entry of the palette: arms placing a duplicate of it, as choosing an entry arms placing it. */
    static BiConsumer<Optional<PalettePanel.UserComponent>, JPopupMenu> paletteContributor(EditorView view) {
        return (entry, menu) -> entry.filter(component -> component.bundle().root().isMachine())
                .ifPresent(component -> menu.add(new JMenuItem(Actions.of(Messages.text("machine.duplicate"),
                        () -> placeDuplicate(view, component.bundle())))));
    }

    private static void placeDuplicate(EditorView view, DefinitionBundle bundle) {
        ComponentDefinition copy = MachineDuplication.copyOf(bundle.root(),
                MachineNaming.circuitOf(view.openDocument()).document(), view.services().library());
        DefinitionBundle duplicate = new DefinitionBundle(copy, bundle.dependencies());
        view.setToolOnEveryPane(() -> new PlaceDefinitionTool(duplicate));
        view.canvas().requestFocusInWindow();
    }
}
