package cl.almejo.vsim.automation.present;

import java.time.Duration;
import java.util.Objects;

/**
 * The window's presentation mode (spec §4, §5): whether the AI presents its work, at which pace, and the pause
 * between steps in «De corrido».
 */
public record PresentationSettings(boolean presenting, PresentationPace pace, Duration pause) {

    /** The shortest and longest pause the user can choose between steps (spec §5). */
    public static final Duration MINIMUM_PAUSE = Duration.ofMillis(300);
    public static final Duration MAXIMUM_PAUSE = Duration.ofSeconds(5);
    public static final Duration DEFAULT_PAUSE = Duration.ofSeconds(1);

    /** Working mode (IA.1): nothing is paced. */
    public static final PresentationSettings WORKING =
            new PresentationSettings(false, PresentationPace.STEP_BY_STEP, DEFAULT_PAUSE);

    public PresentationSettings {
        Objects.requireNonNull(pace, "pace");
        Objects.requireNonNull(pause, "pause");
        if (pause.compareTo(MINIMUM_PAUSE) < 0) {
            pause = MINIMUM_PAUSE;
        } else if (pause.compareTo(MAXIMUM_PAUSE) > 0) {
            pause = MAXIMUM_PAUSE;
        }
    }
}
