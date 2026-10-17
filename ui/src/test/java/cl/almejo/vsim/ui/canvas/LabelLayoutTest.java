package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.LabelOffset;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.ui.paint.ComponentDrawing;
import org.junit.jupiter.api.Test;

import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class LabelLayoutTest {

    private final ComponentType type = StandardComponentTypes.registry().require(StandardComponentTypes.NOT);

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
}
