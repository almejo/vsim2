package cl.almejo.vsim.automation;

import java.util.List;
import java.util.Objects;

/**
 * One place of a circuit for {@code vsim_list_places}: the main circuit ({@code definition} "main", no uses) or a
 * definition, named {@code name}, with each place it is used at ({@link PlaceUse}).
 */
public record PlaceSummary(String definition, String name, List<PlaceUse> uses) {

    public PlaceSummary {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(name, "name");
        uses = List.copyOf(uses);
    }

    /** One use: the simulation {@code path} to pass as {@code context}, and the ids of the instances it goes through. */
    public record PlaceUse(String path, List<String> references) {

        public PlaceUse {
            Objects.requireNonNull(path, "path");
            references = List.copyOf(references);
        }
    }
}
