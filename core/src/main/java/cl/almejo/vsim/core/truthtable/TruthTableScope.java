package cl.almejo.vsim.core.truthtable;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinDirection;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBuilder;
import cl.almejo.vsim.core.library.DefinitionPin;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.simulation.CircuitFlattener;
import cl.almejo.vsim.core.simulation.FlatCircuit;
import cl.almejo.vsim.core.simulation.FlatComponent;
import cl.almejo.vsim.core.simulation.FlatNet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntFunction;

/**
 * Decides what a truth table covers (spec §5.1, §5.2): the selected elements, or the whole circuit when nothing is
 * selected, or — for a single selected user-component instance — that component's body with its interface pins as
 * columns. The scope becomes a temporary document that is compiled and flattened once; the live document is only
 * read. Call it on the document's thread (the EDT in the application).
 */
public final class TruthTableScope {

    private static final Set<String> INPUT_TYPES = Set.of(StandardComponentTypes.SWITCH,
            StandardComponentTypes.INPUT_PIN);
    private static final Set<String> OUTPUT_TYPES = Set.of(StandardComponentTypes.LED,
            StandardComponentTypes.OUTPUT_PIN);
    private static final Set<String> SEQUENTIAL_TYPES = Set.of(StandardComponentTypes.FLIP_FLOP_DATA,
            StandardComponentTypes.CLOCK, StandardComponentTypes.REGISTER, StandardComponentTypes.COUNTER);
    /** Top to bottom, then left to right: the order {@code PinOrder} uses. */
    private static final Comparator<Component> BY_POSITION = Comparator
            .comparingInt((Component component) -> component.position().y())
            .thenComparingInt(component -> component.position().x());

    private TruthTableScope() {
    }

    /**
     * Prepares the table of {@code selection} in {@code document} (an empty selection means the whole circuit).
     * {@code registry} must know the standard types; the document's definitions are resolved here.
     */
    public static Preparation prepare(CircuitDocument document, Set<ElementId> selection,
                                      ComponentTypeRegistry registry) {
        Optional<ComponentDefinition> instance = singleInstance(document, selection);
        CircuitDocument evaluated = instance.isPresent()
                ? instance.get().bodyDocument(document)
                : copyOf(document, selection, registry);
        ComponentTypeRegistry evaluatedRegistry = registry.extendedWith(SubcircuitTypes.resolverFor(evaluated));
        Netlist netlist = new NetlistCompiler(evaluatedRegistry).compile(evaluated);
        FlatCircuit circuit = withoutTimeDiagrams(new CircuitFlattener(evaluatedRegistry).flatten(evaluated, netlist));
        List<Column> inputs;
        List<Column> outputs;
        if (instance.isPresent()) {
            inputs = pinColumns(instance.get(), PinDirection.INPUT);
            outputs = pinColumns(instance.get(), PinDirection.OUTPUT);
        } else {
            List<Component> inputComponents = sortedOfTypes(evaluated, INPUT_TYPES);
            List<Component> outputComponents = sortedOfTypes(evaluated, OUTPUT_TYPES);
            Set<String> taken = new HashSet<>();
            inputComponents.forEach(component -> taken.add(component.label().strip()));
            outputComponents.forEach(component -> taken.add(component.label().strip()));
            inputs = columns(inputComponents, evaluatedRegistry, taken, TruthTableScope::letters);
            outputs = columns(outputComponents, evaluatedRegistry, taken, number -> "Y" + number);
        }
        int inputBits = inputs.stream().mapToInt(Column::width).sum();
        if (circuit.components().stream().map(FlatComponent::type)
                .anyMatch(type -> SEQUENTIAL_TYPES.contains(type.id()))) {
            return new Preparation.Refused(Preparation.Reason.SEQUENTIAL, inputBits);
        }
        if (inputs.isEmpty()) {
            return new Preparation.Refused(Preparation.Reason.NO_INPUTS, inputBits);
        }
        if (outputs.isEmpty()) {
            return new Preparation.Refused(Preparation.Reason.NO_OUTPUTS, inputBits);
        }
        if (inputBits > Preparation.MAXIMUM_INPUT_BITS) {
            return new Preparation.Refused(Preparation.Reason.TOO_MANY_INPUT_BITS, inputBits);
        }
        Map<String, String> outputNets = new HashMap<>();
        for (Column output : outputs) {
            outputNets.put(output.componentId(), netlist.netForPin(new PinRef(new ElementId(output.componentId()), 0))
                    .map(net -> net.id().value())
                    .orElse("p:" + output.componentId() + "#0"));
        }
        if (instance.isPresent()) {
            return new Preparation.Ready(evaluated, inputs, outputs, circuit, outputNets,
                    CombinationalLoops.exists(circuit), instanceInputPins(selection.iterator().next(), instance.get()));
        }
        return new Preparation.Ready(evaluated, inputs, outputs, circuit, outputNets,
                CombinationalLoops.exists(circuit));
    }

