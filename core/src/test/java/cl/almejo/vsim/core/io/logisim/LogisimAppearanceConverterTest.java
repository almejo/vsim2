package cl.almejo.vsim.core.io.logisim;

import cl.almejo.vsim.core.component.PinDirection;
import cl.almejo.vsim.core.component.appearance.SymbolNode;
import cl.almejo.vsim.core.component.appearance.SymbolPath;
import cl.almejo.vsim.core.component.appearance.SymbolPoint;
import cl.almejo.vsim.core.component.appearance.SymbolText;
import cl.almejo.vsim.core.component.appearance.TextAlignment;
import cl.almejo.vsim.core.component.appearance.TextSize;
import cl.almejo.vsim.core.document.NoteColor;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.logisim.LogisimAppearance.Anchor;
import cl.almejo.vsim.core.io.logisim.LogisimAppearance.Curve;
import cl.almejo.vsim.core.io.logisim.LogisimAppearance.Oval;
import cl.almejo.vsim.core.io.logisim.LogisimAppearance.Paint;
import cl.almejo.vsim.core.io.logisim.LogisimAppearance.Polyline;
import cl.almejo.vsim.core.io.logisim.LogisimAppearance.Port;
import cl.almejo.vsim.core.io.logisim.LogisimAppearance.Rectangle;
import cl.almejo.vsim.core.io.logisim.LogisimAppearance.Shape;
import cl.almejo.vsim.core.io.logisim.LogisimAppearance.Text;
import cl.almejo.vsim.core.io.logisim.LogisimAppearance.TextAnchor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class LogisimAppearanceConverterTest {

    private static final Paint BLACK_LINE = new Paint("#000000", "none", 2);
    private static final Paint GRAY_LINE = new Paint("#808080", "none", 2);
    private static final LogisimAttributes NO_LABEL = LogisimAttributes.of(LogisimCircuit.DEFAULT_ATTRIBUTES);

    @Test
    void aRectangleATextAndFourPortsBecomeABoxWithItsPinsOnTheEdges() {
        List<LogisimInterfacePin> pins = List.of(
                input("A", 1, 100, 100), output("Y", 8, 300, 100), input("S", 2, 100, 200), input("C", 1, 100, 300));
        List<Shape> shapes = List.of(
                new Rectangle(50, 50, 40, 60, 0, BLACK_LINE),
                new Text(70, 80, "ALU", LogisimFont.DEFAULT, TextAnchor.MIDDLE, "#000000"),
                inputPort(100, 100, 50, 70), outputPort(300, 100, 90, 80),
                inputPort(100, 200, 70, 50), inputPort(100, 300, 70, 110),
                new Anchor(87, 77, 6, 6, "east"));

        ConvertedAppearance converted = convert(shapes, pins);

        assertThat(converted.size()).isEqualTo(new GridSize(4, 6));
        assertThat(converted.anchor()).isEqualTo(new GridPoint(4, 3));
        assertThat(converted.facing()).isEqualTo("east");
        assertThat(positions(converted)).containsExactly(
                Map.entry("A", new GridPoint(0, 2)), Map.entry("Y", new GridPoint(4, 3)),
                Map.entry("S", new GridPoint(2, 0)), Map.entry("C", new GridPoint(2, 6)));
        assertThat(converted.pins()).extracting(pin -> pin.spec().direction()).containsExactly(
                PinDirection.INPUT, PinDirection.OUTPUT, PinDirection.INPUT, PinDirection.INPUT);
        assertThat(converted.pins()).extracting(pin -> pin.spec().width()).containsExactly(1, 8, 2, 1);
        SymbolPath rectangle = converted.drawing().paths().getFirst();
        assertThat(points(rectangle)).containsExactly(point(0, 0), point(32, 0), point(32, 48), point(0, 48));
        assertThat(rectangle.closed()).isTrue();
        assertThat(rectangle.filled()).isFalse();
        assertThat(rectangle.line()).isEqualTo(NoteColor.NONE);
        SymbolText text = converted.drawing().texts().getFirst();
        assertThat(text.text()).contains("ALU");
        assertThat(text.alignment()).isEqualTo(TextAlignment.CENTER);
        assertThat(text.size()).isEqualTo(TextSize.MEDIUM);
        // the baseline is at 80: the middle of the letters is a little higher
        assertThat(text.anchor()).isEqualTo(point(16, 21));
        assertThat(converted.drawing().showPinNames()).isFalse();
        assertThat(converted.notes()).isEmpty();
    }

    @Test
    void aPortInsideTheBoxMovesToTheNearestEdgeWithANote() {
        List<LogisimInterfacePin> pins = List.of(input("W", 1, 10, 10), output("E", 1, 20, 10),
                input("N", 1, 30, 10), input("S", 1, 40, 10), input("ON", 1, 50, 10));
        List<Shape> shapes = List.of(new Rectangle(0, 0, 100, 100, 0, BLACK_LINE),
                inputPort(10, 10, 20, 50), outputPort(20, 10, 80, 50),
                inputPort(30, 10, 50, 20), inputPort(40, 10, 50, 80), inputPort(50, 10, 0, 30));

        ConvertedAppearance converted = convert(shapes, pins);

        assertThat(positions(converted)).containsExactly(
                Map.entry("W", new GridPoint(0, 5)), Map.entry("E", new GridPoint(10, 5)),
                Map.entry("N", new GridPoint(5, 0)), Map.entry("S", new GridPoint(5, 10)),
                Map.entry("ON", new GridPoint(0, 3)));
        assertThat(converted.notes()).extracting(ImportNote::messageKey).containsOnly(LogisimNotes.PORT_MOVED);
        assertThat(converted.notes()).extracting(note -> note.arguments().getFirst())
                .containsExactlyInAnyOrder("W", "E", "N", "S");
        assertThat(converted.notes()).allMatch(note -> note.kind() == ImportNote.Kind.APPROXIMATED);
    }

    @Test
    void aPortOnASlantedSideGoesToTheEdgeItFacesAndAMovedPortNeverTakesATakenPoint() {
        // the ALU's trapezoid: its Op port sits on the slanted bottom side, as near the right edge as the bottom one
        List<LogisimInterfacePin> pins = List.of(input("Op", 4, 10, 10), output("Z", 32, 20, 10),
                input("X", 32, 30, 10), input("near", 1, 40, 10));
        List<Shape> shapes = List.of(
                new Polyline(true, List.of(new LogisimPoint(140, 70), new LogisimPoint(200, 100),
                        new LogisimPoint(200, 140), new LogisimPoint(140, 170)), BLACK_LINE),
                inputPort(10, 10, 180, 150), outputPort(20, 10, 200, 120), inputPort(30, 10, 140, 120),
                inputPort(40, 10, 160, 122));

        ConvertedAppearance converted = convert(shapes, pins);

        assertThat(positions(converted)).containsEntry("Op", new GridPoint(4, 10))
                .containsEntry("Z", new GridPoint(6, 5)).containsEntry("X", new GridPoint(0, 5))
                .containsEntry("near", new GridPoint(0, 6));
    }

    @Test
    void linesPolylinesPolygonsAndCurvesKeepTheirNodes() {
        List<Shape> shapes = List.of(
                new Polyline(false, List.of(new LogisimPoint(0, 0), new LogisimPoint(50, 50),
                        new LogisimPoint(100, 0)), BLACK_LINE),
                new Polyline(true, List.of(new LogisimPoint(0, 100), new LogisimPoint(50, 60),
                        new LogisimPoint(100, 100)), new Paint("#000000", "#ffffff", 1)),
                new Curve(new LogisimPoint(10, 0), new LogisimPoint(30, 0), new LogisimPoint(20, 20), GRAY_LINE),
                new LogisimAppearance.Line(new LogisimPoint(0, 50), new LogisimPoint(100, 50), BLACK_LINE),
                new Polyline(true, List.of(new LogisimPoint(60, 60), new LogisimPoint(70, 70),
                        new LogisimPoint(60, 70)), new Paint("#000000", "#ff0000", 1)));

        ConvertedAppearance converted = convert(shapes, List.of());

        assertThat(converted.size()).isEqualTo(new GridSize(10, 10));
        List<SymbolPath> paths = converted.drawing().paths();
        assertThat(points(paths.get(0))).containsExactly(point(0, 0), point(40, 40), point(80, 0));
        assertThat(paths.get(0).closed()).isFalse();
        assertThat(points(paths.get(1))).containsExactly(point(0, 80), point(40, 48), point(80, 80));
        assertThat(paths.get(1).closed()).isTrue();
        assertThat(paths.get(1).filled()).isTrue();
        assertThat(paths.get(1).fill()).isEqualTo(NoteColor.NONE);
        SymbolPath curve = paths.get(2);
        assertThat(curve.nodes()).containsExactly(SymbolNode.quadratic(point(8, 0), point(16, 16)),
                SymbolNode.straight(point(24, 0)));
        assertThat(curve.line()).isEqualTo(NoteColor.GRAY);
        assertThat(points(paths.get(3))).containsExactly(point(0, 40), point(80, 40));
        assertThat(paths.get(4).filled()).isTrue();
        assertThat(paths.get(4).fill()).isNotIn(NoteColor.RED, NoteColor.NONE);
    }

    @Test
    void ovalsAndRoundedRectanglesAreDrawnWithCurves() {
        List<Shape> shapes = List.of(new Oval(0, 0, 40, 20, BLACK_LINE),
                new Rectangle(50, 0, 40, 20, 5, BLACK_LINE));

        ConvertedAppearance converted = convert(shapes, List.of());

        SymbolPath oval = converted.drawing().paths().get(0);
        assertThat(oval.closed()).isTrue();
        assertThat(oval.nodes()).hasSize(4).allMatch(SymbolNode::curved);
        assertThat(points(oval)).containsExactlyInAnyOrder(point(32, 8), point(16, 16), point(0, 8), point(16, 0));
        assertThat(oval.nodes().stream().flatMap(node -> node.curve().orElseThrow().controls().stream()))
                .allMatch(control -> control.x() >= 0 && control.x() <= 32 && control.y() >= 0 && control.y() <= 16);
        SymbolPath rounded = converted.drawing().paths().get(1);
        assertThat(rounded.nodes()).hasSize(8);
        assertThat(rounded.nodes().stream().filter(SymbolNode::curved)).hasSize(4);
        assertThat(points(rounded)).contains(point(44, 0), point(68, 0), point(72, 4), point(40, 12));
    }

    @Test
    void aCircuitWithoutDrawingGetsLogisimsDefaultBox() {
        List<LogisimInterfacePin> pins = List.of(input("A", 1, 100, 100), input("B", 1, 100, 200),
                output("Y", 1, 300, 100));

        ConvertedAppearance converted = LogisimAppearanceConverter.convert("adder", LogisimAppearance.NONE, pins,
                NO_LABEL);

        // Logisim: two pins on the west side, one on the east, a 30 x 30 box with its notch at the top
        assertThat(converted.size()).isEqualTo(new GridSize(3, 3));
        assertThat(positions(converted)).containsExactly(Map.entry("A", new GridPoint(0, 1)),
                Map.entry("B", new GridPoint(0, 2)), Map.entry("Y", new GridPoint(3, 1)));
        assertThat(converted.anchor()).isEqualTo(new GridPoint(3, 1));
        List<SymbolPath> paths = converted.drawing().paths();
        assertThat(paths).hasSize(2);
        assertThat(paths.get(0).nodes()).containsExactly(SymbolNode.quadratic(point(9, 1), point(12, 9)),
                SymbolNode.straight(point(15, 1)));
        assertThat(points(paths.get(1))).containsExactly(point(0, 0), point(24, 0), point(24, 24), point(0, 24));
        assertThat(converted.drawing().texts()).isEmpty();
        assertThat(converted.notes()).isEmpty();
    }

    @Test
    void theDefaultBoxPutsPinsOnTheSideTheyFaceAwayFrom() {
        List<LogisimInterfacePin> pins = List.of(
                new LogisimInterfacePin("down", 1, false, new LogisimPoint(100, 100), "south"),
                new LogisimInterfacePin("up", 1, true, new LogisimPoint(200, 100), "north"));

        ConvertedAppearance converted = LogisimAppearanceConverter.convert("vertical", LogisimAppearance.NONE, pins,
                NO_LABEL);

        // a pin facing south sits on the north side
        GridPoint down = positions(converted).get("down");
        GridPoint up = positions(converted).get("up");
        assertThat(down.y()).isZero();
        assertThat(up.y()).isEqualTo(converted.size().height());
    }

    @Test
    void theCircuitsLabelIsATextInTheMiddleAndATurnedLabelIsNoted() {
        List<LogisimInterfacePin> pins = List.of(input("A", 1, 100, 100), input("B", 1, 100, 200),
                output("Y", 1, 300, 100));
        // «clabelup» is where the top of the letters points: north is upright, east (Logisim's default) sideways
        LogisimAttributes label = LogisimAttributes.of(Map.of("clabel", "ALU", "clabelup", "north",
                "clabelfont", "SansSerif bold 16"));
        LogisimAttributes turned = LogisimAttributes.of(Map.of("clabel", "ALU", "clabelup", "east",
                "clabelfont", "SansSerif plain 12"));

        ConvertedAppearance upright = LogisimAppearanceConverter.convert("alu", LogisimAppearance.NONE, pins, label);
        ConvertedAppearance sideways = LogisimAppearanceConverter.convert("alu", LogisimAppearance.NONE, pins,
                turned);

        SymbolText text = upright.drawing().texts().getFirst();
        assertThat(text.text()).contains("ALU");
        assertThat(text.anchor()).isEqualTo(point(12, 12));
        assertThat(text.alignment()).isEqualTo(TextAlignment.CENTER);
        assertThat(upright.notes()).isEmpty();
        assertThat(sideways.notes()).extracting(ImportNote::messageKey).containsExactly(LogisimNotes.LABEL_TURNED);
    }

    @Test
    void portsAndPinsThatDoNotMatchAreNoted() {
        List<LogisimInterfacePin> pins = List.of(input("A", 1, 100, 100), output("Y", 1, 300, 100));
        List<Shape> shapes = List.of(new Rectangle(0, 0, 40, 40, 0, BLACK_LINE), inputPort(100, 100, 0, 20),
                inputPort(500, 500, 0, 30));

        ConvertedAppearance converted = convert(shapes, pins);

        assertThat(positions(converted)).containsEntry("A", new GridPoint(0, 2)).containsKey("Y");
        GridPoint lonely = positions(converted).get("Y");
        assertThat(lonely.x()).isEqualTo(4);
        assertThat(converted.notes()).extracting(ImportNote::messageKey)
                .containsExactlyInAnyOrder(LogisimNotes.PORT_WITHOUT_PIN, LogisimNotes.PIN_WITHOUT_PORT);
    }

    @Test
    void aDrawingWithMoreTextsThanVsimTakesKeepsTheFirstOnesAndSaysSo() {
        List<Shape> shapes = new ArrayList<>();
        shapes.add(new Rectangle(0, 0, 400, 400, 0, BLACK_LINE));
        IntStream.range(0, 40).forEach(index -> shapes.add(new Text(10 + index * 5, 200, "t" + index,
                LogisimFont.DEFAULT, TextAnchor.START, "#000000")));

        ConvertedAppearance converted = convert(shapes, List.of());

        assertThat(converted.drawing().texts()).hasSize(32);
        assertThat(converted.notes()).extracting(ImportNote::messageKey)
                .containsExactly(LogisimNotes.DRAWING_TOO_BIG);
    }

    @Test
    void anInstanceTurnsByItsFacingAgainstTheDrawingsFacing() {
        ConvertedAppearance converted = convert(List.of(new Rectangle(0, 0, 40, 40, 0, BLACK_LINE),
                new Anchor(37, 17, 6, 6, "east")), List.of());

        assertThat(converted.rotationFor("east")).isEqualTo(Rotation.R0);
        assertThat(converted.rotationFor("south")).isEqualTo(Rotation.R90);
        assertThat(converted.rotationFor("north")).isEqualTo(Rotation.R270);
    }

    @Test
    void readsTheCircuitsOwnAttributesWithLogisimsDefaults() throws Exception {
        LogisimProject project = LogisimTestFiles.read("subcircuits.circ");

        LogisimCircuit leaf = project.circuit("leaf").orElseThrow();
        LogisimCircuit middle = project.circuit("middle").orElseThrow();

        assertThat(leaf.attributes().text("clabel")).isEqualTo("L");
        assertThat(leaf.attributes().text("clabelup")).isEqualTo("east");
        assertThat(leaf.attributes().text("clabelfont")).isEqualTo("SansSerif plain 12");
        assertThat(middle.attributes().text("clabel")).isEmpty();
        assertThat(middle.attributes().text("clabelup")).isEqualTo("north");
    }

    @Test
    void convertsTheCircuitsOfAFile() throws Exception {
        LogisimProject project = LogisimTestFiles.read("subcircuits.circ");

        ConvertedAppearance middle = LogisimAppearanceConverter.convert(project.circuit("middle").orElseThrow());
        ConvertedAppearance leaf = LogisimAppearanceConverter.convert(project.circuit("leaf").orElseThrow());

        assertThat(positions(middle)).containsExactly(Map.entry("a", new GridPoint(0, 2)),
                Map.entry("y", new GridPoint(4, 2)));
        assertThat(middle.pins().get(1).spec().width()).isEqualTo(4);
        assertThat(middle.drawing().texts()).extracting(text -> text.text().orElseThrow()).containsExactly("M");
        assertThat(leaf.pins()).extracting(pin -> pin.spec().name()).containsExactly("in1", "in2", "out1");
        assertThat(leaf.drawing().texts()).extracting(text -> text.text().orElseThrow()).containsExactly("L");
        assertThat(leaf.notes()).extracting(ImportNote::messageKey).containsExactly(LogisimNotes.LABEL_TURNED);
    }

    private static ConvertedAppearance convert(List<Shape> shapes, List<LogisimInterfacePin> pins) {
        return LogisimAppearanceConverter.convert("circuit", new LogisimAppearance(shapes), pins, NO_LABEL);
    }

    private static LogisimInterfacePin input(String label, int width, int x, int y) {
        return new LogisimInterfacePin(label, width, false, new LogisimPoint(x, y), "east");
    }

    private static LogisimInterfacePin output(String label, int width, int x, int y) {
        return new LogisimInterfacePin(label, width, true, new LogisimPoint(x, y), "west");
    }

    /** An input's port, an 8 px box centred on ({@code centreX}, {@code centreY}). */
    private static Port inputPort(int pinX, int pinY, int centreX, int centreY) {
        return new Port(new LogisimPoint(pinX, pinY), centreX - 4, centreY - 4, 8, 8);
    }

    /** An output's port, a 10 px box. */
    private static Port outputPort(int pinX, int pinY, int centreX, int centreY) {
        return new Port(new LogisimPoint(pinX, pinY), centreX - 5, centreY - 5, 10, 10);
    }

    private static Map<String, GridPoint> positions(ConvertedAppearance converted) {
        Map<String, GridPoint> positions = new LinkedHashMap<>();
        converted.pins().forEach(pin -> positions.put(pin.spec().name(), pin.spec().position()));
        return positions;
    }

    private static List<SymbolPoint> points(SymbolPath path) {
        return path.nodes().stream().map(SymbolNode::point).toList();
    }

    private static SymbolPoint point(int x, int y) {
        return new SymbolPoint(x, y);
    }
}
