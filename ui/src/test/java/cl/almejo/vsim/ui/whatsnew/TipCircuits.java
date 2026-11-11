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
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBuilder;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.wiring.CanvasTextBoxes;

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
        builders.put("split-view.vsim", TipCircuits::splitView);
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
     * «Ordenar circuito», before: Y = A · B + C placed by hand and left messy: the switches, the gates and the LED out
     * of line, the wires with steps, two loose wire ends (one up from A's wire, one past the LED's corner) and C's
     * wire in two pieces.
     */
    static CircuitDocument tidyBefore() {
        Builder builder = new Builder("Ordenar circuito");
        builder.placeAt(StandardComponentTypes.AND, 11, 6, "");
        builder.placeAt(StandardComponentTypes.OR, 20, 11, "");
        builder.placeByPin(StandardComponentTypes.SWITCH, new GridPoint(4, 4), 0, ON, "A");
        builder.route(new GridPoint(4, 4), new GridPoint(9, 4), new GridPoint(9, 6), new GridPoint(11, 6));
        builder.route(new GridPoint(9, 4), new GridPoint(9, 1));
        builder.placeByPin(StandardComponentTypes.SWITCH, new GridPoint(3, 12), 0, ON, "B");
        builder.route(new GridPoint(3, 12), new GridPoint(7, 12), new GridPoint(7, 10), new GridPoint(11, 10));
        builder.placeByPin(StandardComponentTypes.SWITCH, new GridPoint(5, 18), 0, OFF, "C");
        builder.route(new GridPoint(5, 18), new GridPoint(10, 18), new GridPoint(17, 18), new GridPoint(17, 15),
                new GridPoint(20, 15));
        builder.route(new GridPoint(15, 8), new GridPoint(17, 8), new GridPoint(17, 11), new GridPoint(20, 11));
        builder.route(new GridPoint(24, 13), new GridPoint(27, 13), new GridPoint(30, 13));
        builder.route(new GridPoint(27, 13), new GridPoint(27, 9));
        builder.placeByPin(StandardComponentTypes.LED, new GridPoint(27, 9), 0, Map.of("color", "green"), "Y");
        return builder.document;
    }

    /**
     * «Ordenar circuito», after: {@link #tidyBefore} as Editar ▸ «Ordenar circuito» leaves it with its default options
     * (what a fresh install's preferences hold) and the canvas's text boxes.
     */
    static CircuitDocument tidyAfter() {
        CircuitDocument before = tidyBefore();
        ComponentTypeRegistry registry = StandardComponentTypes.registry();
        Set<ElementId> everything = before.elements().stream().map(DocumentElement::id)
                .collect(Collectors.toUnmodifiableSet());
        CircuitDocument after = CircuitTidy.plan(before, registry, everything,
                AppPreferences.inMemory().tidyOptions(), new CanvasTextBoxes(registry), () -> false, stage -> { })
                .orElseThrow().after();
        after.setName(before.name());
        return after;
    }

    /**
     * «Dividir la vista»: two switches into an XOR, then a NOT and an LED. The XOR sits in the middle of the circuit,
     * so the picture's zoomed-in pane (which shows the middle) shows it big.
     */
    static CircuitDocument splitView() {
        Builder builder = new Builder("Dividir la vista");
        builder.placeByPin(StandardComponentTypes.SWITCH, new GridPoint(4, 6), 0, ON, "A");
        builder.placeByPin(StandardComponentTypes.SWITCH, new GridPoint(4, 10), 0, ON, "B");
        builder.placeAt(StandardComponentTypes.XOR, 9, 6, "");
        builder.route(new GridPoint(4, 6), new GridPoint(9, 6));
        builder.route(new GridPoint(4, 10), new GridPoint(9, 10));
        builder.route(new GridPoint(13, 8), new GridPoint(15, 8));
        Component inverter = builder.placeByPin(StandardComponentTypes.BUFFER, new GridPoint(15, 8), 0,
                Map.of(StandardComponentTypes.NEGATE, true), "");
        builder.ledAfter(builder.pin(inverter, 1), 2, "Y");
        return builder.document;
    }
}
