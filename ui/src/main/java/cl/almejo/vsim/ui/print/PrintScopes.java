package cl.almejo.vsim.ui.print;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameContents;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.canvas.CircuitBounds;
import cl.almejo.vsim.ui.canvas.DrawnBounds;
import cl.almejo.vsim.ui.export.CircuitImage;
import cl.almejo.vsim.ui.frames.FramePainter;
import cl.almejo.vsim.ui.paint.TextFonts;

import java.awt.geom.Rectangle2D;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
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

    /** The area of {@code scope} without the user's sticky notes (see the overload that includes them). */
    public static Optional<PrintArea> area(PrintInput input, PrintScope scope) {
        return area(input, scope, false);
    }

    /**
     * Empty when the scope has nothing to paint (an empty circuit or selection, a frame that is gone). Sticky notes
     * are part of the area, and of what a sheet paints, only when {@code includeStickyNotes}.
     */
    public static Optional<PrintArea> area(PrintInput input, PrintScope scope, boolean includeStickyNotes) {
        return switch (scope) {
            case PrintScope.WholeCircuit wholeCircuit -> wholeCircuit(input, includeStickyNotes);
            case PrintScope.Selection selection -> selection(input, selection.ids(), includeStickyNotes);
            case PrintScope.Frame frame -> frame(input, frame.frameId(), includeStickyNotes);
        };
    }

    /**
     * The area of «Todo el circuito» (drawn bounds plus the image export's margin) of any document, the live one
     * included: what the sheets on the canvas are laid out over, and what {@link #area} gives a capture of it, so
     * the canvas and the paper start from the same rectangle. Empty when nothing counts.
     */
    public static Optional<Rectangle2D> wholeCircuitArea(CircuitDocument document, ComponentTypeRegistry registry,
                                                         boolean includeStickyNotes) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(registry, "registry");
        return DrawnBounds.of(counted(document, includeStickyNotes), registry).map(PrintScopes::withMargin);
    }

    /**
     * Where each element «Todo el circuito» paints is drawn, in grid units ({@link DrawnBounds}, the notion its area is
     * made of): what the canvas tells empty sheets by ({@link PrintedSheets}), as {@link #drawnBounds} does for a
     * capture.
     */
    public static List<Bounds> wholeCircuitDrawnBounds(CircuitDocument document, ComponentTypeRegistry registry,
                                                       boolean includeStickyNotes, boolean blackAndWhite) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(registry, "registry");
        return counted(document, includeStickyNotes).stream()
                .flatMap(element -> paintedBounds(element, registry, blackAndWhite).stream()).toList();
    }

    /**
     * Where each element {@code area} paints is drawn, in grid units, measured with the capture's frozen text font:
     * what the paper tells empty sheets by ({@link PrintedSheets}).
     */
    static List<Bounds> drawnBounds(PrintInput input, PrintArea area, boolean blackAndWhite) {
        return TextFonts.withFont(input.textFont(), () -> area.elements().stream()
                .map(id -> input.document().element(id)).flatMap(Optional::stream)
                .flatMap(element -> paintedBounds(element, input.registry(), blackAndWhite).stream()).toList());
    }

    /**
     * Where {@code element} paints. A frame paints only its border (four lines), its title bar and, in colour with a
     * tint, its inside: an untinted or black-and-white frame is not on the sheets its empty inside crosses.
     */
    private static List<Bounds> paintedBounds(DocumentElement element, ComponentTypeRegistry registry,
                                              boolean blackAndWhite) {
        if (!(element instanceof Frame frame)) {
            return List.of(DrawnBounds.of(element, registry));
        }
        Bounds whole = new Bounds(frame.topLeft().x(), frame.topLeft().y(), frame.width(), frame.height());
        if (!blackAndWhite && frame.tint() != FrameTint.NONE) {
            return List.of(whole);
        }
        int titleBar = (int) Math.ceil(FramePainter.TITLE_BAR_HEIGHT / GridPoint.PIXELS_PER_UNIT);
        return List.of(new Bounds(whole.x(), whole.y(), whole.width(), 0),
                new Bounds(whole.x(), whole.maximumY(), whole.width(), 0),
                new Bounds(whole.x(), whole.y(), 0, whole.height()),
                new Bounds(whole.maximumX(), whole.y(), 0, whole.height()),
                new Bounds(whole.x(), whole.y(), whole.width(), Math.min(titleBar, whole.height())));
    }

    private static boolean counts(DocumentElement element, boolean includeStickyNotes) {
        return includeStickyNotes || !(element instanceof StickyNote);
    }

    private static List<DocumentElement> counted(CircuitDocument document, boolean includeStickyNotes) {
        return document.elements().stream().filter(element -> counts(element, includeStickyNotes)).toList();
    }

    private static Optional<PrintArea> wholeCircuit(PrintInput input, boolean includeStickyNotes) {
        Set<ElementId> everything = new LinkedHashSet<>();
        counted(input.document(), includeStickyNotes).forEach(element -> everything.add(element.id()));
        return wholeCircuitArea(input.document(), input.registry(), includeStickyNotes)
                .map(area -> new PrintArea(area, everything, Optional.empty()));
    }

    private static Optional<PrintArea> selection(PrintInput input, Set<ElementId> ids,
                                                  boolean includeStickyNotes) {
        CircuitDocument document = input.document();
        List<ElementId> present = ids.stream().filter(id -> document.element(id).isPresent()).toList();
        Set<ElementId> expanded = FrameContents.withLockedBlocks(document, input.registry(), present);
        List<DocumentElement> painted = document.elements().stream()
                .filter(element -> expanded.contains(element.id()) && counts(element, includeStickyNotes))
                .toList();
        Set<ElementId> inDocumentOrder = new LinkedHashSet<>();
        painted.forEach(element -> inDocumentOrder.add(element.id()));
        return DrawnBounds.of(painted, input.registry())
                .map(bounds -> new PrintArea(withMargin(bounds), inDocumentOrder, Optional.empty()));
    }

    private static Optional<PrintArea> frame(PrintInput input, ElementId frameId,
                                              boolean includeStickyNotes) {
        return input.document().frame(frameId).map(frame -> crop(input, frame, includeStickyNotes));
    }

    /** Every element drawn on the frame's rectangle, touching its border included; the clip cuts what sticks out. */
    private static PrintArea crop(PrintInput input, Frame frame, boolean includeStickyNotes) {
        Bounds rectangle = new Bounds(frame.topLeft().x(), frame.topLeft().y(), frame.width(), frame.height());
        Set<ElementId> drawnInside = new LinkedHashSet<>();
        for (DocumentElement element : input.document().elements()) {
            if (counts(element, includeStickyNotes)
                    && DrawnBounds.of(element, input.registry()).intersects(rectangle)) {
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
