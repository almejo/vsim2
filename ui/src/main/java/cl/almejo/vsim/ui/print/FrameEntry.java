package cl.almejo.vsim.ui.print;

import cl.almejo.vsim.core.document.ElementId;

import java.util.Objects;

/**
 * One frame in the «Marco» list of «Exportar a PDF…»: its id (what the export uses), the title the list shows
 * («Marco sin título» for an untitled one, a number after each repeated title, so no two entries read the same) and
 * its nesting depth (0 for a frame inside no other; the list indents by it).
 */
public record FrameEntry(ElementId id, String displayTitle, int depth) {

    public FrameEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayTitle, "displayTitle");
        if (depth < 0) {
            throw new IllegalArgumentException("negative depth: " + depth);
        }
    }
}
