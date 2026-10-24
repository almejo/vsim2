package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.NoteColor;
import cl.almejo.vsim.core.document.NoteStyle;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.geometry.GridPoint;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SetNoteStyleCommandTest {

    private final CircuitDocument document = new CircuitDocument();

    private TextLabel note(String text, NoteStyle style) {
        TextLabel note = new TextLabel(document.newLabelId(), new GridPoint(0, document.labels().size() * 3), text,
                style);
        document.add(note);
        return note;
    }

    private NoteStyle styleOf(TextLabel note) {
        return document.label(note.id()).orElseThrow().style();
    }

    @Test
    void oneColourChangesOnEveryNoteAndTheOthersKeepTheirs() {
        TextLabel plain = note("uno", NoteStyle.PLAIN);
        TextLabel bordered = note("dos", new NoteStyle(NoteColor.NONE, NoteColor.BLUE, NoteColor.RED));

        Optional<Command> command = SetNoteStyleCommand.setting(document, List.of(plain.id(), bordered.id()),
                NoteStyle.Role.BACKGROUND, NoteColor.YELLOW);
        command.orElseThrow().apply(document);

        assertThat(styleOf(plain)).isEqualTo(new NoteStyle(NoteColor.YELLOW, NoteColor.NONE, NoteColor.NONE));
        assertThat(styleOf(bordered)).isEqualTo(new NoteStyle(NoteColor.YELLOW, NoteColor.BLUE, NoteColor.RED));
        assertThat(command.get().description()).isEqualTo("command.noteStyle");
    }

    @Test
    void revertGivesEachNoteItsOwnStyleBackAndKeepsItsText() {
        TextLabel plain = note("uno", NoteStyle.PLAIN);
        TextLabel bordered = note("dos", new NoteStyle(NoteColor.NONE, NoteColor.BLUE, NoteColor.RED));
        Command command = SetNoteStyleCommand.setting(document, List.of(plain.id(), bordered.id()),
                NoteStyle.Role.BORDER, NoteColor.GREEN).orElseThrow();

        command.apply(document);
        command.revert(document);

        assertThat(document.labels()).containsExactly(plain, bordered);
    }

    @Test
    void aChangeThatChangesNothingIsNoCommand() {
        TextLabel yellow = note("uno", new NoteStyle(NoteColor.YELLOW, NoteColor.NONE, NoteColor.NONE));

        assertThat(SetNoteStyleCommand.setting(document, List.of(yellow.id()), NoteStyle.Role.BACKGROUND,
                NoteColor.YELLOW)).isEmpty();
    }

    @Test
    void movingAndRotatingAStyledNoteKeepItsStyle() {
        NoteStyle style = new NoteStyle(NoteColor.GRAY, NoteColor.WHITE, NoteColor.BLACK);
        TextLabel note = note("uno", style);

        assertThat(note.translate(3, 4).style()).isEqualTo(style);
        assertThat(note.withText("otro").style()).isEqualTo(style);
        new RotateElementsCommand(List.of(note.id()), true,
                cl.almejo.vsim.core.component.standard.StandardComponentTypes.registry()).apply(document);
        assertThat(styleOf(note)).isEqualTo(style);
    }

    @Test
    void aPlainStyleIsNotBoxedAndAnyBackgroundOrBorderIs() {
        assertThat(NoteStyle.PLAIN.isPlain()).isTrue();
        assertThat(NoteStyle.PLAIN.boxed()).isFalse();
        assertThat(NoteStyle.PLAIN.with(NoteStyle.Role.TEXT, NoteColor.RED).boxed()).isFalse();
        assertThat(NoteStyle.PLAIN.with(NoteStyle.Role.BORDER, NoteColor.RED).boxed()).isTrue();
        assertThat(NoteStyle.PLAIN.with(NoteStyle.Role.BACKGROUND, NoteColor.RED).boxed()).isTrue();
    }
}
