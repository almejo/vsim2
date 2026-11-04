package cl.almejo.vsim.ui.sheets;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.geometry.GridPoint;
import org.junit.jupiter.api.Test;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SheetAnchorsTest {

    /** How many collections the weak-key test asks for before giving up; one is nearly always enough. */
    private static final int COLLECTION_ATTEMPTS = 50;

    private final SheetAnchors anchors = new SheetAnchors();
    private final CircuitDocument document = new CircuitDocument();
    private final CircuitDocument other = new CircuitDocument();
    private final List<CircuitDocument> told = new ArrayList<>();

    @Test
    void aDocumentHasNoAnchorUntilOneIsTaken() {
        assertThat(anchors.anchor(document)).isEmpty();

        anchors.take(document, new GridPoint(-4, 7));

        assertThat(anchors.anchor(document)).contains(new GridPoint(-4, 7));
        assertThat(anchors.anchor(other)).isEmpty();
    }

    @Test
    void takingKeepsTheAnchorAlreadyThere() {
        anchors.take(document, new GridPoint(1, 2));
        anchors.addListener(told::add);

        anchors.take(document, new GridPoint(50, 60));

        assertThat(anchors.anchor(document)).contains(new GridPoint(1, 2));
        assertThat(told).as("nothing changed").isEmpty();
    }

    @Test
    void clearingForgetsOnlyThatDocument() {
        anchors.take(document, new GridPoint(1, 2));
        anchors.take(other, new GridPoint(3, 4));

        anchors.clear(document);

        assertThat(anchors.anchor(document)).isEmpty();
        assertThat(anchors.anchor(other)).contains(new GridPoint(3, 4));
        anchors.take(document, new GridPoint(9, 9));
        assertThat(anchors.anchor(document)).as("taken again after a clear").contains(new GridPoint(9, 9));
    }

    @Test
    void listenersHearWhichDocumentChangedAndOnlyRealChanges() {
        anchors.addListener(told::add);

        anchors.take(document, new GridPoint(1, 2));
        anchors.clear(other);
        anchors.clear(document);
        anchors.clear(document);

        assertThat(told).containsExactly(document, document);
    }

    @Test
    void aRemovedListenerHearsNothing() {
        Consumer<CircuitDocument> listener = told::add;
        anchors.addListener(listener);
        assertThat(anchors.listenerCount()).isEqualTo(1);

        anchors.removeListener(listener);
        anchors.take(document, new GridPoint(1, 2));

        assertThat(anchors.listenerCount()).isZero();
        assertThat(told).isEmpty();
    }

    @Test
    void nullsAreProgrammingErrors() {
        assertThatThrownBy(() -> anchors.take(null, new GridPoint(0, 0))).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> anchors.take(document, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> anchors.addListener(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void aClosedDocumentDoesNotStayInMemoryForItsAnchor() {
        WeakReference<CircuitDocument> closed = takeOnAThrowawayDocument();

        // the map drops the entry once the collector has queued the cleared key, which can take a few rounds
        for (int attempt = 0; attempt < COLLECTION_ATTEMPTS && anchors.documentCount() > 0; attempt++) {
            System.gc();
        }

        assertThat(closed.get()).isNull();
        assertThat(anchors.documentCount()).isZero();
    }

    private WeakReference<CircuitDocument> takeOnAThrowawayDocument() {
        CircuitDocument throwaway = new CircuitDocument();
        anchors.take(throwaway, new GridPoint(0, 0));
        assertThat(anchors.documentCount()).isEqualTo(1);
        return new WeakReference<>(throwaway);
    }
}
