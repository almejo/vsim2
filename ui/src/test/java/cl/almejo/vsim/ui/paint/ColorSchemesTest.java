package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.tunnel.Tunnels;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    /** A tunnel's name and hole against the inside of its tag (WCAG large or bold text). */
    private static final double TUNNEL_TEXT = 3.0;

    /** «Placa»: wire 1 against wire 0 (WCAG non-text contrast), so they differ in lightness, not only in hue. */
    private static final double BOARD_LEVELS = 2.0;
    /** «Placa»: how far a frame tint over the green board moves it, in RGB units (DARK's green tint moves it 41). */
    private static final double BOARD_TINT_VISIBLE = 30;
    /** «Placa»: a silkscreen colour is an off-white, this close to white. */
    private static final double SILKSCREEN = 1.2;

    private static final List<ColorScheme> ALL = List.of(ColorSchemes.CLASSIC, ColorSchemes.LIGHT, ColorSchemes.DARK,
            ColorSchemes.PASTEL, ColorSchemes.CSIM, ColorSchemes.TWILIGHT, ColorSchemes.PLACA);
    /** Schemes whose labels and outlines must read as body text. */
    private static final List<ColorScheme> STRICT = List.of(ColorSchemes.CLASSIC, ColorSchemes.LIGHT,
            ColorSchemes.DARK, ColorSchemes.PLACA);
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
        assertThat(ColorSchemes.ids()).containsExactly("classic", "light", "dark", "pastel", "csim", "twilight",
                "placa");
        assertThat(ColorSchemes.byId("classic")).containsSame(ColorSchemes.CLASSIC);
        assertThat(ColorSchemes.byId("light")).containsSame(ColorScheme.LIGHT);
        assertThat(ColorSchemes.byId("dark")).containsSame(ColorSchemes.DARK);
        assertThat(ColorSchemes.byId("pastel")).containsSame(ColorSchemes.PASTEL);
        assertThat(ColorSchemes.byId("csim")).containsSame(ColorSchemes.CSIM);
        assertThat(ColorSchemes.byId("twilight")).containsSame(ColorSchemes.TWILIGHT);
        assertThat(ColorSchemes.byId("placa")).containsSame(ColorSchemes.PLACA);
        assertThat(ColorSchemes.byId("Twilight")).isEmpty();
        assertThat(ColorSchemes.byId("neon")).isEmpty();
        assertThat(ColorSchemes.byId("")).isEmpty();
    }

    @Test
    void theTwentyOneRolesReadAndReplaceEachColorOfAScheme() {
        assertThat(ColorRole.values()).hasSize(21);
        assertThat(ColorRole.values()).extracting(ColorRole::key).containsExactly("background", "grid", "gridLine",
                "wireZero",
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
    void everySchemeHasAFaintGridLineAndAStrongerTenUnitLine() {
        List<ColorScheme> schemes = new ArrayList<>(ALL);
        schemes.add(MonochromeScheme.of(ColorScheme.LIGHT));
        for (ColorScheme scheme : schemes) {
            Color minor = over(scheme.gridLine(), scheme.background());
            Color major = over(scheme.gridLineMajor(), scheme.background());
            assertThat(minor).as("minor line on its board").isNotEqualTo(scheme.background());
            assertThat(contrast(minor, scheme.background())).as("minor line is faint").isLessThan(1.5);
            assertThat(contrast(major, scheme.background())).as("major line is stronger")
                    .isGreaterThan(contrast(minor, scheme.background()));
            assertThat(scheme.gridLineMajor().getRGB() & 0xFFFFFF).isEqualTo(scheme.gridLine().getRGB() & 0xFFFFFF);
        }
    }

    /** Spec §6: the guides are cyan, readable on the board and on the grid, and never a wire's or the selection's. */
    @Test
    void theGuideColourShowsOnEveryBoardAndOverTheGrid() {
        for (ColorScheme scheme : ALL) {
            Color guide = over(scheme.guide(), scheme.background());
            assertThat(contrast(guide, scheme.background())).as("guide on %s", scheme).isGreaterThanOrEqualTo(2);
            assertThat(contrast(guide, over(scheme.gridLineMajor(), scheme.background()))).as("guide over grid")
                    .isGreaterThanOrEqualTo(1.8);
            assertThat(guide).isNotEqualTo(scheme.selection()).isNotEqualTo(scheme.wireHighImpedance());
            assertThat(scheme.guide().getAlpha()).as("slightly translucent").isBetween(160, 254);
            assertThat(scheme.guide().getBlue()).as("cyan").isGreaterThan(scheme.guide().getRed());
            assertThat(scheme.guide().getGreen()).as("cyan").isGreaterThan(scheme.guide().getRed());
        }
    }

    @Test
    void theDerivedGridLineIsTheDotsColourAtFortyPercent() {
        assertThat(ColorScheme.derivedGridLine(new Color(0x102030))).isEqualTo(new Color(0x10, 0x20, 0x30, 102));
        assertThat(ColorScheme.derivedGridLine(new Color(10, 20, 30, 100))).isEqualTo(new Color(10, 20, 30, 40));
    }

    /** {@code color} composited over the opaque {@code below}. */
    private static Color over(Color color, Color below) {
        double alpha = color.getAlpha() / 255.0;
        return new Color((int) Math.round(color.getRed() * alpha + below.getRed() * (1 - alpha)),
                (int) Math.round(color.getGreen() * alpha + below.getGreen() * (1 - alpha)),
                (int) Math.round(color.getBlue() * alpha + below.getBlue() * (1 - alpha)));
    }

    @Test
    void theDefaultSchemeFollowsTheTheme() {
        assertThat(ColorSchemes.forTheme(false)).isSameAs(ColorSchemes.LIGHT);
        assertThat(ColorSchemes.forTheme(true)).isSameAs(ColorSchemes.DARK);
    }

    @Test
    void schemesHaveSpanishNames() {
        assertThat(ColorSchemes.ids().stream().map(ColorSchemes::displayName))
                .containsExactly("VSim clásico", "Claro", "Oscuro", "Pastel (VSim)", "CSim (VSim)", "Twilight (VSim)",
                        "Placa");
        assertThat(ColorSchemes.displayName("neon")).isEqualTo("neon");
    }

    @Test
    void placaIsBoardInEnglish() {
        Messages.initialize(Locale.ENGLISH);
        try {
            assertThat(ColorSchemes.displayName("placa")).isEqualTo("Board");
        } finally {
            Messages.initialize(Messages.SPANISH);
        }
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
    void theAlignmentLinesStandOutFromTheBoardTheGridTheSelectionAndTheWires() {
        for (ColorScheme scheme : ALL) {
            Color guide = scheme.alignmentGuide();
            assertThat(contrast(guide, scheme.background())).as("%s", scheme).isGreaterThanOrEqualTo(3);
            assertThat(guide).as("%s", scheme).isNotEqualTo(scheme.selection()).isNotEqualTo(scheme.grid())
                    .isNotEqualTo(scheme.gridLine());
            for (Function<ColorScheme, Color> state : WIRE_STATES) {
                assertThat(guide).as("%s", scheme).isNotEqualTo(state.apply(scheme));
            }
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
    void wiresOfTheNewSchemesMeetTheGraphicsContrast() {
        for (ColorScheme scheme : List.of(ColorSchemes.DARK, ColorSchemes.PLACA)) {
            for (Function<ColorScheme, Color> state : WIRE_STATES) {
                assertThat(contrast(state.apply(scheme), scheme.background())).as("%s", scheme)
                        .isGreaterThanOrEqualTo(DARK_WIRES);
            }
        }
    }

    /** The PCB look: a green solder mask, copper traces, bright gold at 1, white silkscreen, faint lighter vias. */
    @Test
    void placaLooksLikeAPrintedCircuitBoard() {
        ColorScheme board = ColorSchemes.PLACA;
        assertThat(board.isDark()).isTrue();
        assertThat(board.background().getGreen()).as("green board")
                .isGreaterThan(board.background().getRed()).isGreaterThan(board.background().getBlue());
        assertThat(board.wireZero().getRed()).as("copper")
                .isGreaterThan(board.wireZero().getGreen()).isGreaterThan(board.wireZero().getBlue());
        assertThat(board.wireOne().getRed()).as("gold")
                .isGreaterThan(board.wireOne().getGreen()).isGreaterThan(board.wireOne().getBlue());
        assertThat(luminance(board.wireOne())).as("1 brighter than 0").isGreaterThan(luminance(board.wireZero()));
        assertThat(contrast(board.wireOne(), board.wireZero())).isGreaterThanOrEqualTo(BOARD_LEVELS);
        assertThat(contrast(board.text(), Color.WHITE)).as("white silkscreen").isLessThan(SILKSCREEN);
        assertThat(contrast(board.componentStroke(), Color.WHITE)).as("white silkscreen").isLessThan(SILKSCREEN);
        Color vias = over(board.grid(), board.background());
        assertThat(vias.getGreen()).as("vias: a lighter green").isGreaterThan(board.background().getGreen());
        assertThat(contrast(vias, board.background())).as("vias are faint").isBetween(1.1, 2.0);
        assertThat(over(board.gridLine(), board.background()).getGreen()).as("lines: a lighter green")
                .isGreaterThan(board.background().getGreen());
    }

    /** The glow of a step and of the AI's changes, the pending outline and the find highlight use the selection. */
    @Test
    void placaSelectionAndGlowStandOutFromTheBoardAndFromEveryWire() {
        ColorScheme board = ColorSchemes.PLACA;
        assertThat(contrast(board.selection(), board.background())).isGreaterThanOrEqualTo(DARK_WIRES);
        assertThat(contrast(board.preview(), board.background())).isGreaterThanOrEqualTo(DARK_WIRES);
        for (Function<ColorScheme, Color> state : WIRE_STATES) {
            assertThat(distance(board.selection(), state.apply(board))).isGreaterThanOrEqualTo(DISTINCT);
        }
        assertThat(contrast(board.segmentOn(), board.displayBackground())).isGreaterThanOrEqualTo(DARK_WIRES);
        assertThat(contrast(board.diagramTrace(), board.diagramBackground())).isGreaterThanOrEqualTo(READABLE);
    }

    @Test
    void placaFramesShowOnTheGreenBoardAndTheirTitlesReadOnEveryTint() {
        ColorScheme board = ColorSchemes.PLACA;
        for (FrameTint tint : FrameTint.values()) {
            Color tinted = over(board.frameTint(tint), board.background());
            assertThat(contrast(board.frameTitle(), tinted)).as("%s", tint).isGreaterThanOrEqualTo(READABLE);
            if (tint != FrameTint.NONE) {
                assertThat(distance(tinted, board.background())).as("%s", tint)
                        .isGreaterThanOrEqualTo(BOARD_TINT_VISIBLE);
            }
        }
        assertThat(contrast(over(board.frameBorder(), board.background()), board.background()))
                .isGreaterThanOrEqualTo(DARK_WIRES);
    }

    @Test
    void tunnelNamesAreReadableOnTheirTagAndTheColoursCanBeToldApart() {
        for (ColorScheme scheme : ALL) {
            for (int color = Tunnels.NO_COLOR; color < Tunnels.COLOR_COUNT; color++) {
                assertThat(contrast(scheme.tunnelColor(color), scheme.tunnelSurface())).as("%s colour %d", scheme, color)
                        .isGreaterThanOrEqualTo(TUNNEL_TEXT);
                for (int other = color + 1; other < Tunnels.COLOR_COUNT; other++) {
                    assertThat(distance(scheme.tunnelColor(color), scheme.tunnelColor(other)))
                            .as("%s colours %d and %d", scheme, color, other).isGreaterThanOrEqualTo(DISTINCT);
                }
            }
            assertThat(contrast(scheme.tunnelHoleRim(), scheme.tunnelHole())).isGreaterThanOrEqualTo(TUNNEL_TEXT);
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
