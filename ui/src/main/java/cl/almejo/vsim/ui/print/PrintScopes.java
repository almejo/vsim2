package cl.almejo.vsim.ui.print;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameContents;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.canvas.CircuitBounds;
import cl.almejo.vsim.ui.canvas.DrawnBounds;
import cl.almejo.vsim.ui.export.CircuitImage;

import java.awt.geom.Rectangle2D;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Turns a {@link PrintScope} into the {@link PrintArea} of a frozen {@link PrintInput} (spec §3.3): the whole circuit
 * and a selection are their drawn bounds plus the image export's margin; a selection takes locked frames with their
 * contents, like copy and move; a frame is its rectangle as a visual crop, with everything drawn across it.
 */
public final class PrintScopes {

    /**
     * How far a frame's crop reaches past its rectangle, in grid units: one canvas pixel, the width of the frame's
     * border, which is drawn centred on the rectangle and would otherwise be cut in half.
     */
    static final double FRAME_BORDER_ALLOWANCE = 1.0 / GridPoint.PIXELS_PER_UNIT;

    private PrintScopes() {
    }

    /** Empty when the scope has nothing to paint (an empty circuit or selection, a frame that is gone). */
    public static Optional<PrintArea> area(PrintInput input, PrintScope scope) {
        return switch (scope) {
            case PrintScope.WholeCircuit wholeCircuit -> wholeCircuit(input);
            case PrintScope.Selection selection -> selection(input, selection.ids());
            case PrintScope.Frame frame -> frame(input, frame.frameId());
        };
    }

    private static Optional<PrintArea> wholeCircuit(PrintInput input) {
        CircuitDocument document = input.document();
        Set<ElementId> everything = new LinkedHashSet<>();
        document.elements().forEach(element -> everything.add(element.id()));
        return CircuitBounds.of(document, input.registry())
                .map(bounds -> new PrintArea(withMargin(bounds), everything, Optional.empty()));
    }

    private static Optional<PrintArea> selection(PrintInput input, Set<ElementId> ids) {
        CircuitDocument document = input.document();
        List<ElementId> present = ids.stream().filter(id -> document.element(id).isPresent()).toList();
        Set<ElementId> expanded = FrameContents.withLockedBlocks(document, input.registry(), present);
        List<DocumentElement> painted = document.elements().stream()
                .filter(element -> expanded.contains(element.id()))
                .toList();
        Set<ElementId> inDocumentOrder = new LinkedHashSet<>();
        painted.forEach(element -> inDocumentOrder.add(element.id()));
        return DrawnBounds.of(painted, input.registry())
                .map(bounds -> new PrintArea(withMargin(bounds), inDocumentOrder, Optional.empty()));
    }

    private static Optional<PrintArea> frame(PrintInput input, ElementId frameId) {
        return input.document().frame(frameId).map(frame -> crop(input, frame));
    }

    /** Every element drawn on the frame's rectangle, touching its border included; the clip cuts what sticks out. */
    private static PrintArea crop(PrintInput input, Frame frame) {
        Bounds rectangle = new Bounds(frame.topLeft().x(), frame.topLeft().y(), frame.width(), frame.height());
        Set<ElementId> drawnInside = new LinkedHashSet<>();
        for (DocumentElement element : input.document().elements()) {
            if (DrawnBounds.of(element, input.registry()).intersects(rectangle)) {
                drawnInside.add(element.id());
            }
        }
        Rectangle2D cropped = new Rectangle2D.Double(rectangle.x() - FRAME_BORDER_ALLOWANCE,
                rectangle.y() - FRAME_BORDER_ALLOWANCE, rectangle.width() + 2 * FRAME_BORDER_ALLOWANCE,
                rectangle.height() + 2 * FRAME_BORDER_ALLOWANCE);
        return new PrintArea(cropped, drawnInside, Optional.of(cropped));
    }

    private static Rectangle2D withMargin(Bounds bounds) {
        int margin = CircuitImage.MARGIN;
        return new Rectangle2D.Double(bounds.x() - margin, bounds.y() - margin, bounds.width() + 2 * margin,
                bounds.height() + 2 * margin);
    }
}
