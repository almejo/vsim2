package cl.almejo.vsim.automation.read;

import cl.almejo.vsim.core.geometry.GridPoint;

import java.util.List;
import java.util.Map;

/**
 * One component type as the AI places it: its id (what {@code PlacePart} takes), the palette's names, its parameters
 * and the size and pins of the instance the given parameters make (defaults when none are given). Pin positions are
 * relative to the part's origin, unrotated; {@code userComponent} marks user components, the circuit's own and those
 * of the user's library ({@code definitionName} is then the name {@code PlaceInstance} takes, otherwise empty);
 * {@code origin} says which. {@code description} is what Propiedades says about such a part (its rules and
 * assumptions, in the editor's language); only the one-type answer carries it, the catalogue leaves it empty to stay
 * short.
 */
public record ComponentTypeView(String typeId, String category, String spanishName, String englishName,
                                boolean userComponent, String definitionName, Map<String, Object> parameterValues,
                                List<ParameterView> parameters, int width, int height, List<PinSpecView> pins,
                                Origin origin, String description) {

    /** Where a type comes from. */
    public enum Origin {
        /** One of VSim's own components. */
        BUILT_IN,
        /** A user component this circuit already has. */
        CIRCUIT,
        /** A component of the user's library («Mis componentes») not yet in this circuit: placing it brings it in. */
        LIBRARY
    }

    public ComponentTypeView {
        parameterValues = Map.copyOf(parameterValues);
        parameters = List.copyOf(parameters);
        pins = List.copyOf(pins);
        java.util.Objects.requireNonNull(origin, "origin");
        java.util.Objects.requireNonNull(description, "description");
    }

    /**
     * One parameter: {@code kind} is INTEGER, TEXT, CHOICE or COLOR; {@code minimum}/{@code maximum} only for INTEGER
     * (null otherwise), {@code choices} only for CHOICE and COLOR; {@code displayOnly} ones change only the drawing.
     */
    public record ParameterView(String name, String kind, Object defaultValue, Integer minimum, Integer maximum,
                                List<String> choices, boolean displayOnly) {

        public ParameterView {
            choices = List.copyOf(choices);
        }
    }

    /**
     * One pin of the specialised instance: {@code index} is its place among the part's pins (what a pin named only
     * by index means; a {@code PinReference}'s index is instead which pin of that name), {@code direction} INPUT, OUTPUT or BIDIRECTIONAL, {@code width} in bits, 0 when it takes the width of
     * what it is joined to ({@code adaptiveWidth}, a tunnel's pin).
     */
    public record PinSpecView(int index, String name, String direction, int width, boolean adaptiveWidth,
                              GridPoint position) {
    }
}
