package cl.almejo.vsim.core.component.appearance;

import cl.almejo.vsim.core.geometry.GridSize;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SymbolPathCodecTest {
    private final GridSize box = new GridSize(4, 4);

    @Test
    void aQuadraticClosingSegmentRoundTripsWithoutAddingADuplicateNode() {
        SymbolPath path = new SymbolPath(List.of(node(0, 0), node(32, 0),
                SymbolNode.quadratic(new SymbolPoint(16, 32), new SymbolPoint(0, 16))), true, true);
        String data = SymbolPathCodec.encode(path);
        assertThat(data).endsWith("Q 0 16 0 0 Z");
        assertThat(SymbolPathCodec.decode(data, true, box)).isEqualTo(path);
    }

    @Test
    void anOpenPathKeepsItsStraightAndQuadraticSegments() {
        SymbolPath path = new SymbolPath(List.of(SymbolNode.quadratic(new SymbolPoint(0, 0), new SymbolPoint(8, 4)),
                node(16, 8), node(32, 0)), false, false);
        assertThat(SymbolPathCodec.decode(SymbolPathCodec.encode(path), false, box)).isEqualTo(path);
    }

    @Test
    void cubicSegmentsRoundTripIncludingACurvedClosingSegment() {
        SymbolPath path = new SymbolPath(List.of(
                SymbolNode.cubic(new SymbolPoint(0, 16), new SymbolPoint(0, 8), new SymbolPoint(8, 0)),
                SymbolNode.cubic(new SymbolPoint(16, 0), new SymbolPoint(24, 0), new SymbolPoint(32, 8)),
                SymbolNode.cubic(new SymbolPoint(32, 16), new SymbolPoint(32, 32), new SymbolPoint(0, 32))),
                true, true);

        String data = SymbolPathCodec.encode(path);

        assertThat(data).isEqualTo("M 0 16 C 0 8 8 0 16 0 C 24 0 32 8 32 16 C 32 32 0 32 0 16 Z");
        assertThat(SymbolPathCodec.decode(data, true, box)).isEqualTo(path);
    }

    @Test
    void straightQuadraticAndCubicSegmentsMixInOnePath() {
        String data = "M 0 0 L 8 0 Q 16 0 16 8 C 16 16 24 24 32 24";

        assertThat(SymbolPathCodec.encode(SymbolPathCodec.decode(data, false, box))).isEqualTo(data);
    }

    @Test
    void aClosedFigureCanRetainADistinctLastNodeAtItsFirstCoordinate() {
        SymbolPath path = new SymbolPath(List.of(node(0, 0), node(32, 0), node(0, 0)), true, false);
        assertThat(SymbolPathCodec.decode(SymbolPathCodec.encode(path), false, box)).isEqualTo(path);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "M 0 0", "M 0.5 0 L 8 8", "M 0 0 M 8 8", "M 0 0 Q 8 8 16",
            "M 0 0 L 33 8", "M 0 0 L -1 8", "M 0 0 L 8 8 Z", "M 0 0 L 8 8 Z L 16 16",
            "M 0 0 C 8 8 16 16 24", "M 0 0 C 8 8 40 16 24 24", "M 0 0 L 99999999999999999999 8", "M 0 0 L 8 8 garbage"})
    void invalidOrUnsupportedSvgIsRefused(String data) {
        assertThatThrownBy(() -> SymbolPathCodec.decode(data, false, box))
                .isInstanceOf(SymbolPathException.class);
    }

    @Test
    void tooManyNodesAreRefusedBeforeAPathIsBuilt() {
        String data = "M 0 0" + " L 8 8".repeat(256);
        assertThatThrownBy(() -> SymbolPathCodec.decode(data, false, box))
                .isInstanceOf(SymbolPathException.class);
    }

    private static SymbolNode node(int x, int y) {
        return SymbolNode.straight(new SymbolPoint(x, y));
    }
}
