package cl.almejo.vsim.ui.waveform;

import cl.almejo.vsim.core.command.SetProbesCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinDirection;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.document.ProbeTarget;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.ProbeResolver;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Builds the channel edits of the waveform panel as {@link SetProbesCommand}s, one undo step each (spec §3.2):
 * adding a wire, a component's pins or a selection (one channel per net, never more than {@link #MAXIMUM_CHANNELS}),
 * renaming, moving and removing.
 */
public final class ProbeActions {

    public static final int MAXIMUM_CHANNELS = 64;

    /**
     * What an add did: the new probe ids, else the first existing probe on one of the asked nets (its row blinks),
     * and whether the channel cap stopped it.
     */
    public record AddResult(List<String> addedIds, Optional<String> existingId, boolean capReached) {
        public AddResult {
            addedIds = List.copyOf(addedIds);
        }

        public boolean addedAny() {
            return !addedIds.isEmpty();
        }
    }

    private final OpenDocument openDocument;

    public ProbeActions(OpenDocument openDocument) {
        this.openDocument = openDocument;
    }

    /** «Ver en formas de onda» on a wire: its net, anchored to a pin when it has one. */
    public AddResult addWire(ElementId wireId) {
        return add(wireTarget(wireId).map(List::of).orElse(List.of()));
    }

    /** «Ver pines en formas de onda»: the component's pins in pin order, inputs first. */
    public AddResult addComponentPins(ElementId componentId) {
        return add(pinTargets(componentId));
    }

    /** «Ver en formas de onda» on a selection: its wires and the pins of its components, in document order. */
    public AddResult addSelection(Collection<ElementId> ids) {
        CircuitDocument document = openDocument.document();
        List<ProbeTarget> targets = new ArrayList<>();
        ids.stream()
                .filter(id -> document.element(id).isPresent())
                .sorted(Comparator.comparingLong(document::orderOf))
                .forEach(id -> {
                    if (document.wire(id).isPresent()) {
                        wireTarget(id).ifPresent(targets::add);
                    } else if (document.component(id).isPresent()) {
                        targets.addAll(pinTargets(id));
                    }
                });
        return add(targets);
    }

    /** Whether {@code ids} hold a wire or a component (a selection of only labels has nothing to show). */
    boolean hasTargets(Collection<ElementId> ids) {
        CircuitDocument document = openDocument.document();
        return ids.stream().anyMatch(id -> document.wire(id).isPresent() || document.component(id).isPresent());
    }

    /** Renames a channel; an empty or blank name restores its default name. */
    public void rename(String probeId, String newName) {
        String stripped = newName == null ? "" : newName.strip();
        replace(probeId, probe -> probe.withName(stripped.isEmpty() ? defaultName(probe.target()) : stripped),
                "command.renameProbe");
    }

    public void moveUp(String probeId) {
        move(probeId, -1);
    }

    public void moveDown(String probeId) {
        move(probeId, 1);
    }

    public void remove(String probeId) {
        List<Probe> before = openDocument.document().probes();
        List<Probe> after = before.stream().filter(probe -> !probe.id().equals(probeId)).toList();
        if (after.size() != before.size()) {
            openDocument.execute(new SetProbesCommand(before, after, "command.removeProbe"));
        }
    }

    public void removeAll() {
        List<Probe> before = openDocument.document().probes();
        if (!before.isEmpty()) {
            openDocument.execute(new SetProbesCommand(before, List.of(), "command.removeAllProbes"));
        }
    }

    /** {@code U3.q} (label, else id, dot, pin name) for a pin; «cable w12» for a wire of a net without pins. */
    public String defaultName(ProbeTarget target) {
        CircuitDocument document = openDocument.document();
        return switch (target) {
            case ProbeTarget.PinTarget pin -> document.component(pin.componentId())
                    .map(component -> ownerName(component) + "." + pinName(component, pin.pinIndex()))
                    .orElse(pin.componentId().value() + "." + pin.pinIndex());
            case ProbeTarget.WireTarget wire -> Messages.format("waveform.wireName", wire.wireId().value());
        };
    }

