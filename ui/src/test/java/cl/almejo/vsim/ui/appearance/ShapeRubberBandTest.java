package cl.almejo.vsim.ui.appearance;

import cl.almejo.vsim.core.component.appearance.ComponentAppearance;
import cl.almejo.vsim.core.component.appearance.SymbolDraft;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.ui.canvas.ManualFrameTicker;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The shape editor's selection rectangle is the main editor's rubber band: a faint fill of the selection colour under
 * marching ants that move while the band is shown. The box is 20 × 20 units at zoom 1 with its corner at (70, 70), so
 * it spans (70, 70)–(230, 230); a press outside it starts the band at its clamped bottom right corner.
 */
class ShapeRubberBandTest {

    private final ManualFrameTicker ticker = new ManualFrameTicker();

    @Test
    void theBandIsFilledFaintlyWithTheSelectionColour() {
        ShapeCanvas canvas = canvas();
        int background = paintInto(canvas).getRGB(200, 200);

        startBand(canvas);
        int inside = paintInto(canvas).getRGB(200, 200);
        int outside = paintInto(canvas).getRGB(160, 200);

        assertThat(inside).isNotEqualTo(background);
        assertThat(outside).isEqualTo(background);
        Color selection = ColorSchemes.LIGHT.selection();
        Color backgroundColor = new Color(background, true);
        Color insideColor = new Color(inside, true);
        assertThat(insideColor.getRed()).isCloseTo(blend(selection.getRed(), backgroundColor.getRed()), within(2));
        assertThat(insideColor.getGreen()).isCloseTo(blend(selection.getGreen(), backgroundColor.getGreen()),
                within(2));
        assertThat(insideColor.getBlue()).isCloseTo(blend(selection.getBlue(), backgroundColor.getBlue()), within(2));
    }

    @Test
    void theAntsMarchWhileTheBandIsShownAndStopWhenItEnds() {
        ShapeCanvas canvas = canvas();
        assertThat(ticker.isRunning()).isFalse();

        startBand(canvas);
        assertThat(ticker.isRunning()).isTrue();
        double phase = canvas.antsPhase();
        ticker.advance(16);
        assertThat(canvas.antsPhase()).isNotEqualTo(phase);

        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, 0, 0, 180, 180, 1, false,
                MouseEvent.BUTTON1));
        assertThat(ticker.isRunning()).isFalse();
    }

    @Test
    void cancellingTheBandStopsTheAnts() {
        ShapeCanvas canvas = canvas();
        startBand(canvas);

        canvas.cancelOperation();

        assertThat(ticker.isRunning()).isFalse();
    }

    /** Presses outside the box and drags to (180, 180): the band covers (180, 180)–(230, 230). */
    private static void startBand(ShapeCanvas canvas) {
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 0, MouseEvent.BUTTON1_DOWN_MASK,
                300, 280, 1, false, MouseEvent.BUTTON1));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_DRAGGED, 0, MouseEvent.BUTTON1_DOWN_MASK,
                180, 180, 0, false, MouseEvent.NOBUTTON));
    }

    private ShapeCanvas canvas() {
        ComponentDefinition definition = ShapeEditorPanelTest.definition();
        AtomicReference<SymbolDraft> draft = new AtomicReference<>(SymbolDraft.of(ComponentAppearance.of(definition),
                definition.pinout(), false).resizeBox(new GridSize(20, 20)));
        ShapeCanvas canvas = new ShapeCanvas(definition, draft::get, draft::set, draft::set,
                () -> ColorSchemes.LIGHT, () -> { });
        canvas.useAntsTicker(ticker);
        canvas.setZoom(1);
        canvas.setSize(400, 300);
        return canvas;
    }

    private static BufferedImage paintInto(ShapeCanvas canvas) {
        BufferedImage image = new BufferedImage(canvas.getWidth(), canvas.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            canvas.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    /** A channel of the selection colour laid over the background at the main band's fill alpha (40 of 255). */
    private static int blend(int selection, int background) {
        return (int) Math.round((selection * 40 + background * (255 - 40)) / 255.0);
    }
}
