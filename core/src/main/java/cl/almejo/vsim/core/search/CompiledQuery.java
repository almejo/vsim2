package cl.almejo.vsim.core.search;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * A {@link SearchQuery} ready to test texts. Shared by the find bar and the component tree's filter so both always
 * agree.
 *
 * <p>Guard against pathological regular expressions (e.g. {@code (a+)+$}), which could otherwise freeze the EDT:
 * only the first {@link #MAXIMUM_TEXT_LENGTH} characters of a text are searched, and the regular expression reads
 * them through a {@link CharSequence} that counts every character read and aborts after {@link #STEP_BUDGET} reads.
 * The first time that happens the query gives up for good: from then on it matches nothing and {@link #problem()}
 * reports {@link SearchProblem#TOO_EXPENSIVE}, so a large document cannot add many budgets up to a freeze. The
 * budget counts steps, never time, so its outcome does not depend on the machine.
 */
public final class CompiledQuery {

    /** Texts are cut to this many characters before searching. */
    public static final int MAXIMUM_TEXT_LENGTH = 1_000;

    /** How many characters a regular expression may read for one text before the query gives up. */
    public static final int STEP_BUDGET = 2_000_000;

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");

    private final Kind kind;
    private final String needle;
    private final Pattern pattern;
    private final SearchProblem compileProblem;
    private final AtomicBoolean gaveUp = new AtomicBoolean();

    private enum Kind { NOTHING, PLAIN, PLAIN_FOLDED, REGEX, INVALID }

    private CompiledQuery(Kind kind, String needle, Pattern pattern, SearchProblem compileProblem) {
        this.kind = kind;
        this.needle = needle;
        this.pattern = pattern;
        this.compileProblem = compileProblem;
    }

    static CompiledQuery nothing() {
        return new CompiledQuery(Kind.NOTHING, null, null, null);
    }

    static CompiledQuery plain(String text, boolean matchCase) {
        return matchCase ? new CompiledQuery(Kind.PLAIN, text, null, null)
                : new CompiledQuery(Kind.PLAIN_FOLDED, fold(text), null, null);
    }

    static CompiledQuery regex(Pattern pattern) {
        return new CompiledQuery(Kind.REGEX, null, pattern, null);
    }

    static CompiledQuery invalid(SearchProblem problem) {
        return new CompiledQuery(Kind.INVALID, null, null, problem);
    }

    /** True when {@code text} (its first {@link #MAXIMUM_TEXT_LENGTH} characters) contains a match. */
    public boolean matches(String text) {
        if (text == null || text.isEmpty() || matchesNothing()) {
            return false;
        }
        String searched = text.length() > MAXIMUM_TEXT_LENGTH ? text.substring(0, MAXIMUM_TEXT_LENGTH) : text;
        return switch (kind) {
            case PLAIN -> searched.contains(needle);
            case PLAIN_FOLDED -> fold(searched).contains(needle);
            case REGEX -> findWithinBudget(searched);
            case NOTHING, INVALID -> false;
        };
    }

    /** True when no text can match: empty text, an invalid regular expression, or one that gave up. */
    public boolean matchesNothing() {
        return kind == Kind.NOTHING || kind == Kind.INVALID || gaveUp.get();
    }

    /** Why this query matches nothing on purpose; empty for an empty query, which is not a mistake. */
    public Optional<SearchProblem> problem() {
        if (compileProblem != null) {
            return Optional.of(compileProblem);
        }
        return gaveUp.get() ? Optional.of(new SearchProblem(SearchProblem.TOO_EXPENSIVE, List.of()))
                : Optional.empty();
    }

    private boolean findWithinBudget(String text) {
        try {
            return pattern.matcher(new BudgetedText(text, new int[]{STEP_BUDGET})).find();
        } catch (BudgetExhausted exhausted) {
            gaveUp.set(true);
            return false;
        } catch (StackOverflowError overflow) {
            // Deep backtracking recurses in java.util.regex; treat it like an exhausted budget.
            gaveUp.set(true);
            return false;
        }
    }

    /** Decomposes accents (NFD), drops the combining marks and lower-cases with {@link Locale#ROOT}. */
    private static String fold(String text) {
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        return COMBINING_MARKS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT);
    }

    /** A text whose reads share one budget (sub-sequences included). */
    private static final class BudgetedText implements CharSequence {
        private final String text;
        private final int[] remaining;

        BudgetedText(String text, int[] remaining) {
            this.text = text;
            this.remaining = remaining;
        }

        @Override
        public char charAt(int index) {
            if (--remaining[0] < 0) {
                throw new BudgetExhausted();
            }
            return text.charAt(index);
        }

        @Override
        public int length() {
            return text.length();
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return new BudgetedText(text.substring(start, end), remaining);
        }

        @Override
        public String toString() {
            return text;
        }
    }

    private static final class BudgetExhausted extends RuntimeException {
        BudgetExhausted() {
            super(null, null, false, false);
        }
    }
}
