package cl.almejo.vsim.ui.sound;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TerminalSoundsTest {

    private static final long MILLISECOND = 1_000_000L;

    private final List<Tone> played = new ArrayList<>();
    private long nanoseconds;
    private final TerminalSounds sounds = new TerminalSounds(played::add, () -> nanoseconds);

    @Test
    void theBellIsAnEightHundredEightyHertzSquareWaveOfOneHundredTwentyMilliseconds() {
        sounds.bell();

        assertThat(played).singleElement().satisfies(tone -> {
            assertThat(tone.frequencyHertz()).isEqualTo(880.0);
            assertThat(tone.milliseconds()).isEqualTo(120);
            assertThat(tone.waveform()).isEqualTo(Waveform.SQUARE);
        });
    }

    @Test
    void aClickLastsFifteenMilliseconds() {
        sounds.typed(1);

        assertThat(played).singleElement().extracting(Tone::milliseconds).isEqualTo(15);
    }

    @Test
    void clicksCloserThanOneSixtiethOfASecondMergeIntoOne() {
        for (int character = 0; character < 100; character++) {
            sounds.typed(1);
            nanoseconds += MILLISECOND;
        }

        // 100 ms of fast typing: one click per ~16.7 ms at most.
        assertThat(played).hasSizeBetween(5, 7);
    }

    @Test
    void aClickAfterTheIntervalPlaysAgain() {
        sounds.typed(3);
        nanoseconds += 17 * MILLISECOND;
        sounds.typed(1);

        assertThat(played).hasSize(2);
    }

    @Test
    void nothingTypedMakesNoSound() {
        sounds.typed(0);

        assertThat(played).isEmpty();
    }

    @Test
    void theSilentSinkIgnoresTones() {
        new SilentSoundSink().play(new Tone(440, 10, 1, Waveform.SINE));
    }

    @Test
    void theRealSinkIsNeverBuiltByTests() throws IOException {
        Path sources = Path.of("src/test/java");
        try (Stream<Path> files = Files.walk(sources)) {
            List<Path> offenders = files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().equals("TerminalSoundsTest.java"))
                    .filter(path -> contains(path, "new JavaSoundSink"))
                    .toList();
            assertThat(offenders).isEmpty();
        }
    }

    private static boolean contains(Path path, String text) {
        try {
            return Files.readString(path).contains(text);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
