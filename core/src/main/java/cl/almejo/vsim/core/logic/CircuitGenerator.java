package cl.almejo.vsim.core.logic;

import cl.almejo.vsim.core.command.TidyWiresCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.logic.LogicNetwork.ConstantSignal;
import cl.almejo.vsim.core.logic.LogicNetwork.Gate;
import cl.almejo.vsim.core.logic.LogicNetwork.GateSignal;
import cl.almejo.vsim.core.logic.LogicNetwork.InputSignal;
import cl.almejo.vsim.core.logic.LogicNetwork.Signal;
import cl.almejo.vsim.core.wiring.TidyOptions;
import cl.almejo.vsim.core.wiring.WireTidy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Builds the circuit of a minimized table (spec §5): input pins on the left and output pins on the right, labelled
 * with the table's column names in table order, the inverters of the inputs next to them, then the gates in columns
 * by depth (terms, then sums; trees of 2..4-input gates for wide ones), constants for constant outputs, and a frame
 * titled with the table's title around everything.
 * <p>
 * Layout: every part sits in a column; between two columns runs a channel. A net that skips columns crosses each one
 * as a straight horizontal wire (a «feedthrough») in a gap between the parts. In a channel each net gets its own
 * vertical track (the classic rails of the inputs are the tracks of the channel before the first gates) with
 * horizontal wires from its source and to its loads; a net whose only load is level with its source is a straight
 * wire. Tracks are ordered so that a horizontal wire never touches another net's wire (a net leaving at the height
 * where another arrives gets the track to its left); when that is impossible the columns to the right move down a
 * little. Wires only cross, so nothing joins what it should not. The wires are then tidied with the «Ordenar cables»
 * rules ({@link WireTidy}).
 */
public final class CircuitGenerator {

    /** Grid units between two parts of a column (a feedthrough counts as a part of height 0). */
    private static final int GAP = 2;
    /** Grid units between a channel's edge and its first or last track. */
    private static final int CHANNEL_MARGIN = 2;
    /** Grid units from one input (or output) pin to the next: the lower one's label sits in between. */
    private static final int PIN_PITCH = 5;
    /** How far the columns right of a channel may move down to untangle it. */
    private static final int MAXIMUM_SHIFT = 32;
    /** Room around everything inside the frame, like «Enmarcar selección». */
    private static final int FRAME_MARGIN = 2;
    /** The frame's title bar, in grid units. */
    private static final int FRAME_TITLE_BAR = 2;
    /**
     * A generous estimate of a label's width per character in grid units (labels are 10 px text, 8 px per unit; core
     * cannot measure fonts), so channels leave room for the pins' names.
     */
    private static final double LABEL_UNITS_PER_CHARACTER = 1.25;
    /** A label's height above its part, in grid units, with room to spare. */
    private static final int LABEL_HEIGHT = 2;

    private CircuitGenerator() {
    }

    /**
     * The gate counts of the circuit {@link #generate} would build, without building it (the wizard's cost line).
     *
     * @throws IllegalArgumentException when the minimization's form is not the options' form
     */
    public static GateCost cost(Minimization minimization, GeneratorOptions options) {
        return LogicNetwork.of(minimization, options).cost();
    }

    /**
     * The circuit of {@code minimization} (a minimization of {@code spec}) built with {@code options}.
     * {@code registry} must know the standard types.
     *
     * @throws IllegalArgumentException when the minimization is not of {@code spec} or its form is not the options'
     */
    public static GeneratedCircuit generate(TruthTableSpec spec, Minimization minimization, GeneratorOptions options,
                                            ComponentTypeRegistry registry) {
        if (!minimization.spec().equals(spec)) {
            throw new IllegalArgumentException("the minimization is of another table");
        }
        LogicNetwork network = LogicNetwork.of(minimization, options);
        Layout layout = new Layout(spec, network, registry);
        CircuitFragment fragment = layout.build();
        GateCost cost = network.cost();
        return new GeneratedCircuit(fragment, cost.gateCount(), cost.gatesByType());
    }

    /** A part placed in a column: a pin, a gate, a constant or a feedthrough of a net. */
    private static final class Cell {

