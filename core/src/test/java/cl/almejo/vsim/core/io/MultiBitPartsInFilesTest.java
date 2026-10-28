package cl.almejo.vsim.core.io;

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
}
