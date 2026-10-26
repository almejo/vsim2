package cl.almejo.vsim.core.logic;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Test helper: a cost's gate counts per family, keyed by the upper-case names of {@link GateCost#FAMILIES}. */
final class FamilyCounts {

    private FamilyCounts() {
    }

    static Map<String, Integer> of(GateCost cost) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String family : GateCost.FAMILIES) {
            String prefix = family.toLowerCase(Locale.ROOT);
            cost.gatesByType().forEach((type, count) -> {
                if (GateCost.family(type).equals(prefix)) {
                    counts.merge(family, count, Integer::sum);
                }
            });
        }
        return Collections.unmodifiableMap(counts);
    }
}
