package cl.almejo.vsim.ui.frames;

import cl.almejo.vsim.core.command.CreateFrameCommand;
import cl.almejo.vsim.core.component.ElementBounds;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.Optional;

/**
 * The frames of one editor view: Editar ▸ «Enmarcar selección» (and its context menu item). {@link #dispose()}
 * takes back the menu item and the shortcut registration.
 */
public final class FramesFeatures {

    /** Grid units between the selection and the frame drawn around it. */
    public static final int MARGIN = 2;

    private final EditorView view;
    private final Action frameSelection;
    private final JMenu editMenu;
    private final JMenuItem menuItem;
    private final Runnable shortcutRemover;
    private boolean disposed;

    public FramesFeatures(EditorView view) {
        this.view = view;
        this.frameSelection = new AbstractAction(Messages.text("action.frameSelection")) {
            @Override
            public void actionPerformed(ActionEvent event) {
                frameSelectionNow();
            }
        };
        frameSelection.setEnabled(!view.canvas().selection().isEmpty());
        // the selection has no way to drop a listener; after dispose this one only touches its own action
        view.canvas().selection().addListener(
                () -> frameSelection.setEnabled(!view.canvas().selection().isEmpty()));
        this.editMenu = view.menu(MenuId.EDIT);
        this.menuItem = new JMenuItem(frameSelection);
        editMenu.insert(menuItem, indexAfterSelectAll());
        this.shortcutRemover = view.registerShortcutAction("edit.frameSelection", frameSelection);
        view.addContextMenuContributor((menu, target, selection, gridPosition) -> {
            if (!disposed && !selection.isEmpty() && target.isPresent()) {
                menu.add(new JMenuItem(frameSelection));
            }
        });
    }

    private int indexAfterSelectAll() {
        Action selectAll = view.selectionActions().selectAll();
        for (int index = 0; index < editMenu.getItemCount(); index++) {
            JMenuItem item = editMenu.getItem(index);
            if (item != null && item.getAction() == selectAll) {
                return index + 1;
            }
        }
        return editMenu.getItemCount();
    }

    public Action frameSelectionAction() {
        return frameSelection;
    }

    /** The frame around the selection (bounds plus {@link #MARGIN}, at least the minimum size); one undo step. */
    void frameSelectionNow() {
        if (disposed) {
            return;
        }
        List<DocumentElement> elements = view.canvas().selection().ids().stream()
                .map(view.openDocument().document()::element)
                .flatMap(Optional::stream)
                .toList();
        Optional<Bounds> bounds = ElementBounds.union(elements, view.openDocument().registry());
        if (bounds.isEmpty()) {
            return;
        }
        Frame frame = new Frame(view.openDocument().document().newFrameId(),
                new GridPoint(bounds.get().x() - MARGIN, bounds.get().y() - MARGIN),
                Math.max(Frame.MINIMUM_WIDTH, bounds.get().width() + 2 * MARGIN),
                Math.max(Frame.MINIMUM_HEIGHT, bounds.get().height() + 2 * MARGIN),
                Messages.text("frames.defaultTitle"), FrameTint.NONE, false);
        view.openDocument().execute(new CreateFrameCommand(frame));
        view.canvas().selection().select(frame.id());
    }

    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        shortcutRemover.run();
        editMenu.remove(menuItem);
    }
}
