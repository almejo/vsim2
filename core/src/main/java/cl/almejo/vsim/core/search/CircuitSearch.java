package cl.almejo.vsim.core.search;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.library.SubcircuitTypes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Finds the elements of a document whose texts match a {@link CompiledQuery}: component labels (the pin name for
 * input and output pins), type names, free texts and frame titles; wires have no text. Matches inside user components
 * are reported through their top-level instance (see {@link SearchHit}).
 *
 * <p>Order: the top-level elements in reading order of their bounds (top, then left; ties by document order); right
 * after an instance's own hit come the hits inside it, depth first in the order of each definition's body (like the
 * component tree).
 */
public final class CircuitSearch {

    /** Deeper nesting is not searched, like the component tree; the document already rejects recursion. */
    public static final int MAXIMUM_DEPTH = 32;

    private CircuitSearch() {
    }

    /**
     * Every hit of {@code query} in {@code document}. {@code typeDisplayName} gives the visible name of a type id
     * (the definition name for user components), so the UI decides the language.
     */
    public static List<SearchHit> find(CircuitDocument document, ComponentTypeRegistry registry, CompiledQuery query,
                                       Function<String, String> typeDisplayName) {
        if (query.matchesNothing()) {
            return List.of();
        }
        List<DocumentElement> elements = new ArrayList<>();
        elements.addAll(document.components());
        elements.addAll(document.labels());
        elements.addAll(document.frames());
        elements.sort(Comparator.<DocumentElement>comparingInt(element -> topLeft(element, registry).y())
                .thenComparingInt(element -> topLeft(element, registry).x())
                .thenComparingLong(element -> document.orderOf(element.id())));
        Search search = new Search(document, query, typeDisplayName, new ArrayList<>());
        for (DocumentElement element : elements) {
            search.visit(element);
        }
        return List.copyOf(search.hits());
    }

    private record Search(CircuitDocument document, CompiledQuery query, Function<String, String> typeDisplayName,
                          List<SearchHit> hits) {

        void visit(DocumentElement element) {
            switch (element) {
                case Component component -> {
                    componentHit(component).ifPresent(hit -> hits.add(new SearchHit(component.id(), List.of(),
                            hit.field(), hit.text())));
                    visitInside(component, component, List.of(), 0);
                }
                case TextLabel label -> {
                    if (query.matches(label.text())) {
                        hits.add(new SearchHit(label.id(), List.of(), SearchHit.Field.TEXT, label.text()));
                    }
                }
                case Frame frame -> {
                    if (query.matches(frame.title())) {
                        hits.add(new SearchHit(frame.id(), List.of(), SearchHit.Field.FRAME_TITLE, frame.title()));
                    }
                }
                default -> {
                }
            }
        }

        /** The hits among the parts of {@code instance} (when it is a user component), reported on {@code topLevel}. */
        private void visitInside(Component topLevel, Component instance, List<String> path, int depth) {
            if (depth >= MAXIMUM_DEPTH) {
                return;
            }
            Optional<ComponentDefinition> definition = SubcircuitTypes.definitionIdOf(instance.typeId())
                    .flatMap(document::definition);
            if (definition.isEmpty()) {
                return;
            }
            for (Component part : definition.get().body().components()) {
                List<String> partPath = new ArrayList<>(path);
                partPath.add(part.label().isBlank() ? typeDisplayName.apply(part.typeId()) : part.label());
                componentHit(part).ifPresent(hit -> hits.add(new SearchHit(topLevel.id(), partPath, hit.field(),
                        hit.text())));
                visitInside(topLevel, part, partPath, depth + 1);
            }
        }

        /** The label first (a pin name for interface pins), then the type name: one hit per component. */
        private Optional<FieldMatch> componentHit(Component component) {
            if (query.matches(component.label())) {
                return Optional.of(new FieldMatch(InterfacePins.isInterface(component.typeId())
                        ? SearchHit.Field.PIN_NAME : SearchHit.Field.LABEL, component.label()));
            }
            String typeName = typeDisplayName.apply(component.typeId());
            if (query.matches(typeName)) {
                return Optional.of(new FieldMatch(SearchHit.Field.TYPE, typeName));
            }
            return Optional.empty();
        }
    }

    private record FieldMatch(SearchHit.Field field, String text) {
    }

    /** The top-left of the element's bounds; a component of an unknown type counts as its position. */
    private static GridPoint topLeft(DocumentElement element, ComponentTypeRegistry registry) {
        return switch (element) {
            case Component component -> registry.findTypeOf(component)
                    .map(type -> type.bounds(component))
                    .map(bounds -> new GridPoint(bounds.x(), bounds.y()))
                    .orElse(component.position());
            case TextLabel label -> label.position();
            case Frame frame -> frame.topLeft();
            default -> GridPoint.ORIGIN;
        };
    }
}
