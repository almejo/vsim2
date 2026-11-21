package cl.almejo.vsim.ui.theme;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Readable secondary text is {@code ReadableColors.mutedText()}; {@code Label.disabledForeground} is for what is
 * really disabled.
 */
class ThemeColorUsageTest {

    /** Where the disabled colour is right: a disabled control, a ruler tick, an icon-like star, a FlatLaf style key. */
    private static final Set<String> REALLY_DISABLED = Set.of("SwitchBox.java", "PreferenceRow.java", "RulerColors.java",
            "ReadableColors.java", "FavoriteStar.java", "PdfExportDialog.java", "PropertyCard.java");

    @Test
    void secondaryTextNeverUsesTheDisabledColour() throws IOException {
        List<String> offenders;
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            offenders = files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !REALLY_DISABLED.contains(path.getFileName().toString()))
                    .filter(path -> contains(path, "Label.disabledForeground"))
                    .map(path -> path.getFileName().toString()).sorted().toList();
        }
        assertThat(offenders).isEmpty();
    }

    private static boolean contains(Path path, String text) {
        try {
            return Files.readString(path).contains(text);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
