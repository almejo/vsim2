package cl.almejo.vsim.ui.actions;

import cl.almejo.vsim.core.command.RemoveElementsCommand;
import cl.almejo.vsim.core.component.ElementBounds;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.io.CircuitFormatException;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.io.FragmentFormat;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.clipboard.FragmentClipboard;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.tools.FloatingSelectionTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Cut, copy, paste, duplicate and select all for one canvas. The clipboard carries {@link FragmentFormat} JSON;
 * pasting and duplicating float the fragment with {@link FloatingSelectionTool} until it is fixed or cancelled.
 */
public final class SelectionActions {

    /** How far a paste without the mouse over the canvas, or a duplicate, lands from the original (grid units). */
    public static final int PASTE_OFFSET = 2;

    private static final Logger LOGGER = LoggerFactory.getLogger(SelectionActions.class);

    private final OpenDocument openDocument;
    private final CircuitCanvas canvas;
    private final EditorActions editorActions;
    private final FragmentClipboard clipboard;
    private final FragmentFormat format;
    private final Action cut;
    private final Action copy;
    private final Action paste;
    private final Action duplicate;
    private final Action selectAll;

    public SelectionActions(OpenDocument openDocument, CircuitCanvas canvas, EditorActions editorActions,
                            FragmentClipboard clipboard) {
        this.openDocument = openDocument;
        this.canvas = canvas;
        this.editorActions = editorActions;
        this.clipboard = clipboard;
        this.format = new FragmentFormat(openDocument.registry());
        int menuMask = editorActions.menuShortcutMaskForBindings();
        cut = action(Messages.text("action.cut"), KeyStroke.getKeyStroke(KeyEvent.VK_X, menuMask), this::cutSelection);
        copy = action(Messages.text("action.copy"), KeyStroke.getKeyStroke(KeyEvent.VK_C, menuMask), this::copySelection);
        paste = action(Messages.text("action.paste"), KeyStroke.getKeyStroke(KeyEvent.VK_V, menuMask),
                () -> pasteAt(canvas.mouseGridPoint()));
        duplicate = action(Messages.text("action.duplicate"), KeyStroke.getKeyStroke(KeyEvent.VK_D, menuMask),
                this::duplicateSelection);
        selectAll = action(Messages.text("action.selectAll"), KeyStroke.getKeyStroke(KeyEvent.VK_A, menuMask),
                () -> canvas.selection().selectAll(openDocument.document()));
        canvas.selection().addListener(this::refresh);
        refresh();
    }

    public Action cut() { return cut; }
    public Action copy() { return copy; }
    public Action paste() { return paste; }
    public Action duplicate() { return duplicate; }
    public Action selectAll() { return selectAll; }

    /**
     * Floats the clipboard's fragment at {@code mousePoint}; without one, next to where it was copied from, or at
     * the center of the view when it was copied elsewhere. Anything that is not a fragment is ignored.
     */
    public void pasteAt(Optional<GridPoint> mousePoint) {
        Optional<String> json = clipboard.read();
        if (json.isEmpty()) {
            return;
        }
        CircuitFragment fragment;
        try {
            fragment = format.read(json.get());
        } catch (CircuitFormatException | RuntimeException exception) {
            // Like a drop: valid JSON can still make the reader throw (bounds overflowing an int, for one).
            LOGGER.debug("Clipboard does not hold a usable circuit fragment: {}", exception.getMessage());
            return;
        }
        if (fragment.isEmpty()) {
            return;
        }
        canvas.announceIncomingFragment(fragment);
        GridPoint position = mousePoint
                .or(() -> clipboard.originOf(json.get()).map(origin -> origin.translate(PASTE_OFFSET, PASTE_OFFSET)))
                .orElseGet(() -> canvas.viewport().snap(canvas.getWidth() / 2.0, canvas.getHeight() / 2.0));
        startFloating(fragment, position);
    }

