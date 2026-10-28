package cl.almejo.vsim.ui.export;

import cl.almejo.vsim.core.command.RotateElementsCommand;
import cl.almejo.vsim.core.component.standard.MultiBitLayout;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.UiTestSupport;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.paint.SignalColors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** An image export (PNG, SVG and print share it) of a turned multi-bit Switch shows its body upright. */
class RotatedMultiBitExportTest {

    private static final int WIDTH = 8;
    private static final int UNIT = GridPoint.PIXELS_PER_UNIT;

    private OpenDocument openDocument;

    @AfterEach
    void close() {
        if (openDocument != null) {
            openDocument.close();
        }
    }

    @Test
    void aTurnedMultiBitSwitchIsExportedUprightWithItsPinBelow() {
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        Component placed = UiTestSupport.place(openDocument, StandardComponentTypes.SWITCH, 10, 10,
                Map.of("width", WIDTH, "value", "0b10000000"));
        openDocument.execute(new RotateElementsCommand(List.of(placed.id()), true, openDocument.registry()));
        Component turned = openDocument.document().component(placed.id()).orElseThrow();

        CircuitImage image = CircuitImage.of(openDocument.document(), openDocument.netlist(),
                openDocument.netlistToken(), openDocument.latestSnapshot(), openDocument.registry(),
                PainterRegistry.standard(), ColorScheme.LIGHT, Optional.empty()).orElseThrow();
        BufferedImage pixels = render(image);

        GridSize box = MultiBitLayout.boxFor(WIDTH);
        Bounds drawn = image.gridBounds();
        assertThat(drawn.width() - 2 * CircuitImage.MARGIN).isEqualTo(box.width());
        assertThat(drawn.height() - 2 * CircuitImage.MARGIN).isEqualTo(box.height() + MultiBitLayout.LEAD_UNITS);
        GridPoint corner = turned.position().plus(MultiBitLayout.bodyOrigin(WIDTH, "bottom"));
        int bodyX = (corner.x() - drawn.x()) * UNIT;
        int bodyY = (corner.y() - drawn.y()) * UNIT;
        assertThat(cellColor(pixels, WIDTH - 1, bodyX, bodyY))
                .isEqualTo(SignalColors.valueColor(Value.ONE, ColorScheme.LIGHT));
        assertThat(cellColor(pixels, 0, bodyX, bodyY))
                .isEqualTo(SignalColors.valueColor(Value.ZERO, ColorScheme.LIGHT));
    }

    private static BufferedImage render(CircuitImage image) {
        BufferedImage result = new BufferedImage(image.width(1), image.height(1), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();
        image.paint(graphics, 1);
        graphics.dispose();
        return result;
    }

    /** A pixel just above the digit of {@code bit}'s cell, with the body's corner at {@code bodyX}, {@code bodyY}. */
    private static Color cellColor(BufferedImage image, int bit, int bodyX, int bodyY) {
        MultiBitLayout.BitCell cell = MultiBitLayout.cells(WIDTH).stream()
                .filter(candidate -> candidate.bit() == bit).findFirst().orElseThrow();
        return new Color(image.getRGB((int) (bodyX + cell.x() + cell.width() / 2), (int) (bodyY + cell.y() + 1.5)));
    }
}
