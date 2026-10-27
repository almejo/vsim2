package cl.almejo.vsim.ui.sticky;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.CreateFrameCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.document.StickyChecklist;
import cl.almejo.vsim.core.document.StickyColor;
import cl.almejo.vsim.core.document.StickyContent;
import cl.almejo.vsim.core.document.StickyItem;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.core.document.StickyText;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.app.OpenDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.util.ArrayList;
import java.util.List;

import static cl.almejo.vsim.ui.UiTestSupport.texts;
import static org.assertj.core.api.Assertions.assertThat;

/** The sticky note's context menu items (spec §3 «Edit»): colour, kind, fold and «Despegar…». */
class StickyNoteMenuTest {

    private final OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private final List<String> asked = new ArrayList<>();
    private final StickyNoteMenu menu = new StickyNoteMenu(openDocument, () -> null, (parent, firstLine) -> {
        asked.add(firstLine);
        return true;
    });

    @AfterEach
    void close() {
        openDocument.close();
    }

    private StickyNote note(StickyContent content) {
        StickyNote note = new StickyNote(openDocument.document().newStickyNoteId(), new GridPoint(10, 10), 8, 6,
                StickyColor.YELLOW, content, false);
        openDocument.execute(new AddElementsCommand(List.of(note)));
        openDocument.history().clear();
        return note;
    }

    private StickyNote current(StickyNote note) {
        return openDocument.document().stickyNote(note.id()).orElseThrow();
    }

    private static JMenuItem item(JPopupMenu popup, String text) {
        for (java.awt.Component child : popup.getComponents()) {
            if (child instanceof JMenuItem item && text.equals(item.getText())) {
                return item;
            }
        }
        throw new AssertionError("no item " + text + " in " + texts(popup));
    }

    @Test
    void theMenuOffersColourKindFoldAndDespegar() {
        StickyNote note = note(new StickyText("hola"));
        JPopupMenu popup = menu.build(note);
        assertThat(texts(popup)).containsExactly("Color", "Texto", "Lista", "Plegar", "Despegar…");
        JMenu colors = (JMenu) item(popup, "Color");
        assertThat(colors.getItemCount()).isEqualTo(StickyColor.values().length);
        assertThat(colors.getItem(0).isSelected()).isTrue();
        assertThat(item(popup, "Texto").isSelected()).isTrue();
    }

    @Test
    void aColourIsOneUndoStep() {
        StickyNote note = note(new StickyText("hola"));
        JMenu colors = (JMenu) item(menu.build(note), "Color");
        colors.getItem(StickyColor.PINK.ordinal()).doClick();

        assertThat(current(note).color()).isEqualTo(StickyColor.PINK);
        assertThat(openDocument.history().undoDescription()).contains("command.colorStickyNote");
    }

    @Test
    void theKindSwitchesBothWays() {
        StickyNote note = note(new StickyText("uno\n\ndos"));
        item(menu.build(note), "Lista").doClick();
        assertThat(current(note).content()).isEqualTo(new StickyChecklist(List.of(new StickyItem("uno", false),
                new StickyItem("dos", false))));
        assertThat(openDocument.history().undoDescription()).contains("command.stickyNoteKind");

        openDocument.execute(new cl.almejo.vsim.core.command.ChangeStickyNoteCommand(current(note),
                current(note).withContent(((StickyChecklist) current(note).content()).toggled(0))));
        item(menu.build(current(note)), "Texto").doClick();
        assertThat(current(note).content()).isEqualTo(new StickyText("✓ uno\ndos"));
    }

    @Test
    void foldAndUnfold() {
        StickyNote note = note(new StickyText("hola"));
        item(menu.build(note), "Plegar").doClick();
        assertThat(current(note).folded()).isTrue();
        assertThat(texts(menu.build(current(note)))).contains("Desplegar");
        item(menu.build(current(note)), "Desplegar").doClick();
        assertThat(current(note).folded()).isFalse();
    }

    @Test
    void despegarAsksFirst() {
        StickyNote note = note(new StickyText("Comprar LEDs"));
        item(menu.build(note), "Despegar…").doClick();
        assertThat(asked).containsExactly("Comprar LEDs");
        assertThat(openDocument.document().stickyNotes()).isEmpty();
    }

    @Test
    void insideALockedFrameEveryItemIsDisabled() {
        StickyNote note = note(new StickyText("hola"));
        openDocument.execute(new CreateFrameCommand(new Frame(openDocument.document().newFrameId(),
                new GridPoint(5, 5), 30, 20, "Bloque", FrameTint.NONE, true)));
        JPopupMenu popup = menu.build(note);
        for (java.awt.Component child : popup.getComponents()) {
            assertThat(child.isEnabled()).as(((JMenuItem) child).getText()).isFalse();
        }
    }
}
