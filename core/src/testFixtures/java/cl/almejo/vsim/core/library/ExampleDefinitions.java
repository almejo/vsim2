package cl.almejo.vsim.core.library;

import cl.almejo.vsim.core.GateParameters;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;

import java.util.List;
import java.util.Map;

/** Small valid definitions for tests, the painter gallery and demos. All are 4 x 4 boxes with pins a (0,2) and y (4,2). */
public final class ExampleDefinitions {

    public static final String INVERTER = "inverter";
    public static final String DOUBLE_INVERTER = "double-inverter";
    public static final String BYTE_BUFFER = "byte-buffer";

    private static final ComponentTypeRegistry STANDARD = StandardComponentTypes.registry();

    private ExampleDefinitions() {
    }

    /** input-pin c1 (pin at 2,2) → NOT c2 (2,2 → 6,2) → output-pin c3 (pin at 6,2). */
    public static ComponentDefinition inverter() {
        Component input = standard(InterfacePins.INPUT_PIN, "c1", 0, 1, "a");
        Component gate = standard(StandardComponentTypes.BUFFER, "c2", 2, 0, "", GateParameters.NEGATED);
        Component output = standard(InterfacePins.OUTPUT_PIN, "c3", 6, 1, "y");
        return new ComponentDefinition(INVERTER, "Inversor", "", new GridSize(4, 4),
                List.of(new DefinitionPin(PinSpec.input("a", 0, 2), input.id()),
                        new DefinitionPin(PinSpec.output("y", 4, 2), output.id())),
                new CircuitFragment(List.of(input, gate, output), List.of(), List.of()));
    }

    /** Two {@link #inverter()} instances in series: input-pin c1, inverters c2 and c3, output-pin c4. */
    public static ComponentDefinition doubleInverter() {
        Component input = standard(InterfacePins.INPUT_PIN, "c1", 0, 1, "a");
        Component first = instance("c2", 2, 0);
        Component second = instance("c3", 6, 0);
        Component output = standard(InterfacePins.OUTPUT_PIN, "c4", 10, 1, "y");
        return new ComponentDefinition(DOUBLE_INVERTER, "Doble inversor", "", new GridSize(4, 4),
                List.of(new DefinitionPin(PinSpec.input("a", 0, 2), input.id()),
                        new DefinitionPin(PinSpec.output("y", 4, 2), output.id())),
                new CircuitFragment(List.of(input, first, second, output), List.of(), List.of()));
    }

    /** An 8-bit pass-through: 8-bit input-pin c1 (pin at 2,2) touching 8-bit output-pin c2 (pin at 2,2). */
    public static ComponentDefinition byteBuffer() {
        Map<String, Object> eightBits = Map.of(InterfacePins.WIDTH, 8);
        Component input = standard(InterfacePins.INPUT_PIN, "c1", 0, 1, "a", eightBits);
        Component output = standard(InterfacePins.OUTPUT_PIN, "c2", 2, 1, "y", eightBits);
        return new ComponentDefinition(BYTE_BUFFER, "Búfer de 8 bits", "", new GridSize(4, 4),
                List.of(new DefinitionPin(PinSpec.input("a", 0, 2, 8), input.id()),
                        new DefinitionPin(PinSpec.output("y", 4, 2, 8), output.id())),
                new CircuitFragment(List.of(input, output), List.of(), List.of()));
    }

    private static Component standard(String typeId, String id, int x, int y, String label) {
        return standard(typeId, id, x, y, label, Map.of());
    }

    private static Component standard(String typeId, String id, int x, int y, String label,
                                      Map<String, Object> parameters) {
        return STANDARD.require(typeId).newComponent(new ElementId(id), new GridPoint(x, y), Rotation.R0, parameters,
                label);
    }

    private static Component instance(String id, int x, int y) {
        return new Component(new ElementId(id), SubcircuitTypes.typeIdOf(INVERTER), new GridPoint(x, y), Rotation.R0,
                Map.of(), "");
    }
}
