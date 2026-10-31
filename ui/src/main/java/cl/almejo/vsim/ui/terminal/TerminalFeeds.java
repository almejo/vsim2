package cl.almejo.vsim.ui.terminal;

import cl.almejo.vsim.core.simulation.TerminalRequest;
import cl.almejo.vsim.core.simulation.TerminalWindowFrame;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The frames of a document's Terminals for their readers (the window, «Guardar salida»): one request per Terminal
 * however many read it, asking for the most history any of them needs. {@link #poll()}, once per frame on the EDT,
 * hands each new frame to every reader with the bytes it had not seen ({@link ByteStream}) and acknowledges them, so
 * the next frame carries only newer bytes. A reader that comes later gets every byte the log still keeps. EDT only.
 */
public final class TerminalFeeds {

    /** One reader of one Terminal. */
    public final class Subscription {
        private final Feed feed;
        private final Consumer<TerminalUpdate> listener;
        private final ByteStream stream = new ByteStream();
        private int historyLines;
        private boolean cancelled;
        /** False until the first frame answering a request set since it joined: older frames lack bytes it needs. */
        private boolean started;

        private Subscription(Feed feed, int historyLines, Consumer<TerminalUpdate> listener) {
            this.feed = feed;
            this.historyLines = historyLines;
            this.listener = listener;
        }

        /** This reader now needs {@code lines} history rows. */
        public void historyLines(int lines) {
            if (cancelled || lines == historyLines) {
                return;
            }
            historyLines = lines;
            feed.request(feed.acknowledged);
        }

        /** This reader stops reading; the last one stops the Terminal's request. */
        public void cancel() {
            if (cancelled) {
                return;
            }
            cancelled = true;
            feed.subscriptions.remove(this);
            if (feed.subscriptions.isEmpty()) {
                feeds.remove(feed.componentId);
                source.stopRequest(feed.componentId);
            } else {
                feed.request(feed.acknowledged);
            }
        }

        private void deliver(TerminalWindowFrame frame) {
            if (!started && !frame.request().equals(feed.currentRequest)) {
                return;
            }
            started = true;
            ByteStream.Reading reading = stream.read(frame);
            listener.accept(new TerminalUpdate(frame, reading.newBytes(), reading.startedOver()));
        }
    }

    /** The request and the readers of one Terminal. */
    private final class Feed {
        private final String componentId;
        private final List<Subscription> subscriptions = new ArrayList<>();
        private TerminalWindowFrame lastDelivered;
        private TerminalRequest currentRequest;
        private long acknowledged;

        private Feed(String componentId) {
            this.componentId = componentId;
        }

        private void request(long bytesSince) {
            int history = subscriptions.stream().mapToInt(subscription -> subscription.historyLines).max().orElse(0);
            currentRequest = new TerminalRequest(componentId, history, bytesSince);
            source.request(currentRequest);
        }

        private void poll() {
            Optional<TerminalWindowFrame> latest = source.latest(componentId);
            if (latest.isEmpty() || latest.get() == lastDelivered) {
                return;
            }
            TerminalWindowFrame frame = latest.get();
            lastDelivered = frame;
            List.copyOf(subscriptions).forEach(subscription -> subscription.deliver(frame));
            acknowledged = frame.lastByteSequence();
            source.acknowledge(componentId, frame.owner(), acknowledged);
        }
    }

    private final TerminalFrameSource source;
    private final Map<String, Feed> feeds = new LinkedHashMap<>();

    public TerminalFeeds(TerminalFrameSource source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    /**
     * {@code listener} receives every new frame of the Terminal {@code componentId}, with {@code historyLines} rows of
     * history at least. Its first update brings every byte the log keeps.
     */
    public Subscription subscribe(String componentId, int historyLines, Consumer<TerminalUpdate> listener) {
        Objects.requireNonNull(listener, "listener");
        Feed feed = feeds.computeIfAbsent(componentId, Feed::new);
        Subscription subscription = new Subscription(feed, historyLines, listener);
        feed.subscriptions.add(subscription);
        // the newcomer needs every byte again: start the frames over from the first byte the log keeps
        source.acknowledge(componentId, feed.lastDelivered == null ? 0 : feed.lastDelivered.owner(), 0);
        feed.acknowledged = 0;
        feed.request(0);
        return subscription;
    }

    /** Hands the new frames to their readers; once per frame. */
    public void poll() {
        List.copyOf(feeds.values()).forEach(Feed::poll);
    }

    /** Whether no Terminal is being read. */
    public boolean isEmpty() {
        return feeds.isEmpty();
    }

    /** Cancels every reader. */
    public void dispose() {
        List.copyOf(feeds.values()).forEach(feed -> List.copyOf(feed.subscriptions).forEach(Subscription::cancel));
    }
}
