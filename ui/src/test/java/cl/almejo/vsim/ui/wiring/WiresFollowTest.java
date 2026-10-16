package cl.almejo.vsim.ui.wiring;

import cl.almejo.vsim.ui.app.AppPreferences;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WiresFollowTest {

    private final AppPreferences preferences = AppPreferences.inMemory();

    @Test
    void followsForIsTheModeExclusiveOrTheHeldInvertKey() {
        // mode on, SHIFT (the defaults)
        assertThat(WiresFollow.followsFor(preferences, false, false)).isTrue();
        assertThat(WiresFollow.followsFor(preferences, true, false)).isFalse();
        assertThat(WiresFollow.followsFor(preferences, false, true)).isTrue();
        assertThat(WiresFollow.followsFor(preferences, true, true)).isFalse();

        preferences.setWiresFollow(false);
        assertThat(WiresFollow.followsFor(preferences, false, false)).isFalse();
        assertThat(WiresFollow.followsFor(preferences, true, false)).isTrue();
        assertThat(WiresFollow.followsFor(preferences, false, true)).isFalse();

        preferences.setInvertModifier(AppPreferences.InvertModifier.ALT);
        assertThat(WiresFollow.followsFor(preferences, true, false)).isFalse();
        assertThat(WiresFollow.followsFor(preferences, false, true)).isTrue();

        preferences.setInvertModifier(AppPreferences.InvertModifier.NONE);
        assertThat(WiresFollow.followsFor(preferences, true, true)).isFalse();
        preferences.setWiresFollow(true);
        assertThat(WiresFollow.followsFor(preferences, true, true)).isTrue();
    }

    @Test
    void theSegmentKeyIsAltUnlessAltIsTheInvertKeyThenItIsShift() {
        for (AppPreferences.InvertModifier modifier : List.of(AppPreferences.InvertModifier.SHIFT,
                AppPreferences.InvertModifier.NONE)) {
            preferences.setInvertModifier(modifier);
            assertThat(WiresFollow.segmentKeyHeld(preferences, false, false)).isFalse();
            assertThat(WiresFollow.segmentKeyHeld(preferences, true, false)).isFalse();
            assertThat(WiresFollow.segmentKeyHeld(preferences, false, true)).isTrue();
            assertThat(WiresFollow.segmentKeyHeld(preferences, true, true)).isTrue();
        }
        preferences.setInvertModifier(AppPreferences.InvertModifier.ALT);
        assertThat(WiresFollow.segmentKeyHeld(preferences, false, false)).isFalse();
        assertThat(WiresFollow.segmentKeyHeld(preferences, true, false)).isTrue();
        assertThat(WiresFollow.segmentKeyHeld(preferences, false, true)).isFalse();
        assertThat(WiresFollow.segmentKeyHeld(preferences, true, true)).isTrue();
        // the follow mode does not change it
        preferences.setWiresFollow(false);
        assertThat(WiresFollow.segmentKeyHeld(preferences, true, false)).isTrue();
    }
}
