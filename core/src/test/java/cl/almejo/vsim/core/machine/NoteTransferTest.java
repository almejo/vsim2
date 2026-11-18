package cl.almejo.vsim.core.machine;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.StickyChecklist;
import cl.almejo.vsim.core.document.StickyColor;
import cl.almejo.vsim.core.document.StickyItem;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.core.document.StickyText;
import cl.almejo.vsim.core.geometry.GridPoint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Notes cross between a circuit (grid units) and a machine's diagram (pixels at 100 %): size, colour, text, fold. */
class NoteTransferTest {

    @Test
    void aCircuitNoteBecomesAMachineNoteOfTheSameSizeOnScreen() {
        StickyNote note = new StickyNote(new ElementId("s1"), new GridPoint(10, 20), 8, 6, StickyColor.BLUE,
                new StickyText("hello"), true);

        List<MachineNote> notes = NoteTransfer.toMachineNotes(List.of(note));

        assertThat(notes).hasSize(1);
        MachineNote machineNote = notes.getFirst();
        assertThat(machineNote.width()).isEqualTo(64);
        assertThat(machineNote.height()).isEqualTo(48);
        assertThat(machineNote.color()).isEqualTo(StickyColor.BLUE);
        assertThat(machineNote.text()).isEqualTo("hello");
        assertThat(machineNote.folded()).isTrue();
    }

    @Test
    void aChecklistBecomesTheTextOfItsItems() {
        StickyNote note = new StickyNote(new ElementId("s1"), new GridPoint(0, 0), 8, 6, StickyColor.YELLOW,
                new StickyChecklist(List.of(new StickyItem("a", true), new StickyItem("b", false))), false);

        assertThat(NoteTransfer.toMachineNotes(List.of(note)).getFirst().text()).isEqualTo("✓ a\nb");
    }

    @Test
    void positionsKeepTheirDistancesInBothDirections() {
        StickyNote first = new StickyNote(new ElementId("s1"), new GridPoint(10, 10), 8, 6, StickyColor.YELLOW,
                new StickyText("a"), false);
        StickyNote second = new StickyNote(new ElementId("s2"), new GridPoint(30, 20), 8, 6, StickyColor.YELLOW,
                new StickyText("b"), false);

        List<MachineNote> machineNotes = NoteTransfer.toMachineNotes(List.of(first, second));
        assertThat(machineNotes.get(1).position().x() - machineNotes.get(0).position().x()).isEqualTo(160);
        assertThat(machineNotes.get(1).position().y() - machineNotes.get(0).position().y()).isEqualTo(80);

        List<StickyNote> back = NoteTransfer.toStickyNotes(machineNotes);
        assertThat(back.get(1).position().x() - back.get(0).position().x()).isEqualTo(20);
        assertThat(back.get(1).width()).isEqualTo(8);
        assertThat(back.get(0).position()).isEqualTo(new GridPoint(0, 0));
    }

    @Test
    void aMachineNoteBecomesACircuitNoteKeepingColourTextAndFold() {
        MachineNote note = new MachineNote("n1", new DiagramPoint(100, 50), 160, 120, StickyColor.GREEN, "hi", true);

        StickyNote sticky = NoteTransfer.toStickyNotes(List.of(note)).getFirst();

        assertThat(sticky.width()).isEqualTo(20);
        assertThat(sticky.height()).isEqualTo(15);
        assertThat(sticky.color()).isEqualTo(StickyColor.GREEN);
        assertThat(sticky.content()).isEqualTo(new StickyText("hi"));
        assertThat(sticky.folded()).isTrue();
    }
}
