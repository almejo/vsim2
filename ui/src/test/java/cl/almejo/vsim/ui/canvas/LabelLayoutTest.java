package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class LabelLayoutTest {

    private final ComponentType type = StandardComponentTypes.registry().require(StandardComponentTypes.NOT);

    @AfterEach
    void restoreTheFont() {
        TextFonts.set(TextFont.DEFAULT);
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
}
