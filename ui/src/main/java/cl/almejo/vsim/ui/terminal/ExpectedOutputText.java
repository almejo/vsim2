package cl.almejo.vsim.ui.terminal;

import cl.almejo.vsim.core.terminal.ExpectedOutput;
import cl.almejo.vsim.ui.i18n.Messages;

import java.util.OptionalInt;

/**
 * The sentences of the «Salida esperada» tab (spec §8.9, mockup §5): «✓ La salida es igual a la esperada», «… va
 * bien: 12 de 20 caracteres iguales…», «✗ Difiere en el carácter 7: se esperaba '1' y llegó '2'», and what to do when
 * there is nothing to compare with. The comparison is {@link ExpectedOutput}'s, the same one the AI reads.
 */
final class ExpectedOutputText {

    private ExpectedOutputText() {
    }

    /** The sentence for {@code status}. */
    static String message(ExpectedOutput.Status status) {
        return switch (status) {
            case ExpectedOutput.Equal equal -> Messages.text("terminal.expected.equal");
            case ExpectedOutput.GoingWell goingWell -> Messages.format("terminal.expected.goingWell",
                    goingWell.matched(), goingWell.total());
            case ExpectedOutput.Differs differs -> Messages.format("terminal.expected.differs", differs.position(),
                    shown(differs.expected()), shown(differs.actual()));
        };
    }

    /** The part has «Salida esperada: Ninguna». */
    static String none() {
        return Messages.text("terminal.expected.none");
    }

    /** «Desde un archivo .txt» with no file chosen. */
    static String noFile() {
        return Messages.text("terminal.expected.noFile");
    }

    /** The expected file {@code shownPath} could not be read. */
    static String unreadable(String shownPath) {
        return Messages.format("terminal.expected.unreadable", shownPath);
    }

    /** A character as the sentence names it: «'1'», «salto de línea», or «nada» past the end. */
    private static String shown(OptionalInt character) {
        if (character.isEmpty()) {
            return Messages.text("terminal.expected.nothing");
        }
        if (character.getAsInt() == '\n') {
            return Messages.text("terminal.expected.lineFeed");
        }
        return Messages.format("terminal.expected.character", Character.toString(character.getAsInt()));
    }
}
