package cl.almejo.vsim.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The dangling and self-pointing Javadoc parser of {@link JavadocRules}, on inline sources. */
class JavadocRulesTest {

    @Test
    void javadocOnEveryKindOfDeclarationIsFine() {
        String source = """
                package a;

                /** A class. */
                @Deprecated
                public final class Sample<T> {
                    /** A field. */
                    private static final int FIELD = 1;
                    /** A generic field. */
                    private final java.util.Map<String, java.util.List<T>> map = null;
                    /** A constructor. */
                    Sample(int value) {
                    }
                    /** A method. */
                    @SuppressWarnings({"a", "b"})
                    public <U> U method(U value) {
                        return value;
                    }
                    /** An interface. */
                    interface Inner { }
                    /** A record. */
                    record Pair(int first, int second) {
                        /** Compact. */
                        Pair {
                        }
                    }
                    /** An enum. */
                    enum Kind {
                        /** First. */
                        FIRST,
                        /** Second. */
                        SECOND(2),
                        /** Last. */
                        LAST;
                        Kind() { }
                        Kind(int value) { }
                    }
                }
                """;

        assertThat(JavadocRules.problems(source)).isEmpty();
    }

    @Test
    void aJavadocBeforeAnotherCommentOrAStatementDangles() {
        String source = """
                class Sample {
                    /** Lost. */
                    /** Attached. */
                    int field;

                    /** Lost too. */
                    // a plain comment
                    int other;

                    void method() {
                        /** In a body. */
                        call(1);
                    }

                    /** At the end of the class. */
                }
                """;

        List<String> problems = JavadocRules.problems(source);

        assertThat(problems).hasSize(4);
        assertThat(problems.get(0)).startsWith("line 2:").contains("dangling");
        assertThat(problems.get(1)).startsWith("line 6:");
        assertThat(problems.get(2)).startsWith("line 11:");
        assertThat(problems.get(3)).startsWith("line 15:");
    }

    @Test
    void textThatLooksLikeAJavadocInsideStringsAndCommentsIsIgnored() {
        String source = """
                class Sample {
                    String text = "/** not a comment */ ";
                    char quote = '"';
                    // /** neither
                    /* nor /** this */
                    String block = \"\"\"
                            /** inside a text block
                            \"\"\";
                }
                """;

        assertThat(JavadocRules.problems(source)).isEmpty();
    }

    @Test
    void aJavadocThatLinksToTheFieldItDocumentsPointsToItself() {
        String source = """
                class Sample {
                    /** Run once the copy is on disk ({@link #afterCopied}). */
                    private int afterCopied;
                    /** The step is {@value #STEP} units; see {@link #other}. */
                    static final int STEP = 3;
                    /** Refers to {@link #other} and {@link #STEP}, not to itself. */
                    int fine;
                    /** The accessor is {@link #revision()}, not this field. */
                    private long revision;
                }
                """;

        List<String> problems = JavadocRules.problems(source);

        assertThat(problems).hasSize(2);
        assertThat(problems.get(0)).startsWith("line 2:").contains("itself").contains("afterCopied");
        assertThat(problems.get(1)).startsWith("line 4:").contains("STEP");
    }

    @Test
    void anOverloadThatLinksToAnotherSignatureIsNotSelfPointing() {
        String source = """
                class Sample {
                    /** {@link #plan(int, int)} with the default delta. */
                    static int plan(int value) {
                        return 0;
                    }
                    /** The mirror: {@link #size()}. */
                    int size() {
                        return 0;
                    }
                }
                """;

        List<String> problems = JavadocRules.problems(source);

        assertThat(problems).hasSize(1);
        assertThat(problems.get(0)).startsWith("line 6:").contains("size");
    }

    @Test
    void aMethodLinkWithoutParenthesesAndASeeToItselfAreCaught() {
        String source = """
                class Sample {
                    /** Same as {@link #size}. */
                    int size() {
                        return 0;
                    }
                    /**
                     * Counts.
                     *
                     * @see #count
                     */
                    int count() {
                        return 0;
                    }
                    /** A {@link #size} link elsewhere is fine. */
                    int other() {
                        return 0;
                    }
                }
                """;

        List<String> problems = JavadocRules.problems(source);

        assertThat(problems).hasSize(2);
        assertThat(problems.get(0)).startsWith("line 2:").contains("size");
        assertThat(problems.get(1)).startsWith("line 6:").contains("count");
    }

    @Test
    void aLinkWithoutParenthesesIsNotSelfPointingWhenAFieldOrAnOverloadHasThatName() {
        String source = """
                class Sample {
                    private int revision;
                    /** The field is {@link #revision}. */
                    int revision() {
                        return revision;
                    }
                    /** {@link #plan} is the first overload. */
                    int plan(int value) {
                        return 0;
                    }
                    int plan(int value, int delta) {
                        return 0;
                    }
                }
                """;

        assertThat(JavadocRules.problems(source)).isEmpty();
    }
}
