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
import cl.almejo.vsim.ui.canvas.PartFormats;
import cl.almejo.vsim.ui.canvas.TopLevelNets;

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

    /** Renames a channel; an empty or blank name stores {@code ""}: the row shows the live default name again. */
    public void rename(String probeId, String newName) {
        String stripped = newName == null ? "" : newName.strip();
        replace(probeId, probe -> probe.withName(stripped), "command.renameProbe");
    }

    /** «Mostrar como» on a bus channel: any of the {@link Probe} formats, one undo step. */
    public void setFormat(String probeId, String format) {
        replace(probeId, probe -> probe.withFormat(format), "command.setProbeFormat");
    }

    /** The width of the net the channel watches (1 when it watches nothing). */
    int busWidth(String probeId) {
        CircuitDocument document = openDocument.document();
        Netlist netlist = openDocument.netlist();
        return document.probes().stream()
                .filter(probe -> probe.id().equals(probeId))
                .findFirst()
                .flatMap(probe -> ProbeResolver.netOf(probe.target(), document, netlist))
                .flatMap(netId -> netlist.nets().stream().filter(net -> net.id().value().equals(netId)).findFirst())
                .map(net -> net.width())
                .orElse(1);
    }

    /** Whether the channel watches a net wider than one bit (only those have a format to choose). */
    boolean isBus(String probeId) {
        CircuitDocument document = openDocument.document();
        Netlist netlist = openDocument.netlist();
        return document.probes().stream()
                .filter(probe -> probe.id().equals(probeId))
                .findFirst()
                .flatMap(probe -> ProbeResolver.netOf(probe.target(), document, netlist))
                .flatMap(netId -> netlist.nets().stream().filter(net -> net.id().value().equals(netId)).findFirst())
                .map(net -> net.width() > 1)
                .orElse(false);
    }

    public void moveUp(String probeId) {
        move(probeId, -1);
    }

    public void moveDown(String probeId) {
        move(probeId, 1);
    }

    public void remove(String probeId) {
        edit(probes -> probes.stream().filter(probe -> !probe.id().equals(probeId)).toList(), "command.removeProbe");
    }

    public void removeAll() {
        edit(probes -> List.of(), "command.removeAllProbes");
    }

    /** The live default name of {@code target} now ({@link ProbeNames#defaultName}). */
    public String defaultName(ProbeTarget target) {
        return ProbeNames.defaultName(target, openDocument.document(), openDocument.netlist(),
                openDocument.registry());
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

    /**
     * One channel per net: a target whose net already has a channel (or got one in this add) is skipped. Top-level
     * nets the simulation joins through a template count as one net ({@link TopLevelNets#rootOf}).
     */
    private AddResult add(List<ProbeTarget> targets) {
        CircuitDocument document = openDocument.document();
        Netlist netlist = openDocument.netlist();
        TopLevelNets merged = TopLevelNets.of(document, netlist, openDocument.registry());
        List<Probe> before = document.probes();
        Map<String, String> existingNets = ProbeResolver.resolve(before, document, netlist);
        Map<String, String> probeOnNet = new HashMap<>();
        for (Probe probe : before) {
            String netId = existingNets.get(probe.id());
            if (netId != null) {
                probeOnNet.putIfAbsent(merged.rootOf(netId), probe.id());
            }
        }
        Set<String> takenNets = new HashSet<>(probeOnNet.keySet());
        List<Probe> after = new ArrayList<>(before);
        List<String> added = new ArrayList<>();
        Optional<String> existingId = Optional.empty();
        boolean capReached = false;
        for (ProbeTarget target : targets) {
            Optional<String> netId = ProbeResolver.netOf(target, document, netlist).map(merged::rootOf);
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
            Probe probe = new Probe(document.newProbeId(), target, "", formatOf(target, document));
            after.add(probe);
            added.add(probe.id());
        }
        if (!added.isEmpty()) {
            openDocument.execute(new SetProbesCommand(before, after, "command.addProbes"));
        }
        return new AddResult(added, added.isEmpty() ? existingId : Optional.empty(), capReached);
    }

    /** The type id of the Terminal, whose bytes read as characters in a waveform unless its parameter says hex. */
    private static final String TERMINAL_TYPE = "terminal";
    private static final String KEYBOARD_TYPE = "keyboard";
    /** The Terminal's «En formas de onda» parameter, and its value for «Bytes como caracteres». */
    private static final String WAVEFORM_FORMAT = "waveform-format";
    private static final String CHARACTERS = "chars";
    private static final String DATA_PIN = "D";

    /**
     * The format a new channel starts with: the D pin of a Terminal (unless its {@code waveform-format} is not
     * {@code chars}) or of a Teclado (always) carries bytes, so it shows characters; else the part's own format; else hex.
     */
    private String formatOf(ProbeTarget target, CircuitDocument document) {
        if (!(target instanceof ProbeTarget.PinTarget pin)) {
            return partFormat(target, document);
        }
        Optional<Component> component = document.component(pin.componentId());
        Optional<ComponentType> type = component.flatMap(openDocument.registry()::findTypeOf);
        if (type.isEmpty() || pin.pinIndex() >= type.get().pinCount()
                || !DATA_PIN.equals(type.get().pin(pin.pinIndex()).name())) {
            return partFormat(target, document);
        }
        boolean characters = switch (component.get().typeId()) {
            case KEYBOARD_TYPE -> true;
            case TERMINAL_TYPE -> CHARACTERS.equals(component.get().parameters().getOrDefault(WAVEFORM_FORMAT,
                    CHARACTERS));
            default -> false;
        };
        return characters ? Probe.FORMAT_ASCII : partFormat(target, document);
    }

    /** The format of the part that drives the target's net ({@link PartFormats}), else hex. */
    private String partFormat(ProbeTarget target, CircuitDocument document) {
        Netlist netlist = openDocument.netlist();
        return ProbeResolver.netOf(target, document, netlist)
                .flatMap(netId -> netlist.nets().stream().filter(net -> net.id().value().equals(netId)).findFirst())
                .flatMap(net -> PartFormats.of(net, document, openDocument.registry()))
                .orElse(Probe.FORMAT_HEX);
    }

    private void replace(String probeId, UnaryOperator<Probe> change, String descriptionKey) {
        edit(probes -> probes.stream()
                .map(probe -> probe.id().equals(probeId) ? change.apply(probe) : probe)
                .toList(), descriptionKey);
    }

    private void move(String probeId, int direction) {
        edit(probes -> swapped(probes, probeId, direction), "command.moveProbe");
    }

    /** {@code probes} with {@code probeId} swapped with its neighbor; the same list when it cannot move. */
    private static List<Probe> swapped(List<Probe> probes, String probeId, int direction) {
        int index = -1;
        for (int candidate = 0; candidate < probes.size(); candidate++) {
            if (probes.get(candidate).id().equals(probeId)) {
                index = candidate;
            }
        }
        int target = index + direction;
        if (index < 0 || target < 0 || target >= probes.size()) {
            return probes;
        }
        List<Probe> result = new ArrayList<>(probes);
        Collections.swap(result, index, target);
        return result;
    }

    /** One undoable step from the document's probes to {@code change}'s list, only when that list differs. */
    private void edit(UnaryOperator<List<Probe>> change, String descriptionKey) {
        List<Probe> before = openDocument.document().probes();
        List<Probe> after = change.apply(before);
        if (!after.equals(before)) {
            openDocument.execute(new SetProbesCommand(before, after, descriptionKey));
        }
    }
}
