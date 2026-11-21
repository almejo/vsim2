package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.ui.theme.ReadableColors;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** The interface conventions the machine package follows: readable muted text, no literal sizes in layouts. */
class MachineConventionsTest {

    private static final Path SOURCES = Path.of("src/main/java/cl/almejo/vsim/ui/machine");

    @Test
    void aQuietHintUsesTheReadableMutedColour() {
        assertThat(new MachineInspectorRows.Hint("texto", true).getForeground())
                .isEqualTo(ReadableColors.mutedText());
    }

    private static List<Path> sources() {
        try (Stream<Path> files = Files.list(SOURCES)) {
            return files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().startsWith("MachineTest"))
                    .filter(path -> !path.getFileName().toString().startsWith("MachineWaveStrip")).toList();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static String text(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    @Test
    void noMachineFileReadsTheDisabledForegroundForSecondaryText() {
        for (Path path : sources()) {
            assertThat(text(path)).as(path.getFileName().toString()).doesNotContain("\"Label.disabledForeground\"");
        }
    }

    @Test
    void layoutGapsAreInLogicalPixelsSoTheyFollowTheInterfaceSize() {
        for (Path path : sources()) {
            assertThat(text(path)).as(path.getFileName().toString())
                    .doesNotContainPattern("gap(top|bottom|left|right|x|y)? [1-9][0-9]*[,\"]");
        }
    }
}
