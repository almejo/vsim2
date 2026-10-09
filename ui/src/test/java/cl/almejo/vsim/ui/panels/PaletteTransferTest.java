package cl.almejo.vsim.ui.panels;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaletteTransferTest {

    @Test
    void decodesOnlyWhatItEncoded() {
        assertThat(PaletteTransfer.decode(PaletteTransfer.encode("and2"))).contains("and2");
        assertThat(PaletteTransfer.decode("and2")).isEmpty();
        assertThat(PaletteTransfer.decode("")).isEmpty();
        assertThat(PaletteTransfer.decode(null)).isEmpty();
    }
}
