package cl.almejo.vsim.core.library;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.ConstantValue;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.NetId;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.value.Value;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A selection with its test parts turned into what the component's body gets (followups, «Crear componente» turns
 * test parts into pins). Each part in {@code convertedParts} becomes pins on the very points its own pins were, so the
 * body's wires stay connected:
 * <ul>
 *     <li>a switch, a constant or a clock: an input pin as wide as it, with its label;</li>
 *     <li>an LED: an output pin; a 7-segment display: one output pin per connected one-bit input ({@code <label> bit
 *     k}), or a single bus output pin named after it when its bus input is connected; a time diagram: one output pin per
 *     connected channel ({@code <label> in<k>}). A net that already has an output pin, or one from an earlier part,
 *     gets no other.</li>
 * </ul>
 * The other parts stay inside: an unchecked switch as a constant of its current value (a switch at Z stays a switch
 * at Z, a constant cannot float), an unchecked constant or clock as it is; an unchecked LED, display or time diagram is
 * left out (nobody could see it) while the wires that fed it stay. Gates, blocks and everything else go in as they
 * are. The user's document is never changed: the conversion works on a copy, {@link #document()}.
 */
public final class ConvertedSelection {

    private static final String BIT_SEPARATOR = " bit ";

    private final CircuitDocument original;
    private final CircuitDocument document;
    private final Set<ElementId> ids;
    private final Set<ElementId> convertedPins;

    private ConvertedSelection(CircuitDocument original, CircuitDocument document, Set<ElementId> ids,
                               Set<ElementId> convertedPins) {
        this.original = original;
        this.document = document;
        this.ids = Set.copyOf(ids);
        this.convertedPins = Set.copyOf(convertedPins);
    }

    /**
     * Converts the test parts of {@code ids} in {@code document}: those in {@code convertedParts} become pins, the
     * others stay inside or are left out. {@code currentValues} holds a switch's value while simulating (absent: its
     * saved value). Without test parts the result is {@code document} and {@code ids} themselves.
     */
    public static ConvertedSelection of(CircuitDocument document, Collection<ElementId> ids,
                                        Set<ElementId> convertedParts, Map<ElementId, Value> currentValues,
                                        ComponentTypeRegistry registry) {
        List<Component> parts = TestParts.components(document, ids);
        if (parts.isEmpty()) {
            return new ConvertedSelection(document, document, new LinkedHashSet<>(ids), Set.of());
        }
        return new Conversion(document, ids, registry).run(parts, convertedParts, currentValues);
    }

    /** The document the definition is made from: the user's one, or a converted copy of it. */
    public CircuitDocument document() {
        return document;
    }

    /** The elements of {@link #document()} that make the component's body. */
    public Set<ElementId> ids() {
        return ids;
    }

    /** The interface pins that came from test parts. */
    public Set<ElementId> convertedPins() {
        return convertedPins;
    }

    /** The body's interface pins, each side by position ({@link DefinitionBuilder#defaultPinOrder}). */
    public PinOrder defaultPinOrder() {
        return DefinitionBuilder.defaultPinOrder(document, ids);
    }

    /** The pin names: labels, {@code in1}/{@code out1}… for blank interface pins, A, B…/Y1, Y2… for blank parts. */
    public Map<ElementId, String> pinNames() {
        return DefinitionBuilder.pinNames(document, defaultPinOrder(), convertedPins);
    }

    /**
     * The definition of the converted selection with {@code pinOrder}, which must list each pin of
     * {@link #defaultPinOrder()} once, on its side. It may have no pins at all.
     */
    public DefinitionBundle definition(String id, String name, PinOrder pinOrder, ComponentTypeRegistry registry) {
        if (!DefinitionBuilder.sameOrder(pinOrder, defaultPinOrder())) {
            throw new IllegalArgumentException("the pin order must list each pin once: " + pinOrder);
        }
        return DefinitionBuilder.assemble(document, ids, id, name, pinOrder, pinNames(), registry);
    }

