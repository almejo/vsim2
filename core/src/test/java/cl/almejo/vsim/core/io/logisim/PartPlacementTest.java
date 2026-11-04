package cl.almejo.vsim.core.io.logisim;

import cl.almejo.vsim.core.geometry.GridPoint;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PartPlacementTest {

    private static final int NET = 1;
    private static final int OTHER_NET = 2;

    @Test
    void theEstimateIsTheMedianOfWhereEachEndMeetsItsTarget() {
        List<PartPlacement.End> ends = List.of(
                new PartPlacement.End(new GridPoint(0, 0), NET, Optional.of(new GridPoint(10, 5))),
                new PartPlacement.End(new GridPoint(0, 2), NET, Optional.of(new GridPoint(11, 7))),
                new PartPlacement.End(new GridPoint(4, 0), NET, Optional.of(new GridPoint(20, 5))));

        assertThat(PartPlacement.estimate(ends, () -> GridPoint.ORIGIN)).isEqualTo(new GridPoint(11, 5));
    }

    @Test
    void anEndWithNoTargetLeavesTheEstimateToTheFallback() {
        List<PartPlacement.End> ends = List.of(new PartPlacement.End(new GridPoint(1, 1), NET, Optional.empty()));

        assertThat(PartPlacement.estimate(ends, () -> new GridPoint(3, 4))).isEqualTo(new GridPoint(3, 4));
    }

    @Test
    void theSpotNearestTheTargetsThatTouchesNoOtherNetWins() {
        GridOccupancy occupancy = new GridOccupancy();
        occupancy.addPoint(new GridPoint(5, 5), OTHER_NET);
        List<PartPlacement.End> ends = List.of(new PartPlacement.End(GridPoint.ORIGIN, NET,
                Optional.of(new GridPoint(5, 5))));
        PartPlacement placement = new PartPlacement(occupancy);

        Optional<PartPlacement.Spot> spot = placement.bestFree(List.of(
                new PartPlacement.Choice(ends, new GridPoint(5, 5), 0)));

        assertThat(spot).isPresent();
        assertThat(spot.get().position()).isNotEqualTo(new GridPoint(5, 5));
        GridPoint position = spot.get().position();
        assertThat(Math.abs(position.x() - 5) + Math.abs(position.y() - 5)).isEqualTo(1);
    }

    @Test
    void aChoiceWithAPenaltyLosesToAnEqualOneWithout() {
        List<PartPlacement.End> ends = List.of(new PartPlacement.End(GridPoint.ORIGIN, NET,
                Optional.of(new GridPoint(2, 2))));
        PartPlacement placement = new PartPlacement(new GridOccupancy());

        Optional<PartPlacement.Spot> spot = placement.bestFree(List.of(
                new PartPlacement.Choice(ends, new GridPoint(2, 2), 3),
                new PartPlacement.Choice(ends, new GridPoint(2, 2), 0)));

        assertThat(spot).contains(new PartPlacement.Spot(new GridPoint(2, 2), 1));
    }

    @Test
    void apartGoesBelowEverythingDrawnUnderTheEstimate() {
        GridOccupancy occupancy = new GridOccupancy();
        occupancy.addPoint(new GridPoint(0, 10), OTHER_NET);
        List<PartPlacement.End> ends = List.of(new PartPlacement.End(new GridPoint(0, -2), NET, Optional.empty()));
        PartPlacement placement = new PartPlacement(occupancy);

        GridPoint apart = placement.apart(new PartPlacement.Choice(ends, new GridPoint(7, 0), 0));

        assertThat(apart).isEqualTo(new GridPoint(7, 10 + 3 + 2));
    }
}
