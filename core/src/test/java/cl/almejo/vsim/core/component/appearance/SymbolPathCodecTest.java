package cl.almejo.vsim.core.component.appearance;

import cl.almejo.vsim.core.geometry.GridSize;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SymbolPathCodecTest {
    private final GridSize box = new GridSize(4, 4);

    @Test
    void aQuadraticClosingSegmentRoundTripsWithoutAddingADuplicateNode() {
        SymbolPath path = new SymbolPath(List.of(node(0, 0), node(32, 0),
                new SymbolNode(new SymbolPoint(16, 32), Optional.of(new SymbolPoint(0, 16)))), true, true);
        String data = SymbolPathCodec.encode(path);
        assertThat(data).endsWith("Q 0 16 0 0 Z");
        assertThat(SymbolPathCodec.decode(data, true, box)).isEqualTo(path);
    }

    @Test
    void anOpenPathKeepsItsStraightAndQuadraticSegments() {
        SymbolPath path = new SymbolPath(List.of(new SymbolNode(new SymbolPoint(0, 0),
                Optional.of(new SymbolPoint(8, 4))), node(16, 8), node(32, 0)), false, false);
        assertThat(SymbolPathCodec.decode(SymbolPathCodec.encode(path), false, box)).isEqualTo(path);
    }

    @Test
    void aClosedFigureCanRetainADistinctLastNodeAtItsFirstCoordinate() {
        SymbolPath path = new SymbolPath(List.of(node(0, 0), node(32, 0), node(0, 0)), true, false);
        assertThat(SymbolPathCodec.decode(SymbolPathCodec.encode(path), false, box)).isEqualTo(path);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "M 0 0", "M 0.5 0 L 8 8", "M 0 0 M 8 8", "M 0 0 Q 8 8 16",
            "M 0 0 L 33 8", "M 0 0 L -1 8", "M 0 0 L 8 8 Z", "M 0 0 L 8 8 Z L 16 16",
            "M 0 0 C 8 8 16 16 24 24", "M 0 0 L 99999999999999999999 8", "M 0 0 L 8 8 garbage"})
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
        return new SymbolNode(new SymbolPoint(x, y), Optional.empty());
    }
}
