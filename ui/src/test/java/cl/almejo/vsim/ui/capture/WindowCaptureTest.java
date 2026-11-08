package cl.almejo.vsim.ui.capture;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.panes.CanvasPanes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JPanel;
import javax.swing.JRootPane;
import java.awt.Color;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.util.List;
import java.util.Map;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.layOut;
import static cl.almejo.vsim.ui.UiTestSupport.onEditor;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/** Painting the window and the visible circuit into an image, at a given scale, never from the screen. */
class WindowCaptureTest {

    private static final int WINDOW_WIDTH = 800;
    private static final int WINDOW_HEIGHT = 600;

    private OpenDocument document;
    private EditorView view;
    private JRootPane root;

    @BeforeEach
    void aViewInAWindowSizedRootPane() throws Exception {
        CircuitDocument circuit = new CircuitDocument();
        circuit.setName("Sumador");
        document = new OpenDocument(circuit, StandardComponentTypes.registry(), null, false);
        onEditor(() -> place(document, StandardComponentTypes.AND, 10, 10, Map.of()));
        onEditor(() -> {
            EditorServices services = new EditorServices(PainterRegistry.standard(),
                    new Appearance(ColorScheme.LIGHT), AppPreferences.inMemory(), new ComponentLibrary(), List.of());
            view = new EditorView(document, services, NO_WINDOW);
            root = new JRootPane();
            root.setJMenuBar(view.menuBar());
            root.setContentPane(view);
            root.setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
            layOut(root);
            return null;
        });
    }

    @AfterEach
    void close() throws Exception {
        onEditor(() -> {
            view.dispose();
            return null;
        });
        document.close();
    }

    @Test
    void theWindowIsPaintedAtTheRootPanesSizeTimesTheScale() throws Exception {
        BufferedImage atOne = onEditor(() -> WindowCapture.paint(view, CaptureKind.WINDOW, 1.0));
        BufferedImage atTwo = onEditor(() -> WindowCapture.paint(view, CaptureKind.WINDOW, 2.0));

        assertThat(atOne.getWidth()).isEqualTo(WINDOW_WIDTH);
        assertThat(atOne.getHeight()).isEqualTo(WINDOW_HEIGHT);
        assertThat(atTwo.getWidth()).isEqualTo(2 * WINDOW_WIDTH);
        assertThat(atTwo.getHeight()).isEqualTo(2 * WINDOW_HEIGHT);
    }

    @Test
    void anOpenDialogIsPaintedWhereItIsAndWidensThePictureWhenItStandsOut() throws Exception {
        JPanel dialog = onEditor(() -> {
            JPanel panel = new JPanel();
            panel.setBackground(Color.MAGENTA);
            panel.setSize(200, 100);
            return panel;
        });
        // the dialog's right part stands 100 pixels out of the window
        Rectangle place = new Rectangle(WINDOW_WIDTH - 100, 50, 200, 100);
        WindowLayers layers = new WindowLayers(root, List.of(new WindowLayers.PlacedDialog(dialog, place, "Grupos")));

        BufferedImage image = onEditor(() -> layers.paint(2.0, Integer.MAX_VALUE, Integer.MAX_VALUE));

        assertThat(layers.extent()).isEqualTo(new Rectangle(0, 0, WINDOW_WIDTH + 100, WINDOW_HEIGHT));
        assertThat(image.getWidth()).isEqualTo(2 * (WINDOW_WIDTH + 100));
        assertThat(image.getHeight()).isEqualTo(2 * WINDOW_HEIGHT);
        assertThat(image.getRGB(2 * (WINDOW_WIDTH + 50), 2 * 100)).isEqualTo(Color.MAGENTA.getRGB());
        assertThat(image.getRGB(2 * (WINDOW_WIDTH - 150), 2 * 100)).isNotEqualTo(Color.MAGENTA.getRGB());
    }

    @Test
    void theWindowLayersCapTheirPictureAtTheGivenSize() throws Exception {
        WindowLayers layers = new WindowLayers(root, List.of());

        BufferedImage image = onEditor(() -> layers.paint(1.0, 500, 400));

        assertThat(image.getWidth()).isEqualTo(500);
        assertThat(image.getHeight()).isEqualTo(400);
    }

    @Test
    void theVisibleCircuitCoversEveryPaneOfASplitViewAndNothingAroundThem() throws Exception {
        CanvasPanes panes = onEditor(() -> {
            view.canvasPanes().split(CanvasPanes.Orientation.VERTICAL, 0.5);
            layOut(root);
            return view.canvasPanes();
        });
        assertThat(panes.panes()).hasSize(2);
        CircuitCanvas first = panes.panes().getFirst();

        BufferedImage image = onEditor(() -> WindowCapture.paint(view, CaptureKind.VISIBLE_CIRCUIT, 2.0));

        assertThat(panes.getWidth()).isGreaterThan(first.getWidth());
        assertThat(image.getWidth()).isEqualTo(2 * panes.getWidth());
        assertThat(image.getHeight()).isEqualTo(2 * panes.getHeight());
    }

    @Test
    void withoutAScreenTheScaleIsOne() throws Exception {
        assertThat(onEditor(() -> WindowCapture.screenScale(view))).isEqualTo(1.0);
    }

    @Test
    void onA200PercentScreenTheScaleIsTwo() {
        JPanel onHiDpiScreen = new JPanel() {
            @Override
            public GraphicsConfiguration getGraphicsConfiguration() {
                return new ScaledConfiguration(2.0);
            }
        };

        assertThat(WindowCapture.screenScale(onHiDpiScreen)).isEqualTo(2.0);
    }

    /** A screen whose default transform scales by {@code scale}, as a HiDPI screen's does. */
    private static final class ScaledConfiguration extends GraphicsConfiguration {

        private final double scale;

        ScaledConfiguration(double scale) {
            this.scale = scale;
        }

        @Override
        public GraphicsDevice getDevice() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ColorModel getColorModel() {
            return ColorModel.getRGBdefault();
        }

        @Override
        public ColorModel getColorModel(int transparency) {
            return ColorModel.getRGBdefault();
        }

        @Override
        public AffineTransform getDefaultTransform() {
            return AffineTransform.getScaleInstance(scale, scale);
        }

        @Override
        public AffineTransform getNormalizingTransform() {
            return new AffineTransform();
        }

        @Override
        public Rectangle getBounds() {
            return new Rectangle(0, 0, 1920, 1080);
        }
    }
}
