package cl.almejo.vsim.core.bundled;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.examples.CircuitBuilder;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.PinRef;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The checks every bundled example passes (spec M6.1 §3.3), learned from an audit of hand-drawn examples whose wires
 * shorted switches together: the nets are exactly the joins the builder meant ({@link CircuitBuilder#link}), nothing
 * is shorted, mismatched, dangling or left unconnected, no wire runs over a pin, a body or another wire, no two pins
 * share a point, and no text covers a component, a wire or another text. Texts are measured like the UI's
 * {@code LabelLayout} (10 px sans serif, 8 px per grid unit, a component label 3 px above an upright body or left of a
 * sideways one) with a safety margin, since fonts differ a little between machines. Test code only: {@code core}'s
 * main sources never touch AWT ({@code ArchitectureTest} scans {@code src/main/java}).
 */
public final class ExampleChecks {

    private static final Font FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 10);
    private static final FontRenderContext RENDER_CONTEXT = new FontRenderContext(null, true, true);
    private static final double PIXELS_PER_UNIT = GridPoint.PIXELS_PER_UNIT;
    private static final double LABEL_GAP = 3 / PIXELS_PER_UNIT;
    /** Fonts differ a little between machines: texts are measured 10 % wider plus this margin on every side. */
    private static final double TEXT_MARGIN = 0.1;
    /** Half the width of a drawn wire, in grid units, for the text checks. */
    private static final double WIRE_HALF_WIDTH = 0.2;

    /**
     * The block control inputs that may stay unconnected because they take a rest value (spec M7 §2.1: Cin = 0,
     * EN = 1, CLR = LD = 0, the D of a counter that never loads, a ROM's CS = 1), by block type and pin name, and the 7-segment displays' bus input (the examples feed
     * their displays bit by bit). Any other input alone on its net is a problem.
     */
    private static final Map<String, Set<String>> RESTING_PINS = Map.of(
            StandardComponentTypes.ADDER, Set.of("Cin"),
            StandardComponentTypes.DECODER, Set.of("EN"),
            StandardComponentTypes.REGISTER, Set.of("EN", "CLR"),
            StandardComponentTypes.COUNTER, Set.of("D", "LD", "EN", "CLR"),
            StandardComponentTypes.ROM, Set.of("CS"),
            StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY, Set.of(StandardComponentTypes.DISPLAY_BUS_PIN),
            StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY_DOUBLE, Set.of(StandardComponentTypes.DISPLAY_BUS_PIN));

    private ExampleChecks() {
    }

    /** A bundled example: wired and drawn cleanly, with a title, a «Prueba: …» line and every input, output and block
     * named. */
    public static void assertExample(CircuitBuilder builder) {
        List<String> problems = new ArrayList<>(wiringProblems(builder));
        problems.addAll(explanationProblems(builder));
        assertThat(problems).as("problems of «%s»", builder.document().name()).isEmpty();
    }

    /** The body of a user component (inside an example): wired and drawn cleanly, no title needed. */
    public static void assertCleanBody(CircuitBuilder builder) {
        assertThat(wiringProblems(builder)).as("problems of the body «%s»", builder.document().name()).isEmpty();
    }

    /** Every wiring, geometry and overlap problem of the builder's document, as readable lines. */
    public static List<String> wiringProblems(CircuitBuilder builder) {
        CircuitDocument document = builder.document();
        ComponentTypeRegistry registry = builder.registry();
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        List<String> problems = new ArrayList<>();
        intendedJoins(builder, registry, netlist, problems);
        problems.addAll(layoutProblems(document, registry, netlist));
        return problems;
    }

    /**
     * The checks that need no list of intended joins (used on generated circuits, whose meaning the caller checks
     * with a truth table): shorts, mismatches, unconnected inputs, wires over pins, bodies or wires, pins on pins,
     * loose wire ends and covered texts.
     */
    public static List<String> layoutProblems(CircuitDocument document, ComponentTypeRegistry registry) {
        return layoutProblems(document, registry, new NetlistCompiler(registry).compile(document));
    }

    private static List<String> layoutProblems(CircuitDocument document, ComponentTypeRegistry registry,
                                               Netlist netlist) {
        List<String> problems = new ArrayList<>();
        drivers(document, registry, netlist, problems);
        wiresOverPins(document, registry, problems);
        pinsOnPins(document, registry, problems);
        wireEnds(document, registry, problems);
        wiresOverWires(document, problems);
        wiresThroughBodies(document, registry, problems);
        textOverlaps(document, registry, problems);
        return problems;
    }

    /** A free text equal to the name (the title), one starting with «Prueba: », and a label on every input/output
     * and block. */
    public static List<String> explanationProblems(CircuitBuilder builder) {
        CircuitDocument document = builder.document();
        List<String> problems = new ArrayList<>();
        if (document.labels().stream().noneMatch(label -> label.text().equals(document.name()))) {
            problems.add("no title: a free text equal to «" + document.name() + "»");
        }
        if (document.labels().stream().noneMatch(label -> label.text().startsWith("Prueba: "))) {
            problems.add("no «Prueba: …» text");
        }
        Set<String> named = Set.of(StandardComponentTypes.SWITCH, StandardComponentTypes.LED,
                StandardComponentTypes.CLOCK, StandardComponentTypes.INPUT_PIN, StandardComponentTypes.OUTPUT_PIN,
                StandardComponentTypes.ADDER, StandardComponentTypes.ALU, StandardComponentTypes.COMPARATOR,
                StandardComponentTypes.MUX, StandardComponentTypes.DECODER, StandardComponentTypes.REGISTER,
                StandardComponentTypes.COUNTER, StandardComponentTypes.ROM);
        for (Component component : document.components()) {
            if (named.contains(component.typeId()) && component.label().isBlank()) {
                problems.add(component.typeId() + " " + component.id().value() + " has no label");
            }
        }
        return problems;
    }

    private static void intendedJoins(CircuitBuilder builder, ComponentTypeRegistry registry, Netlist netlist,
                                      List<String> problems) {
        Map<PinRef, Set<PinRef>> expected = new HashMap<>();
        for (Component component : builder.document().components()) {
            for (int pinIndex = 0; pinIndex < registry.typeOf(component).pinCount(); pinIndex++) {
                PinRef pin = new PinRef(component.id(), pinIndex);
                expected.put(pin, new HashSet<>(Set.of(pin)));
            }
        }
        for (List<PinRef> link : builder.links()) {
            Set<PinRef> merged = new HashSet<>(expected.get(link.get(0)));
            merged.addAll(expected.get(link.get(1)));
            merged.forEach(pin -> expected.put(pin, merged));
        }
        Set<Set<PinRef>> wanted = new HashSet<>(expected.values());
        Set<Set<PinRef>> actual = new HashSet<>();
        for (Net net : netlist.nets()) {
            if (net.pins().isEmpty()) {
                problems.add("net " + net.id().value() + " joins no pin (a dangling wire)");
            } else {
                actual.add(new HashSet<>(net.pins()));
            }
        }
        for (Set<PinRef> net : actual) {
            if (!wanted.contains(net)) {
                problems.add("unintended net " + describe(builder.document(), net));
            }
        }
        for (Set<PinRef> net : wanted) {
            if (!actual.contains(net)) {
                problems.add("intended net missing " + describe(builder.document(), net));
            }
        }
    }

    private static void drivers(CircuitDocument document, ComponentTypeRegistry registry, Netlist netlist,
                                List<String> problems) {
        for (Net net : netlist.nets()) {
            if (net.widthMismatch()) {
                problems.add("width mismatch on " + describe(document, net.pins()));
            }
            long drivers = net.pins().stream().filter(pin -> spec(document, registry, pin).isDriver()).count();
            boolean hasInput = net.pins().stream().anyMatch(pin -> spec(document, registry, pin).isInput());
            if (drivers > 1) {
                problems.add("short circuit: " + drivers + " drivers on " + describe(document, net.pins()));
            }
            if (hasInput && drivers == 0 && !isRestingPin(document, registry, net.pins())) {
                problems.add("unconnected input on " + describe(document, net.pins()));
            }
        }
    }

    /** A lone block control input with a rest value ({@link #RESTING_PINS}). */
    private static boolean isRestingPin(CircuitDocument document, ComponentTypeRegistry registry,
                                        Set<PinRef> pins) {
        if (pins.size() != 1) {
            return false;
        }
        PinRef pin = pins.iterator().next();
        Component component = document.component(pin.componentId()).orElseThrow();
        return RESTING_PINS.getOrDefault(component.typeId(), Set.of())
                .contains(spec(document, registry, pin).name());
    }

    private static void wiresOverPins(CircuitDocument document, ComponentTypeRegistry registry,
                                      List<String> problems) {
        for (Wire wire : document.wires()) {
            for (Component component : document.components()) {
                ComponentType type = registry.typeOf(component);
                for (int pinIndex = 0; pinIndex < type.pinCount(); pinIndex++) {
                    GridPoint pin = type.pinLocation(component, pinIndex);
                    if (wire.contains(pin) && !pin.equals(wire.start()) && !pin.equals(wire.end())) {
                        problems.add("wire " + wire.id().value() + " runs over pin " + pinIndex + " of "
                                + name(component));
                    }
                }
            }
        }
    }

    private static void pinsOnPins(CircuitDocument document, ComponentTypeRegistry registry, List<String> problems) {
        Map<GridPoint, Component> owners = new HashMap<>();
        for (Component component : document.components()) {
            ComponentType type = registry.typeOf(component);
            for (int pinIndex = 0; pinIndex < type.pinCount(); pinIndex++) {
                Component previous = owners.putIfAbsent(type.pinLocation(component, pinIndex), component);
                if (previous != null && previous != component) {
                    problems.add("pins of " + name(previous) + " and " + name(component) + " share "
                            + type.pinLocation(component, pinIndex));
                }
            }
        }
    }

    private static void wireEnds(CircuitDocument document, ComponentTypeRegistry registry, List<String> problems) {
        Set<GridPoint> pins = new HashSet<>();
        for (Component component : document.components()) {
            ComponentType type = registry.typeOf(component);
            for (int pinIndex = 0; pinIndex < type.pinCount(); pinIndex++) {
                pins.add(type.pinLocation(component, pinIndex));
            }
        }
        for (Wire wire : document.wires()) {
            for (GridPoint end : List.of(wire.start(), wire.end())) {
                boolean touches = pins.contains(end) || document.wires().stream()
                        .anyMatch(other -> other != wire && other.contains(end));
                if (!touches) {
                    problems.add("wire " + wire.id().value() + " ends at " + end + " on nothing");
                }
            }
        }
    }

    private static void wiresOverWires(CircuitDocument document, List<String> problems) {
        List<Wire> wires = new ArrayList<>(document.wires());
        for (int first = 0; first < wires.size(); first++) {
            for (int second = first + 1; second < wires.size(); second++) {
                if (wires.get(first).overlapsCollinear(wires.get(second))) {
                    problems.add("wires " + wires.get(first).id().value() + " and " + wires.get(second).id().value()
                            + " overlap");
                }
            }
        }
    }

    private static void wiresThroughBodies(CircuitDocument document, ComponentTypeRegistry registry,
                                           List<String> problems) {
        for (Component component : document.components()) {
            Bounds body = registry.typeOf(component).bounds(component);
            for (Wire wire : document.wires()) {
                boolean inside = wire.isHorizontal()
                        ? wire.start().y() > body.y() && wire.start().y() < body.y() + body.height()
                        && wire.start().x() < body.x() + body.width() && wire.end().x() > body.x()
                        : wire.start().x() > body.x() && wire.start().x() < body.x() + body.width()
                        && wire.start().y() < body.y() + body.height() && wire.end().y() > body.y();
                if (inside) {
                    problems.add("wire " + wire.id().value() + " runs through the body of " + name(component));
                }
            }
        }
    }

    private static void textOverlaps(CircuitDocument document, ComponentTypeRegistry registry,
                                     List<String> problems) {
        List<Box> texts = new ArrayList<>();
        for (TextLabel label : document.labels()) {
            texts.add(freeText(label));
        }
        for (Component component : document.components()) {
            if (!component.label().isBlank()) {
                texts.add(componentLabel(component, registry.typeOf(component).bounds(component)));
            }
        }
        for (int first = 0; first < texts.size(); first++) {
            Box text = texts.get(first);
            for (int second = first + 1; second < texts.size(); second++) {
                if (text.overlaps(texts.get(second))) {
                    problems.add("text «" + text.name() + "» covers text «" + texts.get(second).name() + "»");
                }
            }
            for (Component component : document.components()) {
                Bounds body = registry.typeOf(component).bounds(component);
                if (text.overlaps(new Box("", body.x(), body.y(), body.width(), body.height()))) {
                    problems.add("text «" + text.name() + "» covers " + name(component));
                }
            }
            for (Wire wire : document.wires()) {
                Box stroke = new Box("", Math.min(wire.start().x(), wire.end().x()) - WIRE_HALF_WIDTH,
                        Math.min(wire.start().y(), wire.end().y()) - WIRE_HALF_WIDTH,
                        Math.abs(wire.end().x() - wire.start().x()) + 2 * WIRE_HALF_WIDTH,
                        Math.abs(wire.end().y() - wire.start().y()) + 2 * WIRE_HALF_WIDTH);
                if (text.overlaps(stroke)) {
                    problems.add("text «" + text.name() + "» covers wire " + wire.id().value());
                }
            }
        }
    }

    /** A text's box in grid units, with the safety margin. */
    private record Box(String name, double left, double top, double width, double height) {

        boolean overlaps(Box other) {
            return left < other.left + other.width && other.left < left + width
                    && top < other.top + other.height && other.top < top + height;
        }
    }

    private static Box freeText(TextLabel label) {
        double width = Math.max(1, textWidth(label.text()));
        return withMargin(label.text(), label.position().x(), label.position().y(), width, textHeight(label.text()));
    }

    private static Box componentLabel(Component component, Bounds body) {
        double width = textWidth(component.label());
        double height = textHeight(component.label());
        boolean sideways = component.rotation() == Rotation.R90 || component.rotation() == Rotation.R270;
        double left = sideways ? body.x() - LABEL_GAP - width : body.x() + body.width() / 2.0 - width / 2;
        double top = sideways ? body.y() + body.height() / 2.0 - height / 2 : body.y() - LABEL_GAP - height;
        return withMargin(component.label(), left, top, width, height);
    }

    private static Box withMargin(String text, double left, double top, double width, double height) {
        double wider = width * 1.1;
        return new Box(text, left - (wider - width) / 2 - TEXT_MARGIN, top - TEXT_MARGIN, wider + 2 * TEXT_MARGIN,
                height + 2 * TEXT_MARGIN);
    }

    private static double textWidth(String text) {
        return text.isEmpty() ? 0 : FONT.getStringBounds(text, RENDER_CONTEXT).getWidth() / PIXELS_PER_UNIT;
    }

    private static double textHeight(String text) {
        LineMetrics metrics = FONT.getLineMetrics(text.isEmpty() ? " " : text, RENDER_CONTEXT);
        return (metrics.getAscent() + metrics.getDescent()) / PIXELS_PER_UNIT;
    }

    private static PinSpec spec(CircuitDocument document, ComponentTypeRegistry registry, PinRef pin) {
        return registry.typeOf(document.component(pin.componentId()).orElseThrow()).pin(pin.pinIndex());
    }

    private static String describe(CircuitDocument document, Set<PinRef> pins) {
        List<String> names = new ArrayList<>();
        for (PinRef pin : pins) {
            names.add(document.component(pin.componentId()).map(ExampleChecks::name).orElse("?") + "#"
                    + pin.pinIndex());
        }
        names.sort(null);
        return names.toString();
    }

    private static String name(Component component) {
        return component.typeId() + " " + component.id().value()
                + (component.label().isBlank() ? "" : " «" + component.label() + "»");
    }
}
