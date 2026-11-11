package cl.almejo.vsim.ui.whatsnew;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.document.StickyColor;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.core.document.StickyText;
import cl.almejo.vsim.core.document.TextLabel;
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
        builders.put("continuous-wire.vsim", TipCircuits::continuousWire);
        builders.put("wire-segment-select.vsim", TipCircuits::wireSegmentSelect);
        builders.put("duplicate-before.vsim", () -> duplicate(1));
        builders.put("duplicate-after.vsim", () -> duplicate(2));
        builders.put("next-problem.vsim", TipCircuits::nextProblem);
        builders.put("clean-wires-before.vsim", TipCircuits::cleanWiresBefore);
        builders.put("clean-wires-after.vsim", TipCircuits::cleanWiresAfter);
        builders.put("find.vsim", TipCircuits::find);
        builders.put("find-action.vsim", TipCircuits::findAction);
        builders.put("split-view.vsim", TipCircuits::splitView);
        builders.put("place-several-before.vsim", () -> placeSeveral(1));
        builders.put("place-several-after.vsim", () -> placeSeveral(4));
        builders.put("slide-wire-before.vsim", () -> slideWire(8));
        builders.put("slide-wire-after.vsim", () -> slideWire(12));
        builders.put("tunnels.vsim", TipCircuits::tunnels);
        builders.put("zoom-to-fit.vsim", TipCircuits::zoomToFit);
        builders.put("arrow-keys-before.vsim", () -> arrowKeys(5));
        builders.put("arrow-keys-after.vsim", () -> arrowKeys(4));
        builders.put("edit-in-place-before.vsim", () -> editInPlace("L1"));
        builders.put("edit-in-place-after.vsim", () -> editInPlace("Y"));
        builders.put("frame-selection.vsim", TipCircuits::frameSelection);
        builders.put("sticky-note.vsim", TipCircuits::stickyNote);
        return builders;
    }

    /**
     * The circuits whose picture is about a problem, with how many rows the Problems list shows for them; every other
     * tip circuit shows none.
     */
    static final Map<String, Integer> PROBLEMS_SHOWN = Map.of("next-problem.vsim", 1);

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

        /** A 1-bit switch whose box starts at ({@code left}, {@code top}); its output is at (left + 4, top + 1). */
        void switchAt(int left, int top, String label, boolean on) {
            place(StandardComponentTypes.SWITCH, new GridPoint(left, top), Rotation.R0, on ? ON : OFF, label);
        }

        /** An LED whose box starts at ({@code left}, {@code top}); its input is at (left + 1, top + 3), below. */
        void ledAt(int left, int top, String label) {
            place(StandardComponentTypes.LED, new GridPoint(left, top), Rotation.R0, NO_PARAMETERS, label);
        }

        /**
         * A 2-input gate at ({@code left}, {@code top}): inputs (left, top) and (left, top + 4), output
         * (left + 4, top + 2).
         */
        void gate(String typeId, int left, int top) {
            placeAt(typeId, left, top, "");
        }

        /** A NOT (a negated buffer) at ({@code left}, {@code top}): input (left, top + 2), output (left + 4, top + 2).
         */
        void not(int left, int top) {
            place(StandardComponentTypes.BUFFER, new GridPoint(left, top), Rotation.R0,
                    Map.of(StandardComponentTypes.NEGATE, true), "");
        }

        /** Straight wires through the points {@code x0, y0, x1, y1, …}. */
        void wire(int... coordinates) {
            for (int index = 2; index + 1 < coordinates.length; index += 2) {
                document.add(new Wire(document.newWireId(),
                        new GridPoint(coordinates[index - 2], coordinates[index - 1]),
                        new GridPoint(coordinates[index], coordinates[index + 1])));
            }
        }

        /** An LED turned a quarter clockwise, its input (now on its left) at ({@code pinX}, {@code pinY}). */
        void ledTurnedAt(int pinX, int pinY) {
            GridPoint input = registry.require(StandardComponentTypes.LED).pin(0).position();
            GridPoint turned = Rotation.R90.apply(input);
            place(StandardComponentTypes.LED, new GridPoint(pinX - turned.x(), pinY - turned.y()), Rotation.R90,
                    NO_PARAMETERS, "");
        }

        /** A wire tunnel of {@code typeId} named {@code name} with its pin at {@code pinPoint}. */
        void tunnel(String typeId, GridPoint pinPoint, String name) {
            placeByPin(typeId, pinPoint, 0, Map.of("name", name), "");
        }

        /**
         * The half adder of {@link #wireSegmentSelect()} shifted {@code shift} units to the right: switches A and B,
         * an XOR and an AND, the sum and carry LEDs. Its gates are at (16 + shift, 4) and (16 + shift, 10).
         */
        void halfAdder(int shift) {
            switchAt(2 + shift, 3, "A", true);
            switchAt(2 + shift, 7, "B", false);
            gate(StandardComponentTypes.XOR, 16 + shift, 4);
            gate(StandardComponentTypes.AND, 16 + shift, 10);
            wire(6 + shift, 4, 10 + shift, 4);
            wire(10 + shift, 4, 16 + shift, 4);
            wire(10 + shift, 4, 10 + shift, 10, 16 + shift, 10);
            wire(6 + shift, 8, 12 + shift, 8);
            wire(12 + shift, 8, 16 + shift, 8);
            wire(12 + shift, 8, 12 + shift, 14, 16 + shift, 14);
            wire(20 + shift, 6, 26 + shift, 6, 26 + shift, 4);
            ledAt(25 + shift, 1, "S");
            wire(20 + shift, 12, 30 + shift, 12, 30 + shift, 10);
            ledAt(29 + shift, 7, "C");
        }

        void text(int x, int y, String text) {
            document.add(new TextLabel(document.newLabelId(), new GridPoint(x, y), text));
        }

        private void place(String typeId, GridPoint position, Rotation rotation, Map<String, Object> parameters,
                           String label) {
            document.add(registry.require(typeId).newComponent(document.newComponentId(), position, rotation,
                    parameters, label));
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
        return tidied(tidyBefore());
    }

    /** {@code before} as Editar ▸ «Ordenar circuito» leaves it: its default options, the canvas's text boxes. */
    private static CircuitDocument tidied(CircuitDocument before) {
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
     * «Cable continuo y el codo», to try: a switch and an LED higher up, not wired yet, so «Probarlo» leaves the user
     * the wire to draw (and its elbow to flip with Espacio).
     */
    static CircuitDocument continuousWire() {
        Builder builder = new Builder("Cable continuo y el codo");
        builder.switchAt(2, 7, "A", true);
        builder.ledAt(13, 2, "Y");
        return builder.document;
    }

    /** «Doble clic: un tramo»: a half adder whose inputs fan out with T junctions: a wire has a stretch and a net. */
    static CircuitDocument wireSegmentSelect() {
        Builder builder = new Builder("Doble clic: un tramo; triple: la red");
        builder.switchAt(2, 3, "A", true);
        builder.switchAt(2, 7, "B", false);
        builder.gate(StandardComponentTypes.XOR, 16, 4);
        builder.gate(StandardComponentTypes.AND, 16, 10);
        builder.wire(6, 4, 10, 4);
        builder.wire(10, 4, 16, 4);
        builder.wire(10, 4, 10, 10, 16, 10);
        builder.wire(6, 8, 12, 8);
        builder.wire(12, 8, 16, 8);
        builder.wire(12, 8, 12, 14, 16, 14);
        builder.wire(20, 6, 26, 6, 26, 4);
        builder.ledAt(25, 1, "S");
        builder.wire(20, 12, 30, 12, 30, 10);
        builder.ledAt(29, 7, "C");
        return builder.document;
    }

    /** «Duplicar»: a switch, a NOT and an LED in a row; after, the same row twice. */
    static CircuitDocument duplicate(int rows) {
        Builder builder = new Builder("Duplicar sin el portapapeles");
        for (int row = 0; row < rows; row++) {
            int top = row * 8;
            builder.switchAt(2, top + 4, "", true);
            builder.wire(6, top + 5, 8, top + 5);
            builder.not(8, top + 3);
            builder.wire(12, top + 5, 16, top + 5, 16, top + 3);
            builder.ledAt(15, top, "");
        }
        return builder.document;
    }

    /**
     * «Saltar al próximo problema»: the outputs of an AND and an OR tied to one LED, a usual first mistake. With A = 1
     * and B = 0 the AND gives 0 and the OR 1: a short circuit, the only row of the Problems list.
     */
    static CircuitDocument nextProblem() {
        Builder builder = new Builder("Saltar al próximo problema");
        builder.switchAt(2, 3, "A", true);
        builder.switchAt(2, 11, "B", false);
        builder.gate(StandardComponentTypes.AND, 14, 4);
        builder.gate(StandardComponentTypes.OR, 14, 10);
        builder.wire(6, 4, 10, 4);
        builder.wire(10, 4, 14, 4);
        builder.wire(10, 4, 10, 10, 14, 10);
        builder.wire(6, 12, 12, 12);
        builder.wire(12, 12, 12, 8, 14, 8);
        builder.wire(12, 12, 12, 14, 14, 14);
        builder.wire(18, 6, 22, 6, 22, 9);
        builder.wire(18, 12, 22, 12, 22, 9);
        builder.wire(22, 9, 26, 9, 26, 7);
        builder.ledAt(25, 4, "Y");
        return builder.document;
    }

    /**
     * «Limpiar cables», before: a switch, a NOT and an LED, the wires cut in pieces in a straight line and two loose
     * ends hanging from them (one with a corner).
     */
    static CircuitDocument cleanWiresBefore() {
        Builder builder = new Builder("Limpiar cables");
        builder.switchAt(2, 4, "A", true);
        builder.not(10, 3);
        builder.ledAt(17, 0, "Y");
        builder.wire(6, 5, 8, 5);
        builder.wire(8, 5, 10, 5);
        builder.wire(8, 5, 8, 8);
        builder.wire(14, 5, 16, 5);
        builder.wire(16, 5, 18, 5, 18, 3);
        builder.wire(16, 5, 16, 8, 19, 8);
        return builder.document;
    }

    /** «Limpiar cables», after: {@link #cleanWiresBefore} as «Ordenar circuito» leaves it. */
    static CircuitDocument cleanWiresAfter() {
        return tidied(cleanWiresBefore());
    }

    /** «Buscar»: Y = A·B + C, its parts labelled and the formula as a text, all of which the search finds. */
    static CircuitDocument find() {
        Builder builder = new Builder("Buscar");
        builder.switchAt(2, 2, "A", true);
        builder.switchAt(2, 6, "B", true);
        builder.switchAt(2, 12, "C", false);
        builder.wire(6, 3, 10, 3);
        builder.wire(6, 7, 10, 7);
        builder.gate(StandardComponentTypes.AND, 10, 3);
        builder.wire(14, 5, 18, 5);
        builder.wire(6, 13, 16, 13, 16, 9, 18, 9);
        builder.gate(StandardComponentTypes.OR, 18, 5);
        builder.wire(22, 7, 26, 7, 26, 5);
        builder.ledAt(25, 2, "Y");
        builder.text(19, 13, "Y = A·B + C");
        return builder.document;
    }

    /** «Buscar una acción»: any small circuit to try the palette on; two switches, an XOR and an LED. */
    static CircuitDocument findAction() {
        Builder builder = new Builder("Buscar una acción");
        builder.switchAt(2, 3, "A", true);
        builder.switchAt(2, 7, "B", false);
        builder.wire(6, 4, 12, 4);
        builder.wire(6, 8, 12, 8);
        builder.gate(StandardComponentTypes.XOR, 12, 4);
        builder.wire(16, 6, 22, 6, 22, 4);
        builder.ledAt(21, 1, "Y");
        return builder.document;
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

    /**
     * «Colocar varias seguidas»: four switches whose wires end where a LED turned with {@code edit.rotateClockwise}
     * fits; before, the first LED is placed; after, Mayús+clic placed the other three.
     */
    private static CircuitDocument placeSeveral(int leds) {
        Builder builder = new Builder("Colocar varias seguidas");
        for (int row = 0; row < 4; row++) {
            int pinRow = 2 + row * 3;
            builder.switchAt(2, pinRow - 1, "", row % 2 == 0);
            builder.wire(6, pinRow, 12, pinRow);
            if (row < leds) {
                builder.ledTurnedAt(12, pinRow);
            }
        }
        return builder.document;
    }

    /**
     * «Deslizar un cable de costado»: a switch, a NOT and an LED, the wire between the first two going around a
     * vertical stretch at {@code stretchX}, which Alt+drag slides sideways (8 before, 12 after).
     */
    private static CircuitDocument slideWire(int stretchX) {
        Builder builder = new Builder("Deslizar un cable de costado");
        builder.switchAt(2, 3, "A", true);
        builder.wire(6, 4, stretchX, 4, stretchX, 10, 16, 10);
        builder.not(16, 8);
        builder.wire(20, 10, 24, 10, 24, 8);
        builder.ledAt(23, 5, "Y");
        return builder.document;
    }

    /** «Túneles»: a switch on a tunnel's entrance far from an exit that feeds an LED: no wire between. */
    private static CircuitDocument tunnels() {
        Builder builder = new Builder("Túneles");
        builder.switchAt(2, 3, "A", true);
        builder.wire(6, 4, 9, 4);
        builder.tunnel(StandardComponentTypes.TUNNEL_ENTRANCE, new GridPoint(9, 4), "A");
        builder.tunnel(StandardComponentTypes.TUNNEL_EXIT, new GridPoint(22, 14), "A");
        builder.wire(22, 14, 28, 14, 28, 12);
        builder.ledAt(27, 9, "Y");
        return builder.document;
    }

    /**
     * «Ver todo y la selección»: a half adder and, far to its right, a lone chain: the zoom that shows both (or just
     * the half adder, the selection) is what the tip is about.
     */
    private static CircuitDocument zoomToFit() {
        Builder builder = new Builder("Ver todo y la selección");
        builder.halfAdder(0);
        builder.switchAt(46, 4, "", true);
        builder.wire(50, 5, 54, 5);
        builder.not(54, 3);
        builder.wire(58, 5, 62, 5, 62, 3);
        builder.ledAt(61, 0, "");
        return builder.document;
    }

    /**
     * «Mover con las flechas»: an AND whose wires jog because it sits one grid step too low; after, ↑ moved it one
     * step (the gate's top at {@code gateTop}: 5 before, 4 after) and the wires run straight.
     */
    private static CircuitDocument arrowKeys(int gateTop) {
        Builder builder = new Builder("Mover con las flechas");
        builder.switchAt(2, 3, "A", true);
        builder.switchAt(2, 7, "B", true);
        builder.gate(StandardComponentTypes.AND, 12, gateTop);
        int offset = gateTop - 4;
        if (offset == 0) {
            builder.wire(6, 4, 12, 4);
            builder.wire(6, 8, 12, 8);
        } else {
            builder.wire(6, 4, 9, 4, 9, 4 + offset, 12, 4 + offset);
            builder.wire(6, 8, 9, 8, 9, 8 + offset, 12, 8 + offset);
        }
        builder.wire(16, 6 + offset, 22, 6 + offset, 22, 4);
        builder.ledAt(21, 1, "");
        return builder.document;
    }

    /** «Editar el texto en el lugar»: a switch and an LED whose label is {@code label} (L1 before, Y after). */
    private static CircuitDocument editInPlace(String label) {
        Builder builder = new Builder("Editar el texto en el lugar");
        builder.switchAt(2, 3, "A", true);
        builder.wire(6, 4, 14, 4);
        builder.ledAt(13, 1, label);
        return builder.document;
    }

    /**
     * «Marcos»: two switches into an XOR and an LED, with a titled frame around just the XOR, with air on every side:
     * the inputs and the LED stay outside, and only the wires going sideways cross its border (none the title bar).
     */
    private static CircuitDocument frameSelection() {
        Builder builder = new Builder("Marcos");
        builder.switchAt(2, 3, "A", true);
        builder.switchAt(2, 7, "B", false);
        builder.wire(6, 4, 12, 4);
        builder.wire(6, 8, 12, 8);
        builder.gate(StandardComponentTypes.XOR, 12, 4);
        builder.wire(16, 6, 22, 6);
        builder.ledTurnedAt(22, 6);
        builder.document.add(new Frame(builder.document.newFrameId(), new GridPoint(9, 1), 11, 9, "A + B",
                FrameTint.NONE, false));
        return builder.document;
    }

    /** «Notas adhesivas»: a switch, a NOT and an LED with a yellow note beside them. */
    private static CircuitDocument stickyNote() {
        Builder builder = new Builder("Notas adhesivas");
        builder.switchAt(2, 5, "A", true);
        builder.wire(6, 6, 8, 6);
        builder.not(8, 4);
        builder.wire(12, 6, 16, 6, 16, 4);
        builder.ledAt(15, 1, "Y");
        builder.document.add(new StickyNote(builder.document.newStickyNoteId(), new GridPoint(22, 1), 8, 6,
                StickyColor.YELLOW, new StickyText("A = 1"), false));
        return builder.document;
    }
}
