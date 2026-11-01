package cl.almejo.vsim.ui.terminal;

import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.component.standard.TerminalTypes;
import cl.almejo.vsim.core.terminal.TerminalScreen;
import cl.almejo.vsim.core.terminal.TerminalSettings;
import cl.almejo.vsim.ui.paint.TerminalAnimation;
import cl.almejo.vsim.ui.paint.TerminalLook;
import cl.almejo.vsim.ui.paint.TerminalMoment;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * What a view animates on a Terminal, on a fake clock: the blinking cursor, Automan's star, the last character's glow
 * (900 ms) and the phosphor persistence after a scroll or a clear (250 / 480 ms); nothing of it when the view is still
 * (reduced motion, an inactive window) or exporting.
 */
class TerminalActivityTest {

    private static final long MILLISECOND = 1_000_000L;
    private static final String TERMINAL = "c1";

    private long now = 10_000 * MILLISECOND;
    private final TerminalActivity activity = new TerminalActivity(() -> now);
    private final TerminalScreen screen = new TerminalScreen(TerminalSettings.vt(3, 10));
    private long owner = 1;

    private static TerminalLook look(Object... keysAndValues) {
        Map<String, Object> parameters = new HashMap<>();
        for (int index = 0; index < keysAndValues.length; index += 2) {
            parameters.put((String) keysAndValues[index], keysAndValues[index + 1]);
        }
        return TerminalLook.of(StandardComponentTypes.registry().require(StandardComponentTypes.TERMINAL)
                .resolveParameters(Map.copyOf(parameters)));
    }

    private static final TerminalLook STEADY_BAR = look(TerminalTypes.CURSOR, "bar", TerminalTypes.BLINK, false);

    private VisibleState.TerminalState write(String text) {
        for (byte value : text.getBytes(StandardCharsets.UTF_8)) {
            screen.write(value & 0xFF, 0);
        }
        return new VisibleState.TerminalState(owner, screen.version(), screen.frame(), false);
    }

    private TerminalMoment live(VisibleState state, TerminalLook look) {
        return activity.moment(TERMINAL, state, look, TerminalAnimation.LIVE);
    }

    private void passTime(long milliseconds) {
        now += milliseconds * MILLISECOND;
    }

    @Test
    void theFirstSightOfAScreenNeitherGlowsNorFades() {
        TerminalMoment moment = live(write("hola\u000c"), look(TerminalTypes.EFFECT, "soft"));
        assertThat(moment.lastGlowAlpha()).isZero();
        assertThat(moment.ghost()).isEmpty();
    }

    @Test
    void theLastCharacterGlowsAndTheGlowEndsAfter900Milliseconds() {
        live(write("a"), STEADY_BAR);
        TerminalMoment written = live(write("b"), STEADY_BAR);
        assertThat(written.lastGlowAlpha()).isCloseTo(TerminalMoment.LAST_GLOW_ALPHA, within(0.001));
        assertThat(written.moving()).isTrue();

        passTime(450);
        double halfway = live(write(""), STEADY_BAR).lastGlowAlpha();
        assertThat(halfway).isBetween(0.0, TerminalMoment.LAST_GLOW_ALPHA).isPositive();

        passTime(450);
        TerminalMoment over = live(write(""), STEADY_BAR);
        assertThat(over.lastGlowAlpha()).isZero();
        assertThat(over.moving()).as("a steady bar and no glow: nothing to animate").isFalse();
    }

    @Test
    void withoutMarkLastNothingGlows() {
        TerminalLook unmarked = look(TerminalTypes.CURSOR, "bar", TerminalTypes.BLINK, false, TerminalTypes.MARK_LAST,
                false);
        live(write("a"), unmarked);
        assertThat(live(write("b"), unmarked).lastGlowAlpha()).isZero();
    }

    @Test
    void aScrollLeavesTheOldPictureFadingForTheEffectsPersistence() {
        TerminalLook soft = look(TerminalTypes.EFFECT, "soft", TerminalTypes.CURSOR, "bar", TerminalTypes.BLINK, false);
        VisibleState.TerminalState before = write("1\r\n2\r\n3");
        live(before, soft);
        TerminalMoment scrolled = live(write("\r\n4"), soft);
        assertThat(scrolled.ghost()).contains(before.frame());
        assertThat(scrolled.ghostOpacity()).isCloseTo(TerminalMoment.GHOST_OPACITY, within(0.001));

        passTime(200);
        assertThat(live(write(""), soft).ghostOpacity()).isPositive();
        passTime(60);
        TerminalMoment faded = live(write(""), soft);
        assertThat(faded.ghost()).as("gone after 250 ms").isEmpty();
        assertThat(faded.ghostOpacity()).isZero();
    }

