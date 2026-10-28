package cl.almejo.vsim.ui.memory;

import cl.almejo.vsim.core.component.standard.MemoryTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.memory.MemoryKind;
import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.actions.ContextMenuContributor;
import cl.almejo.vsim.ui.canvas.Selection;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.EditorView;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.geom.Point2D;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * The context menu of a RAM or ROM: «Editar contenido…», and for a RAM «Restaurar contenido inicial» (a simulation
 * request, not an edit) and «Volcar…».
 */
public final class MemoryContextMenu implements ContextMenuContributor {

    private final EditorView view;
    private final MemoryEditorLauncher launcher;

    public MemoryContextMenu(EditorView view, MemoryEditorLauncher launcher) {
        this.view = view;
        this.launcher = launcher;
    }

    @Override
    public void contribute(JPopupMenu menu, Optional<DocumentElement> target, Selection selection,
                           Point2D gridPosition) {
        if (selection.ids().size() > 1 || target.isEmpty() || !(target.get() instanceof Component component)) {
            return;
        }
        Optional<MemoryKind> kind = MemoryTypes.kindOf(component.typeId());
        if (kind.isEmpty()) {
            return;
        }
        menu.add(item("memory.menu.edit", () -> launcher.open(view, component.id(), OptionalLong.empty())));
        if (kind.get() == MemoryKind.RAM) {
            menu.add(item("memory.menu.restore", () -> view.openDocument().restoreMemory(component.id())));
            menu.add(item("memory.menu.dump", () -> launcher.dump(view, component.id())));
        }
    }

    private static JMenuItem item(String key, Runnable behavior) {
        JMenuItem item = new JMenuItem(Actions.of(Messages.text(key), behavior));
        item.setName(key);
        return item;
    }
}
