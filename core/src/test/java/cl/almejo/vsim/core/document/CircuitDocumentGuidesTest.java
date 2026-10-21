package cl.almejo.vsim.core.document;

import cl.almejo.vsim.core.geometry.GridPoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CircuitDocumentGuidesTest {

    private final CircuitDocument document = new CircuitDocument();
    private final Guide horizontal = new Guide(Guide.Orientation.HORIZONTAL, 10);
    private final Guide vertical = new Guide(Guide.Orientation.VERTICAL, -4);

    @Test
    void guidesStartEmptyAndChangingThemNotifiesOnlyGuideListeners() {
        List<String> calls = new ArrayList<>();
        document.addListener((changed, change) -> calls.add("document"));
        document.addGuideListener(() -> calls.add("guides"));
        long revision = document.revision();
        assertThat(document.guides()).isEmpty();

        document.setGuides(List.of(horizontal, vertical));

        assertThat(document.guides()).containsExactly(horizontal, vertical);
        assertThat(calls).containsExactly("guides");
        assertThat(document.revision()).as("guides never bump the revision").isEqualTo(revision);

        document.setGuides(List.of(horizontal, vertical));
        assertThat(calls).as("an equal list notifies nobody").containsExactly("guides");
    }

    @Test
    void duplicatesCollapseKeepingTheFirstPlace() {
        document.setGuides(List.of(vertical, horizontal, new Guide(Guide.Orientation.VERTICAL, -4)));

        assertThat(document.guides()).containsExactly(vertical, horizontal);
    }

    @Test
    void theSamePositionOnTheOtherAxisIsADifferentGuide() {
        Guide sameNumber = new Guide(Guide.Orientation.VERTICAL, 10);
        document.setGuides(List.of(horizontal, sameNumber));

        assertThat(document.guides()).containsExactly(horizontal, sameNumber);
    }

    @Test
    void theListIsACopyThatCannotBeChangedFromOutside() {
        List<Guide> given = new ArrayList<>(List.of(horizontal));
        document.setGuides(given);
        given.add(vertical);

        assertThat(document.guides()).containsExactly(horizontal);
        assertThatThrownBy(() -> document.guides().add(vertical)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> document.setGuides(Arrays.asList(horizontal, null)))
                .isInstanceOf(NullPointerException.class);
        assertThat(document.guides()).containsExactly(horizontal);
    }

    @Test
    void guideListenersCanBeRemoved() {
        List<String> calls = new ArrayList<>();
        Runnable listener = () -> calls.add("guides");
        document.addGuideListener(listener);
        assertThat(document.guideListenerCount()).isEqualTo(1);
        document.removeGuideListener(listener);
        assertThat(document.guideListenerCount()).isZero();

        document.setGuides(List.of(horizontal));

        assertThat(calls).isEmpty();
    }

    @Test
    void editingElementsDoesNotTouchTheGuides() {
        List<String> calls = new ArrayList<>();
        document.setGuides(List.of(horizontal));
        document.addGuideListener(() -> calls.add("guides"));

        document.add(new Wire(document.newWireId(), new GridPoint(0, 0), new GridPoint(4, 0)));

        assertThat(document.guides()).containsExactly(horizontal);
        assertThat(calls).isEmpty();
    }

    @Test
    void aGuideNeedsAnOrientation() {
        assertThatThrownBy(() -> new Guide(null, 3)).isInstanceOf(NullPointerException.class);
    }
}
