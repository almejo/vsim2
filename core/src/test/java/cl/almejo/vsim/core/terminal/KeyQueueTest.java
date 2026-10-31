package cl.almejo.vsim.core.terminal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The typed-bytes queue: whole characters, the cursor, popping and resizing. */
class KeyQueueTest {

    @Test
    void aCharacterEntersWholeOrNotAtAll() {
        KeyQueue queue = new KeyQueue(3);

        assertThat(queue.insert(new byte[]{'a'})).isTrue();
        assertThat(queue.insert(new byte[]{(byte) 0xE2, (byte) 0x82, (byte) 0xAC})).isFalse();
        assertThat(queue.size()).isEqualTo(1);
        assertThat(queue.insert(new byte[]{(byte) 0xC3, (byte) 0xB1})).isTrue();
        assertThat(queue.head(10)).containsExactly((int) 'a', 0xC3, 0xB1);
        assertThat(queue.insert(new byte[]{'b'})).isFalse();
    }

    @Test
    void poppingMovesTheCursorWithTheBytes() {
        KeyQueue queue = new KeyQueue(8);
        queue.insert(new byte[]{'a'});
        queue.insert(new byte[]{'b'});
        queue.edit(TypingEdit.CURSOR_LEFT);

        assertThat(queue.pop()).isTrue();

        assertThat(queue.head(8)).containsExactly((int) 'b');
        assertThat(queue.cursor()).isZero();
        assertThat(queue.pop()).isTrue();
        assertThat(queue.pop()).isFalse();
        assertThat(queue.peek()).isZero();
    }

    @Test
    void theVersionGrowsOnlyWhenSomethingChanges() {
        KeyQueue queue = new KeyQueue(4);
        long start = queue.version();
        queue.edit(TypingEdit.CURSOR_LEFT);
        queue.edit(TypingEdit.DELETE);
        queue.clear();
        assertThat(queue.version()).isEqualTo(start);

        queue.insert(new byte[]{'a'});
        assertThat(queue.version()).isGreaterThan(start);
    }

    @Test
    void resizingKeepsWhatFitsAndTheCursorInside() {
        KeyQueue queue = new KeyQueue(8);
        for (char character : "hello".toCharArray()) {
            queue.insert(new byte[]{(byte) character});
        }

        KeyQueue smaller = queue.resizedTo(2);

        assertThat(smaller.head(8)).containsExactly((int) 'h', (int) 'e');
        assertThat(smaller.cursor()).isEqualTo(2);
        assertThat(queue.size()).isEqualTo(5);
    }

    @Test
    void aCapacityBelowOneIsRefused() {
        assertThatThrownBy(() -> new KeyQueue(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
