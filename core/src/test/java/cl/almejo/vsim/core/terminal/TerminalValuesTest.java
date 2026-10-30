package cl.almejo.vsim.core.terminal;

import cl.almejo.vsim.core.terminal.ColorReference.PaletteIndex;
import cl.almejo.vsim.core.terminal.ColorReference.Rgb;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The model's values: settings limits, colour references, cells and frames are validated and immutable. */
class TerminalValuesTest {

    private static TerminalSettings settings(int rows, int columns, int scrollback, int dataWidth, int tabWidth) {
        return new TerminalSettings(TerminalMode.VT, rows, columns, scrollback, true, dataWidth, TerminalEncoding.UTF8,
                true, false, true, tabWidth);
    }

    @Test
    void settingsRejectValuesOutsideTheirLimits() {
        assertThatThrownBy(() -> settings(0, 80, 0, 8, 8)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> settings(61, 80, 0, 8, 8)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> settings(24, 161, 0, 8, 8)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> settings(24, 80, 10_001, 8, 8)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> settings(24, 80, 0, 6, 8)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> settings(24, 80, 0, 8, 17)).isInstanceOf(IllegalArgumentException.class);
        assertThat(settings(60, 160, 10_000, 7, 16).rows()).isEqualTo(60);
    }

    @Test
    void theProfilesFollowTheSpecTable() {
        TerminalSettings logisim = TerminalSettings.logisim(24, 80);
        assertThat(logisim.mode()).isEqualTo(TerminalMode.LOGISIM);
        assertThat(logisim.dataWidth()).isEqualTo(7);
        assertThat(logisim.backspaceErases()).isTrue();
        assertThat(logisim.scrollbackLines()).isZero();
        TerminalSettings vt = TerminalSettings.vt(24, 80);
        assertThat(vt.dataWidth()).isEqualTo(8);
        assertThat(vt.backspaceErases()).isFalse();
        assertThat(vt.scrollbackLines()).isEqualTo(1000);
        TerminalSettings teletype = TerminalSettings.teletype(24, 80);
        assertThat(teletype.mode()).isEqualTo(TerminalMode.TELETYPE);
        assertThat(teletype.formFeedClears()).isFalse();
        assertThat(teletype.scrollbackLines()).isEqualTo(10_000);
    }

    @Test
    void colourReferencesAreValidated() {
        assertThatThrownBy(() -> new PaletteIndex(256)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Rgb(0x1000000)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new PaletteIndex(255).index()).isEqualTo(255);
    }

    @Test
    void aCellCopiesItsBytesInAndOut() {
        byte[] bytes = {(byte) 0xC3, (byte) 0xB1};
        TerminalCell cell = new TerminalCell('ñ', CellStyle.PLAIN, bytes, 3);
        bytes[0] = 0;
        cell.bytes()[1] = 0;
        assertThat(cell.bytes()).containsExactly(0xC3, 0xB1);
        assertThat(cell).isEqualTo(new TerminalCell('ñ', CellStyle.PLAIN, new byte[] {(byte) 0xC3, (byte) 0xB1}, 3));
        assertThat(cell.hashCode())
                .isEqualTo(new TerminalCell('ñ', CellStyle.PLAIN, new byte[] {(byte) 0xC3, (byte) 0xB1}, 3).hashCode());
        assertThat(TerminalCell.BLANK.written()).isFalse();
        assertThat(TerminalCell.BLANK.codePoint()).isEqualTo(' ');
    }

    @Test
    void aStyleNeverHoldsNull() {
        assertThatThrownBy(() -> new CellStyle(null, Optional.empty(), false, false, false, false))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void aFrameCopiesItsRowsAndChecksTheirSize() {
        TerminalCell[] row = {TerminalCell.BLANK, TerminalCell.BLANK};
        TerminalFrame frame = new TerminalFrame(1, 2, List.<TerminalCell[]>of(row), 0, 1, true, Optional.empty(), 0,
                1, 0, 0);
        row[0] = new TerminalCell('x', CellStyle.PLAIN, new byte[] {'x'}, 0);
        assertThat(frame.text()).isEmpty();
        assertThatThrownBy(() -> new TerminalFrame(2, 2, List.<TerminalCell[]>of(row), 0, 0, true, Optional.empty(),
                0, 1, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TerminalFrame(1, 2, List.<TerminalCell[]>of(row), 0, 2, true, Optional.empty(),
                0, 1, 0, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
