package cl.almejo.vsim.ui.sound;

/** A sink that plays nothing: tests and headless runs. */
public final class SilentSoundSink implements SoundSink {

    @Override
    public void play(Tone tone) {
        // Intentionally silent.
    }
}
