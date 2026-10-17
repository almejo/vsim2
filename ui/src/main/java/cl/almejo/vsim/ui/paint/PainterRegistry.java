package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.ConstantValue;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.paint.LogicGatePainter.GateShape;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class PainterRegistry {

    private final Map<String, ComponentPainter> painters = new HashMap<>();
    private final ComponentPainter fallback = new GenericBoxPainter();
    private final ComponentPainter subcircuit = new SubcircuitPainter();

    public static PainterRegistry standard() {
        PainterRegistry registry = new PainterRegistry();
        for (int inputs = 2; inputs <= 4; inputs++) {
            registry.register("and" + inputs, new LogicGatePainter(GateShape.AND, false));
            registry.register("or" + inputs, new LogicGatePainter(GateShape.OR, false));
            registry.register("xor" + inputs, new LogicGatePainter(GateShape.XOR, false));
            registry.register("nand" + inputs, new LogicGatePainter(GateShape.AND, true));
            registry.register("nor" + inputs, new LogicGatePainter(GateShape.OR, true));
        }
        registry.register(StandardComponentTypes.NOT, new NotPainter());
        registry.register(StandardComponentTypes.TRI_STATE, new TriStatePainter());
        registry.register(StandardComponentTypes.FLIP_FLOP_DATA, new FlipFlopPainter());
        registry.register(StandardComponentTypes.CLOCK, new ClockPainter());
        registry.register(StandardComponentTypes.SWITCH, new SwitchPainter());
        registry.register(StandardComponentTypes.CONSTANT, new ConstantPainter());
        registry.register(StandardComponentTypes.LED, new LedPainter());
        SevenSegmentsPainter display = new SevenSegmentsPainter();
        registry.register(StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY, display);
        registry.register(StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY_DOUBLE, display);
        registry.register(StandardComponentTypes.TIME_DIAGRAM, new TimeDiagramPainter());
        BusSplitterPainter bus = new BusSplitterPainter();
        registry.register(StandardComponentTypes.BUS_SPLITTER, bus);
        registry.register(StandardComponentTypes.BUS_JOINER, bus);
        InterfacePinPainter interfacePin = new InterfacePinPainter();
        registry.register(InterfacePins.INPUT_PIN, interfacePin);
        registry.register(InterfacePins.OUTPUT_PIN, interfacePin);
        registerBlocks(registry);
        return registry;
    }

    /** The «Bloques» (M7), all drawn by a {@link BlockPainter}. */
    private static void registerBlocks(PainterRegistry registry) {
        registry.register(StandardComponentTypes.ADDER, new BlockPainter("Σ", BlockPainter.Body.CHEVRON));
        registry.register(StandardComponentTypes.REGISTER, new BlockPainter("REG", BlockPainter.Body.RECTANGLE)
                .withLiveText(PainterRegistry::storedValueText));
        registry.register(StandardComponentTypes.COUNTER, new BlockPainter("CNT", BlockPainter.Body.RECTANGLE)
                .withLiveText(PainterRegistry::storedValueText));
    }

    /**
     * What a register or a counter shows under its name: its stored value in its «Mostrar como» format, written like
     * a pin's value box (Z bits and conflicts included); nothing without a state (the palette icon).
     */
    static Optional<String> storedValueText(VisibleState state, Map<String, Object> parameters) {
        if (!(state instanceof VisibleState.StoredValueState stored)) {
            return Optional.empty();
        }
        Object format = parameters.get(ConstantValue.FORMAT);
        return Optional.of(InterfacePinPainter.shownText(stored.value(),
                format == null ? ConstantValue.AUTO : (String) format));
    }

    public void register(String typeId, ComponentPainter painter) {
        painters.put(typeId, painter);
    }

    /** The painter registered for {@code typeId}; {@link SubcircuitPainter} for any {@code subcircuit:} id; a box otherwise. */
    public ComponentPainter painterFor(String typeId) {
        ComponentPainter painter = painters.get(typeId);
        if (painter != null) {
            return painter;
        }
        return SubcircuitTypes.definitionIdOf(typeId).isPresent() ? subcircuit : fallback;
    }

    public boolean hasDedicatedPainter(String typeId) {
        return painters.containsKey(typeId);
    }
}
