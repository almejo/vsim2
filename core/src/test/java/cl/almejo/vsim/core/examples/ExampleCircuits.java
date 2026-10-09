package cl.almejo.vsim.core.examples;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFormat;
import cl.almejo.vsim.core.netlist.PinRef;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.AND2;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.CLOCK;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.FLIP_FLOP_DATA;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.LED;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.NOT;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.OR2;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.SWITCH;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.TIME_DIAGRAM;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.XOR2;

/**
 * Small circuits for trying the M3 features by hand. Run {@link #main} to (re)write the files in
 * {@code examples/m3}; {@code ExampleCircuitsTest} checks that the committed files match these builders.
 * Open one with {@code ./gradlew :ui:run --args="../examples/m3/labels.vsim"}.
 */
public final class ExampleCircuits {

    private static final Rotation[] ALL_ROTATIONS = Rotation.values();

    private ExampleCircuits() {
    }

    /** The examples by file name (without extension), in the order they are written. */
    public static Map<String, Supplier<Builder>> builders() {
        Map<String, Supplier<Builder>> builders = new LinkedHashMap<>();
        builders.put("labels", ExampleCircuits::halfAdderBuilder);
        builders.put("rotated-gates", ExampleCircuits::rotatedGatesBuilder);
        builders.put("short-circuit", ExampleCircuits::shortCircuitBuilder);
        builders.put("selection-playground", ExampleCircuits::selectionPlaygroundBuilder);
        builders.put("windows-demo", ExampleCircuits::windowsDemoBuilder);
        return builders;
    }

    public static CircuitDocument halfAdder() {
        return halfAdderBuilder().document();
    }

    public static CircuitDocument rotatedGates() {
        return rotatedGatesBuilder().document();
    }

    public static CircuitDocument shortCircuit() {
        return shortCircuitBuilder().document();
    }

    public static CircuitDocument selectionPlayground() {
        return selectionPlaygroundBuilder().document();
    }

    public static CircuitDocument windowsDemo() {
        return windowsDemoBuilder().document();
    }

    public static void main(String[] arguments) throws IOException {
        Path directory = Path.of(arguments.length > 0 ? arguments[0] : "../examples/m3");
        Files.createDirectories(directory);
        CircuitFormat format = new CircuitFormat(StandardComponentTypes.registry());
        for (Map.Entry<String, Supplier<Builder>> entry : builders().entrySet()) {
            Path file = directory.resolve(entry.getKey() + ".vsim");
            Files.writeString(file, format.write(entry.getValue().get().document()) + "\n");
            System.out.println("wrote " + file);
        }
    }

    // 1. Half adder with component labels and free text.
    static Builder halfAdderBuilder() {
        Builder builder = new Builder("labels");
        Component switchA = builder.placeByBox(SWITCH, 2, 4, Rotation.R0, Map.of("value", "1"), "A");
        Component switchB = builder.placeByBox(SWITCH, 2, 12, Rotation.R0, Map.of("value", "0"), "B");
        Component xorGate = builder.placeByBox(XOR2, 16, 2, Rotation.R0, Map.of(), "U1");
        Component andGate = builder.placeByBox(AND2, 16, 12, Rotation.R0, Map.of(), "U2");

        builder.route(new GridPoint(6, 5), new GridPoint(9, 5));
        builder.route(new GridPoint(9, 2), new GridPoint(9, 12));
        builder.route(new GridPoint(9, 2), new GridPoint(16, 2));
        builder.route(new GridPoint(9, 12), new GridPoint(16, 12));
        builder.link(switchA, 0, xorGate, 0);
        builder.link(switchA, 0, andGate, 0);

        builder.route(new GridPoint(6, 13), new GridPoint(12, 13));
        builder.route(new GridPoint(12, 6), new GridPoint(12, 16));
        builder.route(new GridPoint(12, 6), new GridPoint(16, 6));
        builder.route(new GridPoint(12, 16), new GridPoint(16, 16));
        builder.link(switchB, 0, xorGate, 1);
        builder.link(switchB, 0, andGate, 1);

        builder.attach(xorGate, 2, LED, Map.of("color", "green"), "Suma", 4);
        builder.attach(andGate, 2, LED, Map.of("color", "yellow"), "Acarreo", 4);

        builder.text(2, 1, "Medio sumador");
        builder.text(2, 20, "Haz click en A y B");
        return builder;
    }

