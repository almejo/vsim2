package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.library.LibraryVersions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CanvasOverlay;
import cl.almejo.vsim.ui.canvas.RenderState;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ComponentDrawing;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * A dot on the top-right corner of every instance whose embedded definition differs from the library entry with the
 * same id ("hay una versión distinta en tu librería", spec §5.6). The set is recomputed on library and document
 * changes, never while painting.
 */
public final class LibraryVersionBadge implements CanvasOverlay {

    private static final double RADIUS = 3;

    private final OpenDocument openDocument;
    private final ComponentLibrary library;
    private final Supplier<ColorScheme> scheme;
    private final Runnable repaint;
    private final Runnable refresher = this::refresh;
    private volatile Set<String> outdated = Set.of();

    public LibraryVersionBadge(OpenDocument openDocument, ComponentLibrary library, Supplier<ColorScheme> scheme,
                               Runnable repaint) {
        this.openDocument = openDocument;
        this.library = library;
        this.scheme = scheme;
        this.repaint = repaint;
    }

    public void install() {
        library.addListener(refresher);
        openDocument.addChangeListener(refresher);
        refresh();
    }

    public void dispose() {
        library.removeListener(refresher);
        openDocument.removeChangeListener(refresher);
    }

    /** Definition ids drawn with the badge. */
    public Set<String> outdatedIds() {
        return outdated;
    }

    @Override
    public void paint(Graphics2D graphics, RenderState state) {
        Set<String> current = outdated;
        if (current.isEmpty()) {
            return;
        }
        ColorScheme colors = scheme.get();
        Graphics2D badge = (Graphics2D) graphics.create();
        try {
            badge.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            badge.setStroke(new BasicStroke(1f));
            for (Component component : state.document().components()) {
                boolean marked = SubcircuitTypes.definitionIdOf(component.typeId())
                        .filter(current::contains)
                        .isPresent();
                Optional<ComponentType> type = openDocument.registry().findTypeOf(component);
                if (!marked || type.isEmpty()) {
                    continue;
                }
                Rectangle2D bounds = ComponentDrawing.documentBounds(type.get(), component);
                Ellipse2D dot = new Ellipse2D.Double(bounds.getMaxX() - 2 * RADIUS - 1, bounds.getMinY() + 1,
                        2 * RADIUS, 2 * RADIUS);
                badge.setColor(colors.selection());
                badge.fill(dot);
                badge.setColor(colors.background());
                badge.draw(dot);
            }
        } finally {
            badge.dispose();
        }
    }

    private void refresh() {
        Set<String> next = LibraryVersions.outdated(openDocument.document().definitions(), library);
        if (!next.equals(outdated)) {
            outdated = next;
            repaint.run();
        }
    }
}