    @Test
    void theStrongEffectFadesLongerAndNoEffectLeavesNoGhost() {
        TerminalLook strong = look(TerminalTypes.EFFECT, "strong", TerminalTypes.CURSOR, "bar", TerminalTypes.BLINK,
                false);
        live(write("x"), strong);
        live(write("\u000c"), strong);
        passTime(400);
        assertThat(live(write(""), strong).ghost()).isPresent();
        passTime(80);
        assertThat(live(write(""), strong).ghost()).isEmpty();

        TerminalLook plain = look(TerminalTypes.EFFECT, "none");
        live(write("y"), plain);
        assertThat(live(write("\u000c"), plain).ghost()).isEmpty();
    }

    @Test
    void aBlinkingCursorIsOnForHalfASecondThenOffAndRestartsOnAChange() {
        TerminalLook blinking = look(TerminalTypes.CURSOR, "bar", TerminalTypes.BLINK, true, TerminalTypes.MARK_LAST,
                false);
        VisibleState.TerminalState state = write("a");
        TerminalMoment shown = live(state, blinking);
        assertThat(shown.cursorOn()).isTrue();
        assertThat(shown.nextChangeNanos()).isEqualTo(now + 500 * MILLISECOND);

        passTime(600);
        assertThat(live(state, blinking).cursorOn()).isFalse();
        passTime(500);
        assertThat(live(state, blinking).cursorOn()).isTrue();
        passTime(500);
        assertThat(live(state, blinking).cursorOn()).isFalse();
        assertThat(live(write("b"), blinking).cursorOn()).as("output shows the cursor again").isTrue();
    }

    @Test
    void automansStarNeverBlinksAndMovesAllTheTime() {
        TerminalLook automan = look(TerminalTypes.CURSOR, "automan", TerminalTypes.BLINK, true);
        VisibleState.TerminalState state = write("a");
        TerminalMoment first = live(state, automan);
        passTime(700);
        TerminalMoment later = live(state, automan);
        assertThat(later.cursorOn()).isTrue();
        assertThat(later.automanSeconds()).isNotNaN().isNotEqualTo(first.automanSeconds());
        assertThat(later.nextChangeNanos()).as("every frame").isLessThanOrEqualTo(now);

        TerminalMoment hidden = live(write("\u001b[?25l"), automan);
        passTime(1000);
        assertThat(live(write(""), automan).moving()).as("a hidden cursor does not move").isFalse();
        assertThat(hidden.cursorOn()).isTrue();
    }

    @Test
    void aStillViewShowsTheCursorTheStaticStarAndNoGlowNorGhost() {
        TerminalLook everything = look(TerminalTypes.CURSOR, "automan", TerminalTypes.EFFECT, "strong");
        activity.moment(TERMINAL, write("a"), everything, TerminalAnimation.STILL);
        TerminalMoment still = activity.moment(TERMINAL, write("\u000cb"), everything, TerminalAnimation.STILL);
        assertThat(still.cursorOn()).isTrue();
        assertThat(still.automanSeconds()).isNaN();
        assertThat(still.lastGlowAlpha()).isZero();
        assertThat(still.ghost()).isEmpty();
        assertThat(still.moving()).isFalse();
        assertThat(still.effects()).as("scanlines and glow stay").isTrue();

        TerminalMoment exported = activity.moment(TERMINAL, write("c"), everything, TerminalAnimation.EXPORT);
        assertThat(exported.effects()).isFalse();
        assertThat(exported.moving()).isFalse();
    }

    @Test
    void aRestartedScreenIsSeenAfreshAndAnotherStateForgetsIt() {
        live(write("a"), STEADY_BAR);
        owner = 2;
        assertThat(live(write("b"), STEADY_BAR).lastGlowAlpha()).as("another owner: first sight").isZero();
        assertThat(live(VisibleState.NONE, STEADY_BAR).moving()).isFalse();
    }
}
