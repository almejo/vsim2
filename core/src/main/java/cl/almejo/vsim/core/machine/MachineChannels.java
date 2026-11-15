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
 * per output. They are not probes: they live in no document, file or history; the root editor adds them to the
 * channels it gives the simulation, and the machine tab's strip reads them by id. Every id starts with
 * {@code machine:}, which no probe id uses, and names the copy by its flat path ({@code machine:w/m:in:x}).
 */
public final class MachineChannels {

    /** The start of every machine channel id. */
    public static final String PREFIX = "machine:";

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

    /**
     * The channels of one copy, its pins found by name in its native type (the hidden state pin is
     * {@link StateMachineTypes#STATE_PIN_NAME}), so they follow whatever pinout its definition has.
     */
    private static List<MachineChannel> channelsOf(FlatComponent copy, StateMachine machine,
                                                   Map<FlatPinRef, String> netOfPin) {
        List<MachineChannel> channels = new ArrayList<>();
        channels.add(new MachineChannel(stateChannelId(copy.id()),
                netOf(netOfPin, copy, StateMachineTypes.STATE_PIN_NAME)));
        for (String input : machine.inputNames()) {
            channels.add(new MachineChannel(inputChannelId(copy.id(), input), netOf(netOfPin, copy, input)));
        }
        for (String output : machine.outputNames()) {
            channels.add(new MachineChannel(outputChannelId(copy.id(), output), netOf(netOfPin, copy, output)));
        }
        return channels;
    }

    private static String netOf(Map<FlatPinRef, String> netOfPin, FlatComponent copy, String pinName) {
        OptionalInt pinIndex = copy.type().pinIndexOf(pinName);
        return pinIndex.isEmpty() ? "" : netOfPin.getOrDefault(new FlatPinRef(copy.id(), pinIndex.getAsInt()), "");
    }

    private static Map<FlatPinRef, String> netsByPin(FlatCircuit circuit) {
        Map<FlatPinRef, String> netOfPin = new HashMap<>();
        for (FlatNet net : circuit.nets()) {
            net.pins().forEach(pin -> netOfPin.put(pin, net.id()));
        }
        return netOfPin;
    }
}