        final int column;
        final int order;
        /** The net it drives (the net it carries, for a feedthrough); null for an output pin. */
        final Signal driven;
        /** The net of each input pin, in pin order. */
        final List<Signal> loads;
        final boolean feedthrough;
        final Component prototype;
        final int width;
        final int height;
        final int outputOffset;
        final List<Integer> inputOffsets;
        int top;
        int rank;

        Cell(int column, int order, Signal driven, List<Signal> loads, Component prototype,
             ComponentTypeRegistry registry) {
            this.column = column;
            this.order = order;
            this.driven = driven;
            this.loads = List.copyOf(loads);
            this.prototype = prototype;
            this.feedthrough = prototype == null;
            if (feedthrough) {
                width = 0;
                height = 0;
                outputOffset = 0;
                inputOffsets = List.of(0);
            } else {
                ComponentType type = registry.typeOf(prototype).forComponent(prototype);
                width = type.size().width();
                height = type.size().height();
                List<Integer> inputs = new ArrayList<>();
                int output = 0;
                for (PinSpec pin : type.pins()) {
                    if (pin.isInput()) {
                        inputs.add(pin.position().y());
                    } else {
                        output = pin.position().y();
                    }
                }
                inputOffsets = List.copyOf(inputs);
                outputOffset = output;
            }
        }

        int bottom() {
            return top + height;
        }

        int outputY() {
            return top + outputOffset;
        }
    }

    /** One load of a net: input pin {@code slot} of {@code cell}. */
    private record Load(Cell cell, int slot) {

        int y() {
            return cell.top + cell.inputOffsets.get(slot);
        }
    }

    /** A net inside one channel: where it leaves the left column and where it arrives in the right one. */
    private record ChannelNet(Signal net, Cell source, List<Integer> targets) {

        int sourceY() {
            return source.outputY();
        }

        boolean straight() {
            return targets.size() == 1 && targets.get(0) == sourceY();
        }
    }

    private static final class Layout {

        private final TruthTableSpec spec;
        private final LogicNetwork network;
        private final ComponentTypeRegistry registry;
        private final CircuitDocument document = new CircuitDocument();
        private final List<List<Cell>> columns = new ArrayList<>();
        private final Map<Signal, Cell> sources = new HashMap<>();
        /** Per net, its cell in each column it reaches (source or feedthrough). */
        private final Map<Signal, Map<Integer, Cell>> presence = new HashMap<>();
        private final Map<Signal, List<Load>> loads = new LinkedHashMap<>();
        private final List<Cell> gateCells = new ArrayList<>();
        private int outputColumn;
        private int cellCount;

        Layout(TruthTableSpec spec, LogicNetwork network, ComponentTypeRegistry registry) {
            this.spec = spec;
            this.network = network;
            this.registry = registry;
            document.setName(spec.title());
        }

        CircuitFragment build() {
            createCells();
            order();
            place();
            List<List<ChannelNet>> channels = route();
            List<ElementId> wires = draw(channels);
            tidy(wires);
            document.add(frame());
            List<ElementId> everything = document.elements().stream().map(DocumentElement::id).toList();
            return CircuitFragment.of(document, everything, registry);
        }

        // --- cells ---

