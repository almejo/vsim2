package cl.almejo.vsim.core.terminal;

import cl.almejo.vsim.core.terminal.ByteLog.Entry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The ring of the last bytes a Terminal received, read by sequence number. */
class ByteLogTest {

    @Test
    void entriesAreNumberedFromOne() {
        ByteLog log = new ByteLog();
        assertThat(log.lastSequence()).isZero();
        log.append(10, 'a');
        log.append(11, ByteLog.UNDEFINED);
        assertThat(log.lastSequence()).isEqualTo(2);
        assertThat(log.since(0)).containsExactly(new Entry(1, 10, 'a'), new Entry(2, 11, ByteLog.UNDEFINED));
        assertThat(log.since(1)).containsExactly(new Entry(2, 11, ByteLog.UNDEFINED));
        assertThat(log.since(2)).isEmpty();
        assertThat(log.since(1).get(0).undefined()).isTrue();
        assertThat(log.since(0).get(0).undefined()).isFalse();
    }

    @Test
    void theRingKeepsOnlyTheLastTenThousand() {
        ByteLog log = new ByteLog();
        for (int index = 0; index < ByteLog.CAPACITY + 5; index++) {
            log.append(index, index & 0xFF);
        }
        List<Entry> all = log.since(0);
        assertThat(all).hasSize(ByteLog.CAPACITY);
        assertThat(all.get(0).sequence()).isEqualTo(6);
        assertThat(all.get(0).time()).isEqualTo(5);
        assertThat(all.get(all.size() - 1).sequence()).isEqualTo(ByteLog.CAPACITY + 5);
        assertThat(log.since(ByteLog.CAPACITY + 3)).extracting(Entry::sequence)
                .containsExactly((long) ByteLog.CAPACITY + 4, (long) ByteLog.CAPACITY + 5);
    }

    @Test
    void clearEmptiesTheLogButKeepsCounting() {
        ByteLog log = new ByteLog();
        log.append(1, 'a');
        log.clear();
        assertThat(log.since(0)).isEmpty();
        log.append(2, 'b');
        assertThat(log.since(0)).containsExactly(new Entry(2, 2, 'b'));
    }

    @Test
    void everyLogHasItsOwnEpochThatAClearAndACopyKeep() {
        ByteLog log = new ByteLog();
        log.append(1, 'a');

        assertThat(new ByteLog().epoch()).isNotEqualTo(log.epoch());
        assertThat(log.copy().epoch()).isEqualTo(log.epoch());
        long before = log.epoch();
        log.clear();
        assertThat(log.epoch()).isEqualTo(before);
    }
}
