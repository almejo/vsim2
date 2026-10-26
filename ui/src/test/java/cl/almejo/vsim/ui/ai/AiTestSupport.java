package cl.almejo.vsim.ui.ai;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** What the AI server tests share: a recorder of the service's states and the «is the port free?» probe. */
final class AiTestSupport {

    private AiTestSupport() {
    }

    /** Listens to {@code service} and adds {@code describe(state)} to the returned list at every state change. */
    static List<String> recordStates(AiServerService service, Function<AiServerState, String> describe) {
        List<String> heard = new ArrayList<>();
        service.addListener(new AiServerService.Listener() {
            @Override
            public void stateChanged() {
                heard.add(describe.apply(service.state()));
            }
        });
        return heard;
    }

    /** Whether {@code port} can be bound on the loopback address right now (the probe frees it at once). */
    static boolean portIsFree(int port) {
        try (ServerSocket probe = new ServerSocket(port, 50, InetAddress.getLoopbackAddress())) {
            return probe.getLocalPort() == port;
        } catch (IOException exception) {
            return false;
        }
    }
}
