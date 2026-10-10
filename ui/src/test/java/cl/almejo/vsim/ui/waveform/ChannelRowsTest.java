package cl.almejo.vsim.ui.waveform;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.document.ProbeTarget;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelRowsTest {

    @Test
    void valuesAreWrittenAsBitsOrHex() {
        assertThat(ChannelRows.valueText(Optional.of(Value.ONE))).isEqualTo("1");
        assertThat(ChannelRows.valueText(Optional.of(Value.ZERO))).isEqualTo("0");
        assertThat(ChannelRows.valueText(Optional.of(Value.HIGH_IMPEDANCE))).isEqualTo("Z");
        assertThat(ChannelRows.valueText(Optional.of(Value.ONE.withConflict()))).isEqualTo("X");
        assertThat(ChannelRows.valueText(Optional.of(Value.of(8, 0x3F)))).isEqualTo("0x3F");
        assertThat(ChannelRows.valueText(Optional.of(Value.of(8, 0x3F).withConflict()))).isEqualTo("X");
        assertThat(ChannelRows.valueText(Optional.empty())).isEmpty();
    }

    @Test
    void rowsFollowTheProbesAndTheirResolution() {
        List<Probe> probes = List.of(new Probe("p1", new ProbeTarget.PinTarget(new ElementId("c1"), 0), "reloj"),
                new Probe("p2", new ProbeTarget.WireTarget(new ElementId("w9")), ""));

        assertThat(ChannelRows.of(probes, Map.of("p1", "n:p:c1#0"))).containsExactly(
                new ChannelRows.ChannelRow("p1", "reloj", true), new ChannelRows.ChannelRow("p2", "p2", false));
    }
}
