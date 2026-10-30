package cl.almejo.vsim.ui.sound;

/** Where the application's sounds go. Implementations never block the caller and never fail when there is no device. */
public interface SoundSink {

    void play(Tone tone);
}
