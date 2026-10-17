package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class ColorSchemesTest {

    /** Enough to tell a thin line from the background; VSim's own Z green on gray is 1.37. */
    private static final double VISIBLE = 1.3;
    /** WCAG AA for text. */
    private static final double READABLE = 4.5;
    /** WCAG for graphics: the dark scheme is new, so it meets it for every wire state. */
    private static final double DARK_WIRES = 3.0;
    /** Minimum RGB distance between two wire states of one scheme. */
    private static final double DISTINCT = 80;
    /** Labels and gate outlines of the VSim ports on their board, as VSim drew them: pastel's blue gates are 1.63. */
    private static final double PORT_LEGIBLE = 1.5;
    /** Text on the component fill of a port (a fill we chose): Twilight's blue labels reach 3.96 on its dark fill. */
    private static final double PORT_TEXT_ON_FILL = 3.0;

    private static final List<ColorScheme> ALL = List.of(ColorSchemes.CLASSIC, ColorSchemes.LIGHT, ColorSchemes.DARK,
            ColorSchemes.PASTEL, ColorSchemes.CSIM, ColorSchemes.TWILIGHT);
    /** Schemes whose labels and outlines must read as body text. */
    private static final List<ColorScheme> STRICT = List.of(ColorSchemes.CLASSIC, ColorSchemes.LIGHT,
            ColorSchemes.DARK);
    /** VSim's other schemes, ported with their own colors. */
    private static final List<ColorScheme> PORTS = List.of(ColorSchemes.PASTEL, ColorSchemes.CSIM,
            ColorSchemes.TWILIGHT);
    private static final List<Function<ColorScheme, Color>> WIRE_STATES = List.of(ColorScheme::wireZero,
            ColorScheme::wireOne, ColorScheme::wireHighImpedance, ColorScheme::wireConflict, ColorScheme::wireUnknown);

    @BeforeEach
    void spanish() {
        Messages.initialize(Messages.SPANISH);
    }

    @Test
    void schemesAreFoundByIdInMenuOrder() {
        assertThat(ColorSchemes.ids()).containsExactly("classic", "light", "dark", "pastel", "csim", "twilight");
        assertThat(ColorSchemes.byId("classic")).containsSame(ColorSchemes.CLASSIC);
        assertThat(ColorSchemes.byId("light")).containsSame(ColorScheme.LIGHT);
        assertThat(ColorSchemes.byId("dark")).containsSame(ColorSchemes.DARK);
        assertThat(ColorSchemes.byId("pastel")).containsSame(ColorSchemes.PASTEL);
        assertThat(ColorSchemes.byId("csim")).containsSame(ColorSchemes.CSIM);
        assertThat(ColorSchemes.byId("twilight")).containsSame(ColorSchemes.TWILIGHT);
        assertThat(ColorSchemes.byId("Twilight")).isEmpty();
        assertThat(ColorSchemes.byId("neon")).isEmpty();
        assertThat(ColorSchemes.byId("")).isEmpty();
    }

    @Test
    void theTwentyRolesReadAndReplaceEachColorOfAScheme() {
        assertThat(ColorRole.values()).hasSize(20);
        assertThat(ColorRole.values()).extracting(ColorRole::key).containsExactly("background", "grid", "wireZero",
                "wireOne", "wireHighImpedance", "wireConflict", "wireUnknown", "componentStroke", "componentFill",
                "componentShade", "shadow", "text", "selection", "preview", "displayBackground", "segmentOn",
                "segmentOff", "diagramBackground", "diagramGrid", "diagramTrace");
        assertThat(ColorRole.scheme(role -> role.get(ColorSchemes.CSIM))).isEqualTo(ColorSchemes.CSIM);
        Color marker = new Color(1, 2, 3, 4);
        for (ColorRole role : ColorRole.values()) {
            ColorScheme changed = ColorSchemes.DARK.with(role, marker);
            for (ColorRole other : ColorRole.values()) {
                assertThat(other.get(changed)).isEqualTo(other == role ? marker : other.get(ColorSchemes.DARK));
            }
        }
    }

    @Test
    void theDefaultSchemeFollowsTheTheme() {
        assertThat(ColorSchemes.forTheme(false)).isSameAs(ColorSchemes.LIGHT);
        assertThat(ColorSchemes.forTheme(true)).isSameAs(ColorSchemes.DARK);
    }

    @Test
    void schemesHaveSpanishNames() {
        assertThat(ColorSchemes.ids().stream().map(ColorSchemes::displayName))
                .containsExactly("VSim clásico", "Claro", "Oscuro", "Pastel (VSim)", "CSim (VSim)", "Twilight (VSim)");
        assertThat(ColorSchemes.displayName("neon")).isEqualTo("neon");
    }

    @Test
    void portsTakeTheColorsOfVSim() {
        assertVSimColors(ColorSchemes.PASTEL, 0xBDBDBD, 0xCCCCCC, 0x000000, 0xDB5340, 0x00FF00, 0x389CD1, 0xCC6600,
                0x990033);
        assertVSimColors(ColorSchemes.CSIM, 0x0033CC, 0x0033CC, 0x000000, 0xFFFFFF, 0x00FF00, 0xFF9933, 0xFFFF00,
                0xFF0000);
        assertVSimColors(ColorSchemes.TWILIGHT, 0x000000, 0x1C1C1C, 0x808080, 0x800000, 0x008000, 0x004680, 0x0066FF,
                0x990099);
    }

    /** The colors.json keys in order: background, grid, off, wires-on, ground, gates, label, signal. */
    private static void assertVSimColors(ColorScheme scheme, int background, int grid, int off, int wiresOn,
                                         int ground, int gates, int label, int signal) {
        assertThat(scheme.background()).isEqualTo(new Color(background));
        assertThat(scheme.grid()).isEqualTo(new Color(grid));
        assertThat(scheme.wireZero()).isEqualTo(new Color(off));
        assertThat(scheme.wireOne()).isEqualTo(new Color(wiresOn));
        assertThat(scheme.wireHighImpedance()).isEqualTo(new Color(ground));
        assertThat(scheme.componentStroke()).isEqualTo(new Color(gates));
        assertThat(scheme.text()).isEqualTo(new Color(label));
        assertThat(scheme.diagramTrace()).isEqualTo(new Color(signal));
    }

    @Test
    void classicTakesTheColorsOfVSim() {
        ColorScheme classic = ColorSchemes.CLASSIC;
        assertThat(classic.background()).isEqualTo(new Color(0xBDBDBD));
        assertThat(classic.grid()).isEqualTo(new Color(0xCCCCCC));
        assertThat(classic.wireZero()).isEqualTo(new Color(0x000000));
        assertThat(classic.wireOne()).isEqualTo(new Color(0xFF0000));
        assertThat(classic.wireHighImpedance()).isEqualTo(new Color(0x00FF00));
        assertThat(classic.componentStroke()).isEqualTo(new Color(0x0000FF));
        assertThat(classic.text()).isEqualTo(new Color(0x6600CC));
        assertThat(classic.selection()).isEqualTo(new Color(0xFFFF00));
        assertThat(classic.displayBackground()).isEqualTo(new Color(0x0000FF));
        assertThat(classic.segmentOn()).isEqualTo(new Color(0xFF0000));
        assertThat(classic.diagramTrace()).isEqualTo(new Color(0xFF0000));
    }

    @Test
    void everyWireStateAndTheSelectionAreVisibleOnTheBackground() {
        for (ColorScheme scheme : ALL) {
            for (Function<ColorScheme, Color> state : WIRE_STATES) {
                assertThat(contrast(state.apply(scheme), scheme.background())).as("%s", scheme)
                        .isGreaterThanOrEqualTo(VISIBLE);
            }
            assertThat(contrast(scheme.selection(), scheme.background())).isGreaterThanOrEqualTo(VISIBLE);
            assertThat(contrast(scheme.segmentOn(), scheme.displayBackground())).isGreaterThanOrEqualTo(VISIBLE);
            assertThat(contrast(scheme.diagramTrace(), scheme.diagramBackground())).isGreaterThanOrEqualTo(VISIBLE);
        }
    }

    @Test
    void textAndOutlinesAreReadable() {
        for (ColorScheme scheme : STRICT) {
            assertThat(contrast(scheme.text(), scheme.background())).isGreaterThanOrEqualTo(READABLE);
            assertThat(contrast(scheme.componentStroke(), scheme.background())).isGreaterThanOrEqualTo(READABLE);
            assertThat(contrast(scheme.text(), scheme.componentFill())).isGreaterThanOrEqualTo(READABLE);
        }
    }

    @Test
    void portsKeepTheirLabelsLegibleAndTheirTextReadableInsideComponents() {
        for (ColorScheme scheme : PORTS) {
            assertThat(contrast(scheme.text(), scheme.background())).as("%s", scheme)
                    .isGreaterThanOrEqualTo(PORT_LEGIBLE);
            assertThat(contrast(scheme.componentStroke(), scheme.background())).as("%s", scheme)
                    .isGreaterThanOrEqualTo(PORT_LEGIBLE);
            assertThat(contrast(scheme.text(), scheme.componentFill())).as("%s", scheme)
                    .isGreaterThanOrEqualTo(PORT_TEXT_ON_FILL);
        }
    }

    @Test
    void darkWiresMeetTheGraphicsContrast() {
        for (Function<ColorScheme, Color> state : WIRE_STATES) {
            assertThat(contrast(state.apply(ColorSchemes.DARK), ColorSchemes.DARK.background()))
                    .isGreaterThanOrEqualTo(DARK_WIRES);
        }
    }

    @Test
    void wireStatesCanBeToldApart() {
        for (ColorScheme scheme : ALL) {
            for (int first = 0; first < WIRE_STATES.size(); first++) {
                for (int second = first + 1; second < WIRE_STATES.size(); second++) {
                    assertThat(distance(WIRE_STATES.get(first).apply(scheme), WIRE_STATES.get(second).apply(scheme)))
                            .as("%s states %d and %d", scheme, first, second).isGreaterThanOrEqualTo(DISTINCT);
                }
            }
        }
    }

    /** WCAG contrast ratio (alpha ignored). */
    private static double contrast(Color first, Color second) {
        double lighter = Math.max(luminance(first), luminance(second));
        double darker = Math.min(luminance(first), luminance(second));
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double luminance(Color color) {
        return 0.2126 * channel(color.getRed()) + 0.7152 * channel(color.getGreen())
                + 0.0722 * channel(color.getBlue());
    }

    private static double channel(int value) {
        double scaled = value / 255.0;
        return scaled <= 0.03928 ? scaled / 12.92 : Math.pow((scaled + 0.055) / 1.055, 2.4);
    }

    private static double distance(Color first, Color second) {
        int red = first.getRed() - second.getRed();
        int green = first.getGreen() - second.getGreen();
        int blue = first.getBlue() - second.getBlue();
        return Math.sqrt(red * red + green * green + blue * blue);
    }
}
