package cl.almejo.vsim.ui.export;

import cl.almejo.vsim.core.bundled.BundledExamples;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ImageExportTest {

    @TempDir
    Path directory;

    private static CircuitImage imageOf(OpenDocument source, ColorScheme scheme) {
        return CircuitImage.of(source.document(), source.netlist(), source.netlistToken(), source.latestSnapshot(),
                source.registry(), PainterRegistry.standard(), scheme, Optional.empty()).orElseThrow();
    }

    private static OpenDocument example(String id) throws Exception {
        return new OpenDocument(BundledExamples.open(id, StandardComponentTypes.registry()),
                StandardComponentTypes.registry(), null, false);
    }

    @Test
    void aPngOfTheFullAdderDecodesWithTheExpectedSizeAtOneAndTwoTimes() throws Exception {
        try (OpenDocument source = example(BundledExamples.ids().stream()
                .filter(id -> id.contains("adder")).findFirst().orElse(BundledExamples.ids().get(0)))) {
            CircuitImage image = imageOf(source, ColorScheme.LIGHT);
            for (int scale : new int[]{1, 2}) {
                Path file = directory.resolve("adder-" + scale + ".png");
                ImageExport.writePng(file, image, scale);
                BufferedImage decoded = ImageIO.read(file.toFile());
                assertThat(decoded.getWidth()).isEqualTo(image.width(scale));
                assertThat(decoded.getHeight()).isEqualTo(image.height(scale));
            }
        }
    }

    @Test
    void writingIntoAReservedFileNeverFollowsALinkPlantedInItsPlace() throws Exception {
        Path elsewhere = Files.writeString(directory.resolve("elsewhere.txt"), "not yours");
        Path link = Files.createSymbolicLink(directory.resolve("circuit.png"), elsewhere);
        BufferedImage pixels = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ImageExport.writePngInto(link, pixels))
                .isInstanceOf(java.io.IOException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ImageExport.writeSvgInto(link, "<svg/>"))
                .isInstanceOf(java.io.IOException.class);
        assertThat(Files.readString(elsewhere)).isEqualTo("not yours");

        Path reserved = Files.createFile(directory.resolve("reserved.png"));
        ImageExport.writePngInto(reserved, pixels);
        assertThat(ImageIO.read(reserved.toFile()).getWidth()).isEqualTo(4);
    }

    @Test
    void theSvgIsWellFormedXmlWithTheSizeOfTheBounds() throws Exception {
        try (OpenDocument source = example(BundledExamples.ids().get(0))) {
            CircuitImage image = imageOf(source, ColorScheme.LIGHT);
            Path file = directory.resolve("circuit.svg");
            ImageExport.writeSvg(file, image);
            Element root = offlineParser().parse(file.toFile()).getDocumentElement();
            assertThat(root.getTagName()).isEqualTo("svg");
            assertThat(root.getAttribute("width")).startsWith(String.valueOf(image.width(1)));
            assertThat(root.getAttribute("height")).startsWith(String.valueOf(image.height(1)));
        }
    }

    /**
     * A parser that never reaches the network: the SVG names the SVG DTD on w3.org, so the external DTD is not
     * loaded, and any other external lookup fails the test.
     */
    private static DocumentBuilder offlineParser() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) -> {
            throw new AssertionError("the SVG test must not look up " + systemId);
        });
        return builder;
    }

    @Test
    void everyBundledExampleExportsToBothFormats() throws Exception {
        for (String id : BundledExamples.ids()) {
            try (OpenDocument source = example(id)) {
                CircuitImage image = imageOf(source, ColorScheme.LIGHT);
                Path png = directory.resolve(id + ".png");
                Path svg = directory.resolve(id + ".svg");
                ImageExport.writePng(png, image, 2);
                ImageExport.writeSvg(svg, image);
                assertThat(Files.size(png)).as(id).isPositive();
                assertThat(Files.size(svg)).as(id).isPositive();
            }
        }
    }
}
