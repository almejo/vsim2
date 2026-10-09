package cl.almejo.vsim.core.library;

import java.util.Collection;
import java.util.Locale;
import java.util.Optional;

/** Visible names of user definitions: required, and unique ignoring case and surrounding spaces (spec §5.6). */
public final class DefinitionNames {

    public enum NameProblem { EMPTY, TAKEN }

    private DefinitionNames() {
    }

    public static Optional<NameProblem> problem(String name, Collection<String> takenNames) {
        if (name == null || name.isBlank()) {
            return Optional.of(NameProblem.EMPTY);
        }
        return takenNames.stream().anyMatch(taken -> same(taken, name))
                ? Optional.of(NameProblem.TAKEN) : Optional.empty();
    }

    public static boolean same(String first, String second) {
        return key(first).equals(key(second));
    }

    /** {@code name} stripped when free, otherwise the first free {@code "name 2"}, {@code "name 3"}, … */
    public static String unique(String name, Collection<String> takenNames) {
        String base = name.strip();
        if (problem(base, takenNames).isEmpty()) {
            return base;
        }
        for (int suffix = 2; ; suffix++) {
            String candidate = base + " " + suffix;
            if (problem(candidate, takenNames).isEmpty()) {
                return candidate;
            }
        }
    }

    private static String key(String name) {
        return name == null ? "" : name.strip().toLowerCase(Locale.ROOT);
    }
}
