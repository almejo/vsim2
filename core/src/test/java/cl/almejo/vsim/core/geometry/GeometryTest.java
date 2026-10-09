package cl.almejo.vsim.core.geometry;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeometryTest {

    @Test
    void fromPixelsUsesEightPixelGridWithFloor() {
        assertThat(GridPoint.fromPixels(152, 224)).isEqualTo(new GridPoint(19, 28));
        assertThat(GridPoint.fromPixels(12, 7)).isEqualTo(new GridPoint(1, 0));
        assertThat(GridPoint.fromPixels(-1, -8)).isEqualTo(new GridPoint(-1, -1));
    }

    @Test
    void pointsOrderByXThenY() {
        assertThat(new GridPoint(1, 5)).isLessThan(new GridPoint(2, 0));
        assertThat(new GridPoint(1, 0)).isLessThan(new GridPoint(1, 1));
    }

    @Test
    void rotationMatchesJava2dQuarterTurns() {
        GridPoint local = new GridPoint(4, 2);
        assertThat(Rotation.R0.apply(local)).isEqualTo(new GridPoint(4, 2));
        assertThat(Rotation.R90.apply(local)).isEqualTo(new GridPoint(-2, 4));
        assertThat(Rotation.R180.apply(local)).isEqualTo(new GridPoint(-4, -2));
        assertThat(Rotation.R270.apply(local)).isEqualTo(new GridPoint(2, -4));
    }

    @Test
    void rotationCycles() {
        assertThat(Rotation.R270.clockwise()).isEqualTo(Rotation.R0);
        assertThat(Rotation.R0.counterClockwise()).isEqualTo(Rotation.R270);
        assertThat(Rotation.fromQuarterTurns(-1)).isEqualTo(Rotation.R270);
        assertThat(Rotation.fromQuarterTurns(5)).isEqualTo(Rotation.R90);
    }

    @Test
    void gridSizeMustBePositive() {
        assertThatThrownBy(() -> new GridSize(0, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void boundsContainsEdgesAndUnion() {
        Bounds bounds = Bounds.of(new GridPoint(2, 3), new GridPoint(0, 0));
        assertThat(bounds).isEqualTo(new Bounds(0, 0, 2, 3));
        assertThat(bounds.contains(new GridPoint(2, 3))).isTrue();
        assertThat(bounds.contains(new GridPoint(3, 3))).isFalse();
        assertThat(bounds.union(new Bounds(5, 5, 1, 1))).isEqualTo(new Bounds(0, 0, 6, 6));
        assertThat(bounds.intersects(new Bounds(2, 3, 4, 4))).isTrue();
        assertThat(bounds.intersects(new Bounds(3, 0, 1, 1))).isFalse();
    }

    @Test
    void rotatedBoundsCoverTheRotatedRectangle() {
        Bounds bounds = Bounds.rotated(new GridPoint(10, 10), new GridSize(4, 2), Rotation.R90);
        assertThat(bounds).isEqualTo(new Bounds(8, 10, 2, 4));
    }

    @Test
    void rotatedBoundsForHalfAndThreeQuarterTurns() {
        GridPoint origin = new GridPoint(10, 10);
        GridSize size = new GridSize(4, 2);
        assertThat(Bounds.rotated(origin, size, Rotation.R0)).isEqualTo(new Bounds(10, 10, 4, 2));
        assertThat(Bounds.rotated(origin, size, Rotation.R180)).isEqualTo(new Bounds(6, 8, 4, 2));
        assertThat(Bounds.rotated(origin, size, Rotation.R270)).isEqualTo(new Bounds(10, 6, 2, 4));
    }

    @Test
    void boundsContainBounds() {
        Bounds outer = new Bounds(0, 0, 10, 10);
        assertThat(outer.contains(new Bounds(2, 2, 3, 3))).isTrue();
        assertThat(outer.contains(outer)).isTrue();
        assertThat(outer.contains(new Bounds(8, 8, 3, 1))).isFalse();
        assertThat(outer.contains(new Bounds(-1, 0, 2, 2))).isFalse();
        assertThat(new Bounds(2, 2, 3, 3).contains(outer)).isFalse();
    }

    @Test
    void intersectsIncludesTouchingEdgesAndCorners() {
        Bounds bounds = new Bounds(0, 0, 2, 2);
        assertThat(bounds.intersects(new Bounds(2, 0, 3, 2))).isTrue();
        assertThat(bounds.intersects(new Bounds(2, 2, 1, 1))).isTrue();
        assertThat(bounds.intersects(new Bounds(-3, 0, 3, 1))).isTrue();
        assertThat(bounds.intersects(new Bounds(3, 0, 1, 1))).isFalse();
        assertThat(bounds.intersects(new Bounds(0, -2, 2, 1))).isFalse();
    }
}
