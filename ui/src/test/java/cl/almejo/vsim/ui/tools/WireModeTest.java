package cl.almejo.vsim.ui.tools;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WireModeTest {

    @Test
    void eachModeComesBackFromItsId() {
        for (WireMode mode : WireMode.values()) {
            assertThat(WireMode.of(mode.id())).isSameAs(mode);
        }
    }

    @Test
    void anIdThisVersionDoesNotKnowIsContinuous() {
        assertThat(WireMode.of("zigzag")).isSameAs(WireMode.CONTINUOUS);
    }
}