        private void createCells() {
            List<Gate> gates = network.gates();
            int[] depths = new int[gates.size()];
            int deepest = 1;
            for (int index = 0; index < gates.size(); index++) {
                Gate gate = gates.get(index);
                if (network.isInputInverter(gate)) {
                    depths[index] = 1;
                } else {
                    int depth = 2;
                    for (Signal input : gate.inputs()) {
                        if (input instanceof GateSignal source) {
                            depth = Math.max(depth, depths[source.gate()] + 1);
                        }
                    }
                    depths[index] = depth;
                }
                deepest = Math.max(deepest, depths[index]);
            }
            outputColumn = deepest + 1;
            for (int column = 0; column <= outputColumn; column++) {
                columns.add(new ArrayList<>());
            }
            for (int input = 0; input < spec.inputCount(); input++) {
                addCell(0, new InputSignal(input), List.of(), component(StandardComponentTypes.INPUT_PIN,
                        Map.of("value", "0", "width", 1), spec.inputNames().get(input)));
            }
            for (int index = 0; index < gates.size(); index++) {
                Gate gate = gates.get(index);
                gateCells.add(addCell(depths[index], new GateSignal(index), gate.inputs(),
                        component(gate.typeId(), Map.of(), "")));
            }
            Set<Signal> constants = new HashSet<>();
            for (Signal output : network.outputs()) {
                if (output instanceof ConstantSignal constant && constants.add(constant)) {
                    addCell(outputColumn - 1, constant, List.of(), component(StandardComponentTypes.CONSTANT,
                            Map.of("width", 1, "value", constant.value() ? "1" : "0"), ""));
                }
            }
            for (int output = 0; output < spec.outputCount(); output++) {
                addCell(outputColumn, null, List.of(network.outputs().get(output)),
                        component(StandardComponentTypes.OUTPUT_PIN, Map.of("width", 1),
                                spec.outputNames().get(output)));
            }
            for (Map.Entry<Signal, List<Load>> entry : new ArrayList<>(loads.entrySet())) {
                Signal net = entry.getKey();
                Cell source = sources.get(net);
                int last = entry.getValue().stream().mapToInt(load -> load.cell().column).max().orElseThrow();
                for (int column = source.column + 1; column < last; column++) {
                    Cell feedthrough = new Cell(column, cellCount++, net, List.of(net), null, registry);
                    columns.get(column).add(feedthrough);
                    presence.get(net).put(column, feedthrough);
                }
            }
        }

        private Component component(String typeId, Map<String, Object> parameters, String label) {
            return registry.require(typeId).newComponent(new ElementId("prototype"), new GridPoint(0, 0),
                    Rotation.R0, parameters, label);
        }

        private Cell addCell(int column, Signal driven, List<Signal> inputs, Component prototype) {
            Cell cell = new Cell(column, cellCount++, driven, inputs, prototype, registry);
            columns.get(column).add(cell);
            if (driven != null) {
                sources.put(driven, cell);
                presence.computeIfAbsent(driven, net -> new HashMap<>()).put(column, cell);
            }
            for (int slot = 0; slot < inputs.size(); slot++) {
                loads.computeIfAbsent(inputs.get(slot), net -> new ArrayList<>()).add(new Load(cell, slot));
            }
            return cell;
        }

        // --- order within the columns ---

        /**
         * Inputs and outputs in table order; next to the inputs, each input's feedthrough and then its inverter;
         * every other column by the mean rank of what it feeds in the next column, so trees stay together.
         */
        private void order() {
            columns.get(0).sort(Comparator.comparingInt(cell -> ((InputSignal) cell.driven).input()));
            setRanks(columns.get(0));
            setRanks(columns.get(outputColumn));
            for (int column = outputColumn - 1; column >= 2; column--) {
                int next = column + 1;
                Map<Cell, Double> keys = new HashMap<>();
                for (Cell cell : columns.get(column)) {
                    keys.put(cell, consumersIn(cell.driven, next).stream()
                            .mapToInt(consumer -> consumer.rank).average().orElse(0));
                }
                columns.get(column).sort(Comparator.comparingDouble((Cell cell) -> keys.get(cell))
                        .thenComparingInt(cell -> cell.order));
                setRanks(columns.get(column));
            }
            if (outputColumn > 1) {
                columns.get(1).sort(Comparator.comparingInt(Layout::inputSideKey).thenComparingInt(cell -> cell.order));
                setRanks(columns.get(1));
            }
        }

        /** Column 1: input {@code i}'s feedthrough, then its inverter, in input order; anything else after them. */
        private static int inputSideKey(Cell cell) {
            if (cell.feedthrough && cell.driven instanceof InputSignal input) {
                return 2 * input.input();
            }
            if (!cell.loads.isEmpty() && cell.loads.get(0) instanceof InputSignal input) {
                return 2 * input.input() + 1;
            }
            return Integer.MAX_VALUE;
        }

        private static void setRanks(List<Cell> cells) {
            for (int index = 0; index < cells.size(); index++) {
                cells.get(index).rank = index;
            }
        }

        /** The cells of column {@code column} that take {@code net}: its loads there and its feedthrough. */
        private List<Cell> consumersIn(Signal net, int column) {
            List<Cell> consumers = new ArrayList<>();
            for (Load load : loads.getOrDefault(net, List.of())) {
                if (load.cell().column == column) {
                    consumers.add(load.cell());
                }
            }
            Cell feedthrough = presence.getOrDefault(net, Map.of()).get(column);
            if (feedthrough != null && feedthrough.feedthrough) {
                consumers.add(feedthrough);
            }
            return consumers;
        }

