package cl.almejo.vsim.ui.frames;

import java.util.List;

/** The icons of the frame features (M6.3), by file name under {@code icons/} (installed with {@code Icons.install}). */
public final class FrameIcons {

    public static final String FRAME_TOOL = "frame-tool";

    private FrameIcons() {
    }

    /** Every frame icon, once. */
    public static List<String> names() {
        return List.of(FRAME_TOOL);
    }
}
