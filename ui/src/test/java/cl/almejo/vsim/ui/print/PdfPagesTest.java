package cl.almejo.vsim.ui.print;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class PdfPagesTest {

    private static final PdfPages.PageSize A4_PORTRAIT =
            new PdfPages.PageSize(Paper.A4.widthPoints(), Paper.A4.heightPoints());
    private static final PdfPages.PageSize LETTER_LANDSCAPE =
            new PdfPages.PageSize(Paper.LETTER.heightPoints(), Paper.LETTER.widthPoints());
    private static final PdfPages.PageSize OFICIO_PORTRAIT =
            new PdfPages.PageSize(Paper.OFICIO.widthPoints(), Paper.OFICIO.heightPoints());

    @TempDir
    Path folder;

    private Path write(List<PdfPages.PageSize> pages, PdfPages.PageContent content) throws IOException {
        Path file = folder.resolve("pages.pdf");
        try (OutputStream output = Files.newOutputStream(file)) {
            PdfPages.write(output, pages, content);
        }
        return file;
    }

    /** Accented text, a clip, a translucent fill and a dashed stroke: what the circuit pages use. */
    private static void paintSample(int pageIndex, Graphics2D graphics) {
        graphics.setColor(Color.BLACK);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        graphics.drawString("Fila " + (pageIndex + 1) + " · Señal ñ á", 40, 60);
        Graphics2D clipped = (Graphics2D) graphics.create();
        clipped.clip(new Rectangle2D.Double(40, 100, 100, 50));
        clipped.fill(new Rectangle2D.Double(0, 0, 400, 400));
        clipped.dispose();
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
        graphics.setColor(new Color(30, 90, 200));
        graphics.fill(new Rectangle2D.Double(40, 200, 120, 60));
        graphics.setComposite(AlphaComposite.SrcOver);
        graphics.setColor(Color.DARK_GRAY);
        graphics.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                new float[] {4f, 2f}, 0f));
        graphics.draw(new Line2D.Double(40, 300, 300, 300));
    }

    @Test
    void writesOnePagePerSizeWithTheExactSizes() throws IOException {
        List<PdfPages.PageSize> sizes = List.of(A4_PORTRAIT, LETTER_LANDSCAPE, OFICIO_PORTRAIT);
        Path file = write(sizes, PdfPagesTest::paintSample);

        assertThat(PdfReading.pageCount(file)).isEqualTo(3);
        for (int pageIndex = 0; pageIndex < sizes.size(); pageIndex++) {
            PdfPages.PageSize read = PdfReading.pageSize(file, pageIndex);
            assertThat(read.widthPoints()).isCloseTo(sizes.get(pageIndex).widthPoints(), within(0.5));
            assertThat(read.heightPoints()).isCloseTo(sizes.get(pageIndex).heightPoints(), within(0.5));
        }
    }

    @Test
    void everythingIsVectorTextIncluded() throws IOException {
        Path file = write(List.of(A4_PORTRAIT, LETTER_LANDSCAPE, OFICIO_PORTRAIT), PdfPagesTest::paintSample);

        for (int pageIndex = 0; pageIndex < 3; pageIndex++) {
            assertThat(PdfReading.hasImages(file, pageIndex)).as("images on page %d", pageIndex).isFalse();
            assertThat(PdfReading.pathOperatorCount(file, pageIndex)).as("paths on page %d", pageIndex)
                    .isGreaterThan(10);
            // Text is drawn as glyph outlines, so nothing is extractable (and no font needs embedding).
            assertThat(PdfReading.text(file, pageIndex)).isEmpty();
            assertThat(PdfReading.operators(file, pageIndex)).doesNotContain("Tj", "TJ", "BT");
        }
    }

    @Test
    void keepsTheClipTheTranslucencyAndTheDashes() throws IOException {
        Path file = write(List.of(A4_PORTRAIT), PdfPagesTest::paintSample);

        List<String> operators = PdfReading.operators(file, 0);
        assertThat(operators).contains("W", "d", "gs");
        assertThat(PdfReading.fillAlphas(file, 0)).anySatisfy(alpha -> assertThat(alpha).isCloseTo(0.4f,
                within(0.01f)));
    }

    @Test
    void theJavaOriginIsTheTopLeftCornerOfAPageWithAFractionalHeight() throws IOException {
        // A4 is 841.89 pt high; a band at y = 0 must touch the top edge and one at h − 2 the bottom edge.
        Path file = write(List.of(A4_PORTRAIT), (pageIndex, graphics) -> {
            graphics.setColor(Color.BLACK);
            graphics.fill(new Rectangle2D.Double(0, 0, A4_PORTRAIT.widthPoints(), 2));
            graphics.fill(new Rectangle2D.Double(0, A4_PORTRAIT.heightPoints() - 2, A4_PORTRAIT.widthPoints(), 2));
        });

        BufferedImage image = PdfReading.render(file, 0, 4f);
        int middleColumn = image.getWidth() / 2;
        assertThat(isDark(image.getRGB(middleColumn, 1))).as("top edge").isTrue();
        assertThat(isDark(image.getRGB(middleColumn, image.getHeight() - 2))).as("bottom edge").isTrue();
        assertThat(isDark(image.getRGB(middleColumn, 16))).as("4 pt below the top").isFalse();
        assertThat(isDark(image.getRGB(middleColumn, image.getHeight() - 17))).as("4 pt above the bottom")
                .isFalse();
    }

    @Test
    void paintsThePagesInOrderWithTheirIndices() throws IOException {
        List<Integer> painted = new ArrayList<>();
        write(List.of(A4_PORTRAIT, A4_PORTRAIT, A4_PORTRAIT), (pageIndex, graphics) -> painted.add(pageIndex));

        assertThat(painted).containsExactly(0, 1, 2);
    }

    @Test
    void aFailingPageWritesNothing() {
        Path file = folder.resolve("failed.pdf");
        assertThatThrownBy(() -> {
            try (OutputStream output = Files.newOutputStream(file)) {
                PdfPages.write(output, List.of(A4_PORTRAIT, A4_PORTRAIT), (pageIndex, graphics) -> {
                    if (pageIndex == 1) {
                        throw new IOException("cancelled");
                    }
                });
            }
        }).isInstanceOf(IOException.class).hasMessage("cancelled");
        assertThat(file).isEmptyFile();
    }

    @Test
    void rejectsNoPagesAndNonPositiveSizes() {
        assertThatThrownBy(() -> PdfPages.write(OutputStream.nullOutputStream(), List.of(),
                (pageIndex, graphics) -> { }))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PdfPages.PageSize(0, 100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PdfPages.PageSize(100, Double.NaN)).isInstanceOf(IllegalArgumentException.class);
    }

    private static boolean isDark(int rgb) {
        Color color = new Color(rgb);
        return color.getRed() + color.getGreen() + color.getBlue() < 3 * 96;
    }
}
