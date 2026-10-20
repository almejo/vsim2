package cl.almejo.vsim.ui.tunnel;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.tunnel.TunnelKind;
import cl.almejo.vsim.core.tunnel.Tunnels;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TunnelShapeTest {

    private static final double UNIT = GridPoint.PIXELS_PER_UNIT;
    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();

    private Component tunnel(TunnelKind kind, Rotation rotation, String name) {
        ComponentType type = registry.require(kind.typeId());
        return type.newComponent(new ElementId("c1"), new GridPoint(10, 20), rotation, Map.of(Tunnels.NAME, name),
                "");
    }

    private static Point2D inDocument(Component component, Point2D local) {
        return TunnelShape.placement(component).transform(local, null);
    }

    @ParameterizedTest
    @EnumSource(Rotation.class)
    void theStubGoesFromThePinToTheCentreOfTheHole(Rotation rotation) {
        Component component = tunnel(TunnelKind.ENTRANCE, rotation, "CLK");
        TunnelShape shape = TunnelShape.of(component);
        GridPoint pin = registry.typeOf(component).pinLocation(component, 0);

        Point2D stubStart = inDocument(component, shape.stub().getP1());
        Point2D stubEnd = inDocument(component, shape.stub().getP2());

        assertThat(stubStart.getX()).isCloseTo(pin.x() * UNIT, within(0.001));
        assertThat(stubStart.getY()).isCloseTo(pin.y() * UNIT, within(0.001));
        assertThat(stubEnd.distance(inDocument(component, shape.holeCentre()))).isLessThan(0.001);
    }

    @ParameterizedTest
    @EnumSource(Rotation.class)
    void thePinIsOutsideTheTagAndTheHoleInsideIt(Rotation rotation) {
        Component component = tunnel(TunnelKind.EXIT, rotation, "DATA");
        TunnelShape shape = TunnelShape.of(component);
        AffineTransform placement = TunnelShape.placement(component);

        assertThat(placement.createTransformedShape(shape.tag()).contains(inDocument(component, shape.pin())))
                .isFalse();
        assertThat(placement.createTransformedShape(shape.tag()).contains(inDocument(component, shape.holeCentre())))
                .isTrue();
    }

    @ParameterizedTest
    @EnumSource(Rotation.class)
    void anEntrancesArrowPointsAtTheHoleWhateverTheTurn(Rotation rotation) {
        Component component = tunnel(TunnelKind.ENTRANCE, rotation, "CLK");
        TunnelShape shape = TunnelShape.of(component);
        Point2D hole = inDocument(component, shape.holeCentre());

        assertThat(inDocument(component, shape.arrowTip()).distance(hole))
                .isLessThan(inDocument(component, shape.arrowBack()).distance(hole));
    }

    @ParameterizedTest
    @EnumSource(Rotation.class)
    void anExitsArrowPointsAwayFromTheHoleWhateverTheTurn(Rotation rotation) {
        Component component = tunnel(TunnelKind.EXIT, rotation, "CLK");
        TunnelShape shape = TunnelShape.of(component);
        Point2D hole = inDocument(component, shape.holeCentre());

        assertThat(inDocument(component, shape.arrowTip()).distance(hole))
                .isGreaterThan(inDocument(component, shape.arrowBack()).distance(hole));
    }

    @ParameterizedTest
    @EnumSource(Rotation.class)
    void theArrowSitsRightNextToTheHoleAndTheTextAfterIt(Rotation rotation) {
        TunnelShape shape = TunnelShape.of(tunnel(TunnelKind.ENTRANCE, rotation, "CLK"));

        double holeEdge = shape.holeCentre().getX() + TunnelShape.HOLE_RADIUS;
        double arrowStart = Math.min(shape.arrowTip().getX(), shape.arrowBack().getX());
        double arrowEnd = Math.max(shape.arrowTip().getX(), shape.arrowBack().getX());
        assertThat(arrowStart - holeEdge).isBetween(0.0, UNIT / 2);
        assertThat(shape.textBox().getMinX()).isGreaterThanOrEqualTo(arrowEnd);
        assertThat(shape.textBox().getMaxX()).isLessThanOrEqualTo(shape.tag().getMaxX());
    }

    @Test
    void theTextIsUprightWhenHorizontalAndSidewaysWhenVertical() {
        assertThat(TunnelShape.textSideways(Rotation.R0)).isFalse();
        assertThat(TunnelShape.textSideways(Rotation.R180)).isFalse();
        assertThat(TunnelShape.textSideways(Rotation.R90)).isTrue();
        assertThat(TunnelShape.textSideways(Rotation.R270)).isTrue();
    }

    @Test
    void turnedHalfwayTheTextBoxLiesOnTheFarSideFromThePin() {
        Component component = tunnel(TunnelKind.ENTRANCE, Rotation.R180, "CLK");
        Rectangle2D textBox = TunnelShape.of(component).documentTextBox(component);

        assertThat(textBox.getMaxX()).isLessThan(component.position().x() * UNIT);
        assertThat(textBox.getHeight()).isLessThan(textBox.getWidth());
    }

    @Test
    void theTagGrowsWithTheNameAndFillsTheComponentsBox() {
        Component shortName = tunnel(TunnelKind.ENTRANCE, Rotation.R0, "A");
        Component longName = tunnel(TunnelKind.ENTRANCE, Rotation.R0, "ADDRESS");
        TunnelShape shortShape = TunnelShape.of(shortName);
        TunnelShape longShape = TunnelShape.of(longName);

        assertThat(longShape.tag().getWidth()).isGreaterThan(shortShape.tag().getWidth());
        assertThat(longShape.textBox().getWidth()).isGreaterThan(shortShape.textBox().getWidth());
        double boxEnd = registry.typeOf(longName).specializedFor(longName.parameters()).size().width() * UNIT;
        assertThat(longShape.tag().getMaxX()).isCloseTo(boxEnd, within(0.001));
    }
}
