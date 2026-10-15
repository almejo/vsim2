package cl.almejo.vsim.core.bundled;

import cl.almejo.vsim.core.component.ElementBounds;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameContents;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.examples.CircuitBuilder;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The bus carries every byte to the LEDs and the display; the ALU computes its four operations and flags. */
class BusAndAluExamplesTest {

    private static final int TITLE_BAND_ROWS = 2;

    @Test
    void theBusAndTheAluAreWiredAndExplainedCleanly() {
        ExampleChecks.assertExample(BusAndAluExamples.bus());
        ExampleChecks.assertCleanBody(BusAndAluExamples.aluBitBodyBuilder());
        ExampleChecks.assertExample(BusAndAluExamples.alu());
    }

    @Test
    void everyByteReachesTheLedsAndTheDoubleDisplay() {
        CircuitBuilder builder = BusAndAluExamples.bus();
        for (int number = 0; number < 256; number++) {
            Map<String, String> switches = new HashMap<>();
            for (int bit = 0; bit < 8; bit++) {
                switches.put("b" + bit, String.valueOf(number >> bit & 1));
            }
            ExampleSimulation simulation = new ExampleSimulation(builder, switches).settle();
            assertThat(simulation.number("b", 8)).as("LEDs for %d", number).isEqualTo(number);
            assertThat(simulation.display(0)).as("display for %d", number).isEqualTo(Value.of(8, number));
        }
    }

    @Test
    void theAluBitComponentHasItsPinsInRippleOrderAndEmbedsTheFullAdder() {
        DefinitionBundle bundle = BusAndAluExamples.aluBitDefinition();
        assertThat(bundle.root().id()).isEqualTo(BusAndAluExamples.ALU_BIT_ID);
        assertThat(bundle.root().name()).isEqualTo("Bit de ALU");
        assertThat(bundle.root().pinout()).extracting(pin -> pin.spec().name())
                .containsExactly("Cin", "A", "B", "S1", "S0", "R", "Cout");
        assertThat(bundle.dependencies()).extracting(ComponentDefinition::id)
                .containsExactly(CombinationalExamples.FULL_ADDER_ID);
        CircuitBuilder alu = BusAndAluExamples.alu();
        assertThat(alu.document().definitions()).extracting(ComponentDefinition::id)
                .containsExactlyInAnyOrder(BusAndAluExamples.ALU_BIT_ID, CombinationalExamples.FULL_ADDER_ID);
        assertThat(alu.document().components()).filteredOn(component -> component.typeId()
                .equals(SubcircuitTypes.typeIdOf(BusAndAluExamples.ALU_BIT_ID))).hasSize(4);
    }

    @Test
    void theAluAddsAndsOrsAndXorsAndRaisesItsFlags() {
        CircuitBuilder builder = BusAndAluExamples.alu();
        List<Integer> samples = List.of(0, 1, 2, 5, 7, 8, 9, 15);
        for (int operation = 0; operation < 4; operation++) {
            for (int numberA : samples) {
                for (int numberB : samples) {
                    for (int carry = 0; carry <= 1; carry++) {
                        Map<String, String> switches = new HashMap<>();
                        for (int bit = 0; bit < 4; bit++) {
                            switches.put("A" + bit, String.valueOf(numberA >> bit & 1));
                            switches.put("B" + bit, String.valueOf(numberB >> bit & 1));
                        }
                        switches.put("Cin", String.valueOf(carry));
                        switches.put("S1", String.valueOf(operation >> 1));
                        switches.put("S0", String.valueOf(operation & 1));
                        int full = switch (operation) {
                            case 0 -> numberA + numberB + carry;
                            case 1 -> numberA & numberB;
                            case 2 -> numberA | numberB;
                            default -> numberA ^ numberB;
                        };
                        int result = full & 15;
                        String context = "S1S0=" + operation + " A=" + numberA + " B=" + numberB + " Cin=" + carry;
                        ExampleSimulation simulation = new ExampleSimulation(builder, switches).settle();
                        assertThat(simulation.display(0)).as(context).isEqualTo(Value.of(4, result));
                        assertThat(simulation.led("Cero")).as(context + " Cero").isEqualTo(Value.of(result == 0));
                        assertThat(simulation.led("Negativo")).as(context + " Negativo")
                                .isEqualTo(Value.of(result >= 8));
                        assertThat(simulation.led("Acarreo")).as(context + " Acarreo")
                                .isEqualTo(Value.of(operation == 0 && full > 15));
                    }
                }
            }
        }
    }

    @Test
    void theAluFramesAreLockedHoldSomethingAndNeverCutAComponentOrOverlap() {
        CircuitBuilder alu = BusAndAluExamples.alu();
        List<Frame> frames = alu.document().frames();
        assertThat(frames).extracting(Frame::title)
                .containsExactly("Sumador y lógica", "Selector de operación", "Banderas");
        assertThat(frames).allMatch(Frame::locked);
        for (Frame frame : frames) {
            assertThat(FrameContents.of(alu.document(), alu.registry(), frame)).as(frame.title()).isNotEmpty();
            for (Component component : alu.document().components()) {
                Bounds bounds = ElementBounds.of(component, alu.registry());
                boolean inside = frame.encloses(bounds.x(), bounds.y(), bounds.maximumX(), bounds.maximumY());
                boolean apart = bounds.maximumX() < frame.topLeft().x()
                        || bounds.x() > frame.topLeft().x() + frame.width()
                        || bounds.maximumY() < frame.topLeft().y()
                        || bounds.y() > frame.topLeft().y() + frame.height();
                assertThat(inside || apart).as("%s cuts %s", frame.title(), component.typeId()).isTrue();
            }
        }
        for (Frame first : frames) {
            for (Frame second : frames) {
                if (first != second) {
                    boolean apart = first.topLeft().x() + first.width() < second.topLeft().x()
                            || second.topLeft().x() + second.width() < first.topLeft().x()
                            || first.topLeft().y() + first.height() < second.topLeft().y()
                            || second.topLeft().y() + second.height() < first.topLeft().y();
                    assertThat(apart).as("%s and %s overlap", first.title(), second.title()).isTrue();
                }
            }
        }
    }

    @Test
    void noAluWireCrossesOrRunsAlongATitleBand() {
        // a press on a title band grabs the frame, so a wire there could not be selected or extended
        CircuitBuilder alu = BusAndAluExamples.alu();
        for (Frame frame : alu.document().frames()) {
            int left = frame.topLeft().x();
            int right = left + frame.width();
            int top = frame.topLeft().y();
            int bottom = top + TITLE_BAND_ROWS;
            for (Wire wire : alu.document().wires()) {
                int wireLeft = Math.min(wire.start().x(), wire.end().x());
                int wireRight = Math.max(wire.start().x(), wire.end().x());
                int wireTop = Math.min(wire.start().y(), wire.end().y());
                int wireBottom = Math.max(wire.start().y(), wire.end().y());
                boolean apart = wireRight < left || wireLeft > right || wireBottom < top || wireTop > bottom;
                assertThat(apart).as("%s touches the title band of %s", wire, frame.title()).isTrue();
            }
        }
    }
}
