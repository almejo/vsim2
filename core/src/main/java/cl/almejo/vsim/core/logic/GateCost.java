package cl.almejo.vsim.core.logic;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a generated circuit costs in gates (the step-4 cost line): the total and the count per component type id
 * ({@code and2}, {@code or3}, {@code xor2}, {@code not}, {@code nand2}, …), in the order AND, OR, XOR, NOT, NAND, NOR
 * and then by number of inputs. Pins and constants are not gates.
 */
public record GateCost(int gateCount, Map<String, Integer> gatesByType) {

    /** The gate families in the order the cost line names them. */
    public static final List<String> FAMILIES = List.of("AND", "OR", "XOR", "NOT", "NAND", "NOR");

    public GateCost {
        gatesByType = Collections.unmodifiableMap(new LinkedHashMap<>(gatesByType));
    }

    /** {@code and3} → {@code and}: the type id without its number of inputs. */
    static String family(String typeId) {
        int end = typeId.length();
        while (end > 0 && Character.isDigit(typeId.charAt(end - 1))) {
            end--;
        }
        return typeId.substring(0, end);
    }
}
