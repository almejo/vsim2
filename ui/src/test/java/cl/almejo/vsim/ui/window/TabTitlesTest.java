package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Two circuits of one window with the same name are told apart, as editors do with their file tabs. */
class TabTitlesTest {

    @BeforeEach
    void spanish() {
        Messages.initialize(Messages.SPANISH);
    }

    private static TabTitles.Named file(String name, String path) {
        return new TabTitles.Named(name, Optional.of(Path.of(path)));
    }

    private static TabTitles.Named untitled(String name) {
        return new TabTitles.Named(name, Optional.empty());
    }

    @Test
    void differentNamesStayAsTheyAre() {
        assertThat(TabTitles.distinct(List.of(file("sumador", "/a/sumador.vsim"), file("ram", "/a/ram.vsim"))))
                .containsExactly("sumador", "ram");
    }

    @Test
    void twoFilesWithTheSameNameGetTheirFolder() {
        assertThat(TabTitles.distinct(List.of(file("4-bit adder", "/work/clase1/4-bit adder.vsim"),
                file("4-bit adder", "/work/clase2/4-bit adder.vsim"), file("ram", "/work/ram.vsim"))))
                .containsExactly("4-bit adder — clase1", "4-bit adder — clase2", "ram");
    }

    @Test
    void sameFolderNamesGoUpOneMoreLevel() {
        assertThat(TabTitles.distinct(List.of(file("a", "/x/uno/src/a.vsim"), file("a", "/x/dos/src/a.vsim"))))
                .containsExactly("a — uno/src", "a — dos/src");
    }

    @Test
    void twoUntitledCopiesAreNumberedFromTheSecondOne() {
        assertThat(TabTitles.distinct(List.of(untitled("4-bit adder"), untitled("4-bit adder"),
                untitled("4-bit adder")))).containsExactly("4-bit adder", "4-bit adder 2", "4-bit adder 3");
    }

    @Test
    void anUntitledCopyBesideAFileKeepsTheNumberAndTheFileGetsItsFolder() {
        assertThat(TabTitles.distinct(List.of(file("4-bit adder", "/work/clase1/4-bit adder.vsim"),
                untitled("4-bit adder")))).containsExactly("4-bit adder — clase1", "4-bit adder 2");
    }
}
