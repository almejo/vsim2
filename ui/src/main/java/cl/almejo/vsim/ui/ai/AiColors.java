package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.ui.theme.ReadableColors;

import javax.swing.UIManager;
import java.awt.Color;

/**
 * The colour of the AI's state text (spec §2: in words and colour; red with text, never colour alone), worked out
 * from the current theme each time: never cache it, the theme changes live.
 */
public final class AiColors {

    private AiColors() {
    }

    static Color of(AiServerState state) {
        return switch (state) {
            case AiServerState.Off off -> muted();
            case AiServerState.Listening listening -> foreground();
            case AiServerState.Connected connected -> success();
            case AiServerState.Working working -> success();
            case AiServerState.PortInUse portInUse -> ReadableColors.errorText();
            case AiServerState.OtherVsim otherVsim -> ReadableColors.errorText();
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
    static Color success() {
        return ReadableColors.dark() ? new Color(0x7FD083) : new Color(0x2E7D32);
    }
}
