package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BusValueTextTest {

    @Test
    void aBusIsWrittenInTheProbesFormat() {
        Value byteValue = Value.of(8, 0xE8);
        assertThat(BusValueText.of(byteValue, Probe.FORMAT_HEX)).isEqualTo("0xE8");
        assertThat(BusValueText.of(byteValue, "binary")).isEqualTo("11101000");
        assertThat(BusValueText.of(byteValue, "octal")).isEqualTo("0o350");
        assertThat(BusValueText.of(byteValue, "decimal")).isEqualTo("232");
        assertThat(BusValueText.of(byteValue, "signed")).isEqualTo("-24");
        assertThat(BusValueText.of(byteValue, "ascii")).isEqualTo("0xE8");
        assertThat(BusValueText.of(byteValue, Probe.fixedFormat(4))).isEqualTo("-1.5");
        assertThat(BusValueText.of(Value.of(8, 0x41), "ascii")).isEqualTo("'A'");
        assertThat(BusValueText.of(Value.of(32, 0x3FC00000L), "float")).isEqualTo("1.5");
        assertThat(BusValueText.of(Value.of(16, 0x3E00), "float")).isEqualTo("1.5");
        assertThat(BusValueText.of(Value.of(64, 0x3FF8000000000000L), "float")).isEqualTo("1.5");
        assertThat(BusValueText.of(Value.of(12, 0x234), "float")).as("no float at 12 bits: hex").isEqualTo("0x234");
    }

    @Test
    void anUndefinedOrConflictingBusStaysTheHexTextWhateverTheFormat() {
        Value partlyZ = new Value(4, 0x9, 0x6, false);
        for (String format : new String[]{"hex", "binary", "octal", "decimal", "signed", "float", "ascii",
                Probe.fixedFormat(2)}) {
            assertThat(BusValueText.of(partlyZ, format)).as(format).isEqualTo("0x?");
            assertThat(BusValueText.of(Value.of(4, 9).withConflict(), format)).as(format).isEqualTo("X");
        }
    }

    @Test
    void theTooltipListsEveryFormatThatMakesSense() {
        assertThat(BusValueText.otherFormats(Value.of(4, 0x9))).isEqualTo("decimal 9 · con signo -7 · octal 0o11");
        assertThat(BusValueText.otherFormats(Value.of(8, 0x41)))
                .isEqualTo("decimal 65 · con signo 65 · octal 0o101 · carácter 'A'");
        assertThat(BusValueText.otherFormats(Value.of(8, 0xE8)))
                .isEqualTo("decimal 232 · con signo -24 · octal 0o350 · carácter 0xE8");
        assertThat(BusValueText.otherFormats(Value.of(16, 0x3E00)))
                .isEqualTo("decimal 15872 · con signo 15872 · octal 0o37000 · flotante 1.5");
        assertThat(BusValueText.otherFormats(Value.of(32, 0xC0200000L)))
                .isEqualTo("decimal 3223322624 · con signo -1071644672 · octal 0o30010000000 · flotante -2.5");
        assertThat(BusValueText.otherFormats(Value.of(12, 0x234)))
                .as("neither characters nor float at 12 bits").isEqualTo("decimal 564 · con signo 564 · octal 0o1064");
        assertThat(BusValueText.otherFormats(Value.of(64, -1L)))
                .startsWith("decimal 18446744073709551615 · con signo -1 · octal 0o1777777777777777777777")
                .endsWith("flotante nan");
    }
}
