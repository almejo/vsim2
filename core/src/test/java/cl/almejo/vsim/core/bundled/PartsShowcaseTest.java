package cl.almejo.vsim.core.bundled;

import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.io.CircuitFormat;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PartsShowcaseTest {

    private PartsShowcase showcase;
    private ExampleSimulation simulation;

    @BeforeEach
    void settle() {
        showcase = PartsShowcase.create();
        simulation = new ExampleSimulation(showcase.builder().document(), Map.of()).settle();
    }

    private Value shown(String key) {
        Component probe = showcase.probes().get(key);
        assertThat(probe).as("probe " + key).isNotNull();
        return ((VisibleState.LedState) simulation.engine().snapshot().componentState(probe.id().value())).value();
    }

    private long number(String key) {
        Value value = shown(key);
        assertThat(value.isFullyDefined()).as(key + " is defined").isTrue();
        return value.bits();
    }

    private void click(String key) {
        Component input = showcase.inputs().get(key);
        assertThat(input).as("input " + key).isNotNull();
        simulation.engine().interact(input.id().value());
        simulation.settle();
    }

    @Test
    void theShowcaseIsWiredAndLaidOutWithNoProblems() {
        assertThat(ExampleChecks.wiringProblems(showcase.builder())).isEmpty();
        CircuitDocument document = showcase.builder().document();
        assertThat(document.frames()).extracting(Frame::title).containsExactlyInAnyOrder("Aritmética", "Cableado",
                "Compuertas", "Multiplexores", "Secuenciales", "Entrada/Salida");
        for (Component component : document.components()) {
            Bounds bounds = showcase.builder().registry().typeOf(component).bounds(component);
            assertThat(document.frames()).as("a frame around %s", component.id().value())
                    .anyMatch(frame -> frame.encloses(bounds.x(), bounds.y(), bounds.x() + bounds.width(),
                            bounds.y() + bounds.height()));
        }
    }

    @Test
    void theShowcaseReadsBackFromItsFileAsItWasWritten() throws Exception {
        CircuitFormat format = new CircuitFormat(showcase.builder().registry());

        CircuitDocument read = format.read(showcase.text());

        assertThat(format.write(read) + "\n").isEqualTo(showcase.text());
    }

    @Test
    void theShowcaseSimulatesWithNoProblems() {
        SimulationSnapshot snapshot = simulation.engine().snapshot();
        assertThat(snapshot.failure()).isEmpty();
        assertThat(snapshot.problems()).isEmpty();
    }

    @Test
    void theArithmeticPartsShowTheInterestingCases() {
        assertThat(number("subtractor:difference")).isEqualTo(253);
        assertThat(number("subtractor:borrow")).isEqualTo(1);
        assertThat(number("negator")).as("the minimum stays itself").isEqualTo(0x80);
        assertThat(number("multiplier:low") | number("multiplier:high") << 8).isEqualTo(30_000);
        assertThat(number("multiplier-signed:low") | number("multiplier-signed:high") << 8).isEqualTo(0xFFFA);
        assertThat(number("divider:quotient")).isEqualTo(14);
        assertThat(number("divider:remainder")).isEqualTo(2);
        assertThat(number("divider:error")).isZero();
        assertThat(number("divider-zero:quotient")).isZero();
        assertThat(number("divider-zero:remainder")).isEqualTo(100);
        assertThat(number("divider-zero:error")).isEqualTo(1);
        assertThat(number("bit-counter")).isEqualTo(5);
    }

    @Test
    void theFiveShiftersShiftTheSameNumberEachInItsWay() {
        assertThat(number("shifter:logical-left")).isEqualTo(0b0101_1000);
        assertThat(number("shifter:logical-right")).isEqualTo(0b0010_0101);
        assertThat(number("shifter:arithmetic-right")).isEqualTo(0b1110_0101);
        assertThat(number("shifter:rotate-left")).isEqualTo(0b0101_1010);
        assertThat(number("shifter:rotate-right")).isEqualTo(0b1010_0101);
    }

    @Test
    void theWiringPartsWidenNarrowAndSelect() {
        assertThat(number("extender:zeros")).isEqualTo(0x008D);
        assertThat(number("extender:sign")).isEqualTo(0xFF8D);
        assertThat(number("extender:narrow")).isEqualTo(0b101);
        assertThat(number("selector")).isEqualTo(0b1010);
        click("selector:group");
        assertThat(number("selector")).isEqualTo(0b0110);
    }

    @Test
    void theTriStateLetsThroughOnlyWhileEnabledAndTheControlledInverterInverts() {
        assertThat(number("tri-state")).isEqualTo(0b1100_1010);
        assertThat(number("controlled-inverter")).isEqualTo(0b0011_0101);
        click("tri-state:enable");
        assertThat(shown("tri-state").isFullyDefined()).as("Z while disabled").isFalse();
    }

    @Test
    void thePlexersPickRouteAndEncode() {
        assertThat(number("mux")).isEqualTo(60);
        assertThat(number("demux:2")).isEqualTo(42);
        assertThat(number("demux:0")).isZero();
        assertThat(number("encoder:number")).isEqualTo(5);
        assertThat(number("encoder:valid")).isEqualTo(1);
    }

    @Test
    void theFlipFlopsAndRegistersChangeOnTheFallingEdgeOfTheirClockSwitch() {
        assertThat(number("jk:q")).isZero();
        assertThat(number("register-8")).isZero();
        for (String clock : new String[]{"jk:clock", "t:clock", "sr:clock", "d:clock", "register-8:clock",
                "register-32:clock"}) {
            click(clock);
        }
        assertThat(number("jk:q")).isEqualTo(1);
        assertThat(number("t:q")).isEqualTo(1);
        assertThat(number("sr:q")).as("forbidden: Q = 0").isZero();
        assertThat(number("sr:not-q")).as("forbidden: Q̅ = 0 too").isZero();
        assertThat(number("d:q")).isEqualTo(1);
        assertThat(number("d:not-q")).isZero();
        assertThat(number("register-8")).isEqualTo(0xA5);
        assertThat(number("register-32")).isEqualTo(0xDEAD_BEEFL);
    }

    @Test
    void theMultiBitLedsShowTheirSwitches() {
        assertThat(number("leds:4")).isEqualTo(0b1010);
        assertThat(number("leds:8")).isEqualTo(0b1100_0011);
        assertThat(number("leds:16")).isEqualTo(0xBEEF);
    }
}
