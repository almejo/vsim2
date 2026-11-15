package cl.almejo.vsim.core.machine;

import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionPin;
import cl.almejo.vsim.core.machine.MachineChannels.MachineChannel;
import cl.almejo.vsim.core.simulation.FlatCircuit;
import cl.almejo.vsim.core.simulation.FlatComponent;
import cl.almejo.vsim.core.simulation.FlatNet;
import cl.almejo.vsim.core.simulation.FlatPinRef;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Every flat machine copy gets one channel for its state, one per input and one per output. */
class MachineChannelsTest {

    private final ComponentType detector = StateMachineTypes.typeOf(MachineDefinitions.create("maquina-1",
            "Máquina 1").withMachine(MachineExamples.twoOnesDetector()));

    @Test
    void aCopyHasItsStateItsInputsAndItsOutputsInThatOrder() {
        FlatCircuit circuit = new FlatCircuit(List.of(new FlatComponent("c1", detector, Map.of())), List.of(
                net("n1", new FlatPinRef("c1", 0)),
                net("n2", new FlatPinRef("c1", 3)),
                stateNet("c1")));

        assertThat(MachineChannels.of(circuit)).containsExactly(
                new MachineChannel("machine:c1:state", "c1#state"),
                new MachineChannel("machine:c1:in:x", "n1"),
                new MachineChannel("machine:c1:out:Z", "n2"));
    }

    @Test
    void anUnconnectedPinRecordsAGap() {
        FlatCircuit circuit = new FlatCircuit(List.of(new FlatComponent("c1", detector, Map.of())),
                List.of(stateNet("c1")));

        assertThat(MachineChannels.of(circuit)).contains(new MachineChannel("machine:c1:in:x", ""),
                new MachineChannel("machine:c1:out:Z", ""));
    }

    @Test
    void aNestedCopyIsNamedByItsPathAndReadsTheRootNet() {
        FlatNet merged = new FlatNet("n1", List.of(new FlatPinRef("w/m", 0)), 1, false, List.of("w/n4"));
        FlatCircuit circuit = new FlatCircuit(List.of(new FlatComponent("w/m", detector, Map.of())),
                List.of(merged, stateNet("w/m")));

        assertThat(MachineChannels.of(circuit)).contains(new MachineChannel("machine:w/m:state", "w/m#state"),
                new MachineChannel("machine:w/m:in:x", "n1"));
        assertThat(circuit.netOfPin("w/m", 0)).contains("n1");
        assertThat(circuit.netOfPin("w/m", 1)).isEmpty();
    }

    @Test
    void otherPartsHaveNoChannels() {
        ComponentType andGate = StandardComponentTypes.registry().require(StandardComponentTypes.AND);
        FlatCircuit circuit = new FlatCircuit(List.of(new FlatComponent("g", andGate, Map.of())), List.of());

        assertThat(MachineChannels.of(circuit)).isEmpty();
        assertThat(StateMachineTypes.machineOf(andGate)).isEmpty();
        assertThat(StateMachineTypes.machineOf(detector)).contains(MachineExamples.twoOnesDetector());
    }

    @Test
    void copiesPastTheCapRecordNothingAndNoCopyIsCutInHalf() {
        int perCopy = 3;
        int copies = MachineChannels.MAXIMUM_CHANNELS / perCopy + 2;
        List<FlatComponent> components = new ArrayList<>();
        List<FlatNet> nets = new ArrayList<>();
        for (int copy = 0; copy < copies; copy++) {
            components.add(new FlatComponent("c" + copy, detector, Map.of()));
            nets.add(stateNet("c" + copy));
        }

        List<MachineChannel> channels = MachineChannels.of(new FlatCircuit(components, nets));

        assertThat(channels).hasSize(MachineChannels.MAXIMUM_CHANNELS / perCopy * perCopy);
        assertThat(channels.getLast().channelId()).isEqualTo(
                MachineChannels.outputChannelId("c" + (MachineChannels.MAXIMUM_CHANNELS / perCopy - 1), "Z"));
    }

    @Test
    void theBehaviourDrivesTheStateOnTheHiddenPinOfItsTypeWhateverThePinout() {
        ComponentDefinition canonical = MachineDefinitions.regenerated(MachineDefinitions.create("maquina-1",
                "Máquina 1").withMachine(MachineExamples.twoOnesDetector()));
        List<DefinitionPin> pinout = new ArrayList<>(canonical.pinout());
        pinout.removeLast(); // a pinout that is not the canonical one (an older file, hito 6's buses)
        ComponentType type = StateMachineTypes.typeOf(canonical.withGeometry(canonical.size(), pinout,
                canonical.appearance()));
        Map<Integer, Value> driven = new HashMap<>();
        BehaviorContext context = new BehaviorContext() {
            @Override
            public Value input(int pinIndex) {
                return Value.ZERO;
            }

            @Override
            public void drive(int pinIndex, Value value, long delay) {
                driven.put(pinIndex, value);
            }

            @Override
            public void schedule(long delay, Runnable action) {
            }

            @Override
            public long now() {
                return 0;
            }
        };

        type.behaviorFactory().create(Map.of()).initialize(context);

        int statePin = type.pinIndexOf(StateMachineTypes.STATE_PIN_NAME).orElseThrow();
        assertThat(statePin).isEqualTo(pinout.size());
        assertThat(driven).containsKey(statePin);
        assertThat(driven.get(statePin).width()).isEqualTo(StateMachineTypes.stateWidth(MachineExamples.twoOnesDetector()));
    }

    @Test
    void theIdsStartWithMachineAndNameTheCopy() {
        assertThat(MachineChannels.stateChannelId("w/m")).isEqualTo("machine:w/m:state");
        assertThat(MachineChannels.inputChannelId("m", "x")).isEqualTo("machine:m:in:x");
        assertThat(MachineChannels.outputChannelId("m", "Z")).isEqualTo("machine:m:out:Z");
    }

    private static FlatNet net(String id, FlatPinRef pin) {
        return new FlatNet(id, List.of(pin), 1, false);
    }

    private static FlatNet stateNet(String flatId) {
        return new FlatNet(StateMachineTypes.stateNetId(flatId), List.of(new FlatPinRef(flatId, 4)), 2, false);
    }
}
