package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.component.PinDirection;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** How a net resolves the values its pins drive, including a pin whose drive is still on its way. */
class RuntimeNetTest {

    private static PinState output(int width, Value driven) {
        PinState pin = new PinState(null, 0, PinSpec.output("out", 0, 0, width));
        pin.driven = driven;
        return pin;
    }

    private static PinState input(int width) {
        return new PinState(null, 0, PinSpec.input("in", 0, 0, width));
    }

    private static PinState transitioning(int width, Value driven) {
        PinState pin = output(width, driven);
        pin.pendingDrives = 1;
        return pin;
    }

    private static RuntimeNet net(int width, PinState... pins) {
        RuntimeNet net = new RuntimeNet("n", width, false);
        for (PinState pin : pins) {
            net.add(pin);
        }
        return net;
    }

    @Test
    void startsAtHighImpedanceAndReportsWhetherItChanged() {
        RuntimeNet net = net(1, output(1, Value.ONE), input(1));

        assertThat(net.value).isEqualTo(Value.HIGH_IMPEDANCE);
        assertThat(net.resolve(0)).isTrue();
        assertThat(net.value).isEqualTo(Value.ONE);
        assertThat(net.resolve(1)).isFalse();
    }

    @Test
    void highImpedanceDriversYieldAndInputsDoNotCount() {
        RuntimeNet net = net(4, output(4, Value.highImpedance(4)), output(4, Value.of(4, 0b1010)), input(4));

        net.resolve(0);

        assertThat(net.value).isEqualTo(Value.of(4, 0b1010));
    }

    @Test
    void disagreeingDriversConflictFromTheTimeTheyFirstDisagree() {
        PinState first = output(1, Value.ONE);
        PinState second = output(1, Value.ZERO);
        RuntimeNet net = net(1, first, second);

        net.resolve(7);
        assertThat(net.value.conflict()).isTrue();
        assertThat(net.conflictSince).isEqualTo(7);
        net.resolve(9);
        assertThat(net.conflictSince).as("still the first time").isEqualTo(7);

        second.driven = Value.ONE;
        net.resolve(11);
        assertThat(net.value).isEqualTo(Value.ONE);
        assertThat(net.conflictSince).isEqualTo(-1);
    }

    @Test
    void aPinWithADrivePendingOnlyFillsTheBitsNobodyDrives() {
        RuntimeNet net = net(4, output(4, new Value(4, 0b0001, 0b1100, false)),
                transitioning(4, new Value(4, 0b1110, 0b0001, false)));

        net.resolve(0);

        assertThat(net.value).as("bits 2 and 3 come from the pending pin, bits 0 and 1 from the stable one")
                .isEqualTo(new Value(4, 0b1101, 0b0000, false));
        assertThat(net.value.conflict()).isFalse();
    }

    @Test
    void aPinWithADrivePendingNeverConflictsWithAStableOne() {
        RuntimeNet net = net(1, output(1, Value.ONE), transitioning(1, Value.ZERO));

        net.resolve(0);

        assertThat(net.value).isEqualTo(Value.ONE);
    }

    @Test
    void pendingPinsAloneFillInOrderAndNeverConflict() {
        RuntimeNet net = net(1, transitioning(1, Value.ONE), transitioning(1, Value.ZERO));

        net.resolve(0);

        assertThat(net.value).as("the first pending pin fills the bit; a pending disagreement is no conflict yet")
                .isEqualTo(Value.ONE);
    }

    @Test
    void aNetWithMismatchedWidthsStaysAtHighImpedance() {
        RuntimeNet net = new RuntimeNet("n", 2, true);
        net.add(output(2, Value.of(2, 0b11)));

        assertThat(net.resolve(0)).isFalse();
        assertThat(net.value).isEqualTo(Value.highImpedance(2));
    }

    @Test
    void sortsItsPinsIntoDriversAndInputs() {
        PinState output = output(1, Value.ONE);
        PinState input = input(1);
        PinState both = new PinState(null, 0, new PinSpec("io", new GridPoint(0, 0), PinDirection.BIDIRECTIONAL, 1));
        RuntimeNet net = net(1, output, input, both);

        assertThat(net.pins).containsExactly(output, input, both);
        assertThat(net.drivers).containsExactly(output, both);
        assertThat(net.inputs).containsExactly(input, both);
    }
}
