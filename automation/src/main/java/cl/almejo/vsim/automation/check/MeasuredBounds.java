package cl.almejo.vsim.automation.check;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ElementBounds;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.geometry.Bounds;

import java.util.Collection;
import java.util.Optional;

/**
 * Grid bounds of elements as the editor draws them: a note with every line of its text and a part with its label, as a
 * {@link TextMeasure} places them (the editor's font), where core's {@link ElementBounds} has only the note's corner
 * and the part's body. The AI's frames ({@code MakeFrame}, the storyboard's stage frames) wrap these.
 */
public final class MeasuredBounds {

    private MeasuredBounds() {
    }

    /** The union of the elements' bounds with the part labels; a part whose type is unknown has none. */
    public static Optional<Bounds> union(Collection<? extends DocumentElement> elements,
                                         ComponentTypeRegistry registry, TextMeasure measure) {
        return union(elements, registry, measure, true);
    }

    /** As {@link #union}, without the part labels (the notes are still measured). */
    public static Optional<Bounds> unionWithoutLabels(Collection<? extends DocumentElement> elements,
                                                      ComponentTypeRegistry registry, TextMeasure measure) {
        return union(elements, registry, measure, false);
    }

    private static Optional<Bounds> union(Collection<? extends DocumentElement> elements,
                                          ComponentTypeRegistry registry, TextMeasure measure, boolean withLabels) {
        Bounds result = null;
        for (DocumentElement element : elements) {
            try {
                Bounds bounds = element instanceof TextLabel label ? inGrid(measure.freeText(label))
                        : ElementBounds.of(element, registry);
                if (withLabels && element instanceof Component part) {
                    Optional<TextMeasure.TextBox> label = registry.findTypeOf(part)
                            .flatMap(type -> measure.componentLabel(type, part));
                    if (label.isPresent()) {
                        bounds = bounds.union(inGrid(label.get()));
                    }
                }
                result = result == null ? bounds : result.union(bounds);
            } catch (RuntimeException unknownType) {
                // No size to frame or to keep clear of.
            }
        }
        return Optional.ofNullable(result);
    }

    /** The whole grid units a text's box lies in. */
    static Bounds inGrid(TextMeasure.TextBox box) {
        int left = (int) Math.floor(box.left());
        int top = (int) Math.floor(box.top());
        int right = (int) Math.ceil(box.left() + box.width());
        int bottom = (int) Math.ceil(box.top() + box.height());
        return new Bounds(left, top, Math.max(1, right - left), Math.max(1, bottom - top));
    }
}
