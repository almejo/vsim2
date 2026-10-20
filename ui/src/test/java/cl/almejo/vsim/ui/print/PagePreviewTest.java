package cl.almejo.vsim.ui.print;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.UiTestSupport;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.TextFont;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PagePreviewTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-20T15:30:00Z"), ZoneOffset.UTC);

    private final OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private final PagePreview preview = new PagePreview();
    private PageLayout layout;
    private PagePainter painter;

    @BeforeEach
    void tiledCircuit() {
        Messages.initialize(Messages.SPANISH);
        UiTestSupport.place(openDocument, StandardComponentTypes.SWITCH, 0, 0, Map.of());
        UiTestSupport.place(openDocument, StandardComponentTypes.SWITCH, 120, 160, Map.of());
        PrintInput input = PrintInput.capture(openDocument, Set.of(), ColorScheme.LIGHT, TextFont.DEFAULT, CLOCK);
        PrintArea area = PrintScopes.area(input, new PrintScope.WholeCircuit()).orElseThrow();
        PrintOptions options = new PrintOptions(new PrintScope.WholeCircuit(), Paper.A4, PageOrientation.PORTRAIT,
                new ScaleMode.Percentage(100), true, false, false, "");
        layout = PageLayout.of(area.gridArea(), PageGeometry.standard(Paper.A4, PageOrientation.PORTRAIT, false),
                options.scaleMode());
        painter = new PagePainter(input, area, options, layout);
        preview.setSize(600, 700);
        preview.doLayout();
        preview.show(painter, layout);
    }

    @AfterEach
    void close() {
        openDocument.close();
    }

    private Point2D mapPointOf(int sheet) {
        Rectangle2D grid = layout.page(sheet).gridRectangle();
        return preview.map().toMap(grid.getCenterX(), grid.getCenterY());
    }

    @Test
    void theFirstSheetIsSelectedAndNamed() {
        assertThat(layout.columns()).isGreaterThan(1);
        assertThat(layout.rows()).isGreaterThan(1);
        assertThat(preview.selectedSheet()).isZero();
        assertThat(preview.sheetLabel()).isEqualTo("Hoja 1 de " + layout.pageCount());
        assertThat(preview.previousButton().isEnabled()).isFalse();
        assertThat(preview.nextButton().isEnabled()).isTrue();
    }

    @Test
    void clickingTheMapSelectsTheSheetUnderThePointer() {
        int last = layout.pageCount() - 1;
        assertThat(preview.map().sheetAt(mapPointOf(last))).hasValue(last);
        preview.map().select(mapPointOf(last));
        assertThat(preview.selectedSheet()).isEqualTo(last);
        assertThat(preview.nextButton().isEnabled()).isFalse();
    }

    @Test
    void theArrowKeysWalkTheSheetsInReadingOrder() {
        preview.map().key(KeyEvent.VK_RIGHT);
        assertThat(preview.selectedSheet()).isEqualTo(1);
        preview.map().key(KeyEvent.VK_DOWN);
        assertThat(preview.selectedSheet()).isEqualTo(1 + layout.columns());
        preview.map().key(KeyEvent.VK_LEFT);
        preview.map().key(KeyEvent.VK_UP);
        assertThat(preview.selectedSheet()).isZero();
        preview.map().key(KeyEvent.VK_UP);
        assertThat(preview.selectedSheet()).as("stays inside the sheets").isZero();
    }

    @Test
    void theNextButtonMovesOneSheet() {
        preview.nextButton().doClick();
        assertThat(preview.selectedSheet()).isEqualTo(1);
        preview.previousButton().doClick();
        assertThat(preview.selectedSheet()).isZero();
    }

    @Test
    void anotherLayoutKeepsTheSelectedSheetWhenItStillExists() {
        preview.selectSheet(2);
        preview.show(painter, layout);
        assertThat(preview.selectedSheet()).isEqualTo(2);
        preview.clear();
        assertThat(preview.painter()).isEmpty();
        assertThat(preview.sheetLabel()).isEmpty();
    }

    @Test
    void theEnlargedSheetIsTheSameSheetThePdfGets() {
        BufferedImage shown = new BufferedImage(200, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = shown.createGraphics();
        preview.sheet().setSize(200, 300);
        preview.sheet().paint(graphics);
        graphics.dispose();
        int white = 0;
        for (int y = 0; y < shown.getHeight(); y++) {
            for (int x = 0; x < shown.getWidth(); x++) {
                if (shown.getRGB(x, y) == Color.WHITE.getRGB()) {
                    white++;
                }
            }
        }
        assertThat(white).as("a white sheet is painted").isGreaterThan(200 * 300 / 4);
    }

    @Test
    void theMapPaintsTheCircuitOnWhiteWithTheSheetGridOverIt() {
        BufferedImage shown = new BufferedImage(preview.map().getWidth(), preview.map().getHeight(),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = shown.createGraphics();
        preview.map().paint(graphics);
        graphics.dispose();
        int white = 0;
        int green = 0;
        for (int y = 0; y < shown.getHeight(); y++) {
            for (int x = 0; x < shown.getWidth(); x++) {
                int rgb = shown.getRGB(x, y) & 0xFFFFFF;
                if (rgb == 0xFFFFFF) {
                    white++;
                } else if ((rgb >> 8 & 0xFF) > (rgb >> 16 & 0xFF) + 30 && (rgb >> 8 & 0xFF) > (rgb & 0xFF) + 30) {
                    green++;
                }
            }
        }
        assertThat(white).isGreaterThan(1000);
        assertThat(green).as("the switches are drawn, in colour").isGreaterThan(0);
    }
}