    private static String ownerName(Component component) {
        return component.label().isBlank() ? component.id().value() : component.label();
    }

    private String pinName(Component component, int pinIndex) {
        return openDocument.registry().findTypeOf(component)
                .filter(type -> pinIndex < type.pinCount())
                .map(type -> type.pin(pinIndex).name())
                .orElse(String.valueOf(pinIndex));
    }

    private Optional<ProbeTarget> wireTarget(ElementId wireId) {
        return ProbeResolver.anchor(wireId, openDocument.document(), openDocument.netlist(), openDocument.registry());
    }

    private List<ProbeTarget> pinTargets(ElementId componentId) {
        Optional<ComponentType> type = openDocument.document().component(componentId)
                .flatMap(openDocument.registry()::findTypeOf);
        if (type.isEmpty()) {
            return List.of();
        }
        List<ProbeTarget> inputs = new ArrayList<>();
        List<ProbeTarget> others = new ArrayList<>();
        for (int index = 0; index < type.get().pinCount(); index++) {
            ProbeTarget target = new ProbeTarget.PinTarget(componentId, index);
            if (type.get().pin(index).direction() == PinDirection.INPUT) {
                inputs.add(target);
            } else {
                others.add(target);
            }
        }
        inputs.addAll(others);
        return inputs;
    }

    /** One channel per net: a target whose net already has a channel (or got one in this add) is skipped. */
    private AddResult add(List<ProbeTarget> targets) {
        CircuitDocument document = openDocument.document();
        Netlist netlist = openDocument.netlist();
        List<Probe> before = document.probes();
        Map<String, String> existingNets = ProbeResolver.resolve(before, document, netlist);
        Map<String, String> probeOnNet = new HashMap<>();
        for (Probe probe : before) {
            String netId = existingNets.get(probe.id());
            if (netId != null) {
                probeOnNet.putIfAbsent(netId, probe.id());
            }
        }
        Set<String> takenNets = new HashSet<>(probeOnNet.keySet());
        List<Probe> after = new ArrayList<>(before);
        List<String> added = new ArrayList<>();
        Optional<String> existingId = Optional.empty();
        boolean capReached = false;
        for (ProbeTarget target : targets) {
            Optional<String> netId = ProbeResolver.netOf(target, document, netlist);
            if (netId.isEmpty()) {
                continue;
            }
            if (!takenNets.add(netId.get())) {
                if (existingId.isEmpty() && probeOnNet.containsKey(netId.get())) {
                    existingId = Optional.of(probeOnNet.get(netId.get()));
                }
                continue;
            }
            if (after.size() >= MAXIMUM_CHANNELS) {
                capReached = true;
                break;
            }
            Probe probe = new Probe(document.newProbeId(), target, defaultName(target));
            after.add(probe);
            added.add(probe.id());
        }
        if (!added.isEmpty()) {
            openDocument.execute(new SetProbesCommand(before, after, "command.addProbes"));
        }
        return new AddResult(added, added.isEmpty() ? existingId : Optional.empty(), capReached);
    }

    private void replace(String probeId, UnaryOperator<Probe> change, String description) {
        List<Probe> before = openDocument.document().probes();
        List<Probe> after = before.stream()
                .map(probe -> probe.id().equals(probeId) ? change.apply(probe) : probe)
                .toList();
        if (!after.equals(before)) {
            openDocument.execute(new SetProbesCommand(before, after, description));
        }
    }

    private void move(String probeId, int direction) {
        List<Probe> before = openDocument.document().probes();
        int index = -1;
        for (int candidate = 0; candidate < before.size(); candidate++) {
            if (before.get(candidate).id().equals(probeId)) {
                index = candidate;
            }
        }
        int target = index + direction;
        if (index < 0 || target < 0 || target >= before.size()) {
            return;
        }
        List<Probe> after = new ArrayList<>(before);
        Collections.swap(after, index, target);
        openDocument.execute(new SetProbesCommand(before, after, "command.moveProbe"));
    }
}
