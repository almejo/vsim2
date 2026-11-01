package cl.almejo.vsim.ui.terminal;

import cl.almejo.vsim.core.terminal.ByteLog;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The byte monitor shows the bytes the log dropped before the window could read them. */
class ByteMonitorPanelTest {

    @BeforeEach
    void spanish() {
        Messages.initialize(Messages.SPANISH);
    }

    private static ByteLog.Entry entry(long sequence) {
        return new ByteLog.Entry(sequence, sequence * 10, 'a');
    }

    @Test
    void lostBytesShowAMarkerRowBeforeTheNewOnes() {
        ByteMonitorPanel monitor = new ByteMonitorPanel();
        monitor.accept(List.of(entry(1)), true, 0);

        monitor.accept(List.of(entry(40)), false, 37);

        assertThat(monitor.rowCount()).isEqualTo(3);
        assertThat(monitor.row(1)).containsExactly("… 37 bytes perdidos …", "", "", "");
        assertThat(monitor.row(2)).containsExactly("400", "61", "97", "a");
        assertThat(monitor.countText()).as("the marker is not a byte").isEqualTo("2 bytes");
        assertThat(monitor.hexDumpText()).startsWith("00000000  61 61 ");
    }

    @Test
    void noMarkerWithoutLostBytesAndARestartEmptiesIt() {
        ByteMonitorPanel monitor = new ByteMonitorPanel();
        monitor.accept(List.of(entry(1)), true, 0);
        monitor.accept(List.of(entry(5)), false, 3);
        monitor.accept(List.of(entry(1)), true, 0);

        assertThat(monitor.rowCount()).isEqualTo(1);
    }
}
