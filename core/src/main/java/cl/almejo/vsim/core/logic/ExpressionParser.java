package cl.almejo.vsim.core.logic;

import cl.almejo.vsim.core.logic.Expression.And;
import cl.almejo.vsim.core.logic.Expression.Constant;
import cl.almejo.vsim.core.logic.Expression.Not;
import cl.almejo.vsim.core.logic.Expression.Or;
import cl.almejo.vsim.core.logic.Expression.Variable;
import cl.almejo.vsim.core.logic.Expression.Xor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Reads one line of «Circuito desde expresiones» (spec §3): {@code NAME = expression}, or an expression alone (a
 * state machine's condition, {@link #parseExpression}).
 * <p>
 * Names are a letter followed by letters, digits or {@code _} ({@code A}, {@code Cin}, {@code A1}, {@code carry_in});
 * letters written together are one name ({@code AB}). NOT is a postfix {@code '} (binds tightest) or a prefix
 * {@code ¬ ! ~}; AND is {@code * · & ∧}, a space between two operands, or two operands written together where a name
 * cannot continue (after {@code '} or {@code )}, before {@code (} or a prefix NOT, around the constants); XOR is
 * {@code ^ ⊕}; OR is {@code + | ∨}; the constants are {@code 0} and {@code 1} (each digit on its own: {@code 10} is
 * 1·0). Precedence NOT &gt; AND &gt; XOR &gt; OR, all left-associative; chains of one operator are flattened into one
 * n-ary operation, also through parentheses ({@code (A B) C} is the AND of A, B and C).
 */
public final class ExpressionParser {

    /** Names that look like letters written together by mistake: two to four capital letters ({@code AB}). */
    private static final Pattern MERGED_LETTERS = Pattern.compile("[A-Z]{2,4}");

    private ExpressionParser() {
    }

    /**
     * What {@link #parseLine} read: the line or the first problem (the leftmost, roughly), and the names that look
     * like merged letters (only for a line without problems; each once, in order of appearance).
     */
    public record ParseResult(Optional<ExpressionLine> line, Optional<ExpressionProblem> problem,
                              List<String> mergedNames) {

        public ParseResult {
            Objects.requireNonNull(line, "line");
            Objects.requireNonNull(problem, "problem");
            mergedNames = List.copyOf(mergedNames);
            if (line.isPresent() == problem.isPresent()) {
                throw new IllegalArgumentException("either a line or a problem");
            }
        }

        static ParseResult failed(ExpressionProblem problem) {
            return new ParseResult(Optional.empty(), Optional.of(problem), List.of());
        }
    }

    /** What {@link #parseExpression} read: the expression or its first problem, and the merged names like a line's. */
    public record ExpressionResult(Optional<Expression> expression, Optional<ExpressionProblem> problem,
                                   List<String> mergedNames) {

        public ExpressionResult {
            Objects.requireNonNull(expression, "expression");
            Objects.requireNonNull(problem, "problem");
            mergedNames = List.copyOf(mergedNames);
            if (expression.isPresent() == problem.isPresent()) {
                throw new IllegalArgumentException("either an expression or a problem");
            }
        }

        static ExpressionResult failed(ExpressionProblem problem) {
            return new ExpressionResult(Optional.empty(), Optional.of(problem), List.of());
        }
    }

    /** True for a valid variable or output name: a letter, then letters, digits or {@code _}. */
    public static boolean isName(String text) {
        if (text.isEmpty() || !Character.isLetter(text.charAt(0)) || isPrime(text.charAt(0))) {
            return false;
        }
        for (int index = 1; index < text.length(); index++) {
            if (!isNamePart(text.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    /** Reads {@code text}; columns in the result are offsets in {@code text}. */
    public static ParseResult parseLine(String text) {
        if (text.isBlank()) {
            return ParseResult.failed(ExpressionProblem.general(ExpressionProblem.EMPTY_LINE));
        }
        int equals = text.indexOf('=');
        if (equals < 0) {
            return ParseResult.failed(ExpressionProblem.general(ExpressionProblem.MISSING_EQUALS));
        }
        String left = text.substring(0, equals);
        String outputName = left.strip();
        if (outputName.isEmpty()) {
            return ParseResult.failed(ExpressionProblem.at(equals, ExpressionProblem.BLANK_OUTPUT_NAME));
        }
        if (!isName(outputName)) {
            return ParseResult.failed(ExpressionProblem.at(left.indexOf(outputName),
                    ExpressionProblem.INVALID_OUTPUT_NAME, outputName));
        }
        if (text.substring(equals + 1).isBlank()) {
            return ParseResult.failed(ExpressionProblem.at(equals + 1, ExpressionProblem.EMPTY_EXPRESSION));
        }
        ExpressionResult result = parseFrom(text, equals + 1);
        if (result.problem().isPresent()) {
            return ParseResult.failed(result.problem().get());
        }
        return new ParseResult(Optional.of(new ExpressionLine(outputName, result.expression().orElseThrow())),
                Optional.empty(), result.mergedNames());
    }

    /**
     * Reads {@code text} as an expression alone, without {@code NAME =} (a state machine's condition); columns in the
     * result are offsets in {@code text}. A blank text is {@link ExpressionProblem#EMPTY_EXPRESSION}.
     */
    public static ExpressionResult parseExpression(String text) {
        Objects.requireNonNull(text, "text");
        if (text.isBlank()) {
            return ExpressionResult.failed(ExpressionProblem.at(0, ExpressionProblem.EMPTY_EXPRESSION));
        }
        return parseFrom(text, 0);
    }

    /**
     * Where a name is written in {@code text}: the name and its {@code [start, end)} offsets. A state machine renames a
     * pin by rewriting these spans, so the rest of the user's text (spacing, primes, operators) stays as typed.
     */
    public record NameSpan(String name, int start, int end) {

        public NameSpan {
            Objects.requireNonNull(name, "name");
            if (start < 0 || end <= start) {
                throw new IllegalArgumentException("a name span needs 0 <= start < end: " + start + ", " + end);
            }
        }
    }

    /**
     * Every name written in {@code text}, in order, read from the tokens alone: it works on a text that does not
     * parse ({@code x +}), and an unknown character is skipped instead of ending the reading.
     */
    public static List<NameSpan> nameSpans(String text) {
        Objects.requireNonNull(text, "text");
        List<NameSpan> spans = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            List<Token> tokens = tokens(text, start);
            for (Token token : tokens) {
                if (token.kind() == Kind.NAME) {
                    spans.add(new NameSpan(token.text(), token.column(), token.column() + token.text().length()));
                }
            }
            Token last = tokens.getLast();
            start = last.kind() == Kind.INVALID ? last.column() + last.text().length() : text.length();
        }
        return List.copyOf(spans);
    }

    private static ExpressionResult parseFrom(String text, int start) {
        Parser parser = new Parser(tokens(text, start));
        try {
            Expression expression = parser.parse();
            return new ExpressionResult(Optional.of(expression), Optional.empty(), mergedNames(expression));
        } catch (ParseFailure failure) {
            return ExpressionResult.failed(failure.problem);
        }
    }

    private static List<String> mergedNames(Expression expression) {
        Set<String> names = new LinkedHashSet<>();
        for (String name : expression.variables()) {
            if (MERGED_LETTERS.matcher(name).matches()) {
                names.add(name);
            }
        }
        return List.copyOf(names);
    }

    // --- tokens ---

    private enum Kind {
        NAME, CONSTANT, PREFIX_NOT, POSTFIX_NOT, AND, XOR, OR, OPEN, CLOSE, END, INVALID;

        /** Tokens an operand can start with: two operands side by side are an AND. */
        boolean startsOperand() {
            return this == NAME || this == CONSTANT || this == PREFIX_NOT || this == OPEN;
        }
    }

    private record Token(Kind kind, String text, int column) {
    }

    /**
     * The tokens of {@code text} from {@code start}, ending with END; an unknown character ends them as an INVALID
     * token, which the parser reports when it gets there (so an earlier problem wins).
     */
    private static List<Token> tokens(String text, int start) {
        List<Token> tokens = new ArrayList<>();
        int index = start;
        while (index < text.length()) {
            char character = text.charAt(index);
            if (Character.isWhitespace(character) || Character.isSpaceChar(character)) {
                index++;
                continue;
            }
            if (Character.isLetter(character) && !isPrime(character)) {
                int end = index + 1;
                while (end < text.length() && isNamePart(text.charAt(end))) {
                    end++;
                }
                tokens.add(new Token(Kind.NAME, text.substring(index, end), index));
                index = end;
                continue;
            }
            Kind kind = switch (character) {
                case '0', '1' -> Kind.CONSTANT;
                case '¬', '!', '~' -> Kind.PREFIX_NOT;
                case '\'', '’', '′', 'ʹ' -> Kind.POSTFIX_NOT;
                case '*', '·', '&', '∧' -> Kind.AND;
                case '^', '⊕' -> Kind.XOR;
                case '+', '|', '∨' -> Kind.OR;
                case '(' -> Kind.OPEN;
                case ')' -> Kind.CLOSE;
                default -> Kind.INVALID;
            };
            if (kind == Kind.INVALID) {
                // Named whole, also a character outside the basic plane (an emoji is two chars).
                int codePoint = text.codePointAt(index);
                tokens.add(new Token(kind, Character.toString(codePoint), index));
                return tokens;
            }
            tokens.add(new Token(kind, String.valueOf(character), index));
            index++;
        }
        tokens.add(new Token(Kind.END, "", text.length()));
        return tokens;
    }

    private static boolean isNamePart(char character) {
        return (Character.isLetterOrDigit(character) || character == '_') && !isPrime(character);
    }

    /** {@code ʹ} (modifier letter prime) is a letter to Java, but pasted after a name it means NOT, like {@code '}. */
    private static boolean isPrime(char character) {
        return character == 'ʹ';
    }

    // --- grammar ---

    private static final class ParseFailure extends Exception {

        final transient ExpressionProblem problem;

        ParseFailure(ExpressionProblem problem) {
            super(problem.messageKey(), null, false, false);
            this.problem = problem;
        }
    }

    /** Recursive descent: or := xor (OR xor)*; xor := and (XOR and)*; and := unary ((AND | side by side) unary)*. */
    private static final class Parser {

        /** Deeper nesting than this is a problem, not a stack overflow; far beyond anything a person writes. */
        static final int MAXIMUM_DEPTH = 500;

        private final List<Token> tokens;
        private int position;
        private int depth;

        Parser(List<Token> tokens) {
            this.tokens = tokens;
        }

        Expression parse() throws ParseFailure {
            Expression expression = or();
            Token next = peek();
            if (next.kind() != Kind.END) {
                throw failure(next, ExpressionProblem.UNEXPECTED_CLOSING_PARENTHESIS);
            }
            return expression;
        }

        private Expression or() throws ParseFailure {
            List<Expression> operands = new ArrayList<>();
            add(operands, xor(), Or.class, Or::operands);
            while (peek().kind() == Kind.OR) {
                position++;
                add(operands, xor(), Or.class, Or::operands);
            }
            return operands.size() == 1 ? operands.get(0) : new Or(operands);
        }

        private Expression xor() throws ParseFailure {
            List<Expression> operands = new ArrayList<>();
            add(operands, and(), Xor.class, Xor::operands);
            while (peek().kind() == Kind.XOR) {
                position++;
                add(operands, and(), Xor.class, Xor::operands);
            }
            return operands.size() == 1 ? operands.get(0) : new Xor(operands);
        }

        private Expression and() throws ParseFailure {
            List<Expression> operands = new ArrayList<>();
            add(operands, unary(), And.class, And::operands);
            while (peek().kind() == Kind.AND || peek().kind().startsOperand()) {
                if (peek().kind() == Kind.AND) {
                    position++;
                }
                add(operands, unary(), And.class, And::operands);
            }
            return operands.size() == 1 ? operands.get(0) : new And(operands);
        }

        /** Adds {@code operand}, or its operands when it is the same operation (from parentheses). */
        private static <T extends Expression> void add(List<Expression> operands, Expression operand,
                                                       Class<T> operation, Function<T, List<Expression>> children) {
            if (operation.isInstance(operand)) {
                operands.addAll(children.apply(operation.cast(operand)));
            } else {
                operands.add(operand);
            }
        }

        private Expression unary() throws ParseFailure {
            int depthOnEntry = depth;
            try {
                if (peek().kind() == Kind.PREFIX_NOT) {
                    enter(peek());
                    position++;
                    return new Not(unary());
                }
                Expression expression = primary();
                while (peek().kind() == Kind.POSTFIX_NOT) {
                    enter(peek());
                    position++;
                    expression = new Not(expression);
                }
                return expression;
            } finally {
                depth = depthOnEntry;
            }
        }

        private Expression primary() throws ParseFailure {
            Token token = peek();
            switch (token.kind()) {
                case NAME -> {
                    position++;
                    return new Variable(token.text());
                }
                case CONSTANT -> {
                    position++;
                    return new Constant(token.text().equals("1"));
                }
                case OPEN -> {
                    enter(token);
                    position++;
                    Expression inside = or();
                    depth--;
                    Token closing = peek();
                    if (closing.kind() == Kind.CLOSE) {
                        position++;
                        return inside;
                    }
                    if (closing.kind() == Kind.END) {
                        throw new ParseFailure(ExpressionProblem.at(token.column(),
                                ExpressionProblem.UNCLOSED_PARENTHESIS));
                    }
                    throw failure(closing, ExpressionProblem.UNCLOSED_PARENTHESIS);
                }
                default -> throw failure(token, ExpressionProblem.MISSING_OPERAND);
            }
        }

        /** One more level of nesting (NOT or parentheses), refused past {@link #MAXIMUM_DEPTH}. */
        private void enter(Token token) throws ParseFailure {
            depth++;
            if (depth > MAXIMUM_DEPTH) {
                throw new ParseFailure(ExpressionProblem.at(token.column(), ExpressionProblem.TOO_NESTED));
            }
        }

        private Token peek() {
            return tokens.get(position);
        }

        /** {@code messageKey} at {@code token}, or the unknown character when that is what the token is. */
        private static ParseFailure failure(Token token, String messageKey) {
            if (token.kind() == Kind.INVALID) {
                return new ParseFailure(ExpressionProblem.at(token.column(), ExpressionProblem.UNEXPECTED_CHARACTER,
                        token.text()));
            }
            return new ParseFailure(ExpressionProblem.at(token.column(), messageKey));
        }
    }
}
