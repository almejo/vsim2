package cl.almejo.vsim.core.io.logisim;

import java.util.List;

/**
 * A component left out: VSim has no equivalent, or the file's values cannot be read (the notes say which), or it does
 * nothing in Logisim either (a tunnel without label, no notes).
 */
public record NotMapped(List<ImportNote> notes) implements LogisimMapping {

    public NotMapped {
        notes = List.copyOf(notes);
    }
}
