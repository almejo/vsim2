package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.tools.SelectTool;
import cl.almejo.vsim.ui.tools.TestToolContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import cl.almejo.vsim.core.geometry.Bounds;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * «Ver la pieza» (spec §5.1): the piece glows for 1.5 s, on the canvas's own clock (a hand-driven one here), and the
 * glow takes its colour from the scheme each time it paints.
 */
class PieceGlowTest {

    /** The inverter's NOT gate, at (2, 0). */
    private static final ElementId GATE = new ElementId("c2");
    private static final ElementId ADDED = new ElementId("c9");

    private final OpenDocument circuit = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private OpenDocument body;
    private CircuitCanvas canvas;

    @BeforeEach
    void enterAnInverter() {
        circuit.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter())));
        circuit.execute(new AddElementsCommand(List.of(new Component(new ElementId("c5"),
                SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER), new GridPoint(0, 0), Rotation.R0, Map.of(),
                ""))));
        body = circuit.openBody(ExampleDefinitions.INVERTER, Optional.of("c5"));
        canvas = new CircuitCanvas(body, new CircuitRenderer(body.registry(), PainterRegistry.standard(),
                ColorScheme.LIGHT), new CanvasHost() {
            @Override
            public void showContextMenu(CircuitCanvas source, Optional<DocumentElement> target,
                                        Point2D screenPosition) {
            }

            @Override
            public void configure(Component component) {
            }
        });
        canvas.setSize(400, 300);
    }

    private final ManualFrameTicker ticker = new ManualFrameTicker();

    @AfterEach
    void closeAll() {
        canvas.dispose();
        body.close();
        circuit.close();
    }

    @Test
    void theGlowLastsOneAndAHalfSecondsAndFades() {
        canvas.useGlowTicker(ticker, ticker::now);

        canvas.glow(new Bounds(0, 0, 4, 4));

        assertThat(canvas.isGlowing()).isTrue();
        assertThat(ticker.isRunning()).isTrue();
        ticker.advance(700);
        double early = canvas.glowStrength();
        ticker.advance(600);
        double late = canvas.glowStrength();
        assertThat(canvas.isGlowing()).isTrue();
        assertThat(early).isGreaterThan(late).isGreaterThan(0);
        ticker.advance(300);
        assertThat(canvas.isGlowing()).isFalse();
        assertThat(ticker.isRunning()).isFalse();
        assertThat(canvas.glowStrength()).isZero();
    }

    @Test
    void withAnimationsOffTheGlowIsSteadyAndGoesOutAtOnceAfterOneAndAHalfSeconds() {
        AtomicBoolean animations = new AtomicBoolean(false);
        canvas.setAnimations(animations::get);
        canvas.useGlowTicker(ticker, ticker::now);

        canvas.glow(new Bounds(0, 0, 4, 4));
        ticker.advance(1300);

        assertThat(canvas.glowStrength()).as("no fade").isEqualTo(1.0);
        animations.set(true);
        assertThat(canvas.glowStrength()).as("a steady glow stays steady when the setting comes back").isEqualTo(1.0);
        ticker.advance(300);
        assertThat(canvas.isGlowing()).isFalse();
        assertThat(ticker.isRunning()).isFalse();
    }

    @Test
    void turningAnimationsOffWhileItFadesEndsTheGlowInsteadOfLightingItAgain() {
        AtomicBoolean animations = new AtomicBoolean(true);
        canvas.setAnimations(animations::get);
        canvas.useGlowTicker(ticker, ticker::now);
        canvas.glow(new Bounds(0, 0, 4, 4));
        ticker.advance(700);
        assertThat(canvas.glowStrength()).isLessThan(1.0);

        animations.set(false);

        assertThat(canvas.glowStrength()).as("never brighter than it was").isZero();
        ticker.advance(16);
        assertThat(canvas.isGlowing()).isFalse();
        assertThat(ticker.isRunning()).isFalse();
    }

    @Test
    void aSecondGlowRestartsTheCountAndDisposeStopsIt() {
        canvas.useGlowTicker(ticker, ticker::now);
        canvas.glow(new Bounds(0, 0, 4, 4));
        ticker.advance(1400);

        canvas.glow(new Bounds(8, 8, 4, 4));
        ticker.advance(1400);

        assertThat(canvas.isGlowing()).isTrue();
        canvas.dispose();
        assertThat(ticker.isRunning()).isFalse();
    }

    @Test
    void itPaintsOverTheCircuitWhileItGlowsAndNotAfter() {
        canvas.useGlowTicker(ticker, ticker::now);
        BufferedImage plain = paint();
        canvas.glow(new Bounds(0, 0, 4, 4));
        BufferedImage glowing = paint();
        ticker.advance(1600);
        BufferedImage after = paint();

        assertThat(differs(plain, glowing)).isTrue();
        assertThat(differs(plain, after)).isFalse();
    }

    private BufferedImage paint() {
        BufferedImage image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            canvas.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static boolean differs(BufferedImage first, BufferedImage second) {
        for (int y = 0; y < first.getHeight(); y++) {
            for (int x = 0; x < first.getWidth(); x++) {
                if (first.getRGB(x, y) != second.getRGB(x, y)) {
                    return true;
                }
            }
        }
        return false;
    }
}
