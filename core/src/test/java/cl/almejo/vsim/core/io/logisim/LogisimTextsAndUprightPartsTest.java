package cl.almejo.vsim.core.io.logisim;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.PinRef;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Logisim's texts keep their anchor (left, centre or right; top, baseline, bottom or centre) with VSim's narrower
 * characters, a text that lands on a part is noted, and parts whose Logisim drawing never turns stay upright.
 */
class LogisimTextsAndUprightPartsTest {

    private static final ComponentTypeRegistry REGISTRY = StandardComponentTypes.registry();

    @Test
    void aLeftTextStartsAtItsAnchorARightOneEndsThereAndACentredOneIsAroundIt() throws Exception {
        CircuitDocument document = importCircuit("""
                <comp lib="6" loc="(200,100)" name="Text">
                  <a name="text" val="abcdefgh"/><a name="halign" val="left"/><a name="valign" val="top"/>
                </comp>
                <comp lib="6" loc="(200,300)" name="Text">
                  <a name="text" val="ijklmnop"/><a name="halign" val="right"/><a name="valign" val="top"/>
                </comp>
                <comp lib="6" loc="(200,500)" name="Text">
                  <a name="text" val="qrstuvwx"/><a name="valign" val="top"/>
                </comp>
                """).document();

        // (200, y) is (30, 1.5 y / 10) in VSim; eight characters are five units wide (five eighths of a unit each).
        assertThat(label(document, "abcdefgh").position()).isEqualTo(new GridPoint(30, 15));
        assertThat(label(document, "ijklmnop").position()).isEqualTo(new GridPoint(25, 45));
        assertThat(label(document, "qrstuvwx").position()).isEqualTo(new GridPoint(28, 75));
    }

    @Test
    void aTextOnItsBaselineSitsAUnitUpAndOneAtItsBottomTwo() throws Exception {
        CircuitDocument document = importCircuit("""
                <comp lib="6" loc="(200,100)" name="Text"><a name="text" val="base"/></comp>
                <comp lib="6" loc="(200,300)" name="Text">
                  <a name="text" val="bottom"/><a name="valign" val="bottom"/>
                </comp>
                """).document();

        assertThat(label(document, "base").position().y()).isEqualTo(14);
        assertThat(label(document, "bottom").position().y()).isEqualTo(43);
    }

    @Test
    void textsThatLandOnPartsAreNotedOnceForTheCircuitAndOnesBesideThemAreNot() throws Exception {
        ImportResult result = importCircuit("""
                <comp lib="1" loc="(200,100)" name="AND Gate"/>
                <comp lib="1" loc="(200,300)" name="OR Gate"/>
                <comp lib="6" loc="(200,100)" name="Text"><a name="text" val="encima"/></comp>
                <comp lib="6" loc="(200,300)" name="Text"><a name="text" val="otro"/></comp>
                <comp lib="6" loc="(600,600)" name="Text"><a name="text" val="lejos"/></comp>
                """);

        assertThat(result.notes()).filteredOn(note -> note.messageKey().equals(LogisimNotes.TEXT_OVER_PART))
                .singleElement().satisfies(note -> {
                    assertThat(note.arguments()).hasSize(1);
                    assertThat(note.arguments().getFirst()).contains("encima", "AND Gate", "otro", "OR Gate")
                            .doesNotContain("lejos");
                });
    }

    @Test
    void aConstantFacingNorthStaysUprightAndKeepsItsWire() throws Exception {
        CircuitDocument document = importCircuit("""
                <comp lib="0" loc="(200,200)" name="Constant"><a name="facing" val="north"/></comp>
                <wire from="(200,100)" to="(200,200)"/>
                <comp lib="0" loc="(200,100)" name="Pin">
                  <a name="facing" val="south"/><a name="output" val="true"/>
                </comp>
                """).document();
        Component constant = only(document, StandardComponentTypes.CONSTANT);
        Component pin = only(document, StandardComponentTypes.OUTPUT_PIN);
        Netlist netlist = new NetlistCompiler(REGISTRY).compile(document);

        assertThat(constant.rotation()).isEqualTo(Rotation.R0);
        assertThat(netlist.netForPin(new PinRef(constant.id(), 0)))
                .isEqualTo(netlist.netForPin(new PinRef(pin.id(), 0)));
    }

    @Test
    void aClockFacingSouthStaysUpright() throws Exception {
        CircuitDocument document = importCircuit("""
                <comp lib="0" loc="(200,200)" name="Clock"><a name="facing" val="south"/></comp>
                """).document();

        assertThat(only(document, StandardComponentTypes.CLOCK).rotation()).isEqualTo(Rotation.R0);
    }

    private static TextLabel label(CircuitDocument document, String text) {
        Optional<TextLabel> found = document.labels().stream().filter(label -> label.text().equals(text)).findFirst();
        assertThat(found).as("a text «%s»", text).isPresent();
        return found.get();
    }

    private static Component only(CircuitDocument document, String typeId) {
        return document.components().stream().filter(component -> component.typeId().equals(typeId))
                .findFirst().orElseThrow();
    }

    private static ImportResult importCircuit(String components) throws Exception {
        String file = """
                <?xml version="1.0" encoding="UTF-8" standalone="no"?>
                <project source="2.7.1" version="1.0">
                  <lib desc="#Wiring" name="0"/>
                  <lib desc="#Gates" name="1"/>
                  <lib desc="#Base" name="6"/>
                  <main name="main"/>
                  <circuit name="main">
                """ + components + """
                  </circuit>
                </project>
                """;
        return LogisimImporter.importFrom(new ByteArrayInputStream(file.getBytes(StandardCharsets.UTF_8)), REGISTRY);
    }
}