    /** The input pins of instance {@code instanceId} in pinout order: the pin index is the place in the pinout. */
    private static List<PinRef> instanceInputPins(ElementId instanceId, ComponentDefinition definition) {
        List<PinRef> pins = new ArrayList<>();
        List<DefinitionPin> pinout = definition.pinout();
        for (int index = 0; index < pinout.size(); index++) {
            if (pinout.get(index).spec().direction() == PinDirection.INPUT) {
                pins.add(new PinRef(instanceId, index));
            }
        }
        return pins;
    }

    /**
     * {@code circuit} without its time diagrams (spec §8.15: they are not columns). A time diagram samples itself
     * forever, so with one in scope no row would ever run out of events and every row would read as oscillating; it
     * has only input pins, so dropping it and its pin references changes no value.
     */
    static FlatCircuit withoutTimeDiagrams(FlatCircuit circuit) {
        Set<String> diagrams = new HashSet<>();
        for (FlatComponent component : circuit.components()) {
            if (component.type().id().equals(StandardComponentTypes.TIME_DIAGRAM)) {
                diagrams.add(component.id());
            }
        }
        if (diagrams.isEmpty()) {
            return circuit;
        }
        List<FlatComponent> components = circuit.components().stream()
                .filter(component -> !diagrams.contains(component.id()))
                .toList();
        List<FlatNet> nets = circuit.nets().stream()
                .map(net -> new FlatNet(net.id(),
                        net.pins().stream().filter(pin -> !diagrams.contains(pin.componentId())).toList(),
                        net.width(), net.widthMismatch(), net.aliases()))
                .toList();
        return new FlatCircuit(circuit.token(), components, nets);
    }

    /** The definition of the one selected element, when it is an instance of a definition the document holds. */
    public static Optional<ComponentDefinition> singleInstance(CircuitDocument document, Set<ElementId> selection) {
        if (selection.size() != 1) {
            return Optional.empty();
        }
        return document.component(selection.iterator().next())
                .flatMap(component -> SubcircuitTypes.definitionIdOf(component.typeId()))
                .flatMap(document::definition);
    }

    private static CircuitDocument copyOf(CircuitDocument document, Set<ElementId> selection,
                                          ComponentTypeRegistry registry) {
        Set<ElementId> ids = selection;
        if (ids.isEmpty()) {
            ids = new HashSet<>();
            for (DocumentElement element : document.elements()) {
                ids.add(element.id());
            }
        }
        CircuitFragment fragment = CircuitFragment.of(document, ids,
                registry.extendedWith(SubcircuitTypes.resolverFor(document)));
        CircuitDocument copy = new CircuitDocument();
        fragment.definitions().forEach(copy::putDefinition);
        fragment.elements().forEach(copy::add);
        return copy;
    }

    private static List<Column> pinColumns(ComponentDefinition definition, PinDirection direction) {
        List<Column> columns = new ArrayList<>();
        for (DefinitionPin pin : definition.pinout()) {
            if (pin.spec().direction() == direction) {
                columns.add(new Column(pin.spec().name(), pin.source().value(), pin.spec().width()));
            }
        }
        return columns;
    }

    private static List<Component> sortedOfTypes(CircuitDocument document, Set<String> typeIds) {
        return document.components().stream()
                .filter(component -> typeIds.contains(component.typeId()))
                .sorted(BY_POSITION)
                .toList();
    }

    /** Labels as they are; blank labels get {@code fallback(1)}, {@code fallback(2)}, … skipping names in use. */
    private static List<Column> columns(List<Component> components, ComponentTypeRegistry registry, Set<String> taken,
                                        IntFunction<String> fallback) {
        List<Column> columns = new ArrayList<>();
        int next = 1;
        for (Component component : components) {
            String name = component.label().strip();
            if (name.isEmpty()) {
                do {
                    name = fallback.apply(next++);
                } while (taken.contains(name));
                taken.add(name);
            }
            columns.add(new Column(name, component.id().value(), registry.typeOf(component).pin(0).width()));
        }
        return columns;
    }

    /** 1 → A, 26 → Z, 27 → AA, like spreadsheet columns. */
    static String letters(int number) {
        return DefinitionBuilder.letters(number);
    }
}
