package cl.almejo.vsim.ui.sound;

import java.awt.GraphicsEnvironment;

/** Chooses the application's sound sink: real sound unless the environment is headless. */
public final class SoundSinks {

    private SoundSinks() {
    }

    public static SoundSink forEnvironment() {
        return GraphicsEnvironment.isHeadless() ? new SilentSoundSink() : new JavaSoundSink();
    }
}
