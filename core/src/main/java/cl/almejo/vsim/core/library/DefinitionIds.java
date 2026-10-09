package cl.almejo.vsim.core.library;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/** Stable ids for user definitions (spec §5.6): a slug of the name, numbered when taken. */
public final class DefinitionIds {

    /** The id used when nothing of the name survives the slug (a name made of symbols only). */
    public static final String FALLBACK = "component";

    private DefinitionIds() {
    }

    /** {@code "Sumador de 4 bits"} → {@code "sumador-de-4-bits"}, then {@code -2}, {@code -3}, … if taken. */
    public static String slug(String name, Set<String> takenIds) {
        String withoutAccents = Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String slug = withoutAccents.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return unique(slug.isEmpty() ? FALLBACK : slug, takenIds);
    }

    /** {@code baseId} itself when free, otherwise the first free {@code baseId-2}, {@code baseId-3}, … */
    public static String unique(String baseId, Set<String> takenIds) {
        if (!takenIds.contains(baseId)) {
            return baseId;
        }
        for (int suffix = 2; ; suffix++) {
            String candidate = baseId + "-" + suffix;
            if (!takenIds.contains(candidate)) {
                return candidate;
            }
        }
    }
}
