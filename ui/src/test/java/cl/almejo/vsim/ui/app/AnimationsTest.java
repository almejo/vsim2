package cl.almejo.vsim.ui.app;

import org.junit.jupiter.api.Test;

import javax.swing.UIManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
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
    void onChangeRunsOnlyWhenTheSettingChangesAndStopsWhenRemoved() {
        AtomicInteger changes = new AtomicInteger();
        int listeners = preferences.listenerCount();
        Runnable stop = Animations.onChange(preferences, changes::incrementAndGet);

        preferences.setSnapToGuides(!preferences.snapToGuides());
        preferences.setAnimateViewMoves(false);
        assertThat(changes.get()).as("other preferences do not count").isZero();

        preferences.setAnimations(false);
        assertThat(changes.get()).isEqualTo(1);
        preferences.setAnimations(true);
        assertThat(changes.get()).isEqualTo(2);

        stop.run();
        assertThat(preferences.listenerCount()).isEqualTo(listeners);
        preferences.setAnimations(false);
        assertThat(changes.get()).isEqualTo(2);
    }

    @Test
    void theLookAndFeelsSmoothScrollingFollowsTheSetting() {
        try {
            Runnable stop = Animations.followInScrolling(preferences);
            assertThat(UIManager.getBoolean(Animations.SMOOTH_SCROLLING)).isTrue();

            preferences.setAnimations(false);
            assertThat(UIManager.getBoolean(Animations.SMOOTH_SCROLLING)).as("«No»: lists jump on the wheel").isFalse();
            preferences.setAnimations(true);
            assertThat(UIManager.getBoolean(Animations.SMOOTH_SCROLLING)).isTrue();

            stop.run();
            preferences.setAnimations(false);
            assertThat(UIManager.getBoolean(Animations.SMOOTH_SCROLLING)).isTrue();
        } finally {
            UIManager.put(Animations.SMOOTH_SCROLLING, null);
        }
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