        // --- heights ---

        /**
         * The inputs from the top, each level with its feedthrough (a straight wire) and followed by its inverter; the
         * first gate column packed from the top; every later column near the mean height of its sources (a tree's
         * root beside its leaves), packed in order; the output pins level with what drives them.
         */
        private void place() {
            placeInputSide();
            for (int column = 2; column < outputColumn; column++) {
                List<Cell> cells = columns.get(column);
                if (column == 2) {
                    pack(cells, cells.stream().map(cell -> Integer.MIN_VALUE).toList(), GAP);
                } else {
                    int left = column - 1;
                    pack(cells, cells.stream().map(cell -> desiredTop(cell, left)).toList(), GAP);
                }
            }
            List<Cell> outputs = columns.get(outputColumn);
            pack(outputs, outputs.stream().map(cell -> desiredTop(cell, outputColumn - 1)).toList(),
                    PIN_PITCH - outputs.get(0).height);
        }

        private void placeInputSide() {
            List<Cell> sideCells = outputColumn > 1 ? columns.get(1) : List.of();
            int inputTop = 0;
            int sideBottom = Integer.MIN_VALUE / 2;
            for (Cell pin : columns.get(0)) {
                int input = ((InputSignal) pin.driven).input();
                List<Cell> beside = sideCells.stream()
                        .filter(cell -> inputSideKey(cell) / 2 == input && inputSideKey(cell) != Integer.MAX_VALUE)
                        .toList();
                int top = inputTop;
                if (!beside.isEmpty()) {
                    // The first cell beside the pin is level with the pin's output; it may not touch the cell above.
                    Cell first = beside.get(0);
                    int firstTopRelative = pin.outputOffset - first.outputOffset;
                    top = Math.max(top, sideBottom + GAP - firstTopRelative);
                }
                pin.top = top;
                int level = pin.outputY();
                int cursor = Integer.MIN_VALUE;
                for (Cell cell : beside) {
                    cell.top = cursor == Integer.MIN_VALUE ? level - cell.outputOffset : cursor + GAP;
                    cursor = cell.bottom();
                    sideBottom = cursor;
                }
                inputTop = top + PIN_PITCH;
            }
            for (Cell cell : sideCells) {
                if (inputSideKey(cell) == Integer.MAX_VALUE) {
                    cell.top = sideBottom == Integer.MIN_VALUE / 2 ? 0 : sideBottom + GAP;
                    sideBottom = cell.bottom();
                }
            }
        }

        /** Level with the mean height of the nets the cell takes, as they leave column {@code left}. */
        private int desiredTop(Cell cell, int left) {
            if (cell.loads.isEmpty()) {
                return Integer.MIN_VALUE;
            }
            double sum = 0;
            for (Signal net : cell.loads) {
                sum += presence.get(net).get(left).outputY();
            }
            int center = (int) Math.round(sum / cell.loads.size());
            int middle = cell.feedthrough ? 0 : cell.inputOffsets.size() == 1 ? cell.inputOffsets.get(0)
                    : cell.outputOffset;
            return center - middle;
        }

        /** Each cell at its wish or {@code gap} under the previous one, whichever is lower. */
        private static void pack(List<Cell> cells, List<Integer> wishes, int gap) {
            int bottom = Integer.MIN_VALUE;
            for (int index = 0; index < cells.size(); index++) {
                Cell cell = cells.get(index);
                int wish = wishes.get(index);
                if (bottom == Integer.MIN_VALUE) {
                    cell.top = wish == Integer.MIN_VALUE ? 0 : wish;
                } else {
                    cell.top = Math.max(wish, bottom + gap);
                }
                bottom = cell.bottom();
            }
        }

        // --- channels ---

