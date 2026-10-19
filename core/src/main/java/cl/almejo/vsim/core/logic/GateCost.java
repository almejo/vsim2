package cl.almejo.vsim.core.logic;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What a generated circuit costs in gates (the step-4 cost line): the total and the count per component type id
 * ({@code and2}, {@code or3}, {@code not}, {@code nand2}, …), in the order AND, OR, NOT, NAND, NOR and then by number
 * of inputs. Pins and constants are not gates.
 */
public record GateCost(int gateCount, Map<String, Integer> gatesByType) {

    /** The gate families in the order the cost line names them. */
    public static final List<String> FAMILIES = List.of("AND", "OR", "NOT", "NAND", "NOR");

    public GateCost {
        gatesByType = Collections.unmodifiableMap(new LinkedHashMap<>(gatesByType));
    }

    /**
     * The counts per family («3 AND, 2 OR, 1 NOT»), keyed by the upper-case names of {@link #FAMILIES}, in that order;
     * families without gates are left out.
     */
    public Map<String, Integer> familyCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String family : FAMILIES) {
            String prefix = family.toLowerCase(Locale.ROOT);
            gatesByType.forEach((type, count) -> {
                if (family(type).equals(prefix)) {
                    counts.merge(family, count, Integer::sum);
                }
            });
        }
        return Collections.unmodifiableMap(counts);
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
