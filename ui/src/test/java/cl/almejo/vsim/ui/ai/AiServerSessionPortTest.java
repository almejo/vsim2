package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.ui.app.AppPreferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicInteger;

import static cl.almejo.vsim.ui.UiTestSupport.freePort;
import static org.assertj.core.api.Assertions.assertThat;

/** The session port (review focus 2): it overrides the remembered port for this run and is never remembered. */
class AiServerSessionPortTest {

    private static final String SECRET = "s3cret-s3cret-s3cret-s3cret";
    private static final int REMEMBERED = 48000;

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final AtomicInteger preferenceChanges = new AtomicInteger();
    private AiServerService service;

    @BeforeEach
    void remember() {
        preferences.setAiPort(REMEMBERED);
        preferences.setAiSecret(SECRET);
        preferences.addListener(preferenceChanges::incrementAndGet);
    }

    @AfterEach
    void stop() {
        if (service != null) {
            service.stop();
        }
    }

    private AiServerService serviceWith(SessionPort sessionPort) {
        return new AiServerService(preferences, new AutomationApi(new NoDocuments()), new FakeAiTasks(),
                Runnable::run, Runnable::run, AiServerService.QueuedWork.NONE, sessionPort);
    }

    @Test
    void withoutASessionPortTheRememberedOneIsUsedAsToday() {
        service = serviceWith(SessionPort.none());

        assertThat(service.isSessionPort()).isFalse();
        assertThat(service.effectivePort()).isEqualTo(REMEMBERED);
        assertThat(service.invalidSessionPort()).isEmpty();
        assertThat(service.claudeCodeCommand()).isEqualTo(AiServerService.claudeCodeCommand(preferences));
    }

    @Test
    void theSessionPortIsWhereItListensAndThePreferencesAreNeverTouched() throws Exception {
        int sessionPort = freePort();
        service = serviceWith(SessionPort.of(sessionPort));

        service.activate();
        assertThat(service.port()).hasValue(sessionPort);
        service.deactivate();
        service.activate();
        service.stop();

        assertThat(service.isSessionPort()).isTrue();
        assertThat(service.effectivePort()).isEqualTo(sessionPort);
        assertThat(preferences.aiPort()).isEqualTo(REMEMBERED);
        assertThat(preferences.aiSecret()).contains(SECRET);
        assertThat(preferenceChanges).as("nothing written to the preferences").hasValue(0);
    }

    @Test
    void theCopiedCommandNamesTheSessionPortAndAServerOfItsOwnWithTheRememberedSecret() {
        service = serviceWith(SessionPort.of(47100));

        assertThat(service.claudeCodeCommand()).isEqualTo("claude mcp add --transport http --scope user vsim-47100 "
                + "http://127.0.0.1:47100/mcp --header \"Authorization: Bearer " + SECRET + "\"");
    }

    @Test
    void theNextFreePortMovesOnlyTheSessionPort() throws Exception {
        try (ServerSocket other = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            int taken = other.getLocalPort();
            service = serviceWith(SessionPort.of(taken));

            service.activate();
            assertThat(service.state()).isEqualTo(new AiServerState.PortInUse(taken));

            assertThat(service.useNextFreePort()).isTrue();

            int moved = service.effectivePort();
            assertThat(moved).isGreaterThan(taken);
            assertThat(service.port()).hasValue(moved);
            assertThat(service.isSessionPort()).isTrue();
            assertThat(service.claudeCodeCommand()).contains(" vsim-" + moved + " ");
            assertThat(preferences.aiPort()).isEqualTo(REMEMBERED);
            assertThat(preferenceChanges).hasValue(0);
        }
    }

    @Test
    void aRememberedPortChangedMeanwhileNeitherRestartsNorMovesTheSessionServer() throws Exception {
        int sessionPort = freePort();
        service = serviceWith(SessionPort.of(sessionPort));
        service.activate();

        preferences.setAiPort(REMEMBERED + 1);

        assertThat(service.port()).hasValue(sessionPort);
        assertThat(service.state()).isEqualTo(new AiServerState.Listening(sessionPort));
    }

    @Test
    void anInvalidValueFallsBackOnTheRememberedPortAndIsKeptToBeSaid() {
        service = serviceWith(new SessionPort(OptionalInt.empty(), Optional.of("abc")));

        assertThat(service.isSessionPort()).isFalse();
        assertThat(service.effectivePort()).isEqualTo(REMEMBERED);
        assertThat(service.invalidSessionPort()).contains("abc");
        assertThat(service.claudeCodeCommand()).contains(" vsim http://127.0.0.1:" + REMEMBERED + "/mcp");
    }
}
