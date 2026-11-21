package cl.almejo.vsim.ui.labels;

import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuSlot;

import javax.swing.Action;
import java.util.ArrayList;
import java.util.List;

/**
 * Adds the «Texto» tool to every editor view: a toolbar button, a Ver menu item and the {@code view.textTool}
 * shortcut id (no default key). Installed before the sticky notes extension: both insert right after Cable, so the
 * order ends up Cable, Marco, Nota adhesiva, Texto, Mover vista, Zoom. The returned runnable takes everything back.
 */
public final class TextToolExtension implements EditorExtension {

    @Override
    public Runnable install(EditorView view) {
        List<Runnable> removers = new ArrayList<>();
        Action tool = Actions.of(Messages.text("action.toolText"),
                () -> view.chooseTool(TextTool.class, TextTool::new));
        Icons.install(tool, TextToolIcons.TOOL);
        removers.add(view.addToolButton(tool, TextTool.class));
        removers.add(view.addToolMenuItem(MenuSlot.TEXT_TOOL, tool, TextTool.class));
        removers.add(view.registerShortcutAction("view.textTool", tool));
        return () -> removers.reversed().forEach(Runnable::run);
    }

}
