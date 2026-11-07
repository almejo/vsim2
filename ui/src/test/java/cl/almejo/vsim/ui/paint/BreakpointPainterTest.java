package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.BreakpointTypes;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BreakpointPainterTest {

    private static final ColorScheme SCHEME = ColorScheme.LIGHT;
    /** The octagon starts where the 12-pixel lead ends; the pin row is 8 pixels down. */
    private static final int CENTRE_Y = 8;
    /** A spot inside the octagon, away from the «=» glyph. */
    private static final int PROBE_X = 12 + 3;

    private final ComponentType type = StandardComponentTypes.registry().require(BreakpointTypes.BREAKPOINT);

    private Color paintedProbe(Map<String, Object> parameters) {
        BufferedImage image = new BufferedImage(80, 24, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        new BreakpointPainter().paint(graphics, type, parameters, VisibleState.NONE, SCHEME);
        graphics.dispose();
        return new Color(image.getRGB(PROBE_X, CENTRE_Y), true);
    }

    private static Map<String, Object> parameters(boolean active, String condition, String value) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(BreakpointTypes.ACTIVE, active);
        parameters.put(BreakpointTypes.CONDITION, condition);
        parameters.put("value", value);
        parameters.put("width", 16);
        return parameters;
    }

    @Test
    void anActiveBreakpointIsFilledWithTheConflictRedAndAnInactiveOneWithTheBodyColour() {
        Color active = paintedProbe(parameters(true, BreakpointTypes.EQUALS, "9"));
        Color inactive = paintedProbe(parameters(false, BreakpointTypes.EQUALS, "9"));

        assertThat(active.getRed()).isGreaterThan(active.getBlue());
        assertThat(inactive).isNotEqualTo(active);
    }

    @Test
    void bothConditionsAndAWideValueDrawWithoutFailing() {
        paintedProbe(parameters(true, BreakpointTypes.DIFFERS, "0xFFFF"));
        paintedProbe(parameters(true, BreakpointTypes.EQUALS, "0"));
    }
}
