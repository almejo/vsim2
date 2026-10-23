package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.ui.theme.ReadableColors;

import javax.swing.UIManager;
import java.awt.Color;

/**
 * The colour of the AI's state text (spec §2: in words and colour; red with text, never colour alone), worked out
 * from the current theme each time: never cache it, the theme changes live. The panel's header and the status bar's
 * indicator share one role per state.
 */
public final class AiColors {

    /** What a state's colour says: muted (off or starting), plain text, success or error. */
    enum Role { MUTED, TEXT, SUCCESS, ERROR }

    private AiColors() {
    }

    static Role role(AiServerState state) {
        return switch (state) {
            case AiServerState.Off off -> Role.MUTED;
            case AiServerState.Starting starting -> Role.MUTED;
            case AiServerState.Listening listening -> Role.TEXT;
            case AiServerState.Connected connected -> Role.SUCCESS;
            case AiServerState.Working working -> Role.SUCCESS;
            case AiServerState.PortInUse portInUse -> Role.ERROR;
            case AiServerState.OtherVsim otherVsim -> Role.ERROR;
            case AiServerState.Failed failed -> Role.ERROR;
        };
    }

    static Color of(AiServerState state) {
        return switch (role(state)) {
            case MUTED -> muted();
            case TEXT -> foreground();
            case SUCCESS -> success();
            case ERROR -> ReadableColors.errorText();
        };
    }

    public static Color foreground() {
        Color color = UIManager.getColor("Label.foreground");
        return color != null ? color : Color.BLACK;
    }

    /** Grey that still reads as text on both themes. */
    public static Color muted() {
        return ReadableColors.dark() ? new Color(0xB0B4BA) : new Color(0x5F6368);
    }

    /** Green for «conectado» and «trabajando»: dark on a light theme, bright on a dark one. */
    public static Color success() {
        return ReadableColors.dark() ? new Color(0x7FD083) : new Color(0x2E7D32);
    }
}
