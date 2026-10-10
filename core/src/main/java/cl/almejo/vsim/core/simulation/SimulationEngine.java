package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.core.waveform.WaveformSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Event-driven simulator. Single-threaded: call it only from one thread
 * (see {@link SimulationThread}). State of unchanged components survives {@link #update}.
 */
public final class SimulationEngine {

    private final Scheduler scheduler = new Scheduler();
    private Map<String, ComponentInstance> instances = new LinkedHashMap<>();
    private Map<String, RuntimeNet> nets = new LinkedHashMap<>();
    private long revision;
    private long token;
    private final WaveformRecorder recorder = new WaveformRecorder();
    /** Alias id → the root net that carries it (FlatNet#aliases), rebuilt with the nets. */
    private Map<String, RuntimeNet> aliasRoots = Map.of();

    Scheduler scheduler() {
        return scheduler;
    }

    public long now() {
        return scheduler.now();
    }

    public long revision() {
        return revision;
    }

    /**
     * Replaces the simulated circuit, keeping the state of unchanged components.
     * An invalid circuit throws IllegalArgumentException and leaves the engine untouched.
     */
    public void update(FlatCircuit circuit) {
        validate(circuit);
        Map<String, ComponentInstance> nextInstances = new LinkedHashMap<>();
        List<ComponentInstance> created = new ArrayList<>();
        for (FlatComponent component : circuit.components()) {
            ComponentInstance existing = instances.get(component.id());
            if (existing != null && existing.matches(component)) {
                nextInstances.put(component.id(), existing);
            } else {
                ComponentInstance instance = new ComponentInstance(this, component);
                nextInstances.put(component.id(), instance);
                created.add(instance);
            }
        }
        for (ComponentInstance previous : instances.values()) {
            if (nextInstances.get(previous.id) != previous) {
                previous.removed = true;
            }
        }
        instances = nextInstances;
        try {
            rewireAndInitialize(circuit, created);
        } catch (RuntimeException exception) {
            created.forEach(instance -> instance.initializationFailed = true);
            throw exception;
        }
        token = circuit.token();
        revision++;
        recorder.rebind(this::netWithId, nets.values(), now());
    }

    private void rewireAndInitialize(FlatCircuit circuit, List<ComponentInstance> created) {
        for (ComponentInstance instance : instances.values()) {
            for (PinState pin : instance.pins) {
                pin.net = null;
            }
        }

        Map<String, RuntimeNet> previousNets = nets;
        Map<String, RuntimeNet> nextNets = new LinkedHashMap<>();
        Map<String, RuntimeNet> nextAliases = new HashMap<>();
        for (FlatNet flatNet : circuit.nets()) {
            RuntimeNet net = new RuntimeNet(flatNet.id(), flatNet.width(), flatNet.widthMismatch());
            net.aliases = flatNet.aliases();
            for (String alias : flatNet.aliases()) {
                nextAliases.put(alias, net);
            }
            RuntimeNet previous = previousNets.get(flatNet.id());
            if (previous != null) {
                net.conflictSince = previous.conflictSince;
            }
            for (FlatPinRef reference : flatNet.pins()) {
                ComponentInstance instance = instances.get(reference.componentId());
                PinState pin = instance.pins[reference.pinIndex()];
                pin.net = net;
                net.pins.add(pin);
            }
            nextNets.put(net.id, net);
        }
        for (ComponentInstance instance : instances.values()) {
            for (PinState pin : instance.pins) {
                if (pin.net == null) {
                    RuntimeNet privateNet = new RuntimeNet("p:" + instance.id + "#" + pin.index, pin.spec.width(), false);
                    pin.net = privateNet;
                    privateNet.pins.add(pin);
                    nextNets.put(privateNet.id, privateNet);
                }
            }
        }
        nets = nextNets;
        aliasRoots = nextAliases;
        nets.values().forEach(net -> net.resolve(now()));
        for (ComponentInstance instance : created) {
            for (PinState pin : instance.pins) {
                pin.lastSeen = instance.input(pin.index);
            }
            instance.behavior.initialize(instance);
        }
        // Apply zero-delay drives due now (initial values of new components) before notifying inputs,
        // so replacing a component never exposes a transient Z to its neighbours.
        scheduler.runUntil(now());
        nets.values().forEach(this::deliver);
    }

    private static void validate(FlatCircuit circuit) {
        Map<String, FlatComponent> componentsById = new HashMap<>();
        for (FlatComponent component : circuit.components()) {
            if (componentsById.putIfAbsent(component.id(), component) != null) {
                throw new IllegalArgumentException("duplicate component id " + component.id());
            }
        }
        for (FlatNet net : circuit.nets()) {
            for (FlatPinRef reference : net.pins()) {
                FlatComponent component = componentsById.get(reference.componentId());
                if (component == null) {
                    throw new IllegalArgumentException("net " + net.id() + " references unknown component "
                            + reference.componentId());
                }
                if (reference.pinIndex() < 0 || reference.pinIndex() >= component.type().pinCount()) {
                    throw new IllegalArgumentException("net " + net.id() + " references pin " + reference.pinIndex()
                            + " of " + component.id() + ", which has " + component.type().pinCount() + " pins");
                }
            }
        }
    }

    public void runUntil(long time) {
        scheduler.runUntil(time);
    }

    public void advance(long duration) {
        runUntil(now() + duration);
    }

    public void interact(String componentId) {
        ComponentInstance instance = instances.get(componentId);
        if (instance != null && instance.behavior.isInteractive()) {
            instance.behavior.interact(instance);
            // Apply zero-delay effects now so a paused simulation shows a consistent picture.
            scheduler.runUntil(now());
        }
    }

    /**
     * Binds waveform channels: channel id → net id (a top-level id, an alias or a nested id; an empty or unknown id
     * records a gap). Channels left out are dropped with their history; the others keep it, even when their net id
     * changed (spec §3.3). Every channel is sampled now.
     */
    public void setProbes(Map<String, String> channels) {
        recorder.setChannels(channels);
        recorder.rebind(this::netWithId, nets.values(), now());
    }

    /** «Limpiar trazas»: drops every recorded event; each channel starts again at its current value. */
    public void clearWaveforms() {
        recorder.clear();
        recorder.rebind(this::netWithId, nets.values(), now());
    }

    /** Grows whenever a trace or the set of channels changes. */
    public long waveformVersion() {
        return recorder.version();
    }

    public boolean hasWaveformChannels() {
        return recorder.hasChannels();
    }

    public WaveformSnapshot waveforms() {
        return recorder.snapshot(now());
    }

    private RuntimeNet netWithId(String id) {
        RuntimeNet net = nets.get(id);
        return net != null ? net : aliasRoots.get(id);
    }

    void netChanged(RuntimeNet net) {
        if (net != null && net.resolve(now())) {
            if (!net.recorders.isEmpty()) {
                recorder.record(net, now());
            }
            deliver(net);
        }
    }

    private void deliver(RuntimeNet net) {
        for (PinState pin : List.copyOf(net.pins)) {
            if (!pin.spec.isInput() || pin.instance.removed || pin.net != net) {
                continue;
            }
            Value seen = net.valueFor(pin);
            if (!seen.equals(pin.lastSeen)) {
                pin.lastSeen = seen;
                pin.instance.behavior.inputChanged(pin.instance, pin.index);
            }
        }
    }

    public SimulationSnapshot snapshot() {
        Map<String, Value> netValues = new HashMap<>();
        List<Problem> problems = new ArrayList<>();
        for (RuntimeNet net : nets.values()) {
            netValues.put(net.id, net.value);
            for (String alias : net.aliases) {
                netValues.put(alias, net.value);
            }
            if (net.widthMismatch) {
                problems.add(new WidthMismatch(net.id, net.pins.stream()
                        .map(pin -> new FlatPinRef(pin.instance.id, pin.index))
                        .toList()));
            } else if (net.value.conflict()) {
                problems.add(new ShortCircuit(net.id, net.pins.stream()
                        .filter(pin -> pin.spec.isDriver() && !pin.driven.isFullyHighImpedance())
                        .map(pin -> new DriverRef(pin.instance.id, pin.index, pin.driven))
                        .toList(), net.conflictSince));
            }
        }
        Map<String, VisibleState> states = new HashMap<>();
        for (ComponentInstance instance : instances.values()) {
            VisibleState state = instance.behavior.visibleState();
            if (!(state instanceof VisibleState.None)) {
                states.put(instance.id, state);
            }
        }
        return new SimulationSnapshot(revision, token, now(), false, netValues, states, problems, Optional.empty());
    }
}
