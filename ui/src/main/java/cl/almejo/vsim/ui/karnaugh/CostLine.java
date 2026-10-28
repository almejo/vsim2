package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.logic.GateCost;
import cl.almejo.vsim.ui.i18n.Messages;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The step-4 cost line: «3 AND, 2 OR, 1 NOT (6 compuertas)», gate families in a fixed order. */
final class CostLine {

    /** The families ({@link GateCost#FAMILIES}) in display order. */
    private static final List<String> FAMILIES = List.of("AND", "OR", "NAND", "NOR", "XOR", "XNOR", "NOT");
    private static final Map<String, String> FAMILY_KEYS = Map.of("AND", "karnaugh.cost.family.and",
            "OR", "karnaugh.cost.family.or", "NAND", "karnaugh.cost.family.nand", "NOR", "karnaugh.cost.family.nor",
            "XOR", "karnaugh.cost.family.xor", "XNOR", "karnaugh.cost.family.xnor", "NOT", "karnaugh.cost.family.not");

    private CostLine() {
    }

    /** {@code gatesByType}: counts by {@linkplain GateCost#key gate key} ({@code and2}, {@code not}, …). */
    static String text(Map<String, Integer> gatesByType) {
        Map<String, Integer> byFamily = new LinkedHashMap<>();
        for (String family : FAMILIES) {
            byFamily.put(family, 0);
        }
        int total = 0;
        for (Map.Entry<String, Integer> entry : gatesByType.entrySet()) {
            String family = GateCost.familyOfKey(entry.getKey());
            if (byFamily.containsKey(family)) {
                byFamily.merge(family, entry.getValue(), Integer::sum);
                total += entry.getValue();
            }
        }
        if (total == 0) {
            return Messages.text("karnaugh.cost.none");
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : byFamily.entrySet()) {
            if (entry.getValue() > 0) {
                parts.add(Messages.format("karnaugh.cost.part", String.valueOf(entry.getValue()),
                        Messages.text(FAMILY_KEYS.get(entry.getKey()))));
            }
        }
        return Messages.format("karnaugh.cost.total", String.join(", ", parts), total);
    }
}
