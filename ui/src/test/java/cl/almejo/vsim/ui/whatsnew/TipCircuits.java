package cl.almejo.vsim.ui.whatsnew;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.layout.CircuitTidy;
import cl.almejo.vsim.core.layout.CircuitTidyOptions;
import cl.almejo.vsim.core.layout.TextBoxes;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBuilder;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.library.SubcircuitTypes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The tips' circuits, built in Java like the bundled examples, so they stay tidy and are easy to change:
 * {@code VSIM_WRITE_BUNDLED=1 ./gradlew :ui:test --tests '*TipCircuitsTest.writeTheResources'} writes them to
 * {@code ui/src/main/resources/tips/}, and {@link TipCircuitsTest} checks the committed files are what is built here.
 */
final class TipCircuits {

    private static final Map<String, Object> ON = Map.of("value", "1");
    private static final Map<String, Object> OFF = Map.of("value", "0");
    private static final Map<String, Object> NO_PARAMETERS = Map.of();
    private static final String HALF_ADDER_ID = "semisumador";

    private TipCircuits() {
    }

    /** Every tip circuit by its resource name, in the tips file's order. */
    static Map<String, Supplier<CircuitDocument>> builders() {
        Map<String, Supplier<CircuitDocument>> builders = new LinkedHashMap<>();
        builders.put("look-inside.vsim", TipCircuits::lookInside);
        builders.put("tidy-circuit-before.vsim", TipCircuits::tidyBefore);
        builders.put("tidy-circuit-after.vsim", TipCircuits::tidyAfter);
        return builders;
    }

    /** A small document with its own registry (the standard types and the document's definitions). */
    private static final class Builder {

        final CircuitDocument document = new CircuitDocument();
        final ComponentTypeRegistry registry = StandardComponentTypes.registry();

        Builder(String name) {
            document.setName(name);
            registry.addResolver(SubcircuitTypes.resolverFor(document));
        }

        /** Places {@code typeId} so its pin {@code pinIndex} lands on {@code pinPoint}. */
        Component placeByPin(String typeId, GridPoint pinPoint, int pinIndex, Map<String, Object> parameters,
                             String label) {
            GridPoint local = registry.require(typeId).specializedFor(parameters).pin(pinIndex).position();
            Component component = registry.require(typeId).newComponent(document.newComponentId(),
                    new GridPoint(pinPoint.x() - local.x(), pinPoint.y() - local.y()), Rotation.R0, parameters,
                    label);
            document.add(component);
            return component;
        }

        /** Places {@code typeId} with its top-left corner at ({@code left}, {@code top}). */
        Component placeAt(String typeId, int left, int top, String label) {
            Component component = registry.require(typeId).newComponent(document.newComponentId(),
                    new GridPoint(left, top), Rotation.R0, NO_PARAMETERS, label);
            document.add(component);
            return component;
        }

        GridPoint pin(Component component, int pinIndex) {
            return registry.typeOf(component).pinLocation(component, pinIndex);
        }

        void route(GridPoint... points) {
            for (int index = 1; index < points.length; index++) {
                document.add(new Wire(document.newWireId(), points[index - 1], points[index]));
            }
        }

        /** An LED standing two rows above the end of a wire that leaves {@code from} to the right. */
        void ledAfter(GridPoint from, int length, String label) {
            GridPoint end = from.translate(length, 0);
            GridPoint ledPin = end.translate(0, -2);
            route(from, end, ledPin);
            placeByPin(StandardComponentTypes.LED, ledPin, 0, Map.of("color", "green"), label);
        }
    }

    /**
     * «Mirar adentro»: two switches into a «Semisumador» of the user (an XOR and an AND inside), its sum and carry on
     * two LEDs. A is on, so the sum shows lit.
     */
    static CircuitDocument lookInside() {
        Builder builder = new Builder("Mirar adentro");
        builder.document.putDefinition(halfAdder());
        Component block = builder.placeAt(SubcircuitTypes.typeIdOf(HALF_ADDER_ID), 14, 4, "");
        GridPoint inputA = builder.pin(block, 0);
        GridPoint inputB = builder.pin(block, 1);
        // the switches stand apart, above and below the block's pins, so their labels have room
        GridPoint switchA = inputA.translate(-8, -3);
        builder.placeByPin(StandardComponentTypes.SWITCH, switchA, 0, ON, "A");
        builder.route(switchA, switchA.translate(5, 0), inputA.translate(-3, 0), inputA);
        GridPoint switchB = inputB.translate(-8, 3);
        builder.placeByPin(StandardComponentTypes.SWITCH, switchB, 0, OFF, "B");
        builder.route(switchB, switchB.translate(5, 0), inputB.translate(-3, 0), inputB);
        builder.ledAfter(builder.pin(block, 2), 4, "S");
        builder.ledAfter(builder.pin(block, 3), 8, "C");
        return builder.document;
    }

