package cl.almejo.vsim.core;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;

import java.util.HashMap;
import java.util.Map;

/** Parameters of the gates for tests: NOT is a Buffer with «Negar salida», NAND an AND with it, and so on. */
public final class GateParameters {

    /** «Negar salida» on: with a Buffer, the NOT; with a 2-input AND, OR or XOR, the NAND, NOR or XNOR. */
    public static final Map<String, Object> NEGATED = Map.of(StandardComponentTypes.NEGATE, true);

    private GateParameters() {
    }

    /** An AND, OR or XOR of {@code count} inputs. */
    public static Map<String, Object> inputs(int count) {
        return Map.of(StandardComponentTypes.GATE_INPUTS, count);
    }

    /** A NAND, NOR or XNOR of {@code count} inputs. */
    public static Map<String, Object> negatedInputs(int count) {
        return Map.of(StandardComponentTypes.GATE_INPUTS, count, StandardComponentTypes.NEGATE, true);
    }

    /** A gate to place: its type id and parameters. */
    public record Gate(String typeId, Map<String, Object> parameters) {
    }

    /**
     * The gate a name stands for, with {@code count} inputs for an AND/OR/XOR family: {@code nand} → an {@code and}
     * with «Negar salida»; {@code not} and {@code buffer} ignore {@code count}. Extra parameters are added.
     */
    public static Gate gate(String name, int count, Map<String, Object> extraParameters) {
        Map<String, Object> parameters = new HashMap<>(extraParameters);
        String typeId = StandardComponentTypes.presets().stream().filter(preset -> preset.id().equals(name))
                .findFirst().map(preset -> {
                    parameters.putAll(preset.parameters());
                    return preset.typeId();
                }).orElse(name);
        if (!typeId.equals(StandardComponentTypes.BUFFER)) {
            parameters.put(StandardComponentTypes.GATE_INPUTS, count);
        }
        return new Gate(typeId, Map.copyOf(parameters));
    }
}