        /** The nets of every channel with their tracks in order; shifts columns down when a channel needs it. */
        private List<List<ChannelNet>> route() {
            List<List<ChannelNet>> channels = new ArrayList<>();
            for (int channel = 0; channel < outputColumn; channel++) {
                List<ChannelNet> ordered = null;
                for (int shift = 0; shift <= MAXIMUM_SHIFT && ordered == null; shift++) {
                    if (shift > 0) {
                        moveDown(channel + 1, 1);
                    }
                    ordered = trackOrder(channelNets(channel));
                }
                if (ordered == null) {
                    throw new IllegalStateException("could not route channel " + channel);
                }
                channels.add(ordered);
            }
            return channels;
        }

        private void moveDown(int fromColumn, int distance) {
            for (int column = fromColumn; column <= outputColumn; column++) {
                for (Cell cell : columns.get(column)) {
                    cell.top += distance;
                }
            }
        }

        private List<ChannelNet> channelNets(int channel) {
            List<ChannelNet> nets = new ArrayList<>();
            for (Cell cell : columns.get(channel)) {
                if (cell.driven == null) {
                    continue;
                }
                List<Integer> targets = new ArrayList<>();
                for (Load load : loads.getOrDefault(cell.driven, List.of())) {
                    if (load.cell().column == channel + 1) {
                        targets.add(load.y());
                    }
                }
                Cell next = presence.get(cell.driven).get(channel + 1);
                if (next != null && next.feedthrough) {
                    targets.add(next.top);
                }
                if (!targets.isEmpty()) {
                    targets.sort(null);
                    nets.add(new ChannelNet(cell.driven, cell, targets));
                }
            }
            return nets;
        }

        /**
         * The nets that need a track, left to right: a net that leaves at a height where another arrives must be left
         * of it, so its wire stops before the other's starts. Ties go by the height where they leave. Null when the
         * rule goes round in a circle.
         */
        private static List<ChannelNet> trackOrder(List<ChannelNet> nets) {
            List<ChannelNet> tracked = nets.stream().filter(net -> !net.straight()).toList();
            Map<ChannelNet, Set<ChannelNet>> after = new HashMap<>();
            Map<ChannelNet, Integer> before = new HashMap<>();
            Map<Integer, ChannelNet> arrivals = new HashMap<>();
            for (ChannelNet net : tracked) {
                after.put(net, new HashSet<>());
                before.put(net, 0);
                net.targets().forEach(target -> arrivals.put(target, net));
            }
            for (ChannelNet net : tracked) {
                ChannelNet arriving = arrivals.get(net.sourceY());
                if (arriving != null && arriving != net && after.get(net).add(arriving)) {
                    before.merge(arriving, 1, Integer::sum);
                }
            }
            PriorityQueue<ChannelNet> ready = new PriorityQueue<>(Comparator.comparingInt(ChannelNet::sourceY));
            tracked.stream().filter(net -> before.get(net) == 0).forEach(ready::add);
            List<ChannelNet> ordered = new ArrayList<>();
            while (!ready.isEmpty()) {
                ChannelNet net = ready.poll();
                ordered.add(net);
                for (ChannelNet later : after.get(net)) {
                    if (before.merge(later, -1, Integer::sum) == 0) {
                        ready.add(later);
                    }
                }
            }
            if (ordered.size() < tracked.size()) {
                return null;
            }
            List<ChannelNet> all = new ArrayList<>(ordered);
            nets.stream().filter(ChannelNet::straight).forEach(all::add);
            return all;
        }

        // --- drawing ---

