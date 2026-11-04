package cl.almejo.vsim.core.io.logisim;

import cl.almejo.vsim.core.io.CircuitFormatException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LogisimReaderTest {

    @Test
    void readsBothCircuitsAndTheMainOne() throws Exception {
        LogisimProject project = read("reader-basics.circ");

        assertThat(project.mainCircuit()).isEqualTo("top");
        assertThat(project.circuits()).extracting(LogisimCircuit::name).containsExactly("half", "top");
        assertThat(project.circuit("half")).isPresent();
        assertThat(project.circuit("nowhere")).isEmpty();
        assertThat(project.options()).containsEntry("gateUndefined", "ignore").containsEntry("simlimit", "1000");
    }

    @Test
    void resolvesEachComponentsLibraryByItsIndex() throws Exception {
        LogisimCircuit top = read("reader-basics.circ").circuit("top").orElseThrow();

        assertThat(component(top, "AND Gate").library()).isEqualTo("#Gates");
        assertThat(component(top, "Multiplexer").library()).isEqualTo("#Plexers");
        assertThat(component(top, "Constant").library()).isEqualTo("#Wiring");
        assertThat(component(top, "AND Gate").location()).isEqualTo(new LogisimPoint(200, 100));
    }

    @Test
    void aSubcircuitInstanceHasNoLibraryAndTheCircuitsName() throws Exception {
        LogisimCircuit top = read("reader-basics.circ").circuit("top").orElseThrow();

        LogisimComponent half = component(top, "half");

        assertThat(half.library()).isEmpty();
        assertThat(half.isSubcircuit()).isTrue();
        assertThat(half.attributes().text("facing")).isEqualTo("east");
    }

    @Test
    void anOmittedAttributeTakesLogisimsDefault() throws Exception {
        LogisimCircuit top = read("reader-basics.circ").circuit("top").orElseThrow();

        LogisimAttributes and = component(top, "AND Gate").attributes();

        // Logisim 2.7's AND gate: 5 inputs, size 50, facing east, no negated input
        assertThat(and.integer("inputs")).isEqualTo(5);
        assertThat(and.integer("size")).isEqualTo(50);
        assertThat(and.integer("width")).isEqualTo(1);
        assertThat(and.text("facing")).isEqualTo("east");
        assertThat(IntStream.range(0, 5).mapToObj(input -> and.bool("negate" + input))).containsOnly(false);
        assertThat(and.has("negate5")).isFalse();
    }

    @Test
    void anAttributeWrittenInTheFileWinsOverTheDefault() throws Exception {
        LogisimCircuit top = read("reader-basics.circ").circuit("top").orElseThrow();

        LogisimAttributes or = component(top, "OR Gate").attributes();

        assertThat(or.integer("inputs")).isEqualTo(2);
        assertThat(or.bool("negate0")).isFalse();
        assertThat(or.bool("negate1")).isTrue();
    }

    @Test
    void theToolsOfALibraryDoNotChangeThePlacedComponents() throws Exception {
        LogisimCircuit top = read("reader-basics.circ").circuit("top").orElseThrow();

        // the file's <lib><tool name="Constant"> says width 2 and value 0x3: only new placements use it
        LogisimAttributes constant = component(top, "Constant").attributes();

        assertThat(constant.integer("width")).isEqualTo(1);
        assertThat(constant.text("value")).isEqualTo("0x1");
    }

    @Test
    void aSplitterWithoutBitsSendsBitNToEndNAndTheRestEvenly() throws Exception {
        LogisimCircuit top = read("reader-basics.circ").circuit("top").orElseThrow();

        LogisimAttributes splitter = splitterAt(top, 400, 100).attributes();

        // checked against Logisim 2.7.1 itself: bits 0..3 to ends 0..3, then 4-7, 8-15, 16-23, 24-31 to ends 0..3
        List<String> ends = IntStream.range(0, 32).mapToObj(bit -> splitter.text("bit" + bit)).toList();
        assertThat(ends.subList(0, 4)).containsExactly("0", "1", "2", "3");
        assertThat(ends.subList(4, 8)).containsOnly("0");
        assertThat(ends.subList(8, 16)).containsOnly("1");
        assertThat(ends.subList(16, 24)).containsOnly("2");
        assertThat(ends.subList(24, 32)).containsOnly("3");
        assertThat(splitter.text("facing")).isEqualTo("east");
        assertThat(splitter.text("appear")).isEqualTo("left");
    }

    @Test
    void aSplitterKeepsNoneAndIgnoresAnEndItDoesNotHave() throws Exception {
        LogisimCircuit top = read("reader-basics.circ").circuit("top").orElseThrow();

        LogisimAttributes splitter = splitterAt(top, 400, 300).attributes();

        // fanout 3, 8 bits: bit0 = 2 written, bit5 = none, bit6 = 9 (no such end: the even split's end 2 stays)
        assertThat(IntStream.range(0, 8).mapToObj(bit -> splitter.text("bit" + bit)))
                .containsExactly("2", "1", "2", "1", "1", "none", "2", "2");
    }

    @Test
    void aMultiplexerWithoutSelectHasOneSelectBitAndForThisVersionAnEnable() throws Exception {
        LogisimCircuit top = read("reader-basics.circ").circuit("top").orElseThrow();

        LogisimAttributes mux = component(top, "Multiplexer").attributes();

        assertThat(mux.integer("select")).isEqualTo(1);
        assertThat(mux.integer("width")).isEqualTo(32);
        assertThat(mux.bool("enable")).isTrue();
        assertThat(mux.text("selloc")).isEqualTo("bl");
    }

    @Test
    void aFileOlderThanTheEnableInputHasMultiplexersWithoutIt() throws Exception {
        String file = """
                <project source="2.6.0" version="1.0">
                  <lib desc="#Plexers" name="2"/>
                  <circuit name="main"><comp lib="2" loc="(10,10)" name="Multiplexer"/></circuit>
                </project>
                """;

        LogisimProject project = readText(file);

        assertThat(project.circuits().getFirst().components().getFirst().attributes().bool("enable")).isFalse();
    }

    @Test
    void readsTheWiresInLogisimPixels() throws Exception {
        LogisimCircuit top = read("reader-basics.circ").circuit("top").orElseThrow();

        assertThat(top.wires()).containsExactly(
                new LogisimWire(new LogisimPoint(60, 40), new LogisimPoint(60, 90)),
                new LogisimWire(new LogisimPoint(20, 40), new LogisimPoint(60, 40)));
    }

    @Test
    void aTextKeepsItsFont() throws Exception {
        LogisimCircuit top = read("reader-basics.circ").circuit("top").orElseThrow();

        List<LogisimComponent> texts = top.components().stream()
                .filter(component -> component.name().equals("Text")).toList();

        assertThat(texts.getFirst().attributes().text("text")).isEqualTo("Hola");
        assertThat(texts.getFirst().attributes().text("font")).isEqualTo("SansSerif bolditalic 14");
        assertThat(texts.get(1).attributes().text("text")).isEmpty();
        assertThat(texts.get(1).attributes().text("font")).isEqualTo("SansSerif plain 12");
        assertThat(texts.get(1).attributes().text("halign")).isEqualTo("center");
    }

    @Test
    void readsTheAppearanceShapes() throws Exception {
        LogisimAppearance appearance = read("reader-basics.circ").circuit("half").orElseThrow().appearance();

        assertThat(appearance.shapes()).hasSize(11);
        assertThat(appearance.shapes().get(0)).isEqualTo(new LogisimAppearance.Rectangle(50, 50, 30, 40, 0,
                new LogisimAppearance.Paint("#000000", "none", 2)));
        assertThat(appearance.shapes().get(1)).isEqualTo(new LogisimAppearance.Rectangle(55, 55, 20, 20, 5,
                new LogisimAppearance.Paint("#000000", "#ffffff", 1)));
        assertThat(appearance.shapes().get(2)).isEqualTo(new LogisimAppearance.Oval(55, 66, 10, 8,
                new LogisimAppearance.Paint("none", "#000000", 1)));
        assertThat(appearance.shapes().get(3)).isEqualTo(new LogisimAppearance.Line(new LogisimPoint(50, 60),
                new LogisimPoint(80, 60), new LogisimAppearance.Paint("#808080", "none", 1)));
        assertThat(appearance.shapes().get(4)).isEqualTo(new LogisimAppearance.Polyline(false,
                List.of(new LogisimPoint(50, 50), new LogisimPoint(60, 40), new LogisimPoint(70, 50)),
                new LogisimAppearance.Paint("#000000", "none", 1)));
        assertThat(appearance.shapes().get(5)).isInstanceOfSatisfying(LogisimAppearance.Polyline.class,
                polygon -> assertThat(polygon.closed()).isTrue());
        assertThat(appearance.shapes().get(6)).isEqualTo(new LogisimAppearance.Curve(new LogisimPoint(61, 51),
                new LogisimPoint(69, 51), new LogisimPoint(65, 61), new LogisimAppearance.Paint("#808080", "none", 2)));
        assertThat(appearance.shapes().get(7)).isEqualTo(new LogisimAppearance.Text(65, 75, "Half",
                new LogisimFont("SansSerif", true, true, 14), LogisimAppearance.TextAnchor.START, "#000000"));
        assertThat(appearance.shapes().get(8)).isEqualTo(new LogisimAppearance.Port(new LogisimPoint(100, 200),
                46, 66, 8, 8));
        assertThat(appearance.shapes().get(10)).isEqualTo(new LogisimAppearance.Anchor(77, 67, 6, 6, "east"));
    }

    @Test
    void aCurveMayGiveItsLastPointsRelativeToItsStartAndAnyOtherPathIsANote() throws Exception {
        LogisimProject project = readText("<project source=\"2.7.1\" version=\"1.0\"><circuit name=\"c\"><appear>"
                + "<path d=\"M61,51 q4,10 8,0\" fill=\"none\" stroke=\"#808080\" stroke-width=\"2\"/>"
                + "<path d=\"M 61 51 Q 65 61 69 51\" fill=\"none\" stroke=\"#808080\" stroke-width=\"2\"/>"
                + "<path d=\"M61,51 L65,61\" fill=\"none\" stroke=\"#808080\"/>"
                + "<path d=\"M61.5,51 Q65,61 69,51\" fill=\"none\" stroke=\"#808080\"/>"
                + "</appear></circuit></project>");
        LogisimAppearance.Curve notch = new LogisimAppearance.Curve(new LogisimPoint(61, 51), new LogisimPoint(69, 51),
                new LogisimPoint(65, 61), new LogisimAppearance.Paint("#808080", "none", 2));

        assertThat(project.circuit("c").orElseThrow().appearance().shapes()).containsExactly(notch, notch);
        assertThat(project.notes()).filteredOn(note -> note.messageKey().equals(LogisimNotes.BAD_SHAPE)).hasSize(2);
    }

    @Test
    void anUnknownAppearanceShapeIsANote() throws Exception {
        LogisimProject project = read("reader-basics.circ");

        assertThat(project.notes()).anySatisfy(note -> {
            assertThat(note.kind()).isEqualTo(ImportNote.Kind.UNSUPPORTED);
            assertThat(note.circuit()).isEqualTo("half");
            assertThat(note.messageKey()).isEqualTo(LogisimNotes.UNKNOWN_SHAPE);
            assertThat(note.arguments()).containsExactly("blink");
        });
    }

    @Test
    void aCircuitWithoutAppearanceHasNoShapes() throws Exception {
        assertThat(read("reader-basics.circ").circuit("top").orElseThrow().appearance().shapes()).isEmpty();
    }

    @Test
    void aComponentOfAnUndeclaredLibraryIsANoteNotAFailure() throws Exception {
        LogisimProject project = read("reader-basics.circ");

        assertThat(project.circuit("top").orElseThrow().components())
                .noneMatch(component -> component.name().equals("Ghost"));
        assertThat(project.notes()).anySatisfy(note -> {
            assertThat(note.kind()).isEqualTo(ImportNote.Kind.INVALID);
            assertThat(note.circuit()).isEqualTo("top");
            assertThat(note.where()).contains(new LogisimPoint(800, 100));
            assertThat(note.messageKey()).isEqualTo(LogisimNotes.MISSING_LIBRARY);
            assertThat(note.arguments()).containsExactly("Ghost", "9");
        });
    }

    @Test
    void anUnknownLibraryIsANoteAndItsComponentsKeepOnlyTheirAttributes() throws Exception {
        String file = """
                <project source="2.7.1" version="1.0">
                  <lib desc="jar#/somewhere/fancy.jar#Fancy" name="0"/>
                  <circuit name="main">
                    <comp lib="0" loc="(10,10)" name="Sparkle"><a name="glow" val="9"/></comp>
                  </circuit>
                </project>
                """;

        LogisimProject project = readText(file);

        LogisimComponent sparkle = project.circuits().getFirst().components().getFirst();
        assertThat(sparkle.library()).isEqualTo("jar#/somewhere/fancy.jar#Fancy");
        assertThat(sparkle.attributes().integer("glow")).isEqualTo(9);
        assertThat(project.notes()).singleElement().satisfies(note -> {
            assertThat(note.kind()).isEqualTo(ImportNote.Kind.UNSUPPORTED);
            assertThat(note.messageKey()).isEqualTo(LogisimNotes.UNKNOWN_LIBRARY);
            assertThat(note.where()).isEmpty();
        });
    }

    @Test
    void withoutAMainElementTheFirstCircuitIsTheMainOne() throws Exception {
        LogisimProject project = readText("""
                <project source="2.7.1" version="1.0">
                  <circuit name="first"/>
                  <circuit name="second"/>
                </project>
                """);

        assertThat(project.mainCircuit()).isEqualTo("first");
    }

    @Test
    void textThatIsNotXmlIsRefused() {
        assertThatThrownBy(() -> readText("{\"format\": \"vsim2\"}"))
                .isInstanceOfSatisfying(CircuitFormatException.class,
                        exception -> assertThat(exception.reason()).isEqualTo(CircuitFormatException.Reason.NOT_XML));
    }

    @Test
    void xmlThatIsNotALogisimProjectIsRefused() {
        assertThatThrownBy(() -> readText("<svg><rect/></svg>"))
                .isInstanceOfSatisfying(CircuitFormatException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(CircuitFormatException.Reason.NOT_LOGISIM_PROJECT));
    }

    @Test
    void aFileThatReferencesAnExternalEntityIsRefusedWithoutReadingIt() {
        String file = """
                <?xml version="1.0"?>
                <!DOCTYPE project [<!ENTITY secret SYSTEM "file:///etc/passwd">]>
                <project source="2.7.1"><circuit name="&secret;"/></project>
                """;

        assertThatThrownBy(() -> readText(file)).isInstanceOf(CircuitFormatException.class);
    }

    @Test
    void everyBuiltInPartGetsLogisimsDefaults() throws Exception {
        LogisimCircuit main = read("every-part.circ").circuit("main").orElseThrow();

        // a few values checked against Logisim 2.7.1's own load of this fixture
        assertThat(component(main, "Register").attributes().text("trigger")).isEqualTo("rising");
        assertThat(component(main, "Register").attributes().integer("width")).isEqualTo(8);
        assertThat(component(main, "RAM").attributes().text("bus")).isEqualTo("combined");
        assertThat(component(main, "ROM").attributes().text("contents")).isEqualTo("addr/data: 8 8\n0\n");
        assertThat(component(main, "Comparator").attributes().text("mode")).isEqualTo("twosComplement");
        assertThat(component(main, "Shifter").attributes().text("shift")).isEqualTo("ll");
        assertThat(component(main, "Bit Extender").attributes().text("type")).isEqualTo("zero");
        assertThat(component(main, "NOT Gate").attributes().integer("size")).isEqualTo(30);
        assertThat(component(main, "XOR Gate").attributes().text("xor")).isEqualTo("1");
        assertThat(component(main, "Pin").attributes().bool("tristate")).isTrue();
        assertThat(component(main, "Pin").attributes().text("facing")).isEqualTo("east");
        assertThat(component(main, "Tunnel").attributes().text("facing")).isEqualTo("west");
        assertThat(component(main, "Clock").attributes().integer("highDuration")).isEqualTo(1);
        assertThat(component(main, "Keyboard").attributes().integer("buflen")).isEqualTo(32);
        assertThat(component(main, "TTY").attributes().integer("rows")).isEqualTo(8);
        assertThat(component(main, "Counter").attributes().text("max")).isEqualTo("0xff");
        assertThat(component(main, "LED").attributes().text("color")).isEqualTo("#f00000");
        assertThat(component(main, "Ground").attributes().text("facing")).isEqualTo("south");
        assertThat(main.components()).hasSize(57);
        assertThat(read("every-part.circ").notes()).isEmpty();
    }

    private static LogisimComponent component(LogisimCircuit circuit, String name) {
        return circuit.components().stream().filter(component -> component.name().equals(name)).findFirst()
                .orElseThrow();
    }

    private static LogisimComponent splitterAt(LogisimCircuit circuit, int x, int y) {
        return circuit.components().stream()
                .filter(component -> component.name().equals("Splitter")
                        && component.location().equals(new LogisimPoint(x, y)))
                .findFirst().orElseThrow();
    }

    static LogisimProject read(String fixture) throws IOException, CircuitFormatException {
        try (InputStream input = LogisimReaderTest.class.getResourceAsStream(fixture)) {
            assertThat(input).as(fixture).isNotNull();
            return new LogisimReader().read(input);
        }
    }

    private static LogisimProject readText(String text) throws CircuitFormatException {
        return new LogisimReader().read(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
    }
}
