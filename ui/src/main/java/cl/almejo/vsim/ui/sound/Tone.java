package cl.almejo.vsim.ui.sound;

/** A short beep to synthesize: pitch, length, loudness (0 to 1) and shape. */
public record Tone(double frequencyHertz, int milliseconds, double volume, Waveform waveform) {

    public Tone {
        if (frequencyHertz <= 0 || milliseconds <= 0) {
            throw new IllegalArgumentException("a tone needs a positive frequency and length");
        }
        volume = Math.max(0.0, Math.min(1.0, volume));
    }
}
