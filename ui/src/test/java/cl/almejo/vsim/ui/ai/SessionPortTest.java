package cl.almejo.vsim.ui.ai;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;

/** The AI's port for one session, from {@code -Dvsim.ai.port} or {@code VSIM_AI_PORT}: never remembered. */
class SessionPortTest {

    @Test
    void nothingGivenMeansTheRememberedPort() {
        SessionPort port = SessionPort.fromLaunch(name -> null, name -> null);

        assertThat(port.isGiven()).isFalse();
        assertThat(port.port()).isEmpty();
        assertThat(port.invalidValue()).isEmpty();
        assertThat(port).isEqualTo(SessionPort.none());
    }

    @Test
    void thePropertyOrTheEnvironmentGivesThePortAndThePropertyWins() {
        Map<String, String> properties = Map.of("vsim.ai.port", "47100");
        Map<String, String> environment = Map.of("VSIM_AI_PORT", "47200");

        assertThat(SessionPort.fromLaunch(properties::get, name -> null).port()).hasValue(47100);
        assertThat(SessionPort.fromLaunch(name -> null, environment::get).port()).hasValue(47200);
        assertThat(SessionPort.fromLaunch(properties::get, environment::get).port()).hasValue(47100);
        assertThat(SessionPort.fromLaunch(name -> " 47300 ", name -> null).port()).hasValue(47300);
    }

    @Test
    void anInvalidValueIsIgnoredAndKeptToBeSaid() {
        for (String invalid : new String[] {"abc", "80", "70000", "-1", "47 100"}) {
            SessionPort port = SessionPort.fromLaunch(Map.of("vsim.ai.port", invalid)::get, name -> null);

            assertThat(port.port()).as(invalid).isEmpty();
            assertThat(port.isGiven()).as(invalid).isFalse();
            assertThat(port.invalidValue()).as(invalid).contains(invalid);
        }
    }

    @Test
    void anInvalidPropertyStillWinsOverTheEnvironment() {
        SessionPort port = SessionPort.fromLaunch(Map.of("vsim.ai.port", "abc")::get,
                Map.of("VSIM_AI_PORT", "47200")::get);

        assertThat(port).isEqualTo(new SessionPort(OptionalInt.empty(), Optional.of("abc")));
    }

    @Test
    void aBlankValueIsAsIfNothingWasGiven() {
        assertThat(SessionPort.fromLaunch(name -> "  ", name -> null)).isEqualTo(SessionPort.none());
        assertThat(SessionPort.fromLaunch(name -> "", Map.of("VSIM_AI_PORT", "47200")::get).port())
                .hasValue(47200);
    }
}
