package cl.almejo.vsim.ui.terminal;

import cl.almejo.vsim.core.simulation.TerminalRequest;
import cl.almejo.vsim.core.terminal.ByteLog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** How a reader of a Terminal's frames tells the new bytes, and a «Reiniciar» from a rebuilt part. */
class ByteStreamTest {

    private final FakeTerminals terminals = new FakeTerminals();
    private final FakeTerminals.FakeTerminal terminal = terminals.add("tty", 2, 10);
    private final ByteStream stream = new ByteStream();

    private ByteStream.Reading read() {
        terminals.publish("tty");
        return stream.read(terminals.latest("tty").orElseThrow());
    }

    @Test
    void theFirstFrameStartsTheStreamWithEveryByteItHas() {
        terminals.request(new TerminalRequest("tty", 0, 0));
        terminal.write("ab");

        ByteStream.Reading reading = read();

        assertThat(reading.startedOver()).isTrue();
        assertThat(reading.newBytes()).extracting(ByteLog.Entry::value).containsExactly((int) 'a', (int) 'b');
    }

    @Test
    void laterFramesOfTheSameScreenGiveOnlyTheBytesNotSeenYet() {
        terminals.request(new TerminalRequest("tty", 0, 0));
        terminal.write("ab");
        read();
        terminal.write("c");

        ByteStream.Reading reading = read();

        assertThat(reading.startedOver()).isFalse();
        assertThat(reading.newBytes()).extracting(ByteLog.Entry::value).containsExactly((int) 'c');
        assertThat(read().newBytes()).as("the same bytes twice are not new").isEmpty();
    }

    @Test
    void aRestartStartsTheStreamOverEvenWhenMoreBytesArrivedThanBefore() {
        terminals.request(new TerminalRequest("tty", 0, 0));
        terminal.write("ab");
        read();

        terminal.restart().write("xyz");
        ByteStream.Reading reading = read();

        assertThat(reading.startedOver()).isTrue();
        assertThat(reading.newBytes()).extracting(ByteLog.Entry::value)
                .containsExactly((int) 'x', (int) 'y', (int) 'z');
    }

    @Test
    void aRestartThatRepeatsTheLastByteSeenStillStartsTheStreamOver() {
        terminals.request(new TerminalRequest("tty", 0, 0));
        terminal.write("a");
        read();

        terminal.restart().write("a");
        ByteStream.Reading reading = read();

        assertThat(reading.startedOver()).as("the new log has the same byte 1 at the same time").isTrue();
        assertThat(reading.newBytes()).extracting(ByteLog.Entry::value).containsExactly((int) 'a');
    }

    @Test
    void aRebuildWithNothingNewKeepsTheStreamGoingAcrossManyRebuilds() {
        terminals.request(new TerminalRequest("tty", 0, 0));
        terminal.write("a");
        read();

        terminal.rebuild().rebuild();
        ByteStream.Reading reading = read();

        assertThat(reading.startedOver()).isFalse();
        assertThat(reading.newBytes()).isEmpty();
    }

    @Test
    void aRebuiltPartKeepsTheStreamGoing() {
        terminals.request(new TerminalRequest("tty", 0, 0));
        terminal.write("ab");
        read();

        terminal.rebuild().write("c");
        ByteStream.Reading reading = read();

        assertThat(reading.startedOver()).isFalse();
        assertThat(reading.newBytes()).extracting(ByteLog.Entry::value).containsExactly((int) 'c');
    }

    @Test
    void theBytesTheLogDroppedBetweenTwoFramesAreCounted() {
        terminals.request(new TerminalRequest("tty", 0, 0));
        terminal.write("ab");
        assertThat(read().lostBytes()).isZero();

        terminal.write("x".repeat(ByteLog.CAPACITY + 5));
        ByteStream.Reading reading = read();

        assertThat(reading.startedOver()).isFalse();
        assertThat(reading.lostBytes()).isEqualTo(5);
        assertThat(reading.newBytes()).hasSize(ByteLog.CAPACITY);
    }
}
