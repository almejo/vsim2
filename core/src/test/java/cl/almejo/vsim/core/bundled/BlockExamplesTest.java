package cl.almejo.vsim.core.bundled;

import cl.almejo.vsim.core.component.ElementBounds;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameContents;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.examples.CircuitBuilder;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The Mini ALU computes what its «Prueba:» text promises, and the accumulator adds its input on every clock edge. */
class BlockExamplesTest {

    private static final int TITLE_BAND_ROWS = 2;

    @Test
    void theMiniAluAndTheAccumulatorAreWiredAndExplainedCleanly() {
        ExampleChecks.assertExample(BlockExamples.miniAlu());
        ExampleChecks.assertExample(BlockExamples.accumulator());
    }

    @Test
    void fiveMinusThreeIsTwoWithNoFlag() {
        ExampleSimulation simulation = aluWith(5, 3, 0b001);
        assertThat(simulation.outputPin("Y")).isEqualTo(Value.of(4, 2));
        assertThat(flags(simulation)).isEqualTo("Z=0 N=0 C=0 V=0");
    }

    @Test
    void sevenPlusOneOverflowsIntoMinusEight() {
        ExampleSimulation simulation = aluWith(7, 1, 0b000);
        assertThat(simulation.outputPin("Y")).isEqualTo(Value.of(4, 8));
        assertThat(flags(simulation)).isEqualTo("Z=0 N=1 C=0 V=1");
    }

    @Test
    void threeMinusThreeIsZeroAndOneMinusTwoBorrows() {
        assertThat(flags(aluWith(3, 3, 0b001))).isEqualTo("Z=1 N=0 C=0 V=0");
        ExampleSimulation borrow = aluWith(1, 2, 0b001);
        assertThat(borrow.outputPin("Y")).isEqualTo(Value.of(4, 15));
        assertThat(flags(borrow)).isEqualTo("Z=0 N=1 C=1 V=0");
        assertThat(aluWith(0b1100, 0b1010, 0b100).outputPin("Y")).as("XOR").isEqualTo(Value.of(4, 0b0110));
    }

    @Test
    void theMiniAluFrameIsLockedHoldsTheAluAndHasNoWireOnItsTitleBand() {
        CircuitBuilder builder = BlockExamples.miniAlu();
        assertThat(builder.document().frames()).singleElement().satisfies(frame -> {
            assertThat(frame.title()).isEqualTo("ALU");
            assertThat(frame.locked()).isTrue();
            assertThat(FrameContents.of(builder.document(), builder.registry(), frame)).isNotEmpty();
            for (Component component : builder.document().components()) {
                Bounds bounds = ElementBounds.of(component, builder.registry());
                boolean inside = frame.encloses(bounds.x(), bounds.y(), bounds.maximumX(), bounds.maximumY());
                boolean apart = bounds.maximumX() < frame.topLeft().x()
                        || bounds.x() > frame.topLeft().x() + frame.width()
                        || bounds.maximumY() < frame.topLeft().y()
                        || bounds.y() > frame.topLeft().y() + frame.height();
                assertThat(inside || apart).as("the frame cuts %s", component.typeId()).isTrue();
            }
            assertNoWireOnTheTitleBand(builder, frame);
        });
    }

    @Test
    void theAccumulatorAddsItsInputOnEveryFallingEdgeAndClrEmptiesIt() {
        // Clock 500/500, low first: falling edges at 1000, 2000, 3000, …
        ExampleSimulation simulation = new ExampleSimulation(BlockExamples.accumulator(), Map.of());
        simulation.setInput("Sumando", Value.of(4, 3));
        simulation.runUntil(100);
        assertThat(simulation.outputPin("Total")).isEqualTo(Value.of(4, 0));
        for (int edge = 1; edge <= 3; edge++) {
            simulation.runUntil(1000L * edge + 100);
            assertThat(simulation.outputPin("Total")).as("after %d edges", edge).isEqualTo(Value.of(4, 3L * edge));
        }
        simulation.runUntil(3200);
        simulation.togglePin("CLR");
        simulation.runUntil(3300);
        assertThat(simulation.outputPin("Total")).as("CLR = 1 clears at once").isEqualTo(Value.of(4, 0));
        simulation.runUntil(4100);
        assertThat(simulation.outputPin("Total")).as("held at 0 while CLR = 1").isEqualTo(Value.of(4, 0));
        simulation.togglePin("CLR");
        simulation.runUntil(5100);
        assertThat(simulation.outputPin("Total")).as("counting again").isEqualTo(Value.of(4, 3));
    }

    @Test
    void theAccumulatorWrapsAfterFifteen() {
        ExampleSimulation simulation = new ExampleSimulation(BlockExamples.accumulator(), Map.of());
        simulation.setInput("Sumando", Value.of(4, 5));
        simulation.runUntil(4100);
        assertThat(simulation.outputPin("Total")).as("4 × 5 = 20 = 16 + 4").isEqualTo(Value.of(4, 4));
    }

    private static ExampleSimulation aluWith(long numberA, long numberB, long operation) {
        ExampleSimulation simulation = new ExampleSimulation(BlockExamples.miniAlu(), Map.of());
        simulation.setInput("A", Value.of(4, numberA));
        simulation.setInput("B", Value.of(4, numberB));
        simulation.setInput("OP", Value.of(3, operation));
        return simulation.settle();
    }

    private static String flags(ExampleSimulation simulation) {
        StringBuilder text = new StringBuilder();
        for (String flag : new String[]{"Z", "N", "C", "V"}) {
            Value value = simulation.outputPin(flag);
            text.append(text.isEmpty() ? "" : " ").append(flag).append('=')
                    .append(value.equals(Value.ONE) ? "1" : value.equals(Value.ZERO) ? "0" : value.toString());
        }
        return text.toString();
    }

    private static void assertNoWireOnTheTitleBand(CircuitBuilder builder, Frame frame) {
        // a press on a title band grabs the frame, so a wire there could not be selected or extended
        int left = frame.topLeft().x();
        int right = left + frame.width();
        int top = frame.topLeft().y();
        int bottom = top + TITLE_BAND_ROWS;
        for (Wire wire : builder.document().wires()) {
            boolean apart = Math.max(wire.start().x(), wire.end().x()) < left
                    || Math.min(wire.start().x(), wire.end().x()) > right
                    || Math.max(wire.start().y(), wire.end().y()) < top
                    || Math.min(wire.start().y(), wire.end().y()) > bottom;
            assertThat(apart).as("%s touches the title band", wire).isTrue();
        }
    }
}
