package cl.almejo.vsim.ui.appearance;

import cl.almejo.vsim.core.component.appearance.SymbolNode;
import cl.almejo.vsim.core.component.appearance.SymbolPath;
import cl.almejo.vsim.core.component.appearance.SymbolPoint;
import cl.almejo.vsim.core.component.appearance.SymbolTemplates;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ShapeSegmentsTest {
    @Test
    void curvePickingFollowsTheCurveRatherThanItsStraightChord() {
        SymbolPath curve = new SymbolPath(List.of(
                SymbolNode.quadratic(new SymbolPoint(0, 0), new SymbolPoint(24, 32)),
                SymbolNode.straight(new SymbolPoint(48, 0))), false, false);
        SymbolPath line = SymbolTemplates.line(new SymbolPoint(0, 0), new SymbolPoint(48, 0), false);

        assertThat(ShapeSegments.find(List.of(curve, line), 24, 16, 1))
                .contains(new ShapeSegments.Hit(0, 0, new SymbolPoint(24, 16)));
    }

    @Test
    void aCubicSegmentIsPickedOnItsCurve() {
        SymbolPath curve = new SymbolPath(List.of(
                SymbolNode.cubic(new SymbolPoint(0, 0), new SymbolPoint(16, 21), new SymbolPoint(32, 21)),
                SymbolNode.straight(new SymbolPoint(48, 0))), false, false);
        SymbolPath line = SymbolTemplates.line(new SymbolPoint(0, 0), new SymbolPoint(48, 0), false);

        assertThat(ShapeSegments.find(List.of(curve, line), 24, 16, 1))
                .contains(new ShapeSegments.Hit(0, 0, new SymbolPoint(24, 16)));
    }

    @Test
    void clickingAFilledInteriorOrAnExistingEndpointDoesNotChooseAnArbitrarySegment() {
        SymbolPath rectangle = SymbolTemplates.rectangle(new SymbolPoint(0, 0), new SymbolPoint(48, 32), false);

        assertThat(ShapeSegments.find(List.of(rectangle), 24, 16, 2)).isEmpty();
        assertThat(ShapeSegments.find(List.of(rectangle), 0, 0, 2)).isEmpty();
        assertThat(ShapeSegments.find(List.of(rectangle), 13, 1, 2))
                .contains(new ShapeSegments.Hit(0, 0, new SymbolPoint(13, 0)));
    }
}
