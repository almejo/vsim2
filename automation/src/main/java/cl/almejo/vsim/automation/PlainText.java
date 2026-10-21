package cl.almejo.vsim.automation;

import java.util.Map;

/**
 * The texts the AI gives (circuit and component names, labels, frame titles, notes, pin names) are shown by Swing,
 * which renders a text that starts with {@code <html} as HTML and would fetch the images it names: one could make
 * VSim reach the network (spec §9). Such texts are refused; {@code <html} anywhere else is plain text.
 */
public final class PlainText {

    private static final String HTML_START = "<html";

    private PlainText() {
    }

    /** @throws AutomationException INVALID_PARAMETER when {@code text} starts with {@code <html} in any case */
    public static String require(String text, String what) {
        if (text != null && text.regionMatches(true, 0, HTML_START, 0, HTML_START.length())) {
            throw new AutomationException(AutomationError.INVALID_PARAMETER, "A " + what + " cannot start with "
                    + "<html (VSim shows it as plain text only)", Map.of("text", what));
        }
        return text;
    }
}