        private List<ElementId> draw(List<List<ChannelNet>> channels) {
            int inputOverhang = labelOverhang(columns.get(0));
            int outputOverhang = labelOverhang(columns.get(outputColumn));
            int[] columnX = new int[outputColumn + 1];
            int[] columnWidth = new int[outputColumn + 1];
            for (int column = 0; column <= outputColumn; column++) {
                columnWidth[column] = Math.max(2, columns.get(column).stream().mapToInt(cell -> cell.width).max()
                        .orElse(0));
            }
            List<ElementId> wires = new ArrayList<>();
            for (int column = 0; column <= outputColumn; column++) {
                int x = columnX[column];
                for (Cell cell : columns.get(column)) {
                    if (cell.feedthrough) {
                        wires.add(wire(new GridPoint(x, cell.top), new GridPoint(x + columnWidth[column], cell.top)));
                    } else {
                        Component placed = new Component(document.newComponentId(), cell.prototype.typeId(),
                                new GridPoint(x, cell.top), Rotation.R0, cell.prototype.parameters(),
                                cell.prototype.label());
                        document.add(placed);
                    }
                }
                if (column == outputColumn) {
                    break;
                }
                List<ChannelNet> nets = channels.get(column);
                long trackCount = nets.stream().filter(net -> !net.straight()).count();
                int leftMargin = CHANNEL_MARGIN + (column == 0 ? inputOverhang : 0);
                int rightMargin = CHANNEL_MARGIN + (column == outputColumn - 1 ? outputOverhang : 0);
                int left = x + columnWidth[column];
                int right = left + leftMargin + (int) Math.max(0, trackCount - 1) + rightMargin;
                columnX[column + 1] = right;
                int track = left + leftMargin;
                for (ChannelNet net : nets) {
                    int start = net.source().feedthrough ? left : x + net.source().width;
                    int level = net.sourceY();
                    if (net.straight()) {
                        wires.add(wire(new GridPoint(start, level), new GridPoint(right, level)));
                        continue;
                    }
                    wires.add(wire(new GridPoint(start, level), new GridPoint(track, level)));
                    int highest = Math.min(level, net.targets().get(0));
                    int lowest = Math.max(level, net.targets().get(net.targets().size() - 1));
                    if (highest < lowest) {
                        wires.add(wire(new GridPoint(track, highest), new GridPoint(track, lowest)));
                    }
                    for (int target : net.targets()) {
                        wires.add(wire(new GridPoint(track, target), new GridPoint(right, target)));
                    }
                    track++;
                }
            }
            return wires;
        }

        private ElementId wire(GridPoint start, GridPoint end) {
            Wire wire = new Wire(document.newWireId(), start, end);
            document.add(wire);
            return wire.id();
        }

        /** How far the widest label of these pins reaches past the side of its pin, in whole grid units. */
        private static int labelOverhang(List<Cell> pins) {
            int overhang = 0;
            for (Cell pin : pins) {
                double half = labelWidth(pin.prototype.label()) / 2;
                overhang = Math.max(overhang, (int) Math.ceil(half - pin.width / 2.0));
            }
            return overhang;
        }

        private static double labelWidth(String text) {
            return text.isEmpty() ? 0 : text.length() * LABEL_UNITS_PER_CHARACTER + 1;
        }

        private void tidy(List<ElementId> wires) {
            new TidyWiresCommand(WireTidy.plan(document, registry, Set.copyOf(wires), TidyOptions.DEFAULTS))
                    .apply(document);
        }

        /** Around every part, wire and pin label, with the usual margin and the title bar's room on top. */
        private Frame frame() {
            int left = Integer.MAX_VALUE;
            int top = Integer.MAX_VALUE;
            int right = Integer.MIN_VALUE;
            int bottom = Integer.MIN_VALUE;
            List<Bounds> boxes = new ArrayList<>();
            for (Component component : document.components()) {
                Bounds body = registry.typeOf(component).bounds(component);
                boxes.add(body);
                if (!component.label().isBlank()) {
                    double half = labelWidth(component.label()) / 2;
                    double center = body.x() + body.width() / 2.0;
                    int labelLeft = (int) Math.floor(center - half);
                    int labelRight = (int) Math.ceil(center + half);
                    boxes.add(new Bounds(labelLeft, body.y() - LABEL_HEIGHT, labelRight - labelLeft, LABEL_HEIGHT));
                }
            }
            for (Wire wire : document.wires()) {
                boxes.add(Bounds.of(wire.start(), wire.end()));
            }
            for (Bounds box : boxes) {
                left = Math.min(left, box.x());
                top = Math.min(top, box.y());
                right = Math.max(right, box.x() + box.width());
                bottom = Math.max(bottom, box.y() + box.height());
            }
            int frameTop = top - FRAME_MARGIN - FRAME_TITLE_BAR;
            int frameLeft = left - FRAME_MARGIN;
            return new Frame(document.newFrameId(), new GridPoint(frameLeft, frameTop),
                    Math.max(Frame.MINIMUM_WIDTH, right + FRAME_MARGIN - frameLeft),
                    Math.max(Frame.MINIMUM_HEIGHT, bottom + FRAME_MARGIN - frameTop), spec.title(), FrameTint.NONE,
                    false);
        }
    }
}
