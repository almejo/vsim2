package cl.almejo.vsim.ui.sound;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Plays tones with {@code javax.sound.sampled} on its own daemon thread (never the EDT). The line is opened lazily; with
 * no audio device it does nothing. Tones that arrive while the queue is full are dropped. Never built in tests.
 */
public final class JavaSoundSink implements SoundSink {

    private static final Logger LOGGER = LoggerFactory.getLogger(JavaSoundSink.class);
    private static final float SAMPLE_RATE = 22_050f;
    private static final int BITS_PER_SAMPLE = 16;
    private static final int BYTES_PER_SAMPLE = 2;
    private static final int MILLISECONDS_PER_SECOND = 1000;
    private static final int QUEUE_CAPACITY = 64;
    private static final double MAXIMUM_AMPLITUDE = 0.6 * Short.MAX_VALUE;

    private final BlockingQueue<Tone> pending = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private Thread worker;
    private SourceDataLine line;
    private boolean unavailable;

    @Override
    public synchronized void play(Tone tone) {
        if (unavailable) {
            return;
        }
        if (worker == null) {
            worker = new Thread(this::run, "vsim-sound");
            worker.setDaemon(true);
            worker.start();
        }
        pending.offer(tone);
    }

    private void run() {
        while (true) {
            try {
                Tone tone = pending.take();
                if (!open()) {
                    pending.clear();
                    return;
                }
                byte[] samples = synthesize(tone);
                line.write(samples, 0, samples.length);
            } catch (InterruptedException interruption) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private boolean open() {
        if (line != null) {
            return true;
        }
        try {
            AudioFormat format = new AudioFormat(SAMPLE_RATE, BITS_PER_SAMPLE, 1, true, false);
            SourceDataLine opened = AudioSystem.getSourceDataLine(format);
            opened.open(format);
            opened.start();
            line = opened;
            return true;
        } catch (LineUnavailableException | IllegalArgumentException | SecurityException exception) {
            LOGGER.info("No audio device: the terminal sounds stay silent ({})", exception.toString());
            markUnavailable();
            return false;
        }
    }

    private synchronized void markUnavailable() {
        unavailable = true;
    }

    /** 16-bit little-endian mono PCM for the tone; needs no device. */
    static byte[] synthesize(Tone tone) {
        int count = (int) (SAMPLE_RATE * tone.milliseconds() / MILLISECONDS_PER_SECOND);
        byte[] samples = new byte[count * BYTES_PER_SAMPLE];
        for (int index = 0; index < count; index++) {
            double phase = (index * tone.frequencyHertz() / SAMPLE_RATE) % 1.0;
            double unit = switch (tone.waveform()) {
                case SQUARE -> phase < 0.5 ? 1.0 : -1.0;
                case SINE -> Math.sin(2 * Math.PI * phase);
            };
            short value = (short) (unit * tone.volume() * MAXIMUM_AMPLITUDE);
            samples[BYTES_PER_SAMPLE * index] = (byte) value;
            samples[BYTES_PER_SAMPLE * index + 1] = (byte) (value >> 8);
        }
        return samples;
    }
}
