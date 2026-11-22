package cl.almejo.vsim.ui.app;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CommandLineFilesTest {

    @Test
    void validArgumentsBecomePaths() {
        CommandLineFiles parsed = CommandLineFiles.parse(new String[] {"a.vsim", "dir/b.vsim"});

        assertThat(parsed.files()).containsExactly(Path.of("a.vsim"), Path.of("dir/b.vsim"));
        assertThat(parsed.unusable()).isEmpty();
    }

    @Test
    void anArgumentThatIsNotAPathDoesNotStopTheOthers() {
        // A NUL character is not valid in a path on any system.
        CommandLineFiles parsed = CommandLineFiles.parse(new String[] {"a.vsim", "bad\0name.vsim", "c.vsim"});

        assertThat(parsed.files()).containsExactly(Path.of("a.vsim"), Path.of("c.vsim"));
        assertThat(parsed.unusable()).containsExactly("bad\0name.vsim");
    }
}
