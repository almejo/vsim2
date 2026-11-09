package cl.almejo.vsim.ui.app;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class AnimationsTest {

    private final AppPreferences preferences = AppPreferences.inMemory();

    @Test
    void animationsAreOnByDefaultAndFollowTheSettingAtOnce() {
        assertThat(Animations.enabled(preferences)).isTrue();

        preferences.setAnimations(false);
        assertThat(Animations.enabled(preferences)).isFalse();

        preferences.setAnimations(true);
        assertThat(Animations.enabled(preferences)).isTrue();
    }

    @Test
    void theViewTravelsOnlyWithAnimationsAndAnimatedViewMovesBothOn() {
        assertThat(Animations.viewMoves(preferences)).isTrue();

        preferences.setAnimateViewMoves(false);
        assertThat(Animations.viewMoves(preferences)).isFalse();

        preferences.setAnimateViewMoves(true);
        preferences.setAnimations(false);
        assertThat(Animations.viewMoves(preferences)).isFalse();
    }

    @Test
    void nothingReadsTheDesktopAnimationsSwitch() throws IOException {
        Path sources = Path.of("src/main/java");
        try (Stream<Path> files = Files.walk(sources)) {
            assertThat(files.filter(file -> file.toString().endsWith(".java"))
                    .filter(AnimationsTest::readsTheDesktop))
                    .isEmpty();
        }
        assertThat(sources.resolve("cl/almejo/vsim/ui/app/ReducedMotion.java")).doesNotExist();
    }

    private static boolean readsTheDesktop(Path file) {
        try {
            String text = Files.readString(file);
            return text.contains("Gtk/EnableAnimations") || text.contains("ReducedMotion")
                    || text.contains("systemReducesMotion");
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
