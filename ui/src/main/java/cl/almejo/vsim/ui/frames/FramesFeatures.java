package cl.almejo.vsim.ui.frames;

import cl.almejo.vsim.core.command.CreateFrameCommand;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameContents;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.canvas.DrawnBounds;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.tools.SelectTool;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The frames of one editor view: Editar ▸ «Enmarcar selección» (and its context menu item), the select tool's frame
 * gestures (select, move, resize) and the notice when «Borrar» removes a locked block. {@link #dispose()} takes back
 * every registration: menu items, shortcuts, tool button, gestures, the padlock overlay, the delete and selection
 * listeners and the context-menu contributor.
 */
public final class FramesFeatures {

    /** Grid units between the selection and the frame drawn around it. */
    public static final int MARGIN = 2;
    /** Grid units the title bar takes at the top, so the margin under it matches the other sides. */
    private static final int TITLE_BAR_UNITS =
            (int) Math.ceil(FramePainter.TITLE_BAR_HEIGHT / GridPoint.PIXELS_PER_UNIT);

    private final EditorView view;
    private final Action frameSelection;
    private final JMenu editMenu;
    private final JMenuItem menuItem;
    private final Runnable shortcutRemover;
    private final Action frameTool;
    private final JMenu viewMenu;
    private final JMenuItem toolMenuItem;
    private final Runnable toolButtonRemover;
    private final Runnable toolShortcutRemover;
    private final FrameMenu frameMenu;
    private Consumer<Frame> titleEditor;
    private final FrameGestures gestures = new FrameGestures();
    private final Runnable gesturesRemover;
    private final Runnable deleteListenerRemover;
    private final Runnable selectionListener;
    private final Runnable contributorRemover;
    private final FrameLockOverlay lockOverlay;
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
        this.selectionListener = () -> frameSelection.setEnabled(!view.canvas().selection().isEmpty());
        view.canvas().selection().addListener(selectionListener);
        this.editMenu = view.menu(MenuId.EDIT);
        this.menuItem = new JMenuItem(frameSelection);
        editMenu.insert(menuItem, indexAfterSelectAll());
        this.shortcutRemover = view.registerShortcutAction("edit.frameSelection", frameSelection);
        this.titleEditor = frame -> view.editLabel(frame);
        this.frameTool = new AbstractAction(Messages.text("action.toolFrame")) {
            @Override
            public void actionPerformed(ActionEvent event) {
                view.canvas().setTool(new FrameTool());
            }
        };
        Icons.install(frameTool, FrameIcons.FRAME_TOOL);
        this.toolButtonRemover = view.addToolButton(frameTool, FrameTool.class);
        this.viewMenu = view.menu(MenuId.VIEW);
        this.toolMenuItem = new JMenuItem(frameTool);
        viewMenu.insert(toolMenuItem, indexAfterWireTool());
        this.toolShortcutRemover = view.registerShortcutAction("view.frameTool", frameTool);
        this.gesturesRemover = view.canvas().defaultTool() instanceof SelectTool selectTool
                ? selectTool.addDelegate(gestures)
                : () -> {
                };
        this.deleteListenerRemover = view.actions().addDeleteListener(ids -> blockDeletedNotice(
                view.openDocument().document(), view.openDocument().registry(), ids)
                .ifPresent(view.statusBar()::showNotice));
        this.lockOverlay = new FrameLockOverlay(() -> view.canvas().viewport().pixelsPerUnit());
        view.canvas().addOverlay(lockOverlay);
        this.frameMenu = new FrameMenu(view.openDocument(), frame -> titleEditor.accept(frame),
                frame -> view.templateActions().ifPresent(actions -> actions.createFromFrame(frame)));
        this.contributorRemover = view.addContextMenuContributor((menu, target, selection, gridPosition) -> {
            if (target.isPresent() && target.get() instanceof Frame frame) {
                frameMenu.addItems(menu, frame);
            }
            if (!selection.isEmpty() && target.isPresent()) {
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

    private int indexAfterWireTool() {
        Action wireTool = view.actions().wireTool();
        for (int index = 0; index < viewMenu.getItemCount(); index++) {
            JMenuItem item = viewMenu.getItem(index);
            if (item != null && item.getAction() == wireTool) {
                return index + 1;
            }
        }
        return viewMenu.getItemCount();
    }

    /** The «Marco» tool's action (toolbar button and View menu item). */
    public Action frameToolAction() {
        return frameTool;
    }

    /**
     * Replaces what opens the title editor of a new frame (Enmarcar selección); by default the editor F2 uses.
     * Tests swap it so no dialog is shown.
     */
    public void setTitleEditor(Consumer<Frame> titleEditor) {
        this.titleEditor = titleEditor;
    }

    /** The frame menu (⋯ button and right click on the title bar). */
    public FrameMenu frameMenu() {
        return frameMenu;
    }

    /** The select tool's frame gestures in this view. */
    public FrameGestures gestures() {
        return gestures;
    }

    /**
     * The notice for deleting {@code ids} when they hold a whole locked block (the frame and all its contents):
     * «Se borró el bloque «title» (n elementos)», n counting the contents. With several blocks, the largest one; an
     * empty locked frame is no block.
     */
    static Optional<String> blockDeletedNotice(CircuitDocument document, ComponentTypeRegistry registry,
                                               Set<ElementId> ids) {
        Frame largest = null;
        int largestContents = -1;
        for (Frame frame : document.frames()) {
            if (!frame.locked() || !ids.contains(frame.id())) {
                continue;
            }
            Set<ElementId> contents = FrameContents.of(document, registry, frame);
            if (!contents.isEmpty() && ids.containsAll(contents) && contents.size() > largestContents) {
                largest = frame;
                largestContents = contents.size();
            }
        }
        return largest == null ? Optional.empty()
                : Optional.of(Messages.format("frames.blockDeleted", largest.title(),
                String.valueOf(largestContents)));
    }

    public Action frameSelectionAction() {
        return frameSelection;
    }

    /**
     * The frame around the selection as drawn (labels included) plus {@link #MARGIN}, with the title bar's room on
     * top, at least the minimum size; one undo step.
     */
    void frameSelectionNow() {
        if (disposed) {
            return;
        }
        List<DocumentElement> elements = view.canvas().selection().ids().stream()
                .map(view.openDocument().document()::element)
                .flatMap(Optional::stream)
                .toList();
        Optional<Bounds> bounds = DrawnBounds.of(elements, view.openDocument().registry());
        if (bounds.isEmpty()) {
            return;
        }
        int topMargin = MARGIN + TITLE_BAR_UNITS;
        Frame frame = new Frame(view.openDocument().document().newFrameId(),
                new GridPoint(bounds.get().x() - MARGIN, bounds.get().y() - topMargin),
                Math.max(Frame.MINIMUM_WIDTH, bounds.get().width() + 2 * MARGIN),
                Math.max(Frame.MINIMUM_HEIGHT, bounds.get().height() + MARGIN + topMargin),
                Messages.text("frames.defaultTitle"), FrameTint.NONE, false);
        view.openDocument().execute(new CreateFrameCommand(frame));
        view.canvas().selection().select(frame.id());
        titleEditor.accept(frame);
    }

    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        shortcutRemover.run();
        toolShortcutRemover.run();
        toolButtonRemover.run();
        viewMenu.remove(toolMenuItem);
        gesturesRemover.run();
        deleteListenerRemover.run();
        view.canvas().selection().removeListener(selectionListener);
        contributorRemover.run();
        view.canvas().removeOverlay(lockOverlay);
        editMenu.remove(menuItem);
    }
}