    // 2. Each gate in the four rotations; every cell has its own drivers so the wires stay straight.
    static Builder rotatedGatesBuilder() {
        Builder builder = new Builder("rotated-gates");
        String[] rowNames = {"NOT", "AND2", "OR2", "XOR2", "Flip-flop D"};
        String[] typeIds = {NOT, AND2, OR2, XOR2, FLIP_FLOP_DATA};
        String[] columnNames = {"R0", "R90", "R180", "R270"};
        Map<String, Object> clockParameters = Map.of("time-up", 40, "time-down", 40);
        for (int column = 0; column < ALL_ROTATIONS.length; column++) {
            builder.text(11 + 24 * column, 1, columnNames[column]);
        }
        for (int row = 0; row < typeIds.length; row++) {
            int centerY = 12 + 24 * row;
            builder.text(1, centerY - 9, rowNames[row]);
            for (int column = 0; column < ALL_ROTATIONS.length; column++) {
                int centerX = 12 + 24 * column;
                String typeId = typeIds[row];
                Component gate = builder.placeByBox(typeId, centerX - 2, centerY - 2, ALL_ROTATIONS[column], Map.of(), "");
                String switchValue = typeId.equals(OR2) ? "0" : "1";
                Map<String, Object> switchParameters = Map.of("value", switchValue);
                switch (typeId) {
                    case NOT -> {
                        builder.attach(gate, 0, CLOCK, clockParameters, "", 2);
                        builder.attach(gate, 1, LED, Map.of(), "", 2);
                    }
                    case FLIP_FLOP_DATA -> {
                        builder.attach(gate, 0, SWITCH, switchParameters, "", 2);
                        builder.attach(gate, 2, CLOCK, clockParameters, "", 2);
                        builder.attach(gate, 1, LED, Map.of(), "", 2);
                    }
                    default -> {
                        builder.attach(gate, 0, CLOCK, clockParameters, "", 2);
                        builder.attach(gate, 1, SWITCH, switchParameters, "", 2);
                        builder.attach(gate, 2, LED, Map.of(), "", 2);
                    }
                }
            }
        }
        return builder;
    }

    // 3. Two shorts: two switches with opposite values on one wire, and a switch tied to its own inverter.
    static Builder shortCircuitBuilder() {
        Builder builder = new Builder("short-circuit");
        Component highSwitch = builder.placeByBox(SWITCH, 2, 2, Rotation.R0, Map.of("value", "1"), "S1");
        Component lowSwitch = builder.placeByBox(SWITCH, 2, 10, Rotation.R0, Map.of("value", "0"), "S2");
        builder.route(new GridPoint(6, 3), new GridPoint(10, 3));
        builder.route(new GridPoint(10, 3), new GridPoint(10, 11));
        builder.route(new GridPoint(6, 11), new GridPoint(10, 11));
        builder.route(new GridPoint(10, 7), new GridPoint(14, 7));
        Component firstLed = builder.placeByPin(LED, new GridPoint(14, 7), 0, Rotation.R90, Map.of("color", "red"), "Corto 1");
        builder.link(highSwitch, 0, lowSwitch, 0);
        builder.link(highSwitch, 0, firstLed, 0);

        Component loneSwitch = builder.placeByBox(SWITCH, 2, 20, Rotation.R0, Map.of("value", "1"), "S3");
        Component inverter = builder.placeByBox(NOT, 12, 19, Rotation.R0, Map.of(), "N1");
        builder.route(new GridPoint(6, 21), new GridPoint(12, 21));
        builder.route(new GridPoint(8, 21), new GridPoint(8, 27));
        builder.route(new GridPoint(8, 27), new GridPoint(24, 27));
        builder.route(new GridPoint(16, 21), new GridPoint(20, 21));
        builder.route(new GridPoint(20, 21), new GridPoint(20, 27));
        Component secondLed = builder.placeByPin(LED, new GridPoint(24, 27), 0, Rotation.R90, Map.of("color", "red"), "Corto 2");
        builder.link(loneSwitch, 0, inverter, 0);
        builder.link(loneSwitch, 0, inverter, 1);
        builder.link(loneSwitch, 0, secondLed, 0);

        builder.text(2, 0, "Prueba F8 / panel Problemas");
        return builder;
    }

