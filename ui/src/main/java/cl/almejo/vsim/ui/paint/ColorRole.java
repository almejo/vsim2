package cl.almejo.vsim.ui.paint;

import java.awt.Color;
import java.util.function.Function;

/**
 * The twenty colours of a {@link ColorScheme}, in the record's order (M6.8 custom schemes): {@link #key()} names the
 * colour in the stored scheme and in the message {@code scheme.role.<key>}.
 */
public enum ColorRole {
    BACKGROUND("background", ColorScheme::background),
    GRID("grid", ColorScheme::grid),
    WIRE_ZERO("wireZero", ColorScheme::wireZero),
    WIRE_ONE("wireOne", ColorScheme::wireOne),
    WIRE_HIGH_IMPEDANCE("wireHighImpedance", ColorScheme::wireHighImpedance),
    WIRE_CONFLICT("wireConflict", ColorScheme::wireConflict),
    WIRE_UNKNOWN("wireUnknown", ColorScheme::wireUnknown),
    COMPONENT_STROKE("componentStroke", ColorScheme::componentStroke),
    COMPONENT_FILL("componentFill", ColorScheme::componentFill),
    COMPONENT_SHADE("componentShade", ColorScheme::componentShade),
    SHADOW("shadow", ColorScheme::shadow),
    TEXT("text", ColorScheme::text),
    SELECTION("selection", ColorScheme::selection),
    PREVIEW("preview", ColorScheme::preview),
    DISPLAY_BACKGROUND("displayBackground", ColorScheme::displayBackground),
    SEGMENT_ON("segmentOn", ColorScheme::segmentOn),
    SEGMENT_OFF("segmentOff", ColorScheme::segmentOff),
    DIAGRAM_BACKGROUND("diagramBackground", ColorScheme::diagramBackground),
    DIAGRAM_GRID("diagramGrid", ColorScheme::diagramGrid),
    DIAGRAM_TRACE("diagramTrace", ColorScheme::diagramTrace);

    private final String key;
    private final Function<ColorScheme, Color> reader;

    ColorRole(String key, Function<ColorScheme, Color> reader) {
        this.key = key;
        this.reader = reader;
    }

    /** The record component's name: the preference key and the suffix of the role's message key. */
    public String key() {
        return key;
    }

    public Color get(ColorScheme scheme) {
        return reader.apply(scheme);
    }

    /** A scheme with each role's colour given by {@code colors}. */
    public static ColorScheme scheme(Function<ColorRole, Color> colors) {
        return new ColorScheme(colors.apply(BACKGROUND), colors.apply(GRID), colors.apply(WIRE_ZERO),
                colors.apply(WIRE_ONE), colors.apply(WIRE_HIGH_IMPEDANCE), colors.apply(WIRE_CONFLICT),
                colors.apply(WIRE_UNKNOWN), colors.apply(COMPONENT_STROKE), colors.apply(COMPONENT_FILL),
                colors.apply(COMPONENT_SHADE), colors.apply(SHADOW), colors.apply(TEXT), colors.apply(SELECTION),
                colors.apply(PREVIEW), colors.apply(DISPLAY_BACKGROUND), colors.apply(SEGMENT_ON),
                colors.apply(SEGMENT_OFF), colors.apply(DIAGRAM_BACKGROUND), colors.apply(DIAGRAM_GRID),
                colors.apply(DIAGRAM_TRACE));
    }
}
