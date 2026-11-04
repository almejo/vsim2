package cl.almejo.vsim.core.io.logisim;

import cl.almejo.vsim.core.io.logisim.LogisimAppearance.Anchor;
import cl.almejo.vsim.core.io.logisim.LogisimAppearance.Curve;
import cl.almejo.vsim.core.io.logisim.LogisimAppearance.Port;
import cl.almejo.vsim.core.io.logisim.LogisimAppearance.Rectangle;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The box Logisim 2.7 draws for a circuit without {@code <appear>}, as {@code ends-fixture.ends} (its «plain» circuit)
 * and the notches Logisim itself wrote into saved files show it.
 */
class LogisimDefaultAppearanceTest {

    @Test
    void anEmptyCircuitIsTheSmallestBoxWithItsAnchorAtTheTopLeftCorner() {
        List<LogisimAppearance.Shape> shapes = LogisimDefaultAppearance.build(List.of()).shapes();

        assertThat(shapes).containsExactly(
                new Curve(new LogisimPoint(61, 51), new LogisimPoint(69, 51), new LogisimPoint(65, 61), notch()),
                new Rectangle(50, 50, 30, 30, 0, box()),
                new Anchor(47, 47, 6, 6, "east"));
    }

    @Test
    void thePlainCircuitOfTheFixtureHasItsPinsOnTheSidesTheyFaceAwayFrom() {
        List<LogisimInterfacePin> pins = List.of(input(100, 100, "east"), input(100, 150, "east"),
                input(100, 170, "east"), input(200, 50, "south"), input(260, 50, "south"),
                output(200, 200, "north"), output(300, 120, "west"));

        List<LogisimAppearance.Shape> shapes = LogisimDefaultAppearance.build(pins).shapes();

        // three on the west, two on the north, one on the south and the anchor on the only east pin
        assertThat(shapes).contains(new Rectangle(50, 50, 30, 40, 0, box()),
                new Port(new LogisimPoint(100, 100), 46, 56, 8, 8), new Port(new LogisimPoint(100, 150), 46, 66, 8, 8),
                new Port(new LogisimPoint(100, 170), 46, 76, 8, 8), new Port(new LogisimPoint(200, 50), 56, 46, 8, 8),
                new Port(new LogisimPoint(260, 50), 66, 46, 8, 8), new Port(new LogisimPoint(200, 200), 55, 85, 10, 10),
                new Port(new LogisimPoint(300, 120), 75, 65, 10, 10), new Anchor(77, 67, 6, 6, "east"));
    }

    @Test
    void aLonePinIsCentredOnItsSideAndTheBoxMovesHalfAStepToKeepItOnTheGrid() {
        List<LogisimAppearance.Shape> shapes = LogisimDefaultAppearance.build(List.of(input(10, 10, "east")))
                .shapes();

        assertThat(shapes).contains(new Rectangle(50, 55, 30, 30, 0, box()),
                new Port(new LogisimPoint(10, 10), 46, 66, 8, 8), new Anchor(47, 67, 6, 6, "east"));
    }

    @Test
    void pinsOnTheSameSideFollowTheirPlaceInTheCircuitAlongThatSide() {
        List<LogisimInterfacePin> pins = List.of(input(500, 100, "east"), input(170, 100, "east"),
                input(190, 30, "east"), output(400, 90, "west"));

        List<LogisimAppearance.Shape> shapes = LogisimDefaultAppearance.build(pins).shapes();

        assertThat(shapes).filteredOn(Port.class::isInstance).extracting(shape -> ((Port) shape).pin())
                .containsExactly(new LogisimPoint(190, 30), new LogisimPoint(170, 100), new LogisimPoint(500, 100),
                        new LogisimPoint(400, 90));
    }

    private static LogisimInterfacePin input(int x, int y, String facing) {
        return new LogisimInterfacePin("", 1, false, new LogisimPoint(x, y), facing);
    }

    private static LogisimInterfacePin output(int x, int y, String facing) {
        return new LogisimInterfacePin("", 1, true, new LogisimPoint(x, y), facing);
    }

    private static LogisimAppearance.Paint box() {
        return new LogisimAppearance.Paint("#000000", "none", 2);
    }

    private static LogisimAppearance.Paint notch() {
        return new LogisimAppearance.Paint("#808080", "none", 2);
    }
}
