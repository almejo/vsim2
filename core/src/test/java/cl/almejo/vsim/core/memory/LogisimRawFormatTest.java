package cl.almejo.vsim.core.memory;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LogisimRawFormatTest {

    private final LogisimRawFormat format = new LogisimRawFormat();

    @Test
    void readsHexWordsAndRunLengths() throws MemoryFileProblem {
        MemoryFile file = format.read("v2.0 raw\n3a10 4*0 c601");

        assertThat(file.contents().word(0)).isEqualTo(0x3A10);
        assertThat(file.contents().word(5)).isEqualTo(0xC601);
        assertThat(file.contents().count()).isEqualTo(2);
        assertThat(file.header().isEmpty()).isTrue();
    }

    @Test
    void aNonZeroRunRepeatsTheWord() throws MemoryFileProblem {
        MemoryFile file = format.read("v2.0 raw\r\n3*ff\r\n");

        assertThat(file.contents().word(0)).isEqualTo(0xFF);
        assertThat(file.contents().word(2)).isEqualTo(0xFF);
        assertThat(file.contents().word(3)).isZero();
    }

    @Test
    void reportsTheLineOfABadToken() {
        assertThatThrownBy(() -> format.read("v2.0 raw\n1 2\nzz\n"))
                .isInstanceOfSatisfying(MemoryFileProblem.class, problem -> {
                    assertThat(problem.line()).isEqualTo(3);
                    assertThat(problem.messageKey()).isEqualTo("memory.file.notHex");
                });
    }

    @Test
    void aMissingHeaderLineIsAProblem() {
        assertThatThrownBy(() -> format.read("3a10\n"))
                .isInstanceOfSatisfying(MemoryFileProblem.class,
                        problem -> assertThat(problem.messageKey()).isEqualTo("memory.file.notLogisim"));
    }

    @Test
    void writesRunLengthForFourOrMoreEqualWords() {
        MemoryContents contents = MemoryContents.empty().with(0, 0x3A10).with(5, 0xC601);

        String text = format.write(new MemoryFile(MemoryHeader.empty(), contents), 16);

        assertThat(text).startsWith("v2.0 raw\n");
        assertThat(text).contains("3a10 4*0 c601");
    }

    @Test
    void writeThenReadRoundTrips() throws MemoryFileProblem {
        MemoryContents contents = MemoryContents.empty().with(0, 1).with(1, 1).with(2, 1).with(3, 1).with(4, 1)
                .with(9, 0xABCDEF).with(1_000_000, 2);

        MemoryFile back = format.read(format.write(new MemoryFile(MemoryHeader.empty(), contents), 24));

        assertThat(back.contents()).isEqualTo(contents);
    }

    @Test
    void anAbsurdRunLengthOfNonZeroWordsIsRefused() {
        assertThatThrownBy(() -> format.read("v2.0 raw\n99999999999*1\n"))
                .isInstanceOf(MemoryFileProblem.class);
    }

    @Test
    void canReadByFirstLineWhateverTheExtension() {
        assertThat(format.canRead("x.bin", "v2.0 raw")).isTrue();
        assertThat(format.canRead("x.hex", "3A")).isFalse();
        assertThat(format.canWrite()).isTrue();
    }
}
