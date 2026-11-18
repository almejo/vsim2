package cl.almejo.vsim.core.machine;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.core.document.StickyText;
import cl.almejo.vsim.core.geometry.GridPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Sticky notes crossing between a circuit and a machine's diagram (copy in one, paste in the other). A circuit note is
 * in grid units of {@link GridPoint#PIXELS_PER_UNIT} pixels and a diagram note in pixels at 100 %, so the note keeps
 * the size it has on screen. A checklist becomes free text (done items marked), as a machine note has no list. The
 * notes come out with the top-left of the group at the origin (the paste moves them where it wants).
 */
public final class NoteTransfer {

    private NoteTransfer() {
    }

    /** {@code notes} as diagram notes, the top-left of the group at (0, 0), with ids {@code n1}, {@code n2}… */
    public static List<MachineNote> toMachineNotes(List<StickyNote> notes) {
        int left = notes.stream().mapToInt(note -> note.position().x()).min().orElse(0);
        int top = notes.stream().mapToInt(note -> note.position().y()).min().orElse(0);
        List<MachineNote> result = new ArrayList<>();
        for (StickyNote note : notes) {
            result.add(new MachineNote("n" + (result.size() + 1),
                    new DiagramPoint((note.position().x() - left) * (double) GridPoint.PIXELS_PER_UNIT,
                            (note.position().y() - top) * (double) GridPoint.PIXELS_PER_UNIT),
                    note.width() * (double) GridPoint.PIXELS_PER_UNIT,
                    note.height() * (double) GridPoint.PIXELS_PER_UNIT, note.color(),
                    note.content().toText().text(), note.folded()));
        }
        return List.copyOf(result);
    }

    /** {@code notes} as circuit notes, the top-left of the group at (0, 0), with ids {@code sticky1}… */
    public static List<StickyNote> toStickyNotes(List<MachineNote> notes) {
        double left = notes.stream().mapToDouble(note -> note.position().x()).min().orElse(0);
        double top = notes.stream().mapToDouble(note -> note.position().y()).min().orElse(0);
        List<StickyNote> result = new ArrayList<>();
        for (MachineNote note : notes) {
            result.add(new StickyNote(new ElementId("sticky" + (result.size() + 1)),
                    new GridPoint(units(note.position().x() - left), units(note.position().y() - top)),
                    units(note.width()), units(note.height()), note.color(), new StickyText(note.text()),
                    note.folded()));
        }
        return List.copyOf(result);
    }

    private static int units(double pixels) {
        return (int) Math.round(pixels / GridPoint.PIXELS_PER_UNIT);
    }
}
