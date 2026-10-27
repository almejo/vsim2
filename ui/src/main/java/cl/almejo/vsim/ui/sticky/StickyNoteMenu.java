package cl.almejo.vsim.ui.sticky;

import cl.almejo.vsim.core.document.StickyChecklist;
import cl.almejo.vsim.core.document.StickyColor;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.i18n.StickyColorNames;

import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import java.awt.Component;
import java.util.function.Supplier;

/**
 * The items of a sticky note's context menu (spec §3 «Edit»): «Color ▸», «Texto» / «Lista», «Plegar» / «Desplegar»
 * and «Despegar…» (which asks first, like the ✕). They act on the note as it is when the item is chosen, each as one
 * undo step. While a locked frame holds the note every item is disabled. Built as a model: tests inspect it and never
 * show it.
 */
public final class StickyNoteMenu {

    private final OpenDocument openDocument;
    private final StickyNoteEdits edits;
    private final Supplier<Component> parent;
    private final StickyCloseConfirmation confirmation;

    /** {@code parent} is what the «Despegar…» confirmation belongs to. */
    public StickyNoteMenu(OpenDocument openDocument, Supplier<Component> parent,
                          StickyCloseConfirmation confirmation) {
        this.openDocument = openDocument;
        this.edits = new StickyNoteEdits(openDocument);
        this.parent = parent;
        this.confirmation = confirmation;
    }

    /** A popup holding only the note's items, for tests. */
    public JPopupMenu build(StickyNote note) {
        JPopupMenu menu = new JPopupMenu();
        addItems(menu, note);
        return menu;
    }

    /** Adds the note's items to {@code menu}. */
    public void addItems(JPopupMenu menu, StickyNote note) {
        boolean editable = !StickyNoteLock.isLocked(openDocument, note.id());
        JMenu color = new JMenu(Messages.text("stickyNotes.menu.color"));
        ButtonGroup colors = new ButtonGroup();
        for (StickyColor paper : StickyColor.values()) {
            JRadioButtonMenuItem radio = new JRadioButtonMenuItem(StickyColorNames.of(paper), note.color() == paper);
            radio.addActionListener(event -> edits.setColor(note.id(), paper));
            colors.add(radio);
            color.add(radio);
        }
        boolean checklist = note.content() instanceof StickyChecklist;
        ButtonGroup kinds = new ButtonGroup();
        JRadioButtonMenuItem text = new JRadioButtonMenuItem(Messages.text("stickyNotes.menu.text"), !checklist);
        text.addActionListener(event -> edits.setKind(note.id(), false));
        JRadioButtonMenuItem list = new JRadioButtonMenuItem(Messages.text("stickyNotes.menu.list"), checklist);
        list.addActionListener(event -> edits.setKind(note.id(), true));
        kinds.add(text);
        kinds.add(list);
        JMenuItem fold = item(note.folded() ? "stickyNotes.menu.unfold" : "stickyNotes.menu.fold",
                () -> edits.setFolded(note.id(), !note.folded()));
        JMenuItem close = item("stickyNotes.menu.close", () -> edits.close(note.id(), confirmation, parent.get()));
        for (JMenuItem item : new JMenuItem[] {color, text, list, fold, close}) {
            item.setEnabled(editable);
            menu.add(item);
        }
    }

    private static JMenuItem item(String key, Runnable behavior) {
        JMenuItem item = new JMenuItem(Messages.text(key));
        item.addActionListener(event -> behavior.run());
        return item;
    }
}
