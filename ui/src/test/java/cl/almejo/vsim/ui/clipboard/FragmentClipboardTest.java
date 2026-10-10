package cl.almejo.vsim.ui.clipboard;

import cl.almejo.vsim.core.geometry.GridPoint;
import org.junit.jupiter.api.Test;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FragmentClipboardTest {

    private static final String JSON = "{\"formatVersion\":2,\"kind\":\"fragment\"}";

    private final Clipboard systemClipboard = new Clipboard("test");
    private final FragmentClipboard clipboard = new FragmentClipboard(systemClipboard);

    @Test
    void transferableOffersTheVsimFlavorAndPlainText() throws Exception {
        FragmentTransferable transferable = new FragmentTransferable(JSON);
        assertThat(transferable.getTransferDataFlavors())
                .containsExactly(FragmentTransferable.FRAGMENT_FLAVOR, DataFlavor.stringFlavor);
        assertThat(FragmentTransferable.FRAGMENT_FLAVOR.getMimeType()).startsWith("application/x-vsim2-fragment");
        assertThat(FragmentTransferable.FRAGMENT_FLAVOR.getRepresentationClass()).isEqualTo(String.class);
        assertThat(transferable.getTransferData(FragmentTransferable.FRAGMENT_FLAVOR)).isEqualTo(JSON);
        assertThat(transferable.getTransferData(DataFlavor.stringFlavor)).isEqualTo(JSON);
        assertThat(transferable.fragmentJson()).isEqualTo(JSON);
    }

    @Test
    void transferableRejectsOtherFlavors() {
        FragmentTransferable transferable = new FragmentTransferable(JSON);
        assertThat(transferable.isDataFlavorSupported(DataFlavor.imageFlavor)).isFalse();
        assertThatThrownBy(() -> transferable.getTransferData(DataFlavor.imageFlavor))
                .isInstanceOf(UnsupportedFlavorException.class);
    }

    @Test
    void putPlacesTheJsonUnderBothFlavors() throws Exception {
        clipboard.put(JSON, new GridPoint(3, 4), null);
        assertThat(systemClipboard.getData(FragmentTransferable.FRAGMENT_FLAVOR)).isEqualTo(JSON);
        assertThat(systemClipboard.getData(DataFlavor.stringFlavor)).isEqualTo(JSON);
        assertThat(clipboard.read()).contains(JSON);
    }

    @Test
    void readPrefersTheVsimFlavor() {
        systemClipboard.setContents(new Transferable() {
            @Override
            public DataFlavor[] getTransferDataFlavors() {
                return new DataFlavor[]{DataFlavor.stringFlavor, FragmentTransferable.FRAGMENT_FLAVOR};
            }

            @Override
            public boolean isDataFlavorSupported(DataFlavor flavor) {
                return flavor.equals(DataFlavor.stringFlavor) || flavor.equals(FragmentTransferable.FRAGMENT_FLAVOR);
            }

            @Override
            public Object getTransferData(DataFlavor flavor) {
                return flavor.equals(FragmentTransferable.FRAGMENT_FLAVOR) ? "fragment" : "text";
            }
        }, null);
        assertThat(clipboard.read()).contains("fragment");
    }

    @Test
    void readFallsBackToPlainTextAndIsEmptyForAnEmptyClipboard() {
        assertThat(clipboard.read()).isEmpty();
        systemClipboard.setContents(new StringSelection("hola"), null);
        assertThat(clipboard.read()).contains("hola");
    }

    @Test
    void originIsRememberedOnlyForTheLastCopiedJsonAndItsSource() {
        Object first = new Object();
        Object second = new Object();
        assertThat(clipboard.originOf(JSON, first)).isEmpty();
        clipboard.put(JSON, new GridPoint(3, 4), first);
        assertThat(clipboard.originOf(JSON, first)).contains(new GridPoint(3, 4));
        assertThat(clipboard.originOf(JSON, second)).isEmpty();
        assertThat(clipboard.originOf("{}", first)).isEmpty();
        clipboard.put("{}", new GridPoint(7, 8), first);
        assertThat(clipboard.originOf(JSON, first)).isEmpty();
        assertThat(clipboard.originOf("{}", first)).contains(new GridPoint(7, 8));
        clipboard.put(JSON, new GridPoint(1, 1), null); // no source: never "next to the original"
        assertThat(clipboard.originOf(JSON, null)).isEmpty();
        assertThat(clipboard.originOf(JSON, first)).isEmpty();
    }

    @Test
    void busyClipboardIsIgnored() {
        Clipboard busy = new Clipboard("busy") {
            @Override
            public synchronized void setContents(Transferable contents, ClipboardOwner owner) {
                throw new IllegalStateException("owned by another application");
            }

            @Override
            public synchronized Transferable getContents(Object requestor) {
                throw new IllegalStateException("owned by another application");
            }

            @Override
            public boolean isDataFlavorAvailable(DataFlavor flavor) {
                throw new IllegalStateException("owned by another application");
            }
        };
        FragmentClipboard locked = new FragmentClipboard(busy);
        locked.put(JSON, GridPoint.ORIGIN, null);
        assertThat(locked.read()).isEmpty();
        assertThat(locked.originOf(JSON, null)).isEmpty();
    }

    @Test
    void systemClipboardIsSharedByEveryWindow() {
        assertThat(FragmentClipboard.system()).isSameAs(FragmentClipboard.system());
    }
}
