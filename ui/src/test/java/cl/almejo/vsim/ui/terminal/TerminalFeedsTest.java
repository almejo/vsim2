package cl.almejo.vsim.ui.terminal;

import cl.almejo.vsim.core.simulation.TerminalRequest;
import cl.almejo.vsim.core.terminal.ByteLog;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** One feed of frames per Terminal, shared by its readers (window, saved output), polled once per frame. */
class TerminalFeedsTest {

    private final FakeTerminals terminals = new FakeTerminals();
    private final FakeTerminals.FakeTerminal terminal = terminals.add("tty", 2, 10);
    private final TerminalFeeds feeds = new TerminalFeeds(terminals);

    private static List<Integer> values(List<TerminalUpdate> updates) {
        return updates.stream().flatMap(update -> update.newBytes().stream()).map(ByteLog.Entry::value).toList();
    }

    @Test
    void aSubscriptionRequestsTheHistoryItNeedsAndCancellingStopsTheRequest() {
        TerminalFeeds.Subscription subscription = feeds.subscribe("tty", 50, update -> { });

        assertThat(terminals.request("tty")).contains(new TerminalRequest("tty", 50, 0));
        TerminalFeeds.Subscription other = feeds.subscribe("tty", 0, update -> { });
        assertThat(terminals.request("tty").orElseThrow().historyLines()).isEqualTo(50);

        subscription.cancel();
        assertThat(terminals.request("tty").orElseThrow().historyLines()).isZero();
        other.cancel();
        assertThat(terminals.isRequested("tty")).isFalse();
        assertThat(feeds.isEmpty()).isTrue();
    }

    @Test
    void eachNewFrameReachesTheSubscribersOnceAndIsAcknowledged() {
        List<TerminalUpdate> updates = new ArrayList<>();
        feeds.subscribe("tty", 0, updates::add);
        terminal.write("ab");
        terminals.publish("tty");

        feeds.poll();
        feeds.poll();

        assertThat(updates).hasSize(1);
        assertThat(updates.getFirst().startedOver()).isTrue();
        assertThat(values(updates)).containsExactly((int) 'a', (int) 'b');
        terminal.write("c");
        terminals.publish("tty");
        feeds.poll();
        assertThat(updates.getLast().newBytes()).as("the acknowledged bytes are left out")
                .extracting(ByteLog.Entry::value).containsExactly((int) 'c');
    }

    @Test
    void aLateSubscriberGetsEveryByteTheLogKeepsAndTheOthersNoRepeats() {
        List<TerminalUpdate> first = new ArrayList<>();
        feeds.subscribe("tty", 0, first::add);
        terminal.write("ab");
        terminals.publish("tty");
        feeds.poll();

        List<TerminalUpdate> late = new ArrayList<>();
        feeds.subscribe("tty", 0, late::add);
        terminals.publish("tty");
        feeds.poll();

        assertThat(values(late)).containsExactly((int) 'a', (int) 'b');
        assertThat(values(first)).as("the first reader saw them once").containsExactly((int) 'a', (int) 'b');
    }

    @Test
    void aRestartReachesTheSubscribersAsAStartOver() {
        List<TerminalUpdate> updates = new ArrayList<>();
        feeds.subscribe("tty", 0, updates::add);
        terminal.write("abc");
        terminals.publish("tty");
        feeds.poll();

        terminal.restart().write("x");
        terminals.publish("tty");
        feeds.poll();

        assertThat(updates.getLast().startedOver()).isTrue();
        assertThat(updates.getLast().newBytes()).extracting(ByteLog.Entry::value).containsExactly((int) 'x');
    }
}
