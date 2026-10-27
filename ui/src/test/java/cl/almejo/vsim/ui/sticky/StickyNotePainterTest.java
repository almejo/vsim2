package cl.almejo.vsim.ui.sticky;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.StickyColor;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.core.document.StickyText;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.canvas.PaintPolicy;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import cl.almejo.vsim.ui.theme.ReadableColors;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StickyNotePainterTest {

    private static final double READABLE = 4.5;

    private static List<ColorScheme> schemes() {
        List<ColorScheme> schemes = new ArrayList<>();
        schemes.add(ColorScheme.LIGHT);
        for (String id : ColorSchemes.ids()) {
            schemes.add(ColorSchemes.byId(id).orElseThrow());
        }
        return schemes;
    }

    private static StickyNote note(StickyColor color, boolean folded) {
        return new StickyNote(new ElementId("s1"), new GridPoint(1, 1), 8, 6, color, new StickyText("hola mundo"),
                folded);
    }

    private static BufferedImage paint(StickyNote note, ColorScheme scheme, PaintPolicy policy) {
        BufferedImage image = new BufferedImage(120, 90, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(policy.monochrome() ? Color.WHITE : scheme.background());
        graphics.fillRect(0, 0, 120, 90);
        StickyNotePainter.paint(graphics, note, scheme, 8, policy);
        graphics.dispose();
        return image;
    }

    private static boolean differs(BufferedImage first, BufferedImage second) {
        for (int y = 0; y < first.getHeight(); y++) {
            for (int x = 0; x < first.getWidth(); x++) {
                if (first.getRGB(x, y) != second.getRGB(x, y)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    void theTextReadsOnEveryPaperInEveryBuiltInSchemeLightAndDark() {
        for (ColorScheme scheme : schemes()) {
            for (StickyColor color : StickyColor.values()) {
                assertThat(ReadableColors.contrast(scheme.stickyText(color), scheme.stickyPaper(color)))
                        .as("%s on board %s", color, scheme.background())
                        .isGreaterThanOrEqualTo(READABLE);
            }
        }
    }

    @Test
    void doneItemsAndTheRestingCloseStayReadableOnEveryPaper() {
        for (ColorScheme scheme : schemes()) {
            for (StickyColor color : StickyColor.values()) {
                Color paper = scheme.stickyPaper(color);
                Color text = scheme.stickyText(color);
                for (float alpha : new float[]{StickyNotePainter.DONE_ALPHA, StickyNotePainter.CLOSE_REST_ALPHA}) {
                    Color blended = new Color(
                            Math.round(text.getRed() * alpha + paper.getRed() * (1 - alpha)),
                            Math.round(text.getGreen() * alpha + paper.getGreen() * (1 - alpha)),
                            Math.round(text.getBlue() * alpha + paper.getBlue() * (1 - alpha)));
                    assertThat(ReadableColors.contrast(blended, paper))
                            .as("%s at alpha %s on board %s", color, alpha, scheme.background())
                            .isGreaterThanOrEqualTo(READABLE);
                }
            }
        }
    }

    @Test
    void textAndFoldFollowThePapersOwnBrightnessNotTheTheme() {
        ColorScheme dark = schemes().stream().filter(ColorScheme::isDark).findFirst().orElseThrow();
        for (StickyColor color : StickyColor.values()) {
            Color paper = dark.stickyPaper(color);
            boolean brightPaper = ReadableColors.luminance(paper) > 0.3;
            assertThat(brightPaper).as("%s is a bright post-it even on a dark board", color).isTrue();
            assertThat(ReadableColors.luminance(dark.stickyText(color))).isLessThan(0.1);
            assertThat(ReadableColors.luminance(dark.stickyFold(color))).isLessThan(ReadableColors.luminance(paper));
        }
        assertThat(ColorScheme.LIGHT.stickyPaper(StickyColor.YELLOW)).isEqualTo(new Color(0xFFF176));
    }

    @Test
    void theDarkVariantDiffersFromTheLightOne() {
        ColorScheme dark = schemes().stream().filter(ColorScheme::isDark).findFirst().orElseThrow();
        assertThat(dark.stickyPaper(StickyColor.YELLOW)).isNotEqualTo(ColorScheme.LIGHT.stickyPaper(StickyColor.YELLOW));
    }

    @Test
    void everyColourIsADistinctPaperWithAFoldOfItsOwn() {
        for (ColorScheme scheme : schemes()) {
            List<Color> papers = new ArrayList<>();
            for (StickyColor color : StickyColor.values()) {
                papers.add(scheme.stickyPaper(color));
                assertThat(scheme.stickyFold(color)).isNotEqualTo(scheme.stickyPaper(color));
            }
            assertThat(papers).doesNotHaveDuplicates();
            assertThat(scheme.stickyShadow().getAlpha()).isBetween(1, 254);
        }
    }

    @Test
    void thePaperIsPaintedInTheSchemePaperColour() {
        BufferedImage image = paint(note(StickyColor.PINK, false), ColorScheme.LIGHT, PaintPolicy.CANVAS);
        assertThat(new Color(image.getRGB(12, 50), true)).isEqualTo(ColorScheme.LIGHT.stickyPaper(StickyColor.PINK));
    }

    @Test
    void theCloseButtonIsPaintedOnlyWhenThePolicyPaintsButtons() {
        StickyNote note = note(StickyColor.YELLOW, false);
        BufferedImage screen = paint(note, ColorScheme.LIGHT, PaintPolicy.CANVAS);
        BufferedImage withoutButtons = paint(note, ColorScheme.LIGHT, new PaintPolicy(false, false, false, 0, true));
        assertThat(differs(screen, withoutButtons)).isTrue();
    }

    @Test
    void theControlsOfAnOpenNoteAreTheCloseAndTheFoldButtonAndImagesLeaveThemOut() {
        StickyNote note = note(StickyColor.YELLOW, false);
        BufferedImage screen = paint(note, ColorScheme.LIGHT, PaintPolicy.CANVAS);
        BufferedImage image = paint(note, ColorScheme.LIGHT, PaintPolicy.IMAGE);
        BufferedImage withoutControls = paint(note, ColorScheme.LIGHT, PaintPolicy.CANVAS.withStickyControls(false));
        assertThat(PaintPolicy.IMAGE.paintStickyControls()).isFalse();
        assertThat(PaintPolicy.IMAGE.paintFrameButtons()).isTrue();
        assertThat(differs(screen, image)).isTrue();
        assertThat(differs(image, withoutControls)).isFalse();
        // the fold button's centre (8 + 3 + 5 document pixels from the paper's corner) is inked on screen only
        Color atRest = new Color(screen.getRGB(8 + 8, 8 + 8), true);
        assertThat(atRest).isNotEqualTo(ColorScheme.LIGHT.stickyPaper(StickyColor.YELLOW));
        assertThat(new Color(image.getRGB(8 + 8, 8 + 8), true)).isEqualTo(ColorScheme.LIGHT.stickyPaper(StickyColor.YELLOW));
    }

    @Test
    void aFoldedNoteIsPaintedSmaller() {
        BufferedImage open = paint(note(StickyColor.GREEN, false), ColorScheme.LIGHT, PaintPolicy.CANVAS);
        BufferedImage folded = paint(note(StickyColor.GREEN, true), ColorScheme.LIGHT, PaintPolicy.CANVAS);
        // 5 units right and 4 down of the corner: inside the open paper, outside the 2 x 2 folded one
        assertThat(new Color(open.getRGB(48, 40), true)).isEqualTo(ColorScheme.LIGHT.stickyPaper(StickyColor.GREEN));
        assertThat(new Color(folded.getRGB(48, 40), true)).isEqualTo(ColorScheme.LIGHT.background());
    }

    @Test
    void inBlackAndWhiteNoColourReachesThePaper() {
        PaintPolicy mono = new PaintPolicy(true, false, true, 0, true);
        BufferedImage image = paint(note(StickyColor.PINK, false), ColorScheme.LIGHT, mono);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                Color pixel = new Color(image.getRGB(x, y), true);
                assertThat(pixel.getRed()).isEqualTo(pixel.getGreen());
                assertThat(pixel.getGreen()).isEqualTo(pixel.getBlue());
            }
        }
    }
}
