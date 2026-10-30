package cl.almejo.vsim.core.component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What a component's parameters have to do with its type's {@linkplain ComponentType#profiles() profiles} (spec §7):
 * which values differ from the chosen profile's (shown bold with «●» and a «↺»), and the values after choosing a
 * profile or after one «↺». Works on raw values too (what a form holds): a value that cannot be read differs.
 */
public final class ParameterProfiles {

    private ParameterProfiles() {
    }

    /**
     * The profile {@code values} chose (its profile parameter, or the default one), empty for a type without
     * profiles or when the choice cannot be read.
     */
    public static Optional<ParameterProfile> chosen(ComponentType type, Map<String, Object> values) {
        return type.profileParameter()
                .flatMap(type::parameterNamed)
                .flatMap(chooser -> chooser.normalizeIfValid(values.get(chooser.name())))
                .flatMap(name -> type.profile(name.toString()));
    }

    /**
     * The names of the parameters whose value in {@code values} is not the chosen profile's, in the type's order.
     * Parameters the profile does not list never count; a missing value counts as its default.
     */
    public static Set<String> differences(ComponentType type, Map<String, Object> values) {
        Optional<ParameterProfile> profile = chosen(type, values);
        if (profile.isEmpty()) {
            return Set.of();
        }
        Set<String> different = new LinkedHashSet<>();
        for (ParameterSpec parameter : type.parameters()) {
            Object profileValue = profile.get().values().get(parameter.name());
            if (profileValue != null && !parameter.normalizeIfValid(values.get(parameter.name()))
                    .filter(profileValue::equals).isPresent()) {
                different.add(parameter.name());
            }
        }
        return Collections.unmodifiableSet(different);
    }

    /**
     * Every parameter after choosing profile {@code profileName}: the profile parameter names it, each value it lists
     * is its value, and the others keep what {@code values} has (raw, as given) or their default.
     *
     * @throws IllegalArgumentException when the type has no such profile
     */
    public static Map<String, Object> apply(ComponentType type, Map<String, Object> values, String profileName) {
        ParameterProfile profile = type.profile(profileName).orElseThrow(() -> new IllegalArgumentException(
                type.id() + " has no profile " + profileName));
        Map<String, Object> applied = complete(type, values);
        applied.put(type.profileParameter().orElseThrow(), profileName);
        applied.putAll(profile.values());
        return Collections.unmodifiableMap(applied);
    }

    /**
     * Every parameter after «↺» on {@code parameter}: it takes the chosen profile's value, the others keep theirs.
     *
     * @throws IllegalArgumentException when the chosen profile does not list {@code parameter}
     */
    public static Map<String, Object> restore(ComponentType type, Map<String, Object> values, String parameter) {
        Object profileValue = chosen(type, values).map(profile -> profile.values().get(parameter))
                .orElseThrow(() -> new IllegalArgumentException(type.id() + ": the chosen profile does not set "
                        + parameter));
        Map<String, Object> restored = complete(type, values);
        restored.put(parameter, profileValue);
        return Collections.unmodifiableMap(restored);
    }

    /** {@code values} with every parameter of the type, a missing one at its default, in the type's order. */
    private static Map<String, Object> complete(ComponentType type, Map<String, Object> values) {
        Map<String, Object> complete = new LinkedHashMap<>();
        for (ParameterSpec parameter : type.parameters()) {
            Object value = values.get(parameter.name());
            complete.put(parameter.name(), value == null ? parameter.defaultValue() : value);
        }
        return complete;
    }
}