    /**
     * {@link PinBridges#wires} for replacing the selection by an instance of {@code definition} at
     * {@code instancePosition}: outside wires on a converted part reach its pin like they reach an interface pin. The
     * bridge ids are fresh in the user's document.
     */
    public PinBridges.Bridging bridges(ComponentDefinition definition, GridPoint instancePosition,
                                       ComponentTypeRegistry registry) {
        PinBridges.Bridging bridging = PinBridges.wires(document, ids, definition, instancePosition, registry);
        if (document == original) {
            return bridging;
        }
        List<Wire> wires = bridging.wires().stream()
                .map(wire -> new Wire(original.newWireId(), wire.start(), wire.end()))
                .toList();
        return new PinBridges.Bridging(wires, bridging.unconnectedPins(), bridging.blockedPins());
    }

    /** One conversion: a copy of the document where the parts are replaced. */
    private static final class Conversion {

        private final CircuitDocument original;
        private final CircuitDocument working = new CircuitDocument();
        private final Set<ElementId> ids;
        private final ComponentTypeRegistry registry;
        private final Netlist body;
        private final Set<NetId> watched = new HashSet<>();
        private final Set<ElementId> convertedPins = new LinkedHashSet<>();

        Conversion(CircuitDocument original, Collection<ElementId> ids, ComponentTypeRegistry registry) {
            this.original = original;
            this.ids = new LinkedHashSet<>(ids);
            this.registry = registry;
            original.definitions().forEach(working::putDefinition);
            original.elements().forEach(working::add);
            this.body = compileBody();
            for (Component component : original.components()) {
                if (this.ids.contains(component.id()) && component.typeId().equals(InterfacePins.OUTPUT_PIN)) {
                    netOf(component.id(), 0).ifPresent(net -> watched.add(net.id()));
                }
            }
        }

        ConvertedSelection run(List<Component> parts, Set<ElementId> convertedParts,
                               Map<ElementId, Value> currentValues) {
            // Every pin a part could give gets its id first, whatever is checked: the ids of a part's pins never
            // depend on the other parts' choices, so a pin order chosen in the dialog stays valid.
            Map<ElementId, List<ElementId>> slots = new LinkedHashMap<>();
            for (Component part : parts) {
                List<ElementId> partSlots = new ArrayList<>();
                for (int pinIndex = 0; pinIndex < typeOf(part).pinCount(); pinIndex++) {
                    partSlots.add(working.newComponentId());
                }
                slots.put(part.id(), partSlots);
            }
            for (Component part : parts) {
                convert(part, convertedParts.contains(part.id()), slots.get(part.id()), currentValues.get(part.id()));
            }
            return new ConvertedSelection(original, working, ids, convertedPins);
        }

        private void convert(Component part, boolean checked, List<ElementId> slots, Value currentValue) {
            TestParts.Kind kind = TestParts.kindOf(part.typeId()).orElseThrow();
            if (!checked && (kind == TestParts.Kind.CONSTANT || kind == TestParts.Kind.CLOCK)) {
                return;
            }
            if (!checked && kind == TestParts.Kind.SWITCH) {
                keepSwitch(part, slots.get(0), currentValue);
                return;
            }
            remove(part);
            if (!checked) {
                return;
            }
            String label = part.label().strip();
            switch (kind) {
                case SWITCH -> addInput(part, slots.get(0), Map.of("value", switchText(part, currentValue)));
                case CONSTANT -> {
                    Map<String, Object> parameters = typeOf(part).resolveParameters(part.parameters());
                    addInput(part, slots.get(0), Map.of(
                            InterfacePins.WIDTH, parameters.get(StandardComponentTypes.WIDTH),
                            ConstantValue.FORMAT, parameters.get(ConstantValue.FORMAT)));
                }
                case CLOCK -> addInput(part, slots.get(0), Map.of());
                case LED -> addOutput(part, 0, slots.get(0), label);
                case SEVEN_SEGMENTS_DISPLAY -> {
                    int busPin = StandardComponentTypes.sevenSegmentsBusPin(typeOf(part));
                    if (isConnected(part, busPin)) {
                        addOutput(part, busPin, slots.get(busPin), label);
                    } else {
                        for (int bit = 0; bit < busPin; bit++) {
                            if (isConnected(part, bit)) {
                                addOutput(part, bit, slots.get(bit), label.isEmpty() ? "" : label + BIT_SEPARATOR + bit);
                            }
                        }
                    }
                }
                case TIME_DIAGRAM -> {
                    for (int channel = 0; channel < typeOf(part).pinCount(); channel++) {
                        if (isConnected(part, channel)) {
                            addOutput(part, channel, slots.get(channel),
                                    label.isEmpty() ? "" : label + " " + typeOf(part).pin(channel).name());
                        }
                    }
                }
            }
        }

