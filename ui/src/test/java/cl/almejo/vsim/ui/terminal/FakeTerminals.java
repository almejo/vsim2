package cl.almejo.vsim.ui.terminal;

import cl.almejo.vsim.core.simulation.TerminalRequest;
import cl.almejo.vsim.core.simulation.TerminalWindowFrame;
import cl.almejo.vsim.core.terminal.TerminalScreen;
import cl.almejo.vsim.core.terminal.TerminalSettings;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A {@link TerminalFrameSource} for tests: Terminals written by hand, published only when {@link #publish} is called,
 * honouring the requests (history lines, bytes since) and the acknowledgements as the simulation thread does.
 */
final class FakeTerminals implements TerminalFrameSource {

    private static long nextOwner = 1_000;

    /** One fake Terminal: a real screen and an owner that changes on {@link #restart()}. */
    static final class FakeTerminal {
        private TerminalScreen screen;
        private long owner = nextOwner++;
        private long time;

        private FakeTerminal(TerminalSettings settings) {
            screen = new TerminalScreen(settings);
        }

        /** Writes {@code text} as UTF-8 bytes, one time unit apart. */
        FakeTerminal write(String text) {
            for (byte value : text.getBytes(StandardCharsets.UTF_8)) {
                writeByte(value & 0xFF);
            }
            return this;
        }

        FakeTerminal writeByte(int value) {
            time += 10;
            screen.write(value, time);
            return this;
        }

        FakeTerminal writeUndefined() {
            time += 10;
            screen.writeUndefined(time);
            return this;
        }

        /** «Reiniciar»: a new screen, a new owner, bytes counted from 1 again and the time from 0. */
        FakeTerminal restart() {
            screen = new TerminalScreen(screen.settings());
            owner = nextOwner++;
            time = 0;
            return this;
        }

        /** A rebuilt part: a new owner that keeps the screen and the byte numbers. */
        FakeTerminal rebuild() {
            screen = screen.adoptedWith(screen.settings());
            owner = nextOwner++;
            return this;
        }

        long owner() {
            return owner;
        }

        TerminalScreen screen() {
            return screen;
        }
    }

    private record Acknowledgement(long owner, long sequence) {
    }

    private final Map<String, FakeTerminal> terminals = new HashMap<>();
    private final Map<String, TerminalRequest> requests = new HashMap<>();
    private final Map<String, Long> requestOwners = new HashMap<>();
    private final Map<String, Acknowledgement> acknowledgements = new HashMap<>();
    private final Map<String, TerminalWindowFrame> latest = new HashMap<>();
    final List<TerminalRequest> requestsSeen = new ArrayList<>();

    /** A VT Terminal of {@code rows} × {@code columns} named {@code componentId}. */
    FakeTerminal add(String componentId, int rows, int columns) {
        FakeTerminal terminal = new FakeTerminal(TerminalSettings.vt(rows, columns));
        terminals.put(componentId, terminal);
        return terminal;
    }

    FakeTerminal terminal(String componentId) {
        return terminals.get(componentId);
    }

    /** Publishes a new frame of {@code componentId} when someone asks for it. */
    void publish(String componentId) {
        TerminalRequest request = requests.get(componentId);
        FakeTerminal terminal = terminals.get(componentId);
        if (request == null || terminal == null) {
            return;
        }
        long asked = requestOwners.get(componentId) == terminal.owner ? request.bytesSince() : 0;
        Acknowledgement acknowledgement = acknowledgements.get(componentId);
        long since = acknowledgement != null && acknowledgement.owner() == terminal.owner
                ? Math.max(asked, acknowledgement.sequence()) : asked;
        TerminalScreen screen = terminal.screen;
        latest.put(componentId, new TerminalWindowFrame(request, terminal.owner, screen.frame(),
                screen.history(request.historyLines()), screen.byteLog().since(since),
                screen.byteLog().lastSequence(), screen.byteLog().epoch(), screen.transcript(), terminal.time));
    }

    /** Whether a request for {@code componentId} is set now. */
    boolean isRequested(String componentId) {
        return requests.containsKey(componentId);
    }

    Optional<TerminalRequest> request(String componentId) {
        return Optional.ofNullable(requests.get(componentId));
    }

    @Override
    public void request(TerminalRequest request) {
        requestsSeen.add(request);
        requests.put(request.componentId(), request);
        FakeTerminal terminal = terminals.get(request.componentId());
        requestOwners.put(request.componentId(), terminal == null ? 0 : terminal.owner);
    }

    @Override
    public void stopRequest(String componentId) {
        requests.remove(componentId);
        latest.remove(componentId);
        acknowledgements.remove(componentId);
    }

    @Override
    public Optional<TerminalWindowFrame> latest(String componentId) {
        return Optional.ofNullable(latest.get(componentId));
    }

    @Override
    public void acknowledge(String componentId, long owner, long sequence) {
        acknowledgements.put(componentId, new Acknowledgement(owner, sequence));
    }
}
