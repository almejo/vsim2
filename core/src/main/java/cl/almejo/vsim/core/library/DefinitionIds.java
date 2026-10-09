package cl.almejo.vsim.core.library;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/** Stable ids for user definitions (spec §5.6): a slug of the name, numbered when taken. */
public final class DefinitionIds {

    /** The id used when nothing of the name survives the slug (a name made of symbols only). */
    public static final String FALLBACK = "component";

    public static final int MAX_LENGTH = 64;

    private DefinitionIds() {
    }

    /**
     * {@code "Sumador de 4 bits"} → {@code "sumador-de-4-bits"}, then {@code -2}, {@code -3}, … if taken. Ids become
     * file names, so an id (number included) is at most {@link #MAX_LENGTH} characters, cut at a dash when possible.
     */
    public static String slug(String name, Set<String> takenIds) {
        String lowerCase = (name == null ? "" : name).toLowerCase(Locale.ROOT).replace("ß", "ss");
        String withoutAccents = Normalizer.normalize(lowerCase, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = withoutAccents
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        String baseId = slug.isEmpty() ? FALLBACK : slug;
        String candidate = cut(baseId, MAX_LENGTH);
        for (int suffix = 2; takenIds.contains(candidate); suffix++) {
            String number = "-" + suffix;
            candidate = cut(baseId, MAX_LENGTH - number.length()) + number;
        }
        return candidate;
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

    /** The longest prefix of {@code slug} within {@code length}, ending at a dash boundary unless a word is too long. */
    private static String cut(String slug, int length) {
        if (slug.length() <= length) {
            return slug;
        }
        String prefix = slug.substring(0, length);
        int lastDash = prefix.lastIndexOf('-');
        if (slug.charAt(length) != '-' && lastDash > 0) {
            prefix = prefix.substring(0, lastDash);
        }
        return prefix.replaceAll("-+$", "");
    }
}
