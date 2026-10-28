package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.LabelOffset;
import cl.almejo.vsim.core.document.NoteColor;
import cl.almejo.vsim.core.document.NoteStyle;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.ui.paint.ComponentDrawing;
import cl.almejo.vsim.ui.paint.TextFont;
import cl.almejo.vsim.ui.paint.TextFonts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.Font;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class LabelLayoutTest {

    private final ComponentType type = StandardComponentTypes.registry().require(StandardComponentTypes.BUFFER);

    @AfterEach
    void restoreTheFont() {
        TextFonts.set(TextFont.DEFAULT);
    }

    @Test
    void aMemoryHasNoLabelAboveItBecauseItsNameIsInItsTitleBar() {
        for (String typeId : List.of(StandardComponentTypes.RAM, StandardComponentTypes.ROM)) {
            ComponentType memory = StandardComponentTypes.registry().require(typeId);
            Component named = memory.newComponent(new ElementId("m1"), new GridPoint(10, 10), Rotation.R0, Map.of(),
                    "Sprites");
            assertThat(LabelLayout.componentLabel(memory, named)).as(typeId).isEmpty();
        }
    }

    private Component not(Rotation rotation, String label) {
        return type.newComponent(new ElementId("c1"), new GridPoint(10, 10), rotation, Map.of(), label);
    }

    @Test
    void blankLabelsAreNotPlaced() {
        assertThat(LabelLayout.componentLabel(type, not(Rotation.R0, ""))).isEmpty();
        assertThat(LabelLayout.componentLabel(type, not(Rotation.R0, "   "))).isEmpty();
    }

    @Test
    void labelSitsCenteredAboveForR0AndR180() {
        for (Rotation rotation : List.of(Rotation.R0, Rotation.R180)) {
            Component gate = not(rotation, "U1");
            Rectangle2D body = ComponentDrawing.documentBounds(type, gate);
            Rectangle2D label = LabelLayout.componentLabel(type, gate).orElseThrow().bounds();
            assertThat(label.getCenterX()).isCloseTo(body.getCenterX(), within(0.01));
            assertThat(label.getMaxY()).isCloseTo(body.getMinY() - LabelLayout.GAP, within(0.01));
        }
    }

    @Test
    void labelSitsLeftForR90AndR270() {
        for (Rotation rotation : List.of(Rotation.R90, Rotation.R270)) {
            Component gate = not(rotation, "U1");
            Rectangle2D body = ComponentDrawing.documentBounds(type, gate);
            Rectangle2D label = LabelLayout.componentLabel(type, gate).orElseThrow().bounds();
            assertThat(label.getMaxX()).isCloseTo(body.getMinX() - LabelLayout.GAP, within(0.01));
            assertThat(label.getCenterY()).isCloseTo(body.getCenterY(), within(0.01));
        }
    }

    @Test
    void aNoteWithABackgroundOrABorderIsABoxPaddedAroundItsTextAndItsTextStaysPut() {
        TextLabel plain = new TextLabel(new ElementId("t1"), new GridPoint(4, 6), "Ojo\nactivo en bajo");
        double padding = LabelLayout.NOTE_PADDING;
        for (NoteStyle style : List.of(new NoteStyle(NoteColor.YELLOW, NoteColor.NONE, NoteColor.NONE),
                new NoteStyle(NoteColor.NONE, NoteColor.NONE, NoteColor.RED))) {
            TextLabel styled = plain.withStyle(style);
            Rectangle2D text = LabelLayout.freeText(plain).bounds();

            assertThat(LabelLayout.freeText(styled).lines()).isEqualTo(LabelLayout.freeText(plain).lines());
            assertThat(LabelLayout.noteBox(styled)).isEqualTo(new Rectangle2D.Double(text.getX() - padding,
                    text.getY() - padding, text.getWidth() + 2 * padding, text.getHeight() + 2 * padding));
            assertThat(LabelLayout.gridBounds(styled).getWidth() * GridPoint.PIXELS_PER_UNIT)
                    .isCloseTo(text.getWidth() + 2 * padding, within(0.01));
        }
    }

    @Test
    void aPlainNoteOrOneWithOnlyATextColourKeepsItsBoxAsAlways() {
        TextLabel plain = new TextLabel(new ElementId("t1"), new GridPoint(4, 6), "nota");
        TextLabel blue = plain.withStyle(new NoteStyle(NoteColor.NONE, NoteColor.BLUE, NoteColor.NONE));

        assertThat(LabelLayout.noteBox(plain)).isEqualTo(LabelLayout.freeText(plain).bounds());
        assertThat(LabelLayout.gridBounds(blue)).isEqualTo(LabelLayout.gridBounds(plain));
    }

    @Test
    void freeTextStartsAtItsPositionAndGrowsWithItsText() {
        TextLabel shortText = new TextLabel(new ElementId("l1"), new GridPoint(2, 3), "a");
        Rectangle2D grid = LabelLayout.gridBounds(shortText);
        assertThat(grid.getX()).isEqualTo(2.0);
        assertThat(grid.getY()).isEqualTo(3.0);
        assertThat(grid.getHeight()).isGreaterThan(0.5);
        assertThat(LabelLayout.gridBounds(shortText.withText("una anotación larga")).getWidth())
                .isGreaterThan(grid.getWidth());
    }

    @Test
    void emptyFreeTextKeepsAMinimumWidth() {
        TextLabel empty = new TextLabel(new ElementId("l1"), new GridPoint(2, 3), "");
        assertThat(LabelLayout.gridBounds(empty).getWidth()).isGreaterThanOrEqualTo(1.0);
    }

    @Test
    void labelsAndTextsAreMeasuredWithTheCurrentFontAndTheHitAreaFollows() {
        Component gate = not(Rotation.R0, "U1");
        TextLabel note = new TextLabel(new ElementId("l1"), new GridPoint(2, 3), "una nota");
        Rectangle2D smallLabel = LabelLayout.componentLabel(type, gate).orElseThrow().bounds();
        Rectangle2D smallText = LabelLayout.gridBounds(note);

        TextFonts.set(new TextFont(Font.SANS_SERIF, 14));

        assertThat(LabelLayout.font().getSize()).isEqualTo(14);
        Rectangle2D largeLabel = LabelLayout.componentLabel(type, gate).orElseThrow().bounds();
        Rectangle2D largeText = LabelLayout.gridBounds(note);
        assertThat(largeLabel.getWidth()).isGreaterThan(smallLabel.getWidth());
        assertThat(largeLabel.getHeight()).isGreaterThan(smallLabel.getHeight());
        assertThat(largeText.getWidth()).isGreaterThan(smallText.getWidth());
        assertThat(largeText.getHeight()).isGreaterThan(smallText.getHeight());

        CircuitDocument document = new CircuitDocument();
        document.add(note);
        Point2D onlyInTheLargeText = new Point2D.Double(largeText.getMaxX() - 0.05, largeText.getMaxY() - 0.05);
        assertThat(smallText.contains(onlyInTheLargeText)).isFalse();
        assertThat(new HitTester(StandardComponentTypes.registry()).elementAt(document, onlyInTheLargeText))
                .contains(note);
    }

    @Test
    void aFamilyThatIsNotInstalledMeasuresLikeTheDefaultFamily() {
        TextLabel note = new TextLabel(new ElementId("l1"), new GridPoint(2, 3), "una nota");
        Rectangle2D sansSerif = LabelLayout.gridBounds(note);
        TextFonts.set(new TextFont("Fuente De Otro Computador", 10));
        assertThat(LabelLayout.font().getFamily()).isEqualTo(new Font(Font.SANS_SERIF, Font.PLAIN, 10).getFamily());
        assertThat(LabelLayout.gridBounds(note)).isEqualTo(sansSerif);
    }

    @Test
    void anOffsetLabelIsCenteredOnThePositionPlusTheRotatedOffset() {
        LabelOffset offset = new LabelOffset(6, 1);
        for (Rotation rotation : Rotation.values()) {
            Component gate = not(rotation, "Compuerta U1").withLabelOffset(Optional.of(offset));
            GridPoint centre = gate.position().plus(rotation.apply(new GridPoint(offset.x(), offset.y())));
            Rectangle2D label = LabelLayout.componentLabel(type, gate).orElseThrow().bounds();
            assertThat(label.getCenterX()).isCloseTo(centre.x() * GridPoint.PIXELS_PER_UNIT, within(0.01));
            assertThat(label.getCenterY()).isCloseTo(centre.y() * GridPoint.PIXELS_PER_UNIT, within(0.01));
            assertThat(label.getWidth()).isGreaterThan(label.getHeight()); // the text stays horizontal
        }
    }

    @Test
    void offsetForIsTheSnappedInverseOfThePlace() {
        for (Rotation rotation : Rotation.values()) {
            Component gate = not(rotation, "U1");
            LabelOffset offset = new LabelOffset(-3, 7);
            Rectangle2D label = LabelLayout.componentLabel(type, gate.withLabelOffset(Optional.of(offset)))
                    .orElseThrow().bounds();
            assertThat(LabelLayout.offsetFor(gate, label.getCenterX() + 2.5, label.getCenterY() - 3.1))
                    .isEqualTo(offset);
        }
    }

    @Test
    void rotatingKeepsAMovedLabelOnTheSameSideOfTheBody() {
        // a label moved below the body (local +y) is below for R0, left for R90 (y grows down, turns clockwise)
        Component gate = not(Rotation.R0, "U1").withLabelOffset(Optional.of(new LabelOffset(2, 8)));
        Rectangle2D body = ComponentDrawing.documentBounds(type, gate);
        assertThat(LabelLayout.componentLabel(type, gate).orElseThrow().bounds().getMinY())
                .isGreaterThan(body.getMaxY());
        Component turned = gate.withRotation(Rotation.R90);
        Rectangle2D turnedBody = ComponentDrawing.documentBounds(type, turned);
        assertThat(LabelLayout.componentLabel(type, turned).orElseThrow().bounds().getMaxX())
                .isLessThan(turnedBody.getMinX());
    }

    @Test
    void aNoteOfThreeLinesIsAsWideAsItsLongestLineAndOneLineStepTallerPerBreak() {
        TextLabel first = new TextLabel(new ElementId("l1"), new GridPoint(2, 3), "Prueba:");
        TextLabel longest = first.withText("cambia A y B con un clic");
        TextLabel three = first.withText("Prueba:\ncambia A y B con un clic\nmira la salida");
        LabelLayout.PlacedText single = LabelLayout.freeText(first);
        LabelLayout.PlacedText placed = LabelLayout.freeText(three);

        assertThat(placed.bounds().getX()).isEqualTo(single.bounds().getX());
        assertThat(placed.bounds().getY()).isEqualTo(single.bounds().getY());
        assertThat(placed.width()).isCloseTo(LabelLayout.freeText(longest).width(), within(0.01));
        assertThat(placed.lineHeight()).isGreaterThanOrEqualTo(single.height());
        assertThat(placed.height()).isCloseTo(single.height() + 2 * placed.lineHeight(), within(0.01));

        List<LabelLayout.Line> lines = placed.lines();
        assertThat(lines).extracting(LabelLayout.Line::text)
                .containsExactly("Prueba:", "cambia A y B con un clic", "mira la salida");
        for (int index = 0; index < lines.size(); index++) {
            assertThat(lines.get(index).baselineX()).isEqualTo(single.baselineX());
            assertThat((double) lines.get(index).baselineY())
                    .isCloseTo(single.baselineY() + index * placed.lineHeight(), within(0.01));
        }
    }

    @Test
    void aOneLineNoteIsPlacedAsBefore() {
        TextLabel note = new TextLabel(new ElementId("l1"), new GridPoint(2, 3), "una nota");
        LabelLayout.PlacedText placed = LabelLayout.freeText(note);
        Font font = LabelLayout.font();
        java.awt.font.FontRenderContext context = new java.awt.font.FontRenderContext(null, true, true);
        java.awt.font.LineMetrics metrics = font.getLineMetrics("una nota", context);
        assertThat(placed.height()).isEqualTo(metrics.getAscent() + metrics.getDescent());
        assertThat(placed.width()).isEqualTo(font.getStringBounds("una nota", context).getWidth());
        assertThat(placed.lines()).containsExactly(new LabelLayout.Line("una nota", placed.baselineX(),
                placed.baselineY()));
    }

    @Test
    void aWindowsLineBreakAndABlankLineCountAsLines() {
        TextLabel note = new TextLabel(new ElementId("l1"), new GridPoint(0, 0), "a\r\n\nb");
        assertThat(LabelLayout.freeText(note).lines()).extracting(LabelLayout.Line::text)
                .containsExactly("a", "", "b");
    }

    @Test
    void aPartLabelStaysOneLine() {
        Component gate = not(Rotation.R0, "U1");
        LabelLayout.PlacedText placed = LabelLayout.componentLabel(type, gate.withLabel("U1\nU2")).orElseThrow();
        assertThat(placed.lines()).hasSize(1);
        assertThat(placed.height()).isEqualTo(LabelLayout.componentLabel(type, gate).orElseThrow().height());
    }

    @Test
    void theDrawnBoundsOfANoteCoverEveryLine() {
        TextLabel note = new TextLabel(new ElementId("l1"), new GridPoint(2, 3), "uno\ndos\ntres");
        Rectangle2D grid = LabelLayout.gridBounds(note);
        cl.almejo.vsim.core.geometry.Bounds drawn = DrawnBounds.of(note, StandardComponentTypes.registry());
        assertThat(drawn.y()).isEqualTo(3);
        assertThat((double) drawn.y() + drawn.height()).isGreaterThanOrEqualTo(grid.getMaxY());
        assertThat(grid.getHeight()).isGreaterThan(2.5 * LabelLayout.gridBounds(note.withText("uno")).getHeight());
    }
}
