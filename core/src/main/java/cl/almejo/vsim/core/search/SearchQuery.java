package cl.almejo.vsim.core.search;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * What the user typed in a find field and its two options: «Aa» ({@code matchCase}) and «.*» ({@code regex}).
 * {@link #compile()} turns it into the matcher the find bar and the component tree's filter share.
 */
public record SearchQuery(String text, boolean matchCase, boolean regex) {

    public SearchQuery {
        text = text == null ? "" : text;
    }

    /**
     * Plain text is a substring, case- and accent-insensitive unless {@code matchCase}; a regular expression is found
     * anywhere ({@link java.util.regex.Matcher#find()}), case-insensitive (Unicode) unless {@code matchCase}, and
     * keeps accents. Empty text matches nothing; an invalid regular expression matches nothing and says why.
     */
    public CompiledQuery compile() {
        if (text.isEmpty()) {
            return CompiledQuery.nothing();
        }
        if (!regex) {
            return CompiledQuery.plain(text, matchCase);
        }
        try {
            int flags = matchCase ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
            return CompiledQuery.regex(Pattern.compile(text, flags));
        } catch (PatternSyntaxException exception) {
            return CompiledQuery.invalid(problemOf(exception));
        }
    }

    /** The common mistakes get a reason of their own; anything else keeps Java's description. */
    private static SearchProblem problemOf(PatternSyntaxException exception) {
        String description = Objects.requireNonNullElse(exception.getDescription(), "");
        if (description.startsWith("Unclosed group")) {
            return new SearchProblem(SearchProblem.UNCLOSED_GROUP, List.of());
        }
        if (description.startsWith("Unclosed character class")) {
            return new SearchProblem(SearchProblem.UNCLOSED_CHARACTER_CLASS, List.of());
        }
        if (description.startsWith("Unmatched closing ')'")) {
            return new SearchProblem(SearchProblem.UNMATCHED_CLOSING_PARENTHESIS, List.of());
        }
        if (description.startsWith("Dangling meta character")) {
            String pattern = Objects.requireNonNullElse(exception.getPattern(), "");
            int index = exception.getIndex();
            String character = index >= 0 && index < pattern.length() ? String.valueOf(pattern.charAt(index)) : "";
            return new SearchProblem(SearchProblem.NOTHING_TO_REPEAT, List.of(character));
        }
        if (description.startsWith("Unclosed counted closure") || description.startsWith("Illegal repetition")) {
            return new SearchProblem(SearchProblem.BAD_REPETITION, List.of());
        }
        if (description.startsWith("Unescaped trailing backslash")
                || description.startsWith("Illegal/unsupported escape sequence")) {
            return new SearchProblem(SearchProblem.BAD_ESCAPE, List.of());
        }
        if (description.startsWith("Illegal character range")) {
            return new SearchProblem(SearchProblem.BAD_RANGE, List.of());
        }
        return new SearchProblem(SearchProblem.INVALID_REGEX, List.of(description));
    }
}
