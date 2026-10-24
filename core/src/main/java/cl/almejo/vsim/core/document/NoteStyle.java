package cl.almejo.vsim.core.document;

import java.util.Objects;

/**
 * How a note (a {@link TextLabel}) looks: its background, its text colour and its border, each a {@link NoteColor}.
 * {@link #PLAIN} is the note as it always was; a plain note writes no style to the file, so files keep their bytes.
 */
public record NoteStyle(NoteColor background, NoteColor text, NoteColor border) {

    /** No background, the scheme's text colour, no border. */
    public static final NoteStyle PLAIN = new NoteStyle(NoteColor.NONE, NoteColor.NONE, NoteColor.NONE);

    /** The three parts of a style, in the order the inspector and the context menu show them. */
    public enum Role {
        BACKGROUND, TEXT, BORDER
    }

    public NoteStyle {
        Objects.requireNonNull(background, "background");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(border, "border");
    }

    /** True for {@link #PLAIN}. */
    public boolean isPlain() {
        return equals(PLAIN);
    }

    /** True when the note is drawn in a box: it has a background or a border (a text colour alone draws no box). */
    public boolean boxed() {
        return background != NoteColor.NONE || border != NoteColor.NONE;
    }

    /** The colour of {@code role}. */
    public NoteColor color(Role role) {
        return switch (role) {
            case BACKGROUND -> background;
            case TEXT -> text;
            case BORDER -> border;
        };
    }

    /** This style with {@code role} in {@code color}. */
    public NoteStyle with(Role role, NoteColor color) {
        return switch (role) {
            case BACKGROUND -> new NoteStyle(color, text, border);
            case TEXT -> new NoteStyle(background, color, border);
            case BORDER -> new NoteStyle(background, text, color);
        };
    }
}
