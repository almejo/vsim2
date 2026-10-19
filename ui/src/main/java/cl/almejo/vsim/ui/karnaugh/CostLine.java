package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.ui.i18n.Messages;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The step-4 cost line: «3 AND, 2 OR, 1 NOT (6 compuertas)», gate families in a fixed order. */
final class CostLine {

    /** Type id prefixes in display order; a type belongs to the longest one it starts with («nand2» is a NAND). */
    private static final List<String> FAMILIES = List.of("and", "or", "nand", "nor", "xor", "not");
    private static final Map<String, String> FAMILY_KEYS = Map.of("and", "karnaugh.cost.family.and",
            "or", "karnaugh.cost.family.or", "nand", "karnaugh.cost.family.nand", "nor", "karnaugh.cost.family.nor",
            "xor", "karnaugh.cost.family.xor", "not", "karnaugh.cost.family.not");

    private CostLine() {
    }

    static String text(Map<String, Integer> gatesByType) {
        Map<String, Integer> byFamily = new LinkedHashMap<>();
        for (String family : FAMILIES) {
            byFamily.put(family, 0);
        }
        int total = 0;
        for (Map.Entry<String, Integer> entry : gatesByType.entrySet()) {
            String family = familyOf(entry.getKey());
            if (family != null) {
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

    /** The gate family of a type id, or null for what is not a gate (a constant, a pin). */
    private static String familyOf(String typeId) {
        String longestMatch = null;
        for (String family : FAMILIES) {
            String digits = typeId.substring(Math.min(family.length(), typeId.length()));
            if (typeId.startsWith(family) && digits.chars().allMatch(Character::isDigit)
                    && (longestMatch == null || family.length() > longestMatch.length())) {
                longestMatch = family;
            }
        }
        return longestMatch;
    }
}
