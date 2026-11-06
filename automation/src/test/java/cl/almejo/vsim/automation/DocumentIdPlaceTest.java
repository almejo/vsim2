package cl.almejo.vsim.automation;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A place of a circuit is addressed by the circuit's id qualified with it (spec §12). */
class DocumentIdPlaceTest {

    private final DocumentId circuit = new DocumentId("circuit-3");

    @Test
    void aPlaceWithAContextRoundTrips() {
        PlaceRef place = new PlaceRef("alu", Optional.of("c9/c2"));

        DocumentId inPlace = circuit.in(place);

        assertThat(inPlace.base()).isEqualTo(circuit);
        assertThat(inPlace.place()).contains(place);
        assertThat(inPlace).isNotEqualTo(circuit);
    }

    @Test
    void aPlaceWithoutContextIsSinValores() {
        DocumentId inPlace = circuit.in(new PlaceRef("alu", Optional.empty()));

        assertThat(inPlace.place()).contains(new PlaceRef("alu", Optional.empty()));
    }

    @Test
    void theMainPlaceIsTheCircuitItself() {
        assertThat(circuit.in(PlaceRef.main())).isEqualTo(circuit);
        assertThat(circuit.place()).isEmpty();
        assertThat(circuit.base()).isSameAs(circuit);
    }

    @Test
    void aBlankContextMeansNone() {
        assertThat(new PlaceRef("alu", Optional.of(" ")).context()).isEmpty();
        assertThatThrownBy(() -> new PlaceRef(" ", Optional.empty())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anAtSignOrPercentInTheDefinitionOrThePathCannotBreakTheEncoding() {
        PlaceRef place = new PlaceRef("my@alu%20", Optional.of("c9@x/c2%"));

        DocumentId inPlace = circuit.in(place);

        assertThat(inPlace.base()).isEqualTo(circuit);
        assertThat(inPlace.place()).contains(place);
        assertThat(circuit.in(new PlaceRef("@", Optional.empty())).place())
                .contains(new PlaceRef("@", Optional.empty()));
    }
}
