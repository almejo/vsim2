package cl.almejo.vsim.ui.appearance;

import cl.almejo.vsim.core.component.appearance.ComponentAppearance;
import cl.almejo.vsim.core.component.appearance.SymbolDraft;
import cl.almejo.vsim.core.component.appearance.SymbolPoint;
import cl.almejo.vsim.core.component.appearance.SymbolText;
import cl.almejo.vsim.core.component.appearance.TextAlignment;
import cl.almejo.vsim.core.component.appearance.TextSize;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import cl.almejo.vsim.ui.paint.SymbolPainter;
import org.junit.jupiter.api.Test;

import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** The shape editor's larger pins with their hover, and the selection and click area of a text block. */
class ShapePinsAndTextBlocksTest {
    private final ComponentDefinition definition = ShapeEditorPanelTest.definition();
    private final AtomicReference<SymbolDraft> draft = new AtomicReference<>();

    @Test
    void aPinIsDrawnLargerThanAFigureNode() {
        ShapeCanvas canvas = canvasWith();
        Point pin = canvas.pinLocation(definition.pinout().getFirst().source());

        BufferedImage image = paint(canvas);

        // a node is 4 px in radius and a pin dot 6; the pin's dot reaches 4 px from its centre (it was 3 px in radius before), in the pad colour
        assertThat(image.getRGB(pin.x + 4, pin.y)).isEqualTo(ColorSchemes.LIGHT.pad().getRGB());
        assertThat(image.getRGB(pin.x + 8, pin.y)).isNotEqualTo(ColorSchemes.LIGHT.pad().getRGB());
    }

    @Test
    void hoveringAPinRingsItShowsTheDragCursorAndNamesIt() {
        ShapeCanvas canvas = canvasWith();
        Point pin = canvas.pinLocation(definition.pinout().getFirst().source());
        BufferedImage before = paint(canvas);

        move(canvas, pin.x + 2, pin.y + 1);

        assertThat(canvas.hoveredPin()).contains(definition.pinout().getFirst().source());
        assertThat(canvas.getToolTipText()).isEqualTo("A · entrada");
        assertThat(canvas.getCursor().getType()).isEqualTo(Cursor.MOVE_CURSOR);
        BufferedImage after = paint(canvas);
        assertThat(after.getRGB(pin.x + 10, pin.y)).isNotEqualTo(before.getRGB(pin.x + 10, pin.y));

        move(canvas, 300, 250);

        assertThat(canvas.hoveredPin()).isEmpty();
        assertThat(canvas.getToolTipText()).isEqualTo(Messages.text("shape.canvas.hint"));
    }

    @Test
    void theBlockOfARightAlignedTextEndsAtItsAnchorAndHoldsEveryLine() {
        SymbolText one = text(new SymbolPoint(40, 16), "uno", TextSize.MEDIUM, TextAlignment.RIGHT);
        SymbolText two = text(new SymbolPoint(40, 16), "uno\nmuy largo", TextSize.MEDIUM, TextAlignment.RIGHT);

        Rectangle2D oneBlock = SymbolPainter.textBlock(one, "Prueba");
        Rectangle2D twoBlock = SymbolPainter.textBlock(two, "Prueba");

        double anchorX = 40;
        assertThat(oneBlock.getMaxX()).isCloseTo(anchorX, within(0.01));
        assertThat(twoBlock.getMaxX()).isCloseTo(anchorX, within(0.01));
        assertThat(oneBlock.getHeight()).isCloseTo(SymbolPainter.lineHeight(TextSize.MEDIUM), within(0.01));
        assertThat(twoBlock.getHeight()).isCloseTo(2 * SymbolPainter.lineHeight(TextSize.MEDIUM), within(0.01));
        assertThat(twoBlock.getCenterY()).isCloseTo(16, within(0.01));
        assertThat(twoBlock.getWidth()).isGreaterThan(oneBlock.getWidth());
    }

    @Test
    void clickingAnywhereInsideATextBlockSelectsItAndTheSelectionRectangleSurroundsIt() {
        SymbolText longText = text(new SymbolPoint(8, 24), "una linea bastante larga", TextSize.MEDIUM,
                TextAlignment.LEFT);
        ShapeCanvas canvas = canvasWith(longText);
        Rectangle2D block = canvas.paddedTextBlock(longText);
        int farRight = 70 + (int) Math.round(block.getMaxX() - 3);
        int middleY = 70 + (int) Math.round(block.getCenterY());

        press(canvas, farRight, middleY);

        assertThat(draft.get().selection()).hasSize(1);
        BufferedImage image = paint(canvas);
        int top = 70 + (int) Math.round(block.getMinY());
        assertThat(image.getRGB(farRight, top)).isNotEqualTo(ColorSchemes.LIGHT.background().getRGB());
        int below = 70 + (int) Math.round(block.getMaxY()) + 6;
        press(canvas, farRight, below);
        assertThat(draft.get().selection()).isEmpty();
    }

    @Test
    void hoveringATextOutlinesItsBlock() {
        SymbolText label = text(new SymbolPoint(8, 24), "hola", TextSize.MEDIUM, TextAlignment.LEFT);
        ShapeCanvas canvas = canvasWith(label);
        Rectangle2D block = canvas.paddedTextBlock(label);
        int top = 70 + (int) Math.round(block.getMinY());
        int middleX = 70 + (int) Math.round(block.getCenterX());
        BufferedImage before = paint(canvas);

        move(canvas, middleX, 70 + (int) Math.round(block.getCenterY()));

        assertThat(paint(canvas).getRGB(middleX, top)).isNotEqualTo(before.getRGB(middleX, top));
    }

    private static SymbolText text(SymbolPoint anchor, String content, TextSize size, TextAlignment alignment) {
        return new SymbolText(anchor, Optional.of(content), size, alignment);
    }

    private ShapeCanvas canvasWith(SymbolText... texts) {
        SymbolDraft built = SymbolDraft.of(ComponentAppearance.of(definition), definition.pinout(), false);
        for (SymbolText text : texts) {
            built = built.addText(text);
        }
        draft.set(built.select(Set.of()));
        ShapeCanvas canvas = new ShapeCanvas(definition, draft::get, draft::set, draft::set,
                () -> ColorSchemes.LIGHT, () -> { });
        canvas.setZoom(1);
        canvas.setSize(400, 300);
        return canvas;
    }

    private static BufferedImage paint(ShapeCanvas canvas) {
        BufferedImage image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            canvas.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static void move(ShapeCanvas canvas, int x, int y) {
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_MOVED, 0, 0, x, y, 0, false));
    }

    private static void press(ShapeCanvas canvas, int x, int y) {
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 0, MouseEvent.BUTTON1_DOWN_MASK, x, y,
                1, false, MouseEvent.BUTTON1));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, 0, 0, x, y, 1, false,
                MouseEvent.BUTTON1));
    }
}
