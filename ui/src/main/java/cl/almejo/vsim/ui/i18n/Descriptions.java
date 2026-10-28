package cl.almejo.vsim.ui.i18n;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ParameterKind;
import cl.almejo.vsim.core.component.ParameterSpec;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.SubcircuitTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * The «Descripción» of a part: what it does, its pins and its assumptions, as Propiedades, the configure dialog, the
 * palette tooltip and the AI show it. A built-in part's text is a base paragraph keyed by its name id
 * ({@code description.component.and}, {@code description.component.nand} for an AND with «Negar salida»), followed by
 * one paragraph for each parameter value that has its own ({@code description.component.ram.write.level}; a boolean's
 * value is {@code true}/{@code false}, a text parameter's is {@link #TEXT_SET} when it is not blank). A user component's
 * text is its definition's description.
 */
public final class Descriptions {

    /** The start of every description key. */
    public static final String PREFIX = "description.component.";
    /** The value a text parameter has in a paragraph key when it holds something (a memory's link). */
    public static final String TEXT_SET = "set";
    /** What separates two paragraphs of a description. */
    public static final String PARAGRAPH_BREAK = "\n\n";

    private Descriptions() {
    }

    /** The key of the base text of a palette entry, a type or a preset ({@code description.component.nand}). */
    public static String baseKey(String id) {
        return PREFIX + id;
    }

    /** The description of a part of {@code type} with {@code parameters}, in the current language; empty when none. */
    public static String of(ComponentType type, Map<String, Object> parameters) {
        return of(type, parameters, Messages.bundle(Messages.locale()));
    }

    /** Like {@link #of(ComponentType, Map)}, from {@code bundle}. */
    public static String of(ComponentType type, Map<String, Object> parameters, ResourceBundle bundle) {
        Optional<ComponentDefinition> definition = SubcircuitTypes.definitionOf(type);
        if (definition.isPresent()) {
            return definition.get().description().strip();
        }
        Map<String, Object> resolved = resolvedOrRaw(type, parameters);
        List<String> paragraphs = new ArrayList<>();
        textOf(bundle, baseKey(StandardComponentTypes.nameId(type.id(), resolved))).ifPresent(paragraphs::add);
        if (paragraphs.isEmpty()) {
            return "";
        }
        for (ParameterSpec parameter : type.parameters()) {
            valueKey(parameter, resolved.get(parameter.name()))
                    .flatMap(value -> textOf(bundle, PREFIX + type.id() + "." + parameter.name() + "." + value))
                    .ifPresent(paragraphs::add);
        }
        return String.join(PARAGRAPH_BREAK, paragraphs);
    }

    /** The first paragraph of the description (the palette tooltip); empty when there is no description. */
    public static Optional<String> summary(ComponentType type, Map<String, Object> parameters) {
        String description = of(type, parameters);
        if (description.isBlank()) {
            return Optional.empty();
        }
        int end = description.indexOf(PARAGRAPH_BREAK);
        return Optional.of((end < 0 ? description : description.substring(0, end)).strip());
    }

    /** The parameters with their defaults; the raw ones when they do not resolve (a value being typed). */
    private static Map<String, Object> resolvedOrRaw(ComponentType type, Map<String, Object> parameters) {
        try {
            return type.resolveParameters(parameters);
        } catch (IllegalArgumentException exception) {
            return parameters;
        }
    }

    /** How {@code value} appears in a paragraph key; empty for a blank text or a missing value. */
    private static Optional<String> valueKey(ParameterSpec parameter, Object value) {
        if (value == null) {
            return Optional.empty();
        }
        if (parameter.kind() == ParameterKind.TEXT) {
            return value.toString().isBlank() ? Optional.empty() : Optional.of(TEXT_SET);
        }
        return Optional.of(value.toString());
    }

    private static Optional<String> textOf(ResourceBundle bundle, String key) {
        return bundle.containsKey(key) ? Optional.of(bundle.getString(key).strip()) : Optional.empty();
    }
}
