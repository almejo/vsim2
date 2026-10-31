package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.component.standard.TerminalBehavior;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Terminal window frames of {@link SimulationThread} (spec §9): one {@link TerminalRequest} per Terminal, so the
 * window of one Terminal, the saved output of another and their sounds never take each other's frames. Each request
 * gets its own frame, republished when its screen changes (at most every 16 ms while running, at once while paused),
 * and its own acknowledged bytes. The requests are kept and published on the simulation thread; the frames and the
 * acknowledgements are read and written from any thread.
 */
final class TerminalFramePublisher {

    private static final long PUBLISH_INTERVAL_NANOS = 16_000_000L;
    /** The owner of no screen: behaviours count from 1. */
    private static final long NO_TERMINAL = 0;

    /** The reader has every byte up to {@code sequence} of the screen of {@code owner}. */
    private record Acknowledgement(long owner, long sequence) {
    }

    /** One request and when its frame was last published. Simulation thread only. */
    private static final class Feed {
        private final TerminalRequest request;
        /** The owner of the screen when the request was set: its {@code bytesSince} counts only for that screen. */
        private final long requestOwner;
        private boolean requestChanged = true;
        private boolean published;
        private long lastPublishNanos;

        private Feed(TerminalRequest request, long requestOwner) {
            this.request = request;
            this.requestOwner = requestOwner;
        }
    }

    private final Map<String, Feed> feeds = new HashMap<>();
    private final Map<String, TerminalWindowFrame> latestFrames = new ConcurrentHashMap<>();
    private final Map<String, Acknowledgement> acknowledgements = new ConcurrentHashMap<>();

    /** Sets (or replaces) the request for its Terminal; on the simulation thread. */
    void set(TerminalRequest request, SimulationEngine engine) {
        long owner = engine.terminal(request.componentId()).map(TerminalBehavior::owner).orElse(NO_TERMINAL);
        feeds.put(request.componentId(), new Feed(request, owner));
    }

    /** Stops the frames of {@code componentId} and forgets the last one; on the simulation thread. */
    void remove(String componentId) {
        feeds.remove(componentId);
        latestFrames.remove(componentId);
        acknowledgements.remove(componentId);
    }

    /** The latest frame of {@code componentId}; empty without a request or when it names no Terminal. Any thread. */
    Optional<TerminalWindowFrame> latest(String componentId) {
        return Optional.ofNullable(latestFrames.get(componentId));
    }

    /** The reader of {@code componentId} has the bytes up to {@code sequence} of screen {@code owner}. Any thread. */
    void acknowledge(String componentId, long owner, long sequence) {
        acknowledgements.put(componentId, new Acknowledgement(owner, sequence));
    }

    /** Publishes what changed for every request; on the simulation thread after each tick. */
    void publish(SimulationEngine engine, long nowNanos, boolean running) {
        feeds.forEach((componentId, feed) -> publish(engine, componentId, feed, nowNanos, running));
    }

    /**
     * Publishes a frame when the request changed or the screen did (another screen, another version, more bytes).
     * Nothing new, no frame: the history and the bytes are never copied for nothing.
     */
    private void publish(SimulationEngine engine, String componentId, Feed feed, long nowNanos, boolean running) {
        if (running && feed.published && nowNanos - feed.lastPublishNanos < PUBLISH_INTERVAL_NANOS) {
            return;
        }
        Optional<TerminalBehavior> terminal = engine.terminal(componentId);
        TerminalWindowFrame previous = latestFrames.get(componentId);
        if (!feed.requestChanged && (terminal.isEmpty() && previous == null
                || terminal.isPresent() && previous != null && unchanged(previous, terminal.get()))) {
            return;
        }
        if (terminal.isPresent()) {
            latestFrames.put(componentId, TerminalWindowFrame.capture(terminal.get(), feed.request,
                    bytesSince(componentId, feed, terminal.get()), engine.now()));
        } else {
            latestFrames.remove(componentId);
        }
        feed.requestChanged = false;
        feed.published = true;
        feed.lastPublishNanos = nowNanos;
    }

    private static boolean unchanged(TerminalWindowFrame previous, TerminalBehavior terminal) {
        return previous.owner() == terminal.owner() && previous.frame().version() == terminal.screen().version()
                && previous.lastByteSequence() == terminal.screen().byteLog().lastSequence();
    }

    /**
     * The first byte a frame of {@code terminal} leaves out: the acknowledged ones when the reader acknowledged this
     * screen, else the request's when it was set for this screen, else none (a screen started over or rebuilt).
     */
    private long bytesSince(String componentId, Feed feed, TerminalBehavior terminal) {
        long asked = feed.requestOwner == terminal.owner() ? feed.request.bytesSince() : 0;
        Acknowledgement acknowledgement = acknowledgements.get(componentId);
        return acknowledgement != null && acknowledgement.owner() == terminal.owner()
                ? Math.max(asked, acknowledgement.sequence()) : asked;
    }
}