    /** «Semisumador»: S = A xor B, C = A · B, made from a body with input and output pins as a user would. */
    private static ComponentDefinition halfAdder() {
        Builder body = new Builder("Semisumador");
        Component inputA = body.placeByPin(InterfacePins.INPUT_PIN, new GridPoint(6, 6), 0, NO_PARAMETERS, "A");
        Component inputB = body.placeByPin(InterfacePins.INPUT_PIN, new GridPoint(6, 10), 0, NO_PARAMETERS, "B");
        body.placeAt(StandardComponentTypes.XOR, 12, 6, "");
        body.placeAt(StandardComponentTypes.AND, 12, 14, "");
        body.route(new GridPoint(6, 6), new GridPoint(12, 6));
        body.route(new GridPoint(9, 6), new GridPoint(9, 14), new GridPoint(12, 14));
        body.route(new GridPoint(6, 10), new GridPoint(12, 10));
        body.route(new GridPoint(10, 10), new GridPoint(10, 18), new GridPoint(12, 18));
        body.route(new GridPoint(16, 8), new GridPoint(20, 8));
        body.route(new GridPoint(16, 16), new GridPoint(20, 16));
        body.placeByPin(InterfacePins.OUTPUT_PIN, new GridPoint(20, 8), 0, NO_PARAMETERS, "S");
        body.placeByPin(InterfacePins.OUTPUT_PIN, new GridPoint(20, 16), 0, NO_PARAMETERS, "C");
        List<ElementId> everything = body.document.elements().stream().map(DocumentElement::id).toList();
        return DefinitionBuilder.fromSelection(body.document, everything, HALF_ADDER_ID, "Semisumador",
                DefinitionBuilder.defaultPinOrder(body.document, everything), body.registry).root();
    }

    /**
     * «Ordenar circuito», before: a 2-input AND with its switches and LED placed by hand, a little crooked: the parts
     * out of line and the wires of the inputs with steps.
     */
    static CircuitDocument tidyBefore() {
        Builder builder = new Builder("Ordenar circuito");
        Component gate = builder.placeAt(StandardComponentTypes.AND, 13, 7, "");
        builder.placeByPin(StandardComponentTypes.SWITCH, new GridPoint(5, 4), 0, ON, "A");
        builder.route(new GridPoint(5, 4), new GridPoint(9, 4), new GridPoint(9, 7), new GridPoint(13, 7));
        builder.placeByPin(StandardComponentTypes.SWITCH, new GridPoint(4, 13), 0, ON, "B");
        builder.route(new GridPoint(4, 13), new GridPoint(10, 13), new GridPoint(10, 11), new GridPoint(13, 11));
        GridPoint output = builder.pin(gate, 2);
        GridPoint ledPin = output.translate(5, -3);
        builder.route(output, output.translate(5, 0), ledPin);
        builder.placeByPin(StandardComponentTypes.LED, ledPin, 0, Map.of("color", "green"), "Y");
        return builder.document;
    }

    /** «Ordenar circuito», after: {@link #tidyBefore} as «Ordenar circuito» leaves it. */
    static CircuitDocument tidyAfter() {
        CircuitDocument before = tidyBefore();
        ComponentTypeRegistry registry = StandardComponentTypes.registry();
        Set<ElementId> everything = before.elements().stream().map(DocumentElement::id)
                .collect(Collectors.toUnmodifiableSet());
        CircuitDocument after = CircuitTidy.plan(before, registry, everything, CircuitTidyOptions.ALL,
                TextBoxes.NONE, () -> false, stage -> { }).orElseThrow().after();
        after.setName(before.name());
        return after;
    }
}
