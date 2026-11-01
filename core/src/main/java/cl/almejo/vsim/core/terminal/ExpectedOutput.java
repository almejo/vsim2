package cl.almejo.vsim.core.terminal;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * «Salida esperada» (spec §8.9): the text a Terminal received (its transcript) against the text it should receive.
 * One comparison for everyone who shows it, the window's «Salida esperada» tab and the AI's {@code vsim_read_terminal},
 * so they never disagree. Windows line ends count as line feeds and white space at the end does not count (a prompt's
 * trailing space or a last line feed in the .txt). Characters are counted as code points, from 1.
 */
public final class ExpectedOutput {

    /** What the comparison found. */
    public sealed interface Status permits Equal, GoingWell, Differs {
    }

    /** The received text is the expected one. */
    public record Equal() implements Status {
    }

    /** The received text is the beginning of the expected one: {@code matched} of {@code total} characters. */
    public record GoingWell(int matched, int total) implements Status {
    }

    /**
     * The first different character is number {@code position} (from 1): {@code expected} there, empty when the
     * expected text ended, and {@code actual}, empty when the received text ended.
     */
    public record Differs(int position, OptionalInt expected, OptionalInt actual) implements Status {

        public Differs {
            Objects.requireNonNull(expected, "expected");
            Objects.requireNonNull(actual, "actual");
        }
    }

    private ExpectedOutput() {
    }

    /** Compares the received text {@code actual} with {@code expected}. */
    public static Status compare(String expected, String actual) {
        String wanted = normalized(expected);
        String received = normalized(actual);
        if (wanted.stripTrailing().equals(received.stripTrailing())) {
            return new Equal();
        }
        if (wanted.startsWith(received)) {
            return new GoingWell(received.codePointCount(0, received.length()),
                    wanted.codePointCount(0, wanted.length()));
        }
        return firstDifference(wanted, received);
    }

    /** Whether {@code actual} is the {@code expected} text, as {@link #compare} says. */
    public static boolean matches(String expected, String actual) {
        return compare(expected, actual) instanceof Equal;
    }

    private static Differs firstDifference(String expected, String actual) {
        int[] wanted = expected.codePoints().toArray();
        int[] received = actual.codePoints().toArray();
        int index = 0;
        while (index < wanted.length && index < received.length && wanted[index] == received[index]) {
            index++;
        }
        return new Differs(index + 1, index < wanted.length ? OptionalInt.of(wanted[index]) : OptionalInt.empty(),
                index < received.length ? OptionalInt.of(received[index]) : OptionalInt.empty());
    }

    private static String normalized(String text) {
        return text.replace("\r\n", "\n");
    }
}