    /** Clipboard entries of the canvas context menu (see the class tests for what each target gets). */
    public ContextMenuContributor contributor() {
        return (menu, target, selection, gridPosition) -> {
            if (target.isEmpty()) {
                Optional<GridPoint> openedAt = Optional.of(new GridPoint(
                        (int) Math.round(gridPosition.getX()), (int) Math.round(gridPosition.getY())));
                menu.add(ContextMenuEntry.PASTE.tag(
                        new JMenuItem(action(Messages.text("action.paste"), null, () -> pasteAt(openedAt)))));
                addOnce(menu, selectAll, ContextMenuEntry.SELECT_ALL);
                return;
            }
            boolean multiple = selection.ids().size() > 1;
            if (!multiple && target.get() instanceof Wire) {
                return;
            }
            addOnce(menu, cut, ContextMenuEntry.CUT);
            addOnce(menu, copy, ContextMenuEntry.COPY);
            addOnce(menu, duplicate, ContextMenuEntry.DUPLICATE);
            if (multiple) {
                addOnce(menu, editorActions.delete(), ContextMenuEntry.DELETE);
                addOnce(menu, editorActions.rotateClockwise(), ContextMenuEntry.ROTATE_CLOCKWISE);
                addOnce(menu, editorActions.rotateCounterClockwise(), ContextMenuEntry.ROTATE_COUNTER_CLOCKWISE);
            }
        };
    }

    private static void addOnce(JPopupMenu menu, Action action, ContextMenuEntry entry) {
        for (java.awt.Component child : menu.getComponents()) {
            if (child instanceof JMenuItem item && item.getAction() == action) {
                return;
            }
        }
        menu.add(entry.tag(new JMenuItem(action)));
    }

    private void refresh() {
        boolean anySelected = !canvas.selection().isEmpty();
        cut.setEnabled(anySelected);
        copy.setEnabled(anySelected);
        duplicate.setEnabled(anySelected);
    }

    /** @return false when nothing is selected (the clipboard is left alone) */
    private boolean copySelection() {
        Set<ElementId> ids = canvas.selection().ids();
        Optional<Bounds> bounds = boundsOf(ids);
        if (bounds.isEmpty()) {
            return false;
        }
        CircuitFragment fragment = CircuitFragment.of(openDocument.document(), ids, openDocument.registry());
        clipboard.put(format.write(fragment), new GridPoint(bounds.get().x(), bounds.get().y()));
        return true;
    }

    private void cutSelection() {
        Set<ElementId> ids = canvas.selection().ids();
        if (copySelection()) {
            openDocument.execute(new RemoveElementsCommand(ids));
        }
    }

    private void duplicateSelection() {
        Set<ElementId> ids = canvas.selection().ids();
        Optional<Bounds> bounds = boundsOf(ids);
        if (bounds.isEmpty()) {
            return;
        }
        startFloating(CircuitFragment.of(openDocument.document(), ids, openDocument.registry()),
                new GridPoint(bounds.get().x() + PASTE_OFFSET, bounds.get().y() + PASTE_OFFSET));
    }

    /** Bounds of the listed ids that still exist in the document; empty when none does. */
    private Optional<Bounds> boundsOf(Set<ElementId> ids) {
        List<DocumentElement> elements = ids.stream().map(openDocument.document()::element)
                .flatMap(Optional::stream).toList();
        return ElementBounds.union(elements, openDocument.registry());
    }

    private void startFloating(CircuitFragment fragment, GridPoint position) {
        canvas.setTool(new FloatingSelectionTool(fragment, position));
        canvas.requestFocusInWindow();
    }

    private static Action action(String name, KeyStroke accelerator, Runnable behavior) {
        Action action = new AbstractAction(name) {
            @Override
            public void actionPerformed(ActionEvent event) {
                behavior.run();
            }
        };
        if (accelerator != null) {
            action.putValue(Action.ACCELERATOR_KEY, accelerator);
        }
        return action;
    }
}
