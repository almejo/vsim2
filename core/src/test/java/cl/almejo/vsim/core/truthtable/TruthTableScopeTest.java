package cl.almejo.vsim.core.truthtable;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.netlist.PinRef;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TruthTableScopeTest {

    private static Preparation.Ready ready(TruthTableCircuits circuits, Set<ElementId> selection) {
        Preparation preparation = TruthTableScope.prepare(circuits.document, selection, circuits.registry);
        assertThat(preparation).isInstanceOf(Preparation.Ready.class);
        return (Preparation.Ready) preparation;
    }

    private static Preparation.Refused refused(TruthTableCircuits circuits) {
        Preparation preparation = TruthTableScope.prepare(circuits.document, Set.of(), circuits.registry);
        assertThat(preparation).isInstanceOf(Preparation.Refused.class);
        return (Preparation.Refused) preparation;
    }

    @Test
    void timeDiagramsAreLeftOutOfTheEvaluatedCircuit() {
        TruthTableCircuits circuits = TruthTableCircuits.halfAdder();
        circuits.place(StandardComponentTypes.TIME_DIAGRAM, 40, 0, "");

        Preparation.Ready ready = ready(circuits, Set.of());

        assertThat(ready.circuit().components()).extracting(component -> component.type().id())
                .as("a time diagram samples itself forever: it would keep every row busy")
                .doesNotContain(StandardComponentTypes.TIME_DIAGRAM)
                .contains(StandardComponentTypes.XOR2);
        assertThat(ready.inputs()).extracting(Column::name).containsExactly("A", "B");
    }

    @Test
    void theWholeCircuitGivesItsSwitchesAndLedsAsColumns() {
        Preparation.Ready ready = ready(TruthTableCircuits.halfAdder(), Set.of());

        assertThat(ready.inputs()).extracting(Column::name).containsExactly("A", "B");
        assertThat(ready.outputs()).extracting(Column::name).containsExactly("S", "C");
        assertThat(ready.inputBits()).isEqualTo(2);
        assertThat(ready.rowCount()).isEqualTo(4);
        assertThat(ready.hasFeedback()).isFalse();
        assertThat(ready.outputNets()).as("each LED reads the net of its gate output")
                .isEqualTo(Map.of("c13", "n:p:c3#2", "c14", "n:p:c4#2"));
        assertThat(ready.circuit().components()).hasSize(6);
    }

    @Test
    void columnsGoTopToBottomThenLeftToRightAndUnlabeledOnesGetFreeNames() {
        TruthTableCircuits circuits = new TruthTableCircuits();
        circuits.place(StandardComponentTypes.SWITCH, 0, 10, "");
        circuits.place(StandardComponentTypes.SWITCH, 10, 0, "");
        circuits.place(StandardComponentTypes.SWITCH, 0, 0, " B ");
        circuits.place(StandardComponentTypes.LED, 20, 10, "");
        circuits.place(StandardComponentTypes.LED, 20, 0, "Y1");

        Preparation.Ready ready = ready(circuits, Set.of());

        assertThat(ready.inputs()).containsExactly(new Column("B", "c3", 1), new Column("A", "c2", 1),
                new Column("C", "c1", 1));
        assertThat(ready.outputs()).containsExactly(new Column("Y1", "c5", 1), new Column("Y2", "c4", 1));
    }

    @Test
    void defaultLetterNamesContinueLikeSpreadsheetColumns() {
        assertThat(TruthTableScope.letters(1)).isEqualTo("A");
        assertThat(TruthTableScope.letters(26)).isEqualTo("Z");
        assertThat(TruthTableScope.letters(27)).isEqualTo("AA");
        assertThat(TruthTableScope.letters(53)).isEqualTo("BA");
    }

    @Test
    void aSelectionLeavesTheRestOfTheCircuitOut() {
        TruthTableCircuits circuits = TruthTableCircuits.fullAdder();
        Set<ElementId> adder = circuits.ids();
        circuits.addUnrelatedPart();

        Preparation.Ready selected = ready(circuits, adder);
        Preparation.Ready whole = ready(circuits, Set.of());

        assertThat(selected.inputs()).extracting(Column::name).containsExactly("A", "B", "Cin");
        assertThat(selected.outputs()).extracting(Column::name).containsExactly("S", "Cout");
        assertThat(whole.inputs()).extracting(Column::name).containsExactly("A", "B", "Cin", "Z");
        assertThat(whole.outputs()).extracting(Column::name).containsExactly("S", "Cout", "W");
        assertThat(circuits.document.components()).hasSize(12);
    }

    @Test
    void aSingleSelectedInstanceGivesItsDefinitionsPinsAsColumns() {
        TruthTableCircuits circuits = new TruthTableCircuits();
        circuits.place(StandardComponentTypes.SWITCH, 0, 0, "X");
        Component instance = circuits.inverterInstance();

        Preparation.Ready ready = ready(circuits, Set.of(instance.id()));

        assertThat(TruthTableScope.singleInstance(circuits.document, Set.of(instance.id())))
                .hasValueSatisfying(definition -> assertThat(definition.name()).isEqualTo("Inversor"));
        assertThat(ready.inputs()).containsExactly(new Column("a", "c1", 1));
        assertThat(ready.outputs()).containsExactly(new Column("y", "c3", 1));
        assertThat(ready.evaluated().components()).hasSize(3);
    }

    @Test
    void theLiveInputsAreTheInputComponentsOrTheInstancesInputPins() {
        Preparation.Ready whole = ready(TruthTableCircuits.halfAdder(), Set.of());
        TruthTableCircuits withInstance = new TruthTableCircuits();
        withInstance.place(StandardComponentTypes.SWITCH, 0, 0, "X");
        Component instance = withInstance.inverterInstance();

        Preparation.Ready single = ready(withInstance, Set.of(instance.id()));

        assertThat(whole.liveInputPins()).as("the switches A and B themselves")
                .containsExactly(new PinRef(new ElementId("c1"), 0), new PinRef(new ElementId("c2"), 0));
        assertThat(single.liveInputPins()).as("pin «a» of the instance, not the body's input pin")
                .containsExactly(new PinRef(instance.id(), 0));
    }

    @Test
    void onlyTopLevelSwitchesCanBeSetFromARow() {
        TruthTableCircuits withInstance = new TruthTableCircuits();
        withInstance.place(StandardComponentTypes.SWITCH, 0, 0, "X");
        Component instance = withInstance.inverterInstance();
        TruthTableCircuits adder = TruthTableCircuits.fullAdder();

        assertThat(ready(adder, Set.of()).inputsAreTopLevelSwitches()).isTrue();
        assertThat(ready(adder, adder.ids()).inputsAreTopLevelSwitches()).as("a selection").isTrue();
        assertThat(ready(TruthTableCircuits.busPassThrough(), Set.of()).inputsAreTopLevelSwitches())
                .as("an input pin").isFalse();
        assertThat(ready(withInstance, Set.of(instance.id())).inputsAreTopLevelSwitches())
                .as("a single instance").isFalse();
    }

    @Test
    void aBusInputPinCountsItsWidthInBits() {
        Preparation.Ready ready = ready(TruthTableCircuits.busPassThrough(), Set.of());

        assertThat(ready.inputs()).containsExactly(new Column("D", "c1", 2));
        assertThat(ready.outputs()).containsExactly(new Column("Q", "c2", 2));
        assertThat(ready.rowCount()).isEqualTo(4);
    }

    @Test
    void feedbackWithoutFlipFlopsIsPreparedWithAWarning() {
        assertThat(ready(TruthTableCircuits.norLatch(), Set.of()).hasFeedback()).isTrue();
        assertThat(ready(TruthTableCircuits.gatedRing(), Set.of()).hasFeedback()).isTrue();
    }

    @Test
    void aCircuitWithoutInputsOrOutputsIsRefused() {
        TruthTableCircuits onlyLed = new TruthTableCircuits();
        onlyLed.place(StandardComponentTypes.LED, 0, 0, "");
        TruthTableCircuits onlySwitch = new TruthTableCircuits();
        onlySwitch.place(StandardComponentTypes.SWITCH, 0, 0, "");

        assertThat(refused(onlyLed)).isEqualTo(new Preparation.Refused(Preparation.Reason.NO_INPUTS, 0));
        assertThat(refused(onlySwitch)).isEqualTo(new Preparation.Refused(Preparation.Reason.NO_OUTPUTS, 1));
        assertThat(refused(new TruthTableCircuits()).reason()).isEqualTo(Preparation.Reason.NO_INPUTS);
    }

    /** {@code count} one-bit switches in a column and one LED, nothing wired. */
    private static TruthTableCircuits switches(int count) {
        TruthTableCircuits circuits = new TruthTableCircuits();
        for (int index = 0; index < count; index++) {
            circuits.place(StandardComponentTypes.SWITCH, 0, index * 4, "");
        }
        circuits.place(StandardComponentTypes.LED, 20, 0, "");
        return circuits;
    }

    /** One input pin {@code width} bits wide and one LED, nothing wired. */
    private static TruthTableCircuits inputPin(int width) {
        TruthTableCircuits circuits = new TruthTableCircuits();
        circuits.place(InterfacePins.INPUT_PIN, 0, 0, "", Map.of(InterfacePins.WIDTH, width));
        circuits.place(StandardComponentTypes.LED, 20, 0, "");
        return circuits;
    }

    @Test
    void tenInputBitsNeedNoConfirmation() {
        Preparation.Ready tenSwitches = ready(switches(10), Set.of());
        Preparation.Ready tenBitPin = ready(inputPin(10), Set.of());

        assertThat(tenSwitches.rowCount()).isEqualTo(1024);
        assertThat(tenSwitches.needsConfirmation()).isFalse();
        assertThat(tenBitPin.rowCount()).isEqualTo(1024);
        assertThat(tenBitPin.needsConfirmation()).isFalse();
    }

    @Test
    void sixteenInputBitsAreReadyButNeedConfirmation() {
        Preparation.Ready elevenSwitches = ready(switches(11), Set.of());
        Preparation.Ready sixteenBitPin = ready(inputPin(16), Set.of());

        assertThat(elevenSwitches.inputBits()).isEqualTo(11);
        assertThat(elevenSwitches.rowCount()).isEqualTo(2048);
        assertThat(elevenSwitches.needsConfirmation()).isTrue();
        assertThat(sixteenBitPin.rowCount()).isEqualTo(65_536);
        assertThat(sixteenBitPin.needsConfirmation()).isTrue();
    }

    @Test
    void seventeenInputBitsAreTooMany() {
        TruthTableCircuits circuits = inputPin(16);
        circuits.place(StandardComponentTypes.SWITCH, 0, 10, "");

        assertThat(refused(circuits)).isEqualTo(new Preparation.Refused(Preparation.Reason.TOO_MANY_INPUT_BITS, 17));
        assertThat(refused(switches(17)).inputBits()).isEqualTo(17);
    }

    @Test
    void flipFlopsAndClocksAreRefusedEvenInsideAUserComponent() {
        TruthTableCircuits withFlipFlop = TruthTableCircuits.halfAdder();
        withFlipFlop.place(StandardComponentTypes.FLIP_FLOP_DATA, 40, 40, "");
        TruthTableCircuits withTemplate = TruthTableCircuits.halfAdder();
        withTemplate.clockTemplateInstance();
        TruthTableCircuits onlyClock = new TruthTableCircuits();
        onlyClock.place(StandardComponentTypes.CLOCK, 0, 0, "");

        assertThat(refused(withFlipFlop)).isEqualTo(new Preparation.Refused(Preparation.Reason.SEQUENTIAL, 2));
        assertThat(refused(withTemplate).reason()).isEqualTo(Preparation.Reason.SEQUENTIAL);
        assertThat(refused(onlyClock).reason()).as("checked before the missing inputs")
                .isEqualTo(Preparation.Reason.SEQUENTIAL);
    }

    @Test
    void theLiveDocumentIsOnlyRead() {
        TruthTableCircuits circuits = TruthTableCircuits.fullAdder();
        long revision = circuits.document.revision();
        int elements = circuits.document.elements().size();

        Preparation.Ready ready = ready(circuits, Set.of());

        assertThat(circuits.document.revision()).isEqualTo(revision);
        assertThat(circuits.document.elements()).hasSize(elements);
        assertThat(ready.evaluated()).isNotSameAs(circuits.document);
    }
}
