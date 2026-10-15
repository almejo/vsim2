package cl.almejo.vsim.core.component;

import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.Bounds;

import java.util.Collection;
import java.util.Optional;

/** Grid bounds of document elements. A text label counts as its position (zero size). */
public final class ElementBounds {

    private ElementBounds() {
    }

    public static Bounds of(DocumentElement element, ComponentTypeRegistry registry) {
        return switch (element) {
            case Component component -> registry.typeOf(component).bounds(component);
            case Wire wire -> Bounds.of(wire.start(), wire.end());
            case TextLabel label -> new Bounds(label.position().x(), label.position().y(), 0, 0);
            case Frame frame -> new Bounds(frame.topLeft().x(), frame.topLeft().y(), frame.width(), frame.height());
        };
    }

    public static Optional<Bounds> union(Collection<? extends DocumentElement> elements,
                                         ComponentTypeRegistry registry) {
        Bounds result = null;
        for (DocumentElement element : elements) {
            Bounds bounds = of(element, registry);
            result = result == null ? bounds : result.union(bounds);
        }
        return Optional.ofNullable(result);
    }
}
