package cl.almejo.vsim.ui.magnifier;

import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.export.CircuitImage;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;

import javax.swing.JComponent;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * What the magnifier shows (spec §8.2): the body of a definition, painted with the normal renderer ({@link
 * CircuitImage}) fitted to at most {@value #MAXIMUM_WIDTH} x {@value #MAXIMUM_HEIGHT} pixels, with the values of the
 * instance the body place was opened for. It reads the place at every paint, so a picture that stays up follows the
 * simulation; colours are read from the scheme at every paint too. It owns nothing: whoever opened the place closes it.
 */
final class MagnifierPicture extends JComponent {

    static final int MAXIMUM_WIDTH = 320;
    static final int MAXIMUM_HEIGHT = 240;
    /** A body smaller than the window is shown bigger, but not blown up past this. */
    private static final double MAXIMUM_SCALE = 2.0;
    /** The size of the picture of a body with nothing inside. */
    private static final Dimension EMPTY_SIZE = new Dimension(MAXIMUM_WIDTH / 2, MAXIMUM_HEIGHT / 2);

    private final OpenDocument place;
    private final PainterRegistry painters;
    private final Supplier<ColorScheme> scheme;

    MagnifierPicture(OpenDocument place, PainterRegistry painters, Supplier<ColorScheme> scheme) {
        this.place = place;
        this.painters = painters;
        this.scheme = scheme;
        setOpaque(true);
    }

    @Override
    public Dimension getPreferredSize() {
        // the images round up to whole pixels: never past the maximum by that one
        return image().map(image -> new Dimension(Math.min(MAXIMUM_WIDTH, image.width(scaleOf(image))),
                Math.min(MAXIMUM_HEIGHT, image.height(scaleOf(image))))).orElse(EMPTY_SIZE);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D local = (Graphics2D) graphics.create();
        try {
            local.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            local.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            local.setColor(scheme.get().background());
            local.fillRect(0, 0, getWidth(), getHeight());
            image().ifPresent(image -> {
                double scale = scaleOf(image);
                local.translate((getWidth() - image.width(scale)) / 2, (getHeight() - image.height(scale)) / 2);
                image.paint(local, scale);
            });
        } finally {
            local.dispose();
        }
    }

    @Override
    public void paintBorder(Graphics graphics) {
        graphics.setColor(scheme.get().selection());
        graphics.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
    }

    private Optional<CircuitImage> image() {
        return CircuitImage.of(place.document(), place.netlist(), place.netlistToken(), place.latestSnapshot(),
                place.registry(), painters, scheme.get(), Optional.empty());
    }

    private static double scaleOf(CircuitImage image) {
        double fitWidth = (double) MAXIMUM_WIDTH / image.width(1.0);
        double fitHeight = (double) MAXIMUM_HEIGHT / image.height(1.0);
        return Math.min(MAXIMUM_SCALE, Math.min(fitWidth, fitHeight));
    }
}
