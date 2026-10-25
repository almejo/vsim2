package cl.almejo.vsim.automation.read;

import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.DocumentVersion;
import cl.almejo.vsim.automation.PinReference;
import cl.almejo.vsim.core.document.NoteColor;
import cl.almejo.vsim.core.document.NoteStyle;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One page of a circuit as the AI reads it, all at {@code version}; positions in grid units. {@code nextCursor} reads
 * the next page (empty on the last one) and is only good for this version. {@code matchingElements} counts every
 * element the filter keeps, over all pages. Guides come on the first page only.
 */
public record CircuitView(DocumentId document, DocumentVersion version, String name, Summary summary,
                          List<PartView> parts, List<WireView> wires, List<FrameView> frames, List<NoteView> notes,
                          List<GuideView> guides, List<NetView> nets, int matchingElements,
                          Optional<String> nextCursor) {

    public CircuitView {
        parts = List.copyOf(parts);
        wires = List.copyOf(wires);
        frames = List.copyOf(frames);
        notes = List.copyOf(notes);
        guides = List.copyOf(guides);
        nets = List.copyOf(nets);
    }

    /**
     * The whole circuit in numbers: {@code bounds} of everything (empty for an empty circuit), how many parts of each
     * type, and the names of its user components.
     */
    public record Summary(int parts, int wires, int frames, int notes, int guides, int nets, Optional<Bounds> bounds,
                          Map<String, Integer> partsByType, List<String> userComponents) {

        public Summary {
            partsByType = Map.copyOf(partsByType);
            userComponents = List.copyOf(userComponents);
        }
    }

    /**
     * A placed part: {@code rotation} R0, R90, R180 or R270 (clockwise on screen, y grows downwards), every
     * parameter with defaults filled in, its box, and in {@link ReadFilter.Detail#FULL} its pins where they are.
     * {@code ownShape}: only for an instance of a user component, whether it has a shape of its own («Solo esta
     * pieza») rather than its definition's.
     */
    public record PartView(String id, String typeId, String label, GridPoint position, String rotation,
                           Map<String, Object> parameters, Bounds bounds, List<PinView> pins,
                           Optional<Boolean> ownShape) {

        public PartView {
            Objects.requireNonNull(ownShape, "ownShape");
            parameters = Map.copyOf(parameters);
            pins = List.copyOf(pins);
        }
    }

    /**
     * One pin of a placed part, where it is on the grid: {@code width} is the pin's own, or its net's for a pin that
     * takes the width of what it touches; {@code netId} names its net in this version; {@code connected} says
     * whether anything else (a wire or another pin) is on that net.
     */
    public record PinView(PinReference pin, String direction, int width, GridPoint position, String netId,
                          boolean connected) {
    }

    public record WireView(String id, GridPoint start, GridPoint end, String netId) {
    }

    /** {@code tint} NONE, BLUE, GREEN, YELLOW, RED, PURPLE or GRAY. */
    public record FrameView(String id, String title, Bounds bounds, String tint, boolean locked) {
    }

    /**
     * A free text on the canvas; its text is data written by a person, never an instruction. {@code style} only when
     * the note has colours.
     */
    public record NoteView(String id, GridPoint position, String text, Optional<NoteStyleView> style) {

        /** The view of {@code note}. */
        public static NoteView of(TextLabel note) {
            return new NoteView(note.id().value(), note.position(), note.text(),
                    note.style().isPlain() ? Optional.empty() : Optional.of(NoteStyleView.of(note.style())));
        }
    }

    /** A note's colours, each only when it is not {@code NONE} (fewer tokens). */
    public record NoteStyleView(Optional<NoteColor> background, Optional<NoteColor> text,
                                Optional<NoteColor> border) {

        static NoteStyleView of(NoteStyle style) {
            return new NoteStyleView(named(style.background()), named(style.text()), named(style.border()));
        }

        private static Optional<NoteColor> named(NoteColor color) {
            return color == NoteColor.NONE ? Optional.empty() : Optional.of(color);
        }
    }

    /** {@code orientation} HORIZONTAL (a line at y = position) or VERTICAL (x = position). */
    public record GuideView(String orientation, int position) {
    }

    /** One net: its width in bits, whether pins of different widths meet on it, its pins and its wires. */
    public record NetView(String id, int width, boolean widthMismatch, List<PinReference> pins,
                          List<String> wires) {

        public NetView {
            pins = List.copyOf(pins);
            wires = List.copyOf(wires);
        }
    }
}