    // 4. A blinking chain and a 7-segment display fed by switches, to select, copy, duplicate, rotate and move.
    static Builder selectionPlaygroundBuilder() {
        Builder builder = new Builder("selection-playground");
        builder.text(2, 3, "Selecciona con rectangulo, copia, duplica, rota y mueve");
        buildChain(builder, 10, 3, 30, 1, true);

        String[] switchValues = {"1", "0", "1", "0"};
        Component[] switches = new Component[4];
        for (int index = 0; index < 4; index++) {
            switches[index] = builder.placeByBox(SWITCH, 2, 24 + 4 * index, Rotation.R0,
                    Map.of("value", switchValues[index]), "b" + index);
        }
        Component display = builder.placeByBox(SEVEN_SEGMENTS_DISPLAY, 20, 46, Rotation.R0, Map.of(), "Display");
        for (int index = 0; index < 4; index++) {
            int switchY = 25 + 4 * index;
            int laneX = 16 - 2 * index;
            int arrivalY = 39 + index;
            int displayPinX = 24 - index;
            builder.route(new GridPoint(6, switchY), new GridPoint(laneX, switchY), new GridPoint(laneX, arrivalY),
                    new GridPoint(displayPinX, arrivalY), new GridPoint(displayPinX, 46));
            builder.link(switches[index], 0, display, index);
        }
        builder.text(2, 22, "Interruptores b0..b3");
        builder.text(28, 49, "Display de 7 segmentos");
        return builder;
    }

    // 5. A clock into a chain of slow inverters, watched by a time diagram and LEDs.
    static Builder windowsDemoBuilder() {
        Builder builder = new Builder("windows-demo");
        builder.text(2, 1, "Ventana / Nueva ventana: abre dos ventanas y mira ambas");
        Chain chain = buildChain(builder, 14, 3, 30, 5, true);
        Component diagram = builder.placeByBox(TIME_DIAGRAM, 42, 2, Rotation.R0,
                Map.of("sample-interval", 5, "history", 200), "Diagrama");
        for (int tap = 0; tap <= 3; tap++) {
            int diagramY = 2 + 2 * tap;
            int tapX = chain.tapX(tap);
            builder.route(new GridPoint(tapX, 14), new GridPoint(tapX, diagramY));
            builder.route(new GridPoint(tapX, diagramY), new GridPoint(42, diagramY));
            builder.link(chain.source(tap), chain.sourcePin(tap), diagram, tap);
        }
        return builder;
    }

    /**
     * A clock driving {@code gateCount} inverters in a row at height {@code y}; every net between two stages has a tap
     * (a point on its wire at {@link Chain#tapX}) and, if {@code withLeds}, an LED hanging below it.
     */
    private static Chain buildChain(Builder builder, int y, int gateCount, int halfPeriod, int gateDelay, boolean withLeds) {
        Component clock = builder.placeByPin(CLOCK, new GridPoint(6, y), 0, Rotation.R0,
                Map.of("time-up", halfPeriod, "time-down", halfPeriod), "Reloj");
        List<Component> gates = new ArrayList<>();
        for (int index = 0; index < gateCount; index++) {
            gates.add(builder.placeByBox(NOT, 10 + 8 * index, y - 2, Rotation.R0, Map.of("delay", gateDelay), "N" + (index + 1)));
        }
        builder.route(new GridPoint(6, y), new GridPoint(10, y));
        builder.link(clock, 0, gates.get(0), 0);
        for (int index = 0; index < gateCount - 1; index++) {
            builder.route(new GridPoint(14 + 8 * index, y), new GridPoint(18 + 8 * index, y));
            builder.link(gates.get(index), 1, gates.get(index + 1), 0);
        }
        builder.route(new GridPoint(6 + 8 * gateCount, y), new GridPoint(8 + 8 * gateCount, y));
        Chain chain = new Chain(clock, gates);
        if (withLeds) {
            for (int tap = 0; tap <= gateCount; tap++) {
                int tapX = chain.tapX(tap);
                builder.route(new GridPoint(tapX, y), new GridPoint(tapX, y + 4));
                Component led = builder.placeByPin(LED, new GridPoint(tapX, y + 4), 0, Rotation.R180,
                        Map.of("color", "green"), "L" + tap);
                builder.link(chain.source(tap), chain.sourcePin(tap), led, 0);
            }
        }
        return chain;
    }

    private record Chain(Component clock, List<Component> gates) {

        int tapX(int tap) {
            return 8 + 8 * tap;
        }

        Component source(int tap) {
            return tap == 0 ? clock : gates.get(tap - 1);
        }

