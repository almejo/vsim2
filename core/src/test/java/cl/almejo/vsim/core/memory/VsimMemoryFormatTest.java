package cl.almejo.vsim.core.memory;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VsimMemoryFormatTest {

    private static final String EXAMPLE = """
            # vsim: rom name=Sprites words=1024 width=16
            # Suma 1 a la variable en 0x10, para siempre
            @000
            3A10      # LDA 0x10
            C601      # ADD 1
            @010
            0005
            """;

    private final VsimMemoryFormat format = new VsimMemoryFormat();

    @Test
    void readsTheSpecExample() throws MemoryFileProblem {
        MemoryFile file = format.read(EXAMPLE);

        assertThat(file.contents().word(0)).isEqualTo(0x3A10);
        assertThat(file.contents().word(1)).isEqualTo(0xC601);
        assertThat(file.contents().word(0x10)).isEqualTo(5);
        assertThat(file.contents().count()).isEqualTo(3);
        assertThat(file.header().kind()).contains(MemoryKind.ROM);
        assertThat(file.header().name()).contains("Sprites");
        assertThat(file.header().words()).hasValue(1024);
        assertThat(file.header().width()).hasValue(16);
    }

    @Test
    void underscoresCommentsJumpsCrlfBomAndBlankLines() throws MemoryFileProblem {
        String text = "﻿# vsim: ram\r\n\r\n@4\r\nDE_AD  BE_EF # two\r\n\r\n@0 1\r\n";

        MemoryFile file = format.read(text);

        assertThat(file.contents().word(4)).isEqualTo(0xDEAD);
        assertThat(file.contents().word(5)).isEqualTo(0xBEEF);
        assertThat(file.contents().word(0)).isEqualTo(1);
        assertThat(file.header().kind()).contains(MemoryKind.RAM);
    }

    @Test
    void sixteenDigitWordKeepsAll64Bits() throws MemoryFileProblem {
        MemoryFile file = format.read("DEAD_BEEF_0000_0001");

        assertThat(file.contents().word(0)).isEqualTo(0xDEADBEEF00000001L);
    }

    @Test
    void anUnreadableTokenReportsItsLine() {
        assertThatThrownBy(() -> format.read("# one\n@0\n1\n3 C6G1\n"))
                .isInstanceOfSatisfying(MemoryFileProblem.class, problem -> {
                    assertThat(problem.line()).isEqualTo(4);
                    assertThat(problem.messageKey()).isEqualTo("memory.file.notHex");
                    assertThat(problem.arguments()).containsExactly("C6G1");
                });
    }

    @Test
    void seventeenDigitsIsALineProblem() {
        assertThatThrownBy(() -> format.read("1\n12345678901234567\n"))
                .isInstanceOfSatisfying(MemoryFileProblem.class, problem -> {
                    assertThat(problem.line()).isEqualTo(2);
                    assertThat(problem.messageKey()).isEqualTo("memory.file.tooLong");
                });
    }

    @Test
    void aValueWiderThanTheHeaderWidthIsAProblem() {
        assertThatThrownBy(() -> format.read("# vsim: ram width=8\n12\n1FF\n"))
                .isInstanceOfSatisfying(MemoryFileProblem.class, problem -> {
                    assertThat(problem.line()).isEqualTo(3);
                    assertThat(problem.messageKey()).isEqualTo("memory.file.tooWide");
                });
    }

    @Test
    void wordsThatAreNotAPowerOfTwoRoundUp() throws MemoryFileProblem {
        assertThat(format.read("# vsim: words=300\n").header().words()).isEqualTo(OptionalInt.of(512));
        assertThat(format.read("# vsim: words=16777216\n").header().words()).hasValue(16_777_216);
    }

    @Test
    void headerValuesOutOfRangeAreLineOneProblems() {
        for (String header : List.of("# vsim: words=0", "# vsim: words=33554432", "# vsim: width=65",
                "# vsim: width=0", "# vsim: words=abc")) {
            assertThatThrownBy(() -> format.read(header + "\n1\n"))
                    .as(header)
                    .isInstanceOfSatisfying(MemoryFileProblem.class,
                            problem -> assertThat(problem.line()).isEqualTo(1));
        }
    }

    @Test
    void aFileWithoutHeaderHasAnEmptyHeader() throws MemoryFileProblem {
        assertThat(format.read("1 2 3\n").header().isEmpty()).isTrue();
    }

    @Test
    void aNonVsimCommentIsNotAHeader() throws MemoryFileProblem {
        assertThat(format.read("# just a note\n1\n").header().isEmpty()).isTrue();
    }

    @Test
    void writeThenReadKeepsEveryWordAndTheHeader() throws MemoryFileProblem {
        MemoryContents contents = MemoryContents.empty().with(0, 0x3A10).with(1, 0xC601).with(2, 7)
                .with(0x300, 0xFFFF).with(0xFFFFFF, 1);
        MemoryHeader header = new MemoryHeader(Optional.of(MemoryKind.RAM), Optional.of("Sprites"),
                OptionalInt.of(1024), OptionalInt.of(16));

        String text = format.write(new MemoryFile(header, contents), 16);
        MemoryFile back = format.read(text);

        assertThat(back.contents()).isEqualTo(contents);
        assertThat(back.header()).isEqualTo(header);
        assertThat(text).startsWith("# vsim: ram name=Sprites words=1024 width=16");
    }

    @Test
    void writingWithoutHeaderWritesNone() throws MemoryFileProblem {
        String text = format.write(new MemoryFile(MemoryHeader.empty(), MemoryContents.empty().with(0, 1)), 8);

        assertThat(text).doesNotContain("vsim:");
        assertThat(format.read(text).contents().word(0)).isEqualTo(1);
    }

    @Test
    void recognisesFilesByExtensionOrHeader() {
        assertThat(format.canRead("a.hex", "3A")).isTrue();
        assertThat(format.canRead("a.HEX", "3A")).isTrue();
        assertThat(format.canRead("a.bin", "# vsim: rom")).isTrue();
        assertThat(format.canRead("a.bin", "3A")).isFalse();
        assertThat(format.canRead("a.txt", "v2.0 raw")).isFalse();
        assertThat(format.canWrite()).isTrue();
    }
}
