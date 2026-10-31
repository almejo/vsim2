package cl.almejo.vsim.ui.appearance;

import cl.almejo.vsim.core.component.appearance.ComponentAppearance;
import cl.almejo.vsim.core.component.appearance.SymbolDraft;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.AppPreferences.GridStyle;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import org.junit.jupiter.api.Test;

import javax.swing.JToggleButton;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** The shape editor paints the main editor's grid (the shared painter), in the preference's style. */
class ShapeGridTest {
    private static final ColorScheme SCHEME = ColorSchemes.LIGHT;

    @Test
    void dotsArePaintedInTheSchemesGridColourAtTheGridPoints() {
        ShapeCanvas canvas = canvas(1);
        canvas.setGrid(GridStyle.DOTS, 2);
        BufferedImage image = paintInto(canvas);
        int column = column(canvas, -4);
        int row = row(canvas, -4);

        assertThat(image.getRGB(column, row)).isEqualTo(overBackground(SCHEME.grid()));
        assertThat(image.getRGB(column + 3, row + 3)).isEqualTo(SCHEME.background().getRGB());
    }

    @Test
    void linesAreOneStrokeEveryTwoUnitsInTheSchemesLineColour() {
        ShapeCanvas canvas = canvas(1);
        canvas.setGrid(GridStyle.LINES, 2);
        BufferedImage image = paintInto(canvas);
        int column = column(canvas, -4);
        int row = row(canvas, -4);

        assertThat(image.getRGB(column, row + 3)).isEqualTo(overBackground(SCHEME.gridLine()));
        assertThat(image.getRGB(column + 3, row)).isEqualTo(overBackground(SCHEME.gridLine()));
        assertThat(image.getRGB(column + 3, row + 3)).isEqualTo(SCHEME.background().getRGB());
    }

    @Test
    void whenTheDotsWouldBeTooCloseOnlyEveryFourthUnitHasOne() {
        ShapeCanvas canvas = canvas(0.5);
        canvas.setGrid(GridStyle.DOTS, 2);
        BufferedImage image = paintInto(canvas);

        assertThat(image.getRGB(column(canvas, -4), row(canvas, -4))).isNotEqualTo(SCHEME.background().getRGB());
        assertThat(image.getRGB(column(canvas, -2), row(canvas, -4))).isEqualTo(SCHEME.background().getRGB());
    }

    @Test
    void aHiddenGridPaintsNothingWhateverTheStyle() {
        ShapeCanvas canvas = canvas(1);
        canvas.setGrid(GridStyle.LINES, 2);
        canvas.setGridShown(false);
        BufferedImage image = paintInto(canvas);

        assertThat(image.getRGB(column(canvas, -4), row(canvas, -4) + 3)).isEqualTo(SCHEME.background().getRGB());
    }

    @Test
    void theToolbarToggleShowsAndHidesTheGridAndRemembersIt() {
        AppPreferences preferences = AppPreferences.inMemory();
        ShapeEditorPanel panel = panel(preferences);
        JToggleButton toggle = (JToggleButton) ShapeEditorPanelTest.toolbarButtons(panel).stream()
                .filter(button -> Messages.text("shape.grid").equals(button.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
        assertThat(toggle.isSelected()).isTrue();
        assertThat(panel.canvas().gridShown()).isTrue();
        assertThat(toggle.getToolTipText()).contains(Messages.text("shape.grid.hint"));

        toggle.doClick();

        assertThat(panel.canvas().gridShown()).isFalse();
        assertThat(preferences.shapeEditorGrid()).isFalse();
        toggle.doClick();
        assertThat(panel.canvas().gridShown()).isTrue();
        assertThat(preferences.shapeEditorGrid()).isTrue();
    }

    @Test
    void theCanvasFollowsTheGridPreferenceWhileTheEditorIsOpen() {
        AppPreferences preferences = AppPreferences.inMemory();
        ShapeEditorPanel panel = panel(preferences);
        panel.addNotify();
        try {
            preferences.setGridStyle(GridStyle.LINES);
            preferences.setGridLineSpacing(5);

            assertThat(panel.canvas().gridStyle()).isEqualTo(GridStyle.LINES);
            assertThat(panel.canvas().gridLineSpacing()).isEqualTo(5);
        } finally {
            panel.removeNotify();
        }
    }

    private static int column(ShapeCanvas canvas, int gridX) {
        return (int) Math.round(canvas.viewport.gridToScreen(gridX, 0).getX());
    }

    private static int row(ShapeCanvas canvas, int gridY) {
        Point2D point = canvas.viewport.gridToScreen(0, gridY);
        return (int) Math.round(point.getY());
    }

    private static ShapeEditorPanel panel(AppPreferences preferences) {
        ComponentDefinition definition = ShapeEditorPanelTest.definition();
        return new ShapeEditorPanel(new ShapeEditRequest(definition, ComponentAppearance.of(definition),
                ShapeEditScope.INSTANCE, true, false, "A2", 2, "U1"), preferences, () -> SCHEME);
    }

    private static ShapeCanvas canvas(double zoom) {
        ComponentDefinition definition = ShapeEditorPanelTest.definition();
        AtomicReference<SymbolDraft> draft = new AtomicReference<>(SymbolDraft.of(ComponentAppearance.of(definition),
                definition.pinout(), false).resizeBox(new GridSize(20, 20)));
        ShapeCanvas canvas = new ShapeCanvas(definition, draft::get, draft::set, draft::set, () -> SCHEME, () -> { });
        canvas.setZoom(zoom);
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

    /** What {@code color} gives laid over the scheme's background (the grid colours may be translucent). */
    private static int overBackground(Color color) {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(SCHEME.background());
        graphics.fillRect(0, 0, 1, 1);
        graphics.setColor(color);
        graphics.fillRect(0, 0, 1, 1);
        graphics.dispose();
        return image.getRGB(0, 0);
    }
}
