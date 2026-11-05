package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.core.document.ElementId;

import java.util.Objects;
import java.util.Optional;

/**
 * Which part a feature that outlives one tab means (spec §10.7): the part {@code component} of the circuit
 * {@code document}, on the circuit itself ({@code definitionId} empty) or inside the definition {@code definitionId}
 * with the values of the instance at {@code contextPath} (empty: «Sin valores»). The same id on the circuit and inside a
 * component, or inside two pieces of one component, are different parts; two tabs showing the same piece mean the same
 * part. The document is compared by identity (an {@link OpenDocument} is one open circuit).
 */
public record PlaceKey(OpenDocument document, Optional<String> definitionId, Optional<String> contextPath,
                       ElementId component) {

    public PlaceKey {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(definitionId, "definitionId");
        Objects.requireNonNull(contextPath, "contextPath");
        Objects.requireNonNull(component, "component");
    }

    /** The part {@code component} as {@code place} (the circuit, or a component tab's place) shows it now. */
    public static PlaceKey of(OpenDocument place, ElementId component) {
        return new PlaceKey(place.parent().orElse(place), place.definitionId(), place.contextPath(), component);
    }

    /**
     * {@code place} as words that last beyond the session (remembered window bounds): the circuit's file (or its
     * untitled name), then the component and the instance when inside one. The circuit's own place is just the file,
     * as it was before there were component tabs, so what was remembered stays valid.
     */
    public static String storedPlaceOf(OpenDocument place) {
        String circuit = place.path().map(path -> path.toAbsolutePath().normalize().toString())
                .orElseGet(() -> "untitled:" + place.displayName());
        return place.definitionId().map(definition -> circuit + "#" + definition + "@"
                + place.contextPath().orElse("")).orElse(circuit);
    }
}
