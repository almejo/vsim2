package cl.almejo.vsim.ui.clipboard;

import org.junit.jupiter.api.Test;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FragmentTransferableTest {

    private static final String JSON = "{\"formatVersion\":2,\"kind\":\"fragment\"}";

    @Test
    void offersTheFragmentFlavorFirstAndPlainTextSecond() throws Exception {
        FragmentTransferable transferable = new FragmentTransferable(JSON);
        assertThat(transferable.getTransferDataFlavors())
                .containsExactly(FragmentTransferable.FRAGMENT_FLAVOR, DataFlavor.stringFlavor);
        assertThat(transferable.getTransferData(FragmentTransferable.FRAGMENT_FLAVOR)).isEqualTo(JSON);
        assertThat(transferable.getTransferData(DataFlavor.stringFlavor)).isEqualTo(JSON);
    }

    @Test
    void theFragmentFlavorIsVsimsOwnStringFlavor() {
        assertThat(FragmentTransferable.FRAGMENT_FLAVOR.isMimeTypeEqual("application/x-vsim2-fragment")).isTrue();
        assertThat(FragmentTransferable.FRAGMENT_FLAVOR.getRepresentationClass()).isEqualTo(String.class);
        assertThat(FragmentTransferable.FRAGMENT_FLAVOR).isNotEqualTo(DataFlavor.stringFlavor);
    }

    @Test
    void refusesOtherFlavors() {
        FragmentTransferable transferable = new FragmentTransferable(JSON);
        assertThat(transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)).isFalse();
        assertThatThrownBy(() -> transferable.getTransferData(DataFlavor.javaFileListFlavor))
                .isInstanceOf(UnsupportedFlavorException.class);
    }
}