        /** An unchecked switch: a constant of its value, or still a switch when it is at Z. */
        private void keepSwitch(Component part, ElementId slot, Value currentValue) {
            String text = switchText(part, currentValue);
            if (text.equals("Z")) {
                Map<String, Object> parameters = new HashMap<>(part.parameters());
                parameters.put("value", text);
                working.replace(part.withParameters(parameters));
                return;
            }
            remove(part);
            add(placed(StandardComponentTypes.CONSTANT, slot, pinPoint(part, 0), part,
                    Map.of(StandardComponentTypes.WIDTH, 1, "value", text), part.label()));
        }

        private void addInput(Component part, ElementId slot, Map<String, Object> parameters) {
            Component pin = placed(InterfacePins.INPUT_PIN, slot, pinPoint(part, 0), part, parameters, part.label());
            add(pin);
            convertedPins.add(pin.id());
        }

        /** An output pin on pin {@code pinIndex} of {@code part}, unless its net already has one. */
        private void addOutput(Component part, int pinIndex, ElementId slot, String label) {
            Optional<Net> net = netOf(part.id(), pinIndex);
            if (net.isPresent() && !watched.add(net.get().id())) {
                return;
            }
            int width = typeOf(part).pin(pinIndex).width();
            Component pin = placed(InterfacePins.OUTPUT_PIN, slot, pinPoint(part, pinIndex), part,
                    Map.of(InterfacePins.WIDTH, width), label);
            add(pin);
            convertedPins.add(pin.id());
        }

        /** A new {@code typeId} with {@code source}'s rotation whose (only) pin is on {@code pinPoint}. */
        private Component placed(String typeId, ElementId id, GridPoint pinPoint, Component source,
                                 Map<String, Object> parameters, String label) {
            ComponentType type = registry.require(typeId);
            Component atOrigin = type.newComponent(id, GridPoint.ORIGIN, source.rotation(), parameters, label);
            GridPoint offset = type.pinLocation(atOrigin, 0);
            return atOrigin.translate(pinPoint.x() - offset.x(), pinPoint.y() - offset.y());
        }

        private void add(Component component) {
            working.add(component);
            ids.add(component.id());
        }

        private void remove(Component part) {
            working.remove(part.id());
            ids.remove(part.id());
        }

        /** True when something in the selection touches pin {@code pinIndex}: a wire or another pin. */
        private boolean isConnected(Component part, int pinIndex) {
            return netOf(part.id(), pinIndex)
                    .map(net -> net.pins().size() > 1 || !net.wires().isEmpty())
                    .orElse(false);
        }

        private Optional<Net> netOf(ElementId componentId, int pinIndex) {
            return body.netForPin(new PinRef(componentId, pinIndex));
        }

        private GridPoint pinPoint(Component part, int pinIndex) {
            return registry.typeOf(part).pinLocation(part, pinIndex);
        }

        private ComponentType typeOf(Component part) {
            return registry.typeOf(part).forComponent(part);
        }

        /** The selection alone, as the body will be: its nets decide what is connected and what is watched. */
        private Netlist compileBody() {
            CircuitDocument selection = new CircuitDocument();
            original.definitions().forEach(selection::putDefinition);
            for (DocumentElement element : original.elements()) {
                if (ids.contains(element.id())) {
                    selection.add(element);
                }
            }
            return new NetlistCompiler(registry.extendedWith(SubcircuitTypes.resolverFor(selection)))
                    .compile(selection);
        }

        private static String switchText(Component part, Value currentValue) {
            if (currentValue == null) {
                return String.valueOf(part.parameters().get("value"));
            }
            return currentValue.equals(Value.ONE) ? "1" : currentValue.equals(Value.ZERO) ? "0" : "Z";
        }
    }
}