        int sourcePin(int tap) {
            return tap == 0 ? 0 : 1;
        }
    }

    /** Places components by pin or by bounding box and routes wires, remembering which pins are meant to be joined. */
    public static final class Builder {

        private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
        private final CircuitDocument document = new CircuitDocument();
        private final List<List<PinRef>> links = new ArrayList<>();

        Builder(String name) {
            document.setName(name);
        }

        public CircuitDocument document() {
            return document;
        }

        /** Pairs of pins the circuit means to connect; the nets of the document must be exactly their closure. */
        public List<List<PinRef>> links() {
            return links;
        }

        /** Places a component so that its bounding box (after rotation) has its top-left corner at the given point. */
        Component placeByBox(String typeId, int left, int top, Rotation rotation, Map<String, Object> parameters, String label) {
            GridSize size = registry.require(typeId).size();
            GridPoint farCorner = rotation.apply(new GridPoint(size.width(), size.height()));
            GridPoint position = new GridPoint(left - Math.min(0, farCorner.x()), top - Math.min(0, farCorner.y()));
            return place(typeId, position, rotation, parameters, label);
        }

        /** Places a component so that its pin {@code pinIndex} lands on {@code pinPoint}. */
        Component placeByPin(String typeId, GridPoint pinPoint, int pinIndex, Rotation rotation,
                             Map<String, Object> parameters, String label) {
            GridPoint local = registry.require(typeId).pin(pinIndex).position();
            GridPoint rotated = rotation.apply(local);
            return place(typeId, new GridPoint(pinPoint.x() - rotated.x(), pinPoint.y() - rotated.y()), rotation, parameters, label);
        }

        private Component place(String typeId, GridPoint position, Rotation rotation, Map<String, Object> parameters, String label) {
            Component component = registry.require(typeId).newComponent(document.newComponentId(), position, rotation, parameters, label);
            document.add(component);
            return component;
        }

        GridPoint pin(Component component, int pinIndex) {
            return registry.require(component.typeId()).pinLocation(component, pinIndex);
        }

        /**
         * Places a driver or load straight out of a pin: a wire of {@code length} units leaves the pin and
         * ends on pin 0 of the new component, which is rotated to face the pin.
         */
        Component attach(Component target, int pinIndex, String typeId, Map<String, Object> parameters, String label, int length) {
            GridPoint direction = outward(target.typeId(), target.rotation(), pinIndex);
            GridPoint start = pin(target, pinIndex);
            GridPoint end = start.translate(direction.x() * length, direction.y() * length);
            GridPoint wanted = new GridPoint(-direction.x(), -direction.y());
            Rotation facing = null;
            for (Rotation candidate : ALL_ROTATIONS) {
                if (candidate.apply(localOutward(typeId, 0)).equals(wanted)) {
                    facing = candidate;
                }
            }
            Component attached = placeByPin(typeId, end, 0, facing, parameters, label);
            route(start, end);
            link(target, pinIndex, attached, 0);
            return attached;
        }

        void route(GridPoint... points) {
            for (int index = 1; index < points.length; index++) {
                if (!points[index - 1].equals(points[index])) {
                    document.add(new Wire(document.newWireId(), points[index - 1], points[index]));
                }
            }
        }

        void link(Component first, int firstPin, Component second, int secondPin) {
            links.add(List.of(new PinRef(first.id(), firstPin), new PinRef(second.id(), secondPin)));
        }

        TextLabel text(int x, int y, String text) {
            TextLabel label = new TextLabel(document.newLabelId(), new GridPoint(x, y), text);
            document.add(label);
            return label;
        }

        private GridPoint outward(String typeId, Rotation rotation, int pinIndex) {
            return rotation.apply(localOutward(typeId, pinIndex));
        }

        /** Unit vector pointing away from the component body at a pin, before rotation. */
        private GridPoint localOutward(String typeId, int pinIndex) {
            ComponentType type = registry.require(typeId);
            if (typeId.startsWith("seven-segments")) {
                return new GridPoint(0, -1);
            }
            GridPoint pin = type.pin(pinIndex).position();
            if (pin.x() == 0) {
                return new GridPoint(-1, 0);
            }
            if (pin.x() == type.size().width()) {
                return new GridPoint(1, 0);
            }
            return new GridPoint(0, pin.y() == 0 ? -1 : 1);
        }
    }
}
