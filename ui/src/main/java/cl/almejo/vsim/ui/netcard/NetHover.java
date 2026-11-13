package cl.almejo.vsim.ui.netcard;

import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * The rules of hovering a net, without Swing (the approved mockup's behaviour): a wire under the pointer lights its
 * net at once and shows the card after the balloon delay. Leaving the net starts a grace of {@link #GRACE_MILLIS}
 * while the card fades, so the pointer can reach it; over the card the fade stops; leaving the card starts it again.
 * Once the card shows, another net crossed on the way to it takes over only when the pointer rests there for the
 * delay. Pinned, it stays (and ignores other nets) until {@link #close()}.
 * <p>
 * {@code K} identifies a net (its id). The clock is read on every call; {@link #tick()} applies the timeouts and is
 * called by a frame ticker while {@link #needsTicks()}.
 */
final class NetHover<K> {

    /** How long the card waits, fading, after the pointer leaves the net or the card (the mockup's 1 s). */
    static final long GRACE_MILLIS = 1000;

    private static final long NONE = Long.MIN_VALUE;

    private final LongSupplier clock;
    private final LongSupplier initialDelay;
    private K current;
    private long hoveredSince = NONE;
    private boolean cardShown;
    private boolean pinned;
    private boolean overCard;
    private long leftAt = NONE;
    private K candidate;
    private long candidateSince = NONE;

    /** {@code initialDelay} is read each time: the balloon delay may change while the editor runs. */
    NetHover(LongSupplier clock, LongSupplier initialDelay) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.initialDelay = Objects.requireNonNull(initialDelay, "initialDelay");
    }

    /** The pointer is over a wire of net {@code key}. */
    void overWire(K key) {
        Objects.requireNonNull(key, "key");
        long now = clock.getAsLong();
        if (current == null) {
            start(key, now, false);
            return;
        }
        if (pinned || overCard) {
            return;
        }
        if (key.equals(current)) {
            leftAt = NONE;
            clearCandidate();
            return;
        }
        if (!cardShown) {
            start(key, now, false);
            return;
        }
        if (leftAt == NONE) {
            leftAt = now;
        }
        if (!key.equals(candidate)) {
            candidate = key;
            candidateSince = now;
        }
    }

    /** The pointer is over no wire (empty canvas, a part, a pin) or left the pane. */
    void offWire() {
        if (current == null || pinned || overCard) {
            return;
        }
        clearCandidate();
        if (leftAt == NONE) {
            leftAt = clock.getAsLong();
        }
    }

    /** The pointer entered the card: it stays while the pointer is there. */
    void enterCard() {
        if (current == null) {
            return;
        }
        overCard = true;
        leftAt = NONE;
        clearCandidate();
    }

    /** The pointer left the card: unpinned, it fades again. */
    void leaveCard() {
        overCard = false;
        if (current != null && !pinned && leftAt == NONE) {
            leftAt = clock.getAsLong();
        }
    }

    /** 📌: the card and the halo stay until {@link #close()}. */
    void pin() {
        if (current == null) {
            return;
        }
        pinned = true;
        cardShown = true;
        leftAt = NONE;
        clearCandidate();
    }

    /** Nothing lit, no card. */
    void close() {
        current = null;
        hoveredSince = NONE;
        cardShown = false;
        pinned = false;
        overCard = false;
        leftAt = NONE;
        clearCandidate();
    }

    /** The net now known as {@code key} is the one shown (its id changed when the netlist was compiled again). */
    void rekey(K key) {
        if (current != null) {
            current = Objects.requireNonNull(key, "key");
        }
    }

    /** Applies the timeouts at the clock's time: the card after the delay, a rested-on net, the end of the grace. */
    void tick() {
        if (current == null) {
            return;
        }
        long now = clock.getAsLong();
        long delay = initialDelay.getAsLong();
        if (candidate != null && now - candidateSince >= delay) {
            start(candidate, candidateSince, true);
            return;
        }
        if (leftAt != NONE && now - leftAt >= GRACE_MILLIS) {
            close();
            return;
        }
        if (!cardShown && leftAt == NONE && now - hoveredSince >= delay) {
            cardShown = true;
        }
    }

    Optional<K> current() {
        return Optional.ofNullable(current);
    }

    boolean cardShown() {
        return current != null && cardShown;
    }

    boolean pinned() {
        return pinned;
    }

    /** 1 while the card and the halo hold; down to 0 over the grace. */
    float opacity() {
        if (current == null) {
            return 0f;
        }
        if (leftAt == NONE) {
            return 1f;
        }
        float elapsed = (float) (clock.getAsLong() - leftAt) / GRACE_MILLIS;
        return Math.max(0f, Math.min(1f, 1f - elapsed));
    }

    /** True while a timeout is pending: the card's delay, a candidate net or the grace. */
    boolean needsTicks() {
        return current != null && (!cardShown || leftAt != NONE || candidate != null);
    }

    private void start(K key, long since, boolean withCard) {
        current = key;
        hoveredSince = since;
        cardShown = withCard;
        leftAt = NONE;
        clearCandidate();
    }

    private void clearCandidate() {
        candidate = null;
        candidateSince = NONE;
    }
}
