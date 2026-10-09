package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.paint.LogicGatePainter.GateShape;

import java.util.HashMap;
import java.util.Map;

public final class PainterRegistry {

    private final Map<String, ComponentPainter> painters = new HashMap<>();
    private final ComponentPainter fallback = new GenericBoxPainter();

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
        registry.register(StandardComponentTypes.LED, new LedPainter());
        SevenSegmentsPainter display = new SevenSegmentsPainter();
        registry.register(StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY, display);
        registry.register(StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY_DOUBLE, display);
        registry.register(StandardComponentTypes.TIME_DIAGRAM, new TimeDiagramPainter());
        return registry;
    }

    public void register(String typeId, ComponentPainter painter) {
        painters.put(typeId, painter);
    }

    public ComponentPainter painterFor(String typeId) {
        return painters.getOrDefault(typeId, fallback);
    }

    public boolean hasDedicatedPainter(String typeId) {
        return painters.containsKey(typeId);
    }
}
