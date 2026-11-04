package cl.almejo.vsim.ui.print;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The printer's graphics seen from the sheet: whatever base Java's pass or band carries (the four measured in the
 * final review of «Imprimir»), the painters read the PDF's transform, no device configuration and the PDF's font
 * measuring.
 */
class SheetGraphicsTest {

    /** 300 dpi over 72 points per inch. */
    private static final double DPI_SCALE = 300.0 / 72;
    private static final double TOLERANCE = 1e-9;

    /** The four bases of A4 at 300 dpi: portrait and landscape, each as a vector pass and as a raster band. */
    static List<AffineTransform> javaBases() {
        AffineTransform portraitVector = AffineTransform.getScaleInstance(DPI_SCALE, DPI_SCALE);
        AffineTransform portraitBand = new AffineTransform(DPI_SCALE, 0, 0, -DPI_SCALE, 0, 3508);
        AffineTransform landscapeVector = new AffineTransform(0, -DPI_SCALE, DPI_SCALE, 0, 0, 3508);
        AffineTransform landscapeBand = new AffineTransform(0, DPI_SCALE, DPI_SCALE, 0, 0, 0);
        return List.of(portraitVector, portraitBand, landscapeVector, landscapeBand);
    }

    private static void withSheet(AffineTransform base, Consumer<SheetGraphics> work) {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        Graphics2D printer = image.createGraphics();
        printer.setTransform(base);
        SheetGraphics sheet = SheetGraphics.over(printer);
        try {
            work.accept(sheet);
        } finally {
            sheet.dispose();
        }
    }

    private static int quarterTurns(AffineTransform transform) {
        return (int) Math.round(Math.atan2(transform.getShearY(), transform.getScaleX()) / (Math.PI / 2));
    }

    @ParameterizedTest
    @MethodSource("javaBases")
    void theSheetStartsAtTheIdentityWithNoDevice(AffineTransform base) {
        withSheet(base, sheet -> {
            assertThat(sheet.getTransform().isIdentity()).isTrue();
            assertThat(sheet.getDeviceConfiguration()).isNull();
            assertThat(sheet.getFontRenderContext().getTransform().isIdentity()).isTrue();
        });
    }

    @ParameterizedTest
    @MethodSource("javaBases")
    void aPartTurnedAQuarterReadsOneQuarterTurn(AffineTransform base) {
        withSheet(base, sheet -> {
            sheet.translate(40, 30);
            sheet.rotate(Math.PI / 2);
            assertThat(quarterTurns(sheet.getTransform())).isEqualTo(1);
            Graphics2D child = (Graphics2D) sheet.create();
            try {
                child.scale(2, 2);
                assertThat(quarterTurns(child.getTransform())).as("a child keeps the sheet's base").isEqualTo(1);
                assertThat(child.getTransform().getShearY()).isCloseTo(2, within(TOLERANCE));
            } finally {
                child.dispose();
            }
        });
    }

    @ParameterizedTest
    @MethodSource("javaBases")
    void settingTheReadTransformBackPaintsWhereJavaWould(AffineTransform base) {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        Graphics2D printer = image.createGraphics();
        printer.setTransform(base);
        SheetGraphics sheet = SheetGraphics.over(printer);
        try {
            sheet.translate(10, 20);
            AffineTransform saved = sheet.getTransform();
            AffineTransform device = printer.getTransform();
            sheet.rotate(1.0);
            sheet.setTransform(saved);
            assertThat(printer.getTransform()).isEqualTo(device);
            sheet.setTransform(new AffineTransform());
            assertThat(printer.getTransform()).isEqualTo(base);
        } finally {
            sheet.dispose();
        }
    }

    @Test
    void fontsAreMeasuredAsThePdfMeasuresThem() {
        AffineTransform landscapeVector = javaBases().get(2);
        withSheet(landscapeVector, sheet -> {
            Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
            assertThat(sheet.getFontMetrics(font).getFontRenderContext().getTransform().isIdentity()).isTrue();
            assertThat(sheet.getFontRenderContext().usesFractionalMetrics()).isTrue();
        });
    }
}
