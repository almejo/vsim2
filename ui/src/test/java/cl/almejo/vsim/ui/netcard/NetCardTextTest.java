package cl.almejo.vsim.ui.netcard;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.netlist.NetId;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class NetCardTextTest {

    private static NetSummary summary(int width, NetValue value, Optional<NetProblem> problem) {
        return new NetSummary(new NetId("n1"), width, Set.of(new ElementId("w1")), Set.of(), List.of(), List.of(),
                List.of(), value, problem);
    }

    private static NetEnd end(String part, String pin, Optional<String> value) {
        return new NetEnd(new ElementId("c1"), 0, part, pin, value, new GridPoint(0, 0));
    }

    @Test
    void aOneBitNetSaysItsValueAndWhetherItIsHigh() {
        NetSummary high = summary(1, new NetValue.Simulated(Value.ONE), Optional.empty());
        NetSummary low = summary(1, new NetValue.Simulated(Value.ZERO), Optional.empty());

        assertThat(NetCardText.header(high)).isEqualTo("Red de 1 bit = 1");
        assertThat(NetCardText.valueRows(high)).containsExactly(new NetCardText.Row("valor", "1 (alto)"));
        assertThat(NetCardText.valueRows(low)).containsExactly(new NetCardText.Row("valor", "0 (bajo)"));
    }

    @Test
    void aBusIsReadInEveryFormat() {
        NetSummary bus = summary(4, new NetValue.Simulated(Value.of(4, 0xB)), Optional.empty());

        assertThat(NetCardText.header(bus)).isEqualTo("Red de 4 bits = 0xB");
        assertThat(NetCardText.valueRows(bus)).containsExactly(
                new NetCardText.Row("bin", "1011"),
                new NetCardText.Row("hex", "0xB"),
                new NetCardText.Row("dec", "11"),
                new NetCardText.Row("con signo", "-5"),
                new NetCardText.Row("octal", "0o13"));
    }

    @Test
    void aBusPartlyInZOrShortedHasOnlyItsBitsLikeTheBusBalloon() {
        Value partlyFloating = new Value(4, 0b0010, 0b1100, false);
        NetSummary floating = summary(4, new NetValue.Simulated(partlyFloating), Optional.empty());
        NetSummary shorted = summary(4, new NetValue.Simulated(Value.of(4, 3).withConflict()), Optional.empty());

        assertThat(NetCardText.valueRows(floating)).containsExactly(
                new NetCardText.Row("bin", "ZZ10"),
                new NetCardText.Row("hex", "0x?"));
        assertThat(NetCardText.header(shorted)).isEqualTo("Red de 4 bits = X");
        assertThat(NetCardText.valueRows(shorted)).containsExactly(new NetCardText.Row("valor", "X (cortocircuito)"));
    }

    @Test
    void aNetThatIsNotSimulatedSaysSo() {
        NetSummary notSimulating = summary(4, new NetValue.NotSimulating(), Optional.empty());

        assertThat(NetCardText.header(notSimulating)).isEqualTo("Red de 4 bits = sin simular");
        assertThat(NetCardText.valueRows(notSimulating)).isEmpty();
    }

    @Test
    void anEndIsItsPartAndPinWithItsValueWhenItHasOne() {
        assertThat(NetCardText.end(end("NOT U2", "entrada 1", Optional.empty()))).isEqualTo("NOT U2 · entrada 1");
        assertThat(NetCardText.end(end("Interruptor A", "out", Optional.of("1")))).isEqualTo("Interruptor A · out → 1");
    }

    @Test
    void sectionsTunnelsAndProblemsHaveTheirTexts() {
        assertThat(NetCardText.driversTitle()).isEqualTo("La maneja");
        assertThat(NetCardText.readersTitle()).isEqualTo("Llega a");
        assertThat(NetCardText.tunnelsTitle()).isEqualTo("Pasa por");
        assertThat(NetCardText.tunnel("LISTO")).isEqualTo("túnel «LISTO»");
        assertThat(NetCardText.problem(new NetProblem.Undriven())).isEqualTo("⚠ Nadie la maneja: vale Z");
        assertThat(NetCardText.problem(new NetProblem.ShortCircuit(List.of(end("A", "out", Optional.of("1"))))))
                .startsWith("⚠ Cortocircuito");
        assertThat(NetCardText.problem(new NetProblem.WidthMismatch("⚠ Anchos incompatibles: x")))
                .isEqualTo("⚠ Anchos incompatibles: x");
    }
}
