package cl.almejo.vsim.core.machine;

import cl.almejo.vsim.core.simulation.FlatCircuit;
import cl.almejo.vsim.core.simulation.FlatComponent;
import cl.almejo.vsim.core.simulation.FlatNet;
import cl.almejo.vsim.core.simulation.FlatPinRef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The waveform channels every machine copy records from «Reiniciar» on, whether a panel shows them or not (hito 2,
 * decision 2): one for its state (the hidden state pin, {@link StateMachineTypes#stateNetId}), one per input and one
 * per output, or one for a side shown «Como un bus» ({@code machine:w/m:in}), whose bit k is the k-th name
 * ({@link #inputSource}, {@link #outputSource}). They are not probes: they live in no document, file or history;
 * the root editor adds them to the channels it gives the simulation, and the machine tab's strip reads them by id.
 * Every id starts with {@code machine:}, which no probe id uses, and names the copy by its flat path
 * ({@code machine:w/m:in:x}).
 */
public final class MachineChannels {

    /** The start of every machine channel id. */
    public static final String PREFIX = "machine:";

    /** What follows the copy in the id of a bus channel. */
    private static final String INPUT_BUS_SUFFIX = "in";
    private static final String OUTPUT_BUS_SUFFIX = "out";

    /**
     * The chunks of history a machine channel keeps ({@link cl.almejo.vsim.core.waveform.ChannelTrace#CHUNK_SIZE}
     * events each), fewer than a probe's: they are recorded for every copy whether a panel shows them or not.
     */
    public static final int HISTORY_CHUNKS = 8;

    /**
     * The most machine channels one circuit records; copies past it (in the circuit's order, whole copies only)
     * record nothing, and their strip says so.
     */
    public static final int MAXIMUM_CHANNELS = 512;

    private MachineChannels() {
    }

    /**
     * A channel and the net it records; {@code netId} is {@code ""} for a pin in no net, which records a gap.
     */
    public record MachineChannel(String channelId, String netId) {

        public MachineChannel {
            Objects.requireNonNull(channelId, "channelId");
            Objects.requireNonNull(netId, "netId");
        }
    }

    /**
     * The channels of every machine copy of {@code circuit}, copy by copy in the circuit's order: the state, the
     * inputs, then the outputs, each in pin order. A copy whose channels would pass {@link #MAXIMUM_CHANNELS} is left
     * out, and so are the copies after it.
     */
    public static List<MachineChannel> of(FlatCircuit circuit) {
        Objects.requireNonNull(circuit, "circuit");
        Map<FlatPinRef, String> netOfPin = null;
        List<MachineChannel> channels = new ArrayList<>();
        for (FlatComponent component : circuit.components()) {
            Optional<StateMachine> machine = StateMachineTypes.machineOf(component.type());
            if (machine.isEmpty()) {
                continue;
            }
            if (netOfPin == null) {
                netOfPin = netsByPin(circuit);
            }
            List<MachineChannel> copyChannels = channelsOf(component, machine.get(), netOfPin);
            if (channels.size() + copyChannels.size() > MAXIMUM_CHANNELS) {
                break;
            }
            channels.addAll(copyChannels);
        }
        return List.copyOf(channels);
    }

    public static String stateChannelId(String flatId) {
        return PREFIX + flatId + ":state";
    }

    public static String inputChannelId(String flatId, String name) {
        return PREFIX + flatId + ":in:" + name;
    }

    public static String outputChannelId(String flatId, String name) {
        return PREFIX + flatId + ":out:" + name;
    }

    /** The channel of the input bus of a copy whose inputs are «Como un bus». */
    public static String inputBusChannelId(String flatId) {
        return PREFIX + flatId + ":" + INPUT_BUS_SUFFIX;
    }

    /** The channel of the output bus of a copy whose outputs are «Como un bus». */
    public static String outputBusChannelId(String flatId) {
        return PREFIX + flatId + ":" + OUTPUT_BUS_SUFFIX;
    }

    /**
     * Where a name's values are recorded: the channel {@code machine:<copy>:<channelSuffix>} and, when that channel is
     * a bus, the name's bit in it.
     */
    public record ChannelSlice(String channelSuffix, OptionalInt bit) {

        public ChannelSlice {
            Objects.requireNonNull(channelSuffix, "channelSuffix");
            Objects.requireNonNull(bit, "bit");
        }

        /** The channel's id for the copy at flat path {@code flatId}. */
        public String channelId(String flatId) {
            return PREFIX + flatId + ":" + channelSuffix;
        }
    }

    /**
     * Where input {@code name} of {@code machine} is recorded: its own channel, or its bit of the input bus.
     *
     * @throws IllegalArgumentException when the machine has no such input
     */
    public static ChannelSlice inputSource(StateMachine machine, String name) {
        return source(machine, MachinePinEdits.Side.INPUT, name, "in:", INPUT_BUS_SUFFIX);
    }

    /**
     * Where output {@code name} of {@code machine} is recorded: its own channel, or its bit of the output bus.
     *
     * @throws IllegalArgumentException when the machine has no such output
     */
    public static ChannelSlice outputSource(StateMachine machine, String name) {
        return source(machine, MachinePinEdits.Side.OUTPUT, name, "out:", OUTPUT_BUS_SUFFIX);
    }

    private static ChannelSlice source(StateMachine machine, MachinePinEdits.Side side, String name,
                                       String ownPrefix, String busSuffix) {
        Objects.requireNonNull(machine, "machine");
        List<String> names = side == MachinePinEdits.Side.INPUT ? machine.inputNames() : machine.outputNames();
        int index = names.indexOf(name);
        if (index < 0) {
            throw new IllegalArgumentException("the machine has no " + side + " " + name);
        }
        return MachineBody.isBus(machine, side) ? new ChannelSlice(busSuffix, OptionalInt.of(index))
                : new ChannelSlice(ownPrefix + name, OptionalInt.empty());
    }

    /**
     * The channels of one copy, its pins found by name and direction in its native type ({@link MachinePinLayout}),
     * so they follow whatever pinout its definition has: the state, then the inputs (or the input bus), then the
     * outputs (or the output bus).
     */
    private static List<MachineChannel> channelsOf(FlatComponent copy, StateMachine machine,
                                                   Map<FlatPinRef, String> netOfPin) {
        MachinePinLayout layout = MachinePinLayout.of(copy.type(), machine);
        List<MachineChannel> channels = new ArrayList<>();
        channels.add(new MachineChannel(stateChannelId(copy.id()), netOf(netOfPin, copy, layout.state())));
        if (layout.inputBus().isPresent()) {
            channels.add(new MachineChannel(inputBusChannelId(copy.id()),
                    netOf(netOfPin, copy, layout.inputBus().getAsInt())));
        }
        for (String input : machine.inputNames()) {
            Integer pinIndex = layout.inputPins().get(input);
            if (pinIndex != null) {
                channels.add(new MachineChannel(inputChannelId(copy.id(), input), netOf(netOfPin, copy, pinIndex)));
            }
        }
        if (layout.outputBus().isPresent()) {
            channels.add(new MachineChannel(outputBusChannelId(copy.id()),
                    netOf(netOfPin, copy, layout.outputBus().getAsInt())));
        }
        for (String output : machine.outputNames()) {
            Integer pinIndex = layout.outputPins().get(output);
            if (pinIndex != null) {
                channels.add(new MachineChannel(outputChannelId(copy.id(), output),
                        netOf(netOfPin, copy, pinIndex)));
            }
        }
        return channels;
    }

    private static String netOf(Map<FlatPinRef, String> netOfPin, FlatComponent copy, int pinIndex) {
        return netOfPin.getOrDefault(new FlatPinRef(copy.id(), pinIndex), "");
    }

    private static Map<FlatPinRef, String> netsByPin(FlatCircuit circuit) {
        Map<FlatPinRef, String> netOfPin = new HashMap<>();
        for (FlatNet net : circuit.nets()) {
            net.pins().forEach(pin -> netOfPin.put(pin, net.id()));
        }
        return netOfPin;
    }
}
