package cl.almejo.vsim.core.logic;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What a generated circuit costs in gates (the step-4 cost line): the total and the count per gate key ({@code and2},
 * {@code or3}, {@code xor2}, {@code not}, {@code nand2}, {@code xnor2}, …: the part's name and, for a gate, its number
 * of inputs; see {@link #key}), in the order AND, OR, XOR, NOT, NAND, NOR, XNOR and then by number of inputs. Pins and
 * constants are not gates.
 */
public record GateCost(int gateCount, Map<String, Integer> gatesByType) {

    /** The gate families in the order the cost line names them. */
    public static final List<String> FAMILIES = List.of("AND", "OR", "XOR", "NOT", "NAND", "NOR", "XNOR");

    /** The types that count as gates; a plain Buffer is one too, but no family (the generator never places one). */
    private static final Set<String> GATE_TYPES = Set.of(StandardComponentTypes.AND, StandardComponentTypes.OR,
            StandardComponentTypes.XOR, StandardComponentTypes.BUFFER);

    public GateCost {
        gatesByType = Collections.unmodifiableMap(new LinkedHashMap<>(gatesByType));
    }

    /**
     * The family of a part of {@code typeId} with {@code parameters}, read from its type and «Negar salida»: an
     * {@code and} with {@code negate} is a {@code NAND}, a negated {@code buffer} a {@code NOT}. Empty for what is not
     * one of the {@link #FAMILIES} (a pin, a constant, a plain Buffer).
     */
    public static Optional<String> family(String typeId, Map<String, Object> parameters) {
        if (!GATE_TYPES.contains(typeId)) {
            return Optional.empty();
        }
        String family = StandardComponentTypes.nameId(typeId, parameters).toUpperCase(Locale.ROOT);
        return FAMILIES.contains(family) ? Optional.of(family) : Optional.empty();
    }

    /**
     * The key a part of {@code typeId} with {@code parameters} is counted under: its name ({@code nand}, see
     * {@link StandardComponentTypes#nameId}) followed by its number of inputs for an AND/OR/XOR ({@code nand2}), the
     * name alone for a one-input part ({@code not}).
     */
    public static String key(String typeId, Map<String, Object> parameters) {
        String name = StandardComponentTypes.nameId(typeId, parameters);
        Object inputs = parameters.get(StandardComponentTypes.GATE_INPUTS);
        return inputs instanceof Integer count && !StandardComponentTypes.BUFFER.equals(typeId) ? name + count : name;
    }

    /** The family of a {@link #key}: {@code nand3} → {@code NAND}, {@code not} → {@code NOT}. */
    public static String familyOfKey(String key) {
        int end = key.length();
        while (end > 0 && Character.isDigit(key.charAt(end - 1))) {
            end--;
        }
        return key.substring(0, end).toUpperCase(Locale.ROOT);
    }
}
