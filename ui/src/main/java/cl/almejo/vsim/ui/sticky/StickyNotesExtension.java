package cl.almejo.vsim.ui.sticky;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.StickyChecklist;
import cl.almejo.vsim.core.document.StickyItem;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.core.document.StickyText;
import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.tools.SelectTool;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuSlot;

import javax.swing.Action;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Adds sticky notes to every editor view (spec §3): the «Nota adhesiva» tool (toolbar button, Ver menu item and the
 * {@code view.stickyNoteTool} shortcut id, no default key), the select tool's note gestures on every pane, the note's
 * context menu items and the tooltip of a folded note. Installed before the frames extension: the toolbar button and
 * the menu item then land after «Marco» (both are inserted right after Cable), and the gestures are asked before the
 * frames' ones, as a note lies on top of a frame's title bar. The returned runnable takes everything back.
 */
public final class StickyNotesExtension implements EditorExtension {

    private final StickyCloseConfirmation confirmation;

    public StickyNotesExtension() {
        this(StickyCloseConfirmation.dialog());
    }

    /** Tests pass a fake confirmation so no dialog is shown. */
    public StickyNotesExtension(StickyCloseConfirmation confirmation) {
        this.confirmation = Objects.requireNonNull(confirmation, "confirmation");
    }

    @Override
    public Runnable install(EditorView view) {
        List<Runnable> removers = new ArrayList<>();
        Action tool = Actions.of(Messages.text("action.toolStickyNote"),
                () -> view.chooseTool(StickyNoteTool.class, StickyNoteTool::new));
        Icons.install(tool, StickyNoteIcons.TOOL);
        removers.add(view.addToolButton(tool, StickyNoteTool.class));
        removers.add(view.addToolMenuItem(MenuSlot.STICKY_NOTE_TOOL, tool, StickyNoteTool.class));
        removers.add(view.registerShortcutAction("view.stickyNoteTool", tool));
        removers.add(view.forEachPane(this::installOn));
        StickyNoteMenu menu = new StickyNoteMenu(view.openDocument(), view::canvas, confirmation);
        removers.add(view.addContextMenuContributor((popup, target, selection, gridPosition) -> {
            if (target.isPresent() && target.get() instanceof StickyNote note) {
                menu.addItems(popup, note);
            }
        }));
        return () -> removers.reversed().forEach(Runnable::run);
    }

    /** On one pane: the select tool's note gestures and the tooltip of a folded note. */
    private Runnable installOn(CircuitCanvas pane) {
        StickyNoteGestures gestures = new StickyNoteGestures(() -> pane, confirmation);
        Runnable gesturesRemover = pane.defaultTool() instanceof SelectTool selectTool
                ? selectTool.addDelegate(gestures)
                : () -> {
                };
        Runnable toolTipRemover = pane.addToolTipOverride(
                gridPoint -> foldedNoteToolTip(pane.openDocument().document(), gridPoint,
                        pane.viewport().pixelsPerUnit()));
        return () -> {
            toolTipRemover.run();
            gesturesRemover.run();
        };
    }

    /**
     * The text of the folded note under {@code gridPoint}, as an HTML tooltip (spec §2: a folded note shows no text,
     * the hover does): one line per line of text, or per item with ☐ / ✓. Empty elsewhere or on an empty note.
     */
    static Optional<String> foldedNoteToolTip(CircuitDocument document, Point2D gridPoint, double pixelsPerGrid) {
        return StickyNoteHitTester.hit(document, gridPoint, pixelsPerGrid)
                .flatMap(hit -> document.stickyNote(hit.note()))
                .flatMap(StickyNotesExtension::foldedNoteToolTip);
    }

    /**
     * The tooltip of {@code note} when it is folded (its text, one line per line, or its items with ☐ / ✓); empty
     * when it is unfolded or empty. A machine's diagram notes show theirs the same way.
     */
    public static Optional<String> foldedNoteToolTip(StickyNote note) {
        return Optional.of(note)
                .filter(StickyNote::folded)
                .map(StickyNotesExtension::toolTipLines)
                .filter(lines -> !lines.isEmpty())
                .map(lines -> "<html>" + String.join("<br>", lines.stream()
                        .map(StickyNotesExtension::escaped).toList()) + "</html>");
    }

    private static List<String> toolTipLines(StickyNote note) {
        return switch (note.content()) {
            case StickyText text -> text.text().isBlank() ? List.of() : List.of(text.text().strip().split("\n"));
            case StickyChecklist checklist -> checklist.items().stream().map(StickyNotesExtension::itemLine).toList();
        };
    }

    private static String itemLine(StickyItem item) {
        return Messages.format(item.done() ? "stickyNotes.tooltipDone" : "stickyNotes.tooltipPending", item.text());
    }

    private static String escaped(String plain) {
        return plain.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
