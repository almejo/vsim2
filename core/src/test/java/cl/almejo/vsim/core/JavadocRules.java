package cl.almejo.vsim.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds Javadoc comments that document nothing (javac 21 has no {@code dangling-doc-comments} lint) and Javadoc that
 * links to the very member it documents. A small tokenizer plus a classification of what follows a {@code /** *}{@code /}
 * comment: after annotations and modifiers it must be a type, field, method, constructor, enum constant or package
 * declaration. It is a heuristic for the shapes this code base writes, tested in {@code JavadocRulesTest}; it never
 * has to parse a whole file.
 */
final class JavadocRules {

    private enum Kind { WORD, SYMBOL, LINE_COMMENT, BLOCK_COMMENT, DOC, LITERAL }

    private record Token(Kind kind, String text, int line) {
        boolean isSymbol(String symbol) {
            return kind == Kind.SYMBOL && text.equals(symbol);
        }

        boolean isWord(String word) {
            return kind == Kind.WORD && text.equals(word);
        }

        boolean isComment() {
            return kind == Kind.LINE_COMMENT || kind == Kind.BLOCK_COMMENT || kind == Kind.DOC;
        }
    }

    private enum DeclarationKind { TYPE, FIELD, METHOD, CONSTRUCTOR, ENUM_CONSTANT, PACKAGE }

    /** What a Javadoc documents: its kind, its name, and for methods the simple names of the parameter types. */
    private record Declaration(DeclarationKind kind, String name, List<String> parameterTypes) {
    }

    private static final Set<String> MODIFIERS = Set.of("public", "protected", "private", "static", "final",
            "abstract", "sealed", "non-sealed", "default", "synchronized", "native", "transient", "volatile",
            "strictfp");
    private static final Set<String> STATEMENT_WORDS = Set.of("return", "throw", "new", "if", "for", "while", "switch",
            "else", "try", "do", "yield", "assert", "case", "break", "continue", "import", "this", "super", "var");
    private static final Pattern LINK = Pattern.compile(
            "(?:\\{@(?:link|linkplain|value)|@see)\\s+#(\\w+)(?:\\(([^)]*)\\))?");
    private static final Pattern CONSTANT_NAME = Pattern.compile("[A-Z][A-Z0-9_]*");

    private JavadocRules() {
    }

    /** One message per problem, «line N: ...», in source order; empty when the source is clean. */
    static List<String> problems(String source) {
        List<Token> tokens = tokenize(source);
        List<String> problems = new ArrayList<>();
        for (int index = 0; index < tokens.size(); index++) {
            Token token = tokens.get(index);
            if (token.kind() != Kind.DOC) {
                continue;
            }
            Declaration declaration = documented(tokens, index + 1);
            if (declaration == null) {
                problems.add("line " + token.line() + ": dangling Javadoc, it documents no declaration (make it a"
                        + " // or /* */ comment or move it onto the declaration)");
                continue;
            }
            String selfLink = selfLink(token.text(), declaration, tokens);
            if (selfLink != null) {
                problems.add("line " + token.line() + ": Javadoc links to itself ({@link " + selfLink + "}), the"
                        + " member it documents");
            }
        }
        return problems;
    }

    private static String selfLink(String doc, Declaration declaration, List<Token> tokens) {
        Matcher matcher = LINK.matcher(doc);
        while (matcher.find()) {
            if (!matcher.group(1).equals(declaration.name())) {
                continue;
            }
            switch (declaration.kind()) {
                case FIELD, ENUM_CONSTANT -> {
                    // «#name()» is the accessor method of the same name, not the field.
                    if (matcher.group(2) == null && !matcher.group().endsWith(")")) {
                        return "#" + declaration.name();
                    }
                }
                case METHOD, CONSTRUCTOR -> {
                    // Overloads link to each other on purpose: only the same parameter list is the member itself.
                    if (matcher.group(2) != null && parameterTypes(matcher.group(2)).equals(declaration.parameterTypes())) {
                        return "#" + declaration.name() + "(...)";
                    }
                    // «#name» without parentheses is the method itself unless a field or an overload shares the name.
                    if (matcher.group(2) == null && !matcher.group().endsWith(")")
                            && declaration.kind() == DeclarationKind.METHOD
                            && declarationCount(tokens, declaration.name(), "(") == 1
                            && declarationCount(tokens, declaration.name(), ";", "=", ",") == 0) {
                        return "#" + declaration.name();
                    }
                }
                default -> {
                }
            }
        }
        return null;
    }

    /**
     * How many times {@code name} is declared in the file followed by one of {@code followers}: a type-like word (or a
     * closing {@code >} / {@code ]}) before it, and the follower after it. Fields and methods only; a local variable
     * or a call can be counted too, which only makes the rule report less.
     */
    private static int declarationCount(List<Token> tokens, String name, String... followers) {
        int count = 0;
        for (int index = 1; index + 1 < tokens.size(); index++) {
            Token token = tokens.get(index);
            Token before = tokens.get(index - 1);
            Token after = tokens.get(index + 1);
            boolean typeBefore = (before.kind() == Kind.WORD && !STATEMENT_WORDS.contains(before.text()))
                    || before.isSymbol(">") || before.isSymbol("]");
            if (token.isWord(name) && typeBefore && List.of(followers).contains(after.text())
                    && after.kind() == Kind.SYMBOL) {
                count++;
            }
        }
        return count;
    }

    /** What the Javadoc whose token precedes {@code start} documents, or null when it documents nothing. */
    private static Declaration documented(List<Token> tokens, int start) {
        int index = start;
        while (index < tokens.size()) {
            Token token = tokens.get(index);
            if (token.isSymbol("@") && index + 1 < tokens.size() && !tokens.get(index + 1).isWord("interface")) {
                index = skipAnnotation(tokens, index);
            } else if (token.kind() == Kind.WORD && MODIFIERS.contains(token.text())) {
                index++;
            } else {
                break;
            }
        }
        if (index >= tokens.size() || tokens.get(index).isComment() || tokens.get(index).isSymbol("}")) {
            return null;
        }
        Token first = tokens.get(index);
        if (first.isSymbol("@") && tokens.get(index + 1).isWord("interface")) {
            return new Declaration(DeclarationKind.TYPE, wordAt(tokens, index + 2), List.of());
        }
        if (first.kind() == Kind.WORD) {
            switch (first.text()) {
                case "class", "interface", "enum" -> {
                    return new Declaration(DeclarationKind.TYPE, wordAt(tokens, index + 1), List.of());
                }
                case "package" -> {
                    return new Declaration(DeclarationKind.PACKAGE, wordAt(tokens, index + 1), List.of());
                }
                case "record" -> {
                    if (index + 2 < tokens.size() && tokens.get(index + 1).kind() == Kind.WORD
                            && (tokens.get(index + 2).isSymbol("(") || tokens.get(index + 2).isSymbol("<"))) {
                        return new Declaration(DeclarationKind.TYPE, tokens.get(index + 1).text(), List.of());
                    }
                }
                default -> {
                }
            }
            if (STATEMENT_WORDS.contains(first.text())) {
                return null;
            }
        }
        return member(tokens, index);
    }

    private static Declaration member(List<Token> tokens, int start) {
        List<String> words = new ArrayList<>();
        int angleDepth = 0;
        int index = start;
        String lastWord = null;
        boolean afterDot = false;
        while (index < tokens.size()) {
            Token token = tokens.get(index);
            if (token.kind() == Kind.WORD) {
                if (angleDepth == 0) {
                    if (afterDot && lastWord != null) {
                        words.set(words.size() - 1, words.get(words.size() - 1) + "." + token.text());
                    } else {
                        words.add(token.text());
                    }
                }
                afterDot = false;
                lastWord = token.text();
            } else if (token.isSymbol("<")) {
                angleDepth++;
            } else if (token.isSymbol(">")) {
                angleDepth = Math.max(0, angleDepth - 1);
            } else if (token.isSymbol(".") && angleDepth == 0) {
                afterDot = true;
                index++;
                continue;
            } else if (angleDepth == 0 && (token.isSymbol(";") || token.isSymbol("=") || token.isSymbol("(")
                    || token.isSymbol("{") || token.isSymbol(",") || token.isSymbol("}"))) {
                return classify(tokens, index, words);
            } else if (token.kind() == Kind.LITERAL || token.isComment()) {
                return null;
            }
            afterDot = false;
            index++;
        }
        return null;
    }

    private static Declaration classify(List<Token> tokens, int terminatorIndex, List<String> words) {
        String terminator = tokens.get(terminatorIndex).text();
        if (words.isEmpty() || STATEMENT_WORDS.contains(words.get(0))) {
            return null;
        }
        String name = words.get(words.size() - 1);
        boolean qualified = name.contains(".");
        switch (terminator) {
            case "(" -> {
                if (qualified) {
                    return null;
                }
                int close = closing(tokens, terminatorIndex);
                if (words.size() >= 2) {
                    return new Declaration(DeclarationKind.METHOD, name,
                            parameterTypes(tokens.subList(terminatorIndex + 1, close)));
                }
                Token after = close + 1 < tokens.size() ? tokens.get(close + 1) : null;
                if (after != null && (after.isSymbol("{") || after.isWord("throws"))) {
                    return new Declaration(DeclarationKind.CONSTRUCTOR, name,
                            parameterTypes(tokens.subList(terminatorIndex + 1, close)));
                }
                if (CONSTANT_NAME.matcher(name).matches()) {
                    return new Declaration(DeclarationKind.ENUM_CONSTANT, name, List.of());
                }
                return null;
            }
            case ";", "=", "," -> {
                if (words.size() >= 2 && !qualified) {
                    return new Declaration(DeclarationKind.FIELD, name, List.of());
                }
                if (words.size() == 1 && !terminator.equals("=") && CONSTANT_NAME.matcher(name).matches()) {
                    return new Declaration(DeclarationKind.ENUM_CONSTANT, name, List.of());
                }
                return null;
            }
            case "}" -> {
                return words.size() == 1 && CONSTANT_NAME.matcher(name).matches()
                        ? new Declaration(DeclarationKind.ENUM_CONSTANT, name, List.of()) : null;
            }
            case "{" -> {
                // A compact record constructor, or an enum constant with a body.
                return words.size() == 1 && !qualified && Character.isUpperCase(name.charAt(0))
                        ? new Declaration(DeclarationKind.CONSTRUCTOR, name, List.of()) : null;
            }
            default -> {
                return null;
            }
        }
    }

    private static int skipAnnotation(List<Token> tokens, int atIndex) {
        int index = atIndex + 2;
        while (index + 1 < tokens.size() && tokens.get(index).isSymbol(".")) {
            index += 2;
        }
        if (index < tokens.size() && tokens.get(index).isSymbol("(")) {
            index = closing(tokens, index) + 1;
        }
        return index;
    }

    /** The index of the {@code )} that closes the {@code (} at {@code open}, or the last token when unbalanced. */
    private static int closing(List<Token> tokens, int open) {
        int depth = 0;
        for (int index = open; index < tokens.size(); index++) {
            if (tokens.get(index).isSymbol("(")) {
                depth++;
            } else if (tokens.get(index).isSymbol(")")) {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return tokens.size() - 1;
    }

    private static String wordAt(List<Token> tokens, int index) {
        return index < tokens.size() && tokens.get(index).kind() == Kind.WORD ? tokens.get(index).text() : "";
    }

    /** The simple type names of a declaration's parameters: each parameter minus its name, generics and package. */
    private static List<String> parameterTypes(List<Token> parameterTokens) {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < parameterTokens.size(); index++) {
            Token token = parameterTokens.get(index);
            if (token.isSymbol("@")) {
                index = skipAnnotation(parameterTokens, index) - 1;
                continue;
            }
            if (!token.isWord("final")) {
                text.append(token.text()).append(' ');
            }
        }
        return parameterTypes(text.toString(), true);
    }

    /** Splits at the top-level commas; with {@code withNames}, drops each parameter's last word (its name). */
    private static List<String> parameterTypes(String text, boolean withNames) {
        String withoutGenerics = stripGenerics(text).replace("...", "[]");
        List<String> types = new ArrayList<>();
        for (String parameter : withoutGenerics.split(",")) {
            String trimmed = parameter.trim().replaceAll("\\s*\\[\\s*]", "[]");
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] parts = trimmed.split("\\s+");
            String type = withNames && parts.length > 1 ? String.join("", java.util.Arrays.copyOf(parts, parts.length - 1))
                    : String.join("", parts);
            types.add(type.substring(type.lastIndexOf('.') + 1));
        }
        return types;
    }

    /** The types written in a {@code {@link #name(Type, Type)}}: no parameter names there. */
    private static List<String> parameterTypes(String linkText) {
        return parameterTypes(linkText, false);
    }

    private static String stripGenerics(String text) {
        StringBuilder result = new StringBuilder();
        int depth = 0;
        for (char character : text.toCharArray()) {
            if (character == '<') {
                depth++;
            } else if (character == '>') {
                depth = Math.max(0, depth - 1);
            } else if (depth == 0) {
                result.append(character);
            }
        }
        return result.toString();
    }

    private static List<Token> tokenize(String source) {
        List<Token> tokens = new ArrayList<>();
        int length = source.length();
        int line = 1;
        int index = 0;
        while (index < length) {
            char character = source.charAt(index);
            if (character == '\n') {
                line++;
                index++;
            } else if (Character.isWhitespace(character)) {
                index++;
            } else if (source.startsWith("//", index)) {
                int end = source.indexOf('\n', index);
                end = end < 0 ? length : end;
                tokens.add(new Token(Kind.LINE_COMMENT, source.substring(index, end), line));
                index = end;
            } else if (source.startsWith("/*", index)) {
                int end = source.indexOf("*/", index + 2);
                end = end < 0 ? length : end + 2;
                boolean doc = source.startsWith("/**", index) && !source.startsWith("/**/", index);
                String text = source.substring(index, end);
                tokens.add(new Token(doc ? Kind.DOC : Kind.BLOCK_COMMENT, text, line));
                line += (int) text.chars().filter(next -> next == '\n').count();
                index = end;
            } else if (source.startsWith("\"\"\"", index)) {
                int end = textBlockEnd(source, index + 3);
                String text = source.substring(index, end);
                tokens.add(new Token(Kind.LITERAL, text, line));
                line += (int) text.chars().filter(next -> next == '\n').count();
                index = end;
            } else if (character == '"' || character == '\'') {
                int end = quotedEnd(source, index, character);
                tokens.add(new Token(Kind.LITERAL, source.substring(index, end), line));
                index = end;
            } else if (Character.isJavaIdentifierStart(character) || Character.isDigit(character)) {
                int end = index + 1;
                while (end < length && (Character.isJavaIdentifierPart(source.charAt(end))
                        || (Character.isDigit(character) && source.charAt(end) == '.'))) {
                    end++;
                }
                String word = source.substring(index, end);
                if (word.equals("non") && source.startsWith("-sealed", end)) {
                    word = "non-sealed";
                    end += "-sealed".length();
                }
                tokens.add(new Token(Kind.WORD, word, line));
                index = end;
            } else {
                tokens.add(new Token(Kind.SYMBOL, String.valueOf(character), line));
                index++;
            }
        }
        return tokens;
    }

    private static int textBlockEnd(String source, int from) {
        int index = from;
        while (index < source.length()) {
            if (source.charAt(index) == '\\') {
                index += 2;
            } else if (source.startsWith("\"\"\"", index)) {
                return index + 3;
            } else {
                index++;
            }
        }
        return source.length();
    }

    private static int quotedEnd(String source, int start, char quote) {
        int index = start + 1;
        while (index < source.length()) {
            char character = source.charAt(index);
            if (character == '\\') {
                index += 2;
            } else if (character == quote) {
                return index + 1;
            } else if (character == '\n') {
                return index;
            } else {
                index++;
            }
        }
        return source.length();
    }
}
