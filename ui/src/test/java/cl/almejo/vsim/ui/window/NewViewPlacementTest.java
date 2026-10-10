package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.ui.canvas.ViewState;
import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NewViewPlacementTest {

    @Test
    void aNewViewOpensThirtyPixelsDownRightShowingWhatItsSourceShows() {
        ViewState shown = new ViewState(2.0, 10.5, -4.0);
        NewViewPlacement placement = NewViewPlacement.after(new Point(100, 80), shown);
        assertThat(placement.location()).isEqualTo(new Point(130, 110));
        assertThat(placement.viewState()).isSameAs(shown);
    }

    @Test
    void theLocationCannotBeChangedFromOutside() {
        Point source = new Point(0, 0);
        NewViewPlacement placement = NewViewPlacement.after(source, new ViewState(1.0, 0, 0));
        source.translate(5, 5);
        placement.location().translate(7, 7);
        assertThat(placement.location()).isEqualTo(new Point(NewViewPlacement.OFFSET, NewViewPlacement.OFFSET));
    }

    @Test
    void applyingItHandsTheNewWindowTheZoomAndCenterAndTheOffsetLocation() {
        ViewState shown = new ViewState(2.0, 10.5, -4.0);
        List<ViewState> initialStates = new ArrayList<>();
        List<Point> locations = new ArrayList<>();

        NewViewPlacement.after(new Point(100, 80), shown).applyTo(initialStates::add, locations::add);

        assertThat(initialStates).containsExactly(shown);
        assertThat(locations).containsExactly(new Point(130, 110));
    }
}
