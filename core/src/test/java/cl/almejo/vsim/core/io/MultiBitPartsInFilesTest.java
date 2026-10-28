package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The multi-bit Switch and LED in files: old one-bit parts keep their bytes, wider ones keep their width and side. */
class MultiBitPartsInFilesTest {

    private final CircuitFormat format = new CircuitFormat(StandardComponentTypes.registry());

    private static final String OLD_FILE = """
            {
              "format" : "vsim2",
              "savedWith" : "0.0.0",
              "formatVersion" : %d,
              "kind" : "circuit",
              "name" : "",
              "components" : [ {
                "id" : "c1",
                "type" : "switch",
                "x" : 0,
                "y" : 0,
                "rotation" : 0,
                "label" : "",
                "parameters" : {
                  "value" : "Z"
                }
              } ]
            }""".formatted(CircuitFormat.FORMAT_VERSION);

    @Test
    void aOneBitSwitchWrittenBeforeTheWidthExistedLoadsAndSavesWithoutNewKeys() throws CircuitFormatException {
        CircuitDocument loaded = format.read(OLD_FILE);

        assertThat(loaded.components().getFirst().parameters())
                .isEqualTo(Map.of("value", "Z", "width", 1, "side", "right"));
        String written = format.write(loaded);
        assertThat(written).doesNotContain("\"width\"").doesNotContain("\"side\"").contains("\"value\" : \"Z\"");
    }

    @Test
    void aMultiBitSwitchKeepsItsWidthAndASideThatIsNotTheDefault() throws CircuitFormatException {
        CircuitDocument document = new CircuitDocument();
        document.add(new Component(document.newComponentId(), "switch", new GridPoint(0, 0), Rotation.R0,
                Map.of("value", "0xA6", "width", 8, "side", "top"), ""));

        CircuitDocument loaded = format.read(format.write(document));

        assertThat(loaded.components().getFirst().parameters())
                .isEqualTo(Map.of("value", "0xA6", "width", 8, "side", "top"));
    }

    @Test
    void aMultiBitSwitchOnTheDefaultSideDoesNotWriteIt() throws CircuitFormatException {
        CircuitDocument document = new CircuitDocument();
        document.add(new Component(document.newComponentId(), "switch", new GridPoint(0, 0), Rotation.R0,
                Map.of("value", "5", "width", 4, "side", "right"), ""));

        String written = format.write(document);

        assertThat(written).contains("\"width\" : 4").doesNotContain("\"side\"");
        assertThat(format.read(written).components().getFirst().parameters().get("side")).isEqualTo("right");
    }

    @Test
    void aOneBitLedOfAnyColourLoadsAndSavesWithoutNewKeys() throws CircuitFormatException {
        CircuitDocument document = new CircuitDocument();
        document.add(new Component(document.newComponentId(), "led", new GridPoint(0, 0), Rotation.R0,
                Map.of("color", "cyan"), ""));
        String written = format.write(document);

        assertThat(written).doesNotContain("\"width\"").doesNotContain("\"side\"").contains("\"color\" : \"cyan\"");
        assertThat(format.read(written).components().getFirst().parameters())
                .isEqualTo(Map.of("color", "cyan", "width", 1, "side", "left"));
    }

    @Test
    void aMultiBitLedKeepsItsWidthAndASideThatIsNotTheDefault() throws CircuitFormatException {
        CircuitDocument document = new CircuitDocument();
        document.add(new Component(document.newComponentId(), "led", new GridPoint(0, 0), Rotation.R0,
                Map.of("color", "green", "width", 8, "side", "bottom"), ""));
        document.add(new Component(document.newComponentId(), "led", new GridPoint(0, 10), Rotation.R0,
                Map.of("color", "red", "width", 8, "side", "left"), ""));

        String written = format.write(document);
        CircuitDocument loaded = format.read(written);

        assertThat(loaded.components().get(0).parameters())
                .isEqualTo(Map.of("color", "green", "width", 8, "side", "bottom"));
        assertThat(loaded.components().get(1).parameters())
                .isEqualTo(Map.of("color", "red", "width", 8, "side", "left"));
        assertThat(written.split("\"side\"", -1)).as("only the side that is not the default is written").hasSize(2);
    }

    @Test
    void aMultiBitSwitchSavedWithARotationLoadsUprightWithItsPinWhereTheFileHadIt() throws CircuitFormatException {
        CircuitDocument document = new CircuitDocument();
        Component turned = new Component(document.newComponentId(), "switch", new GridPoint(10, 10), Rotation.R90,
                Map.of("value", "5", "width", 4), "");
        document.add(turned);
        ComponentType type = StandardComponentTypes.registry().require("switch");

        Component loaded = format.read(format.write(document)).components().getFirst();

        assertThat(loaded.rotation()).isEqualTo(Rotation.R0);
        assertThat(loaded.parameters().get("side")).isEqualTo("bottom");
        assertThat(type.pinLocation(loaded, 0)).isEqualTo(type.pinLocation(turned, 0));
    }

    @Test
    void aOneBitSwitchSavedWithASideLoadsTurnedThatWay() throws CircuitFormatException {
        CircuitDocument document = new CircuitDocument();
        document.add(new Component(document.newComponentId(), "switch", new GridPoint(10, 10), Rotation.R0,
                Map.of("value", "1", "side", "left"), ""));

        Component loaded = format.read(format.write(document)).components().getFirst();

        assertThat(loaded.rotation()).isEqualTo(Rotation.R180);
        assertThat(loaded.parameters().get("side")).isEqualTo("right");
    }
}
