package cl.almejo.vsim.core.io.logisim;

import java.util.List;

/**
 * What {@link LogisimPartMapper} makes of one Logisim component: a VSim part, a text (a {@code Text}, or a
 * {@code Probe}, which VSim shows as its label), an instance of one of the file's circuits, or nothing. Each carries
 * the notes the report shows for it.
 */
public sealed interface LogisimMapping permits MappedPart, MappedText, MappedSubcircuit, NotMapped {

    /** What the report says about this component; empty when it maps as it is. */
    List<ImportNote> notes();
}
