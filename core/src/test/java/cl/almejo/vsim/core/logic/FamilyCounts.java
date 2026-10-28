package cl.almejo.vsim.core.logic;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Test helper: a cost's gate counts per family, keyed by the upper-case names of {@link GateCost#FAMILIES}. */
final class FamilyCounts {

    private FamilyCounts() {
    }

    static Map<String, Integer> of(GateCost cost) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String family : GateCost.FAMILIES) {
            cost.gatesByType().forEach((key, count) -> {
                if (GateCost.familyOfKey(key).equals(family)) {
                    counts.merge(family, count, Integer::sum);
                }
            });
        }
        return Collections.unmodifiableMap(counts);
    }
}
