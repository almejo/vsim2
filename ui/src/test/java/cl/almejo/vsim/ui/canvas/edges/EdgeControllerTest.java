package cl.almejo.vsim.ui.canvas.edges;

import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.tools.CanvasMouseEvent;
import org.junit.jupiter.api.Test;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/** The per-canvas dispatcher of edge handles (canvas-edges spec §4). */
class EdgeControllerTest {

    private final Dimension size = new Dimension(200, 100);
    private final EdgeController controller = new EdgeController(() -> size);

    private static CanvasMouseEvent at(double x, double y, int button) {
        return new CanvasMouseEvent(new Point2D.Double(x / 8, y / 8), new GridPoint((int) Math.round(x / 8),
                (int) Math.round(y / 8)), new Point2D.Double(x, y), button, false, false, 0);
    }

    private static CanvasMouseEvent at(double x, double y) {
        return at(x, y, CanvasMouseEvent.PRIMARY);
    }

    /** A handle over {@code area} that records what it is given. */
    private static final class RecordingHandle implements EdgeHandle {
        final String name;
        final Rectangle area;
        final int priority;
        final List<String> log;
        Predicate<CanvasMouseEvent> claims = event -> true;
        int painted;

        RecordingHandle(String name, Rectangle area, int priority, List<String> log) {
            this.name = name;
            this.area = area;
            this.priority = priority;
            this.log = log;
        }

        @Override public Shape hitArea(Dimension canvasSize) { return area; }
        @Override public int priority() { return priority; }
        @Override public Cursor cursor() { return Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR); }
        @Override public String tooltip() { return name + " tip"; }
        @Override public boolean claims(CanvasMouseEvent event) { return claims.test(event); }

        @Override
        public void paint(Graphics2D screenGraphics, Dimension canvasSize, boolean hovered) {
            painted++;
            log.add("paint " + name + (hovered ? " hovered" : ""));
        }

        @Override
        public EdgeGesture press(CanvasMouseEvent event) {
            log.add("press " + name);
            return new EdgeGesture() {
                @Override public void drag(CanvasMouseEvent drag) { log.add("drag " + name); }
                @Override public void release(CanvasMouseEvent release) { log.add("release " + name); }
                @Override public void cancel() { log.add("cancel " + name); }
                @Override public void paint(Graphics2D screenGraphics) { log.add("gesture " + name); }
            };
        }
    }

    private final List<String> log = new ArrayList<>();

    @Test
    void aPressOutsideEveryHandleIsNotTaken() {
        controller.add(new RecordingHandle("ruler", new Rectangle(0, 0, 200, 10), 1, log));

        assertThat(controller.press(at(50, 50))).isFalse();
        assertThat(controller.isActive()).isFalse();
        assertThat(controller.drag(at(60, 60))).isFalse();
        assertThat(controller.release(at(60, 60))).isFalse();
        assertThat(log).isEmpty();
    }

    @Test
    void theGestureOwnsEveryDragAndTheReleaseUntilItEnds() {
        controller.add(new RecordingHandle("ruler", new Rectangle(0, 0, 200, 10), 1, log));

        assertThat(controller.press(at(50, 5))).isTrue();
        assertThat(controller.isActive()).isTrue();
        assertThat(controller.drag(at(50, 80))).isTrue();
        assertThat(controller.release(at(50, 80))).isTrue();

        assertThat(log).containsExactly("press ruler", "drag ruler", "release ruler");
        assertThat(controller.isActive()).isFalse();
        assertThat(controller.drag(at(50, 90))).isFalse();
    }

    @Test
    void theHighestPriorityHandleThatClaimsThePressWins() {
        RecordingHandle low = new RecordingHandle("low", new Rectangle(0, 0, 200, 100), 1, log);
        RecordingHandle high = new RecordingHandle("high", new Rectangle(0, 0, 20, 20), 5, log);
        controller.add(high);
        controller.add(low);

        controller.press(at(5, 5));
        controller.release(at(5, 5));
        high.claims = event -> false;
        controller.press(at(5, 5));

        assertThat(log).containsExactly("press high", "release high", "press low");
    }

    @Test
    void anotherButtonOnAHandleIsSwallowedWithoutAGesture() {
        controller.add(new RecordingHandle("ruler", new Rectangle(0, 0, 200, 10), 1, log));

        assertThat(controller.press(at(50, 5, CanvasMouseEvent.SECONDARY))).isTrue();
        assertThat(controller.isActive()).isFalse();
        assertThat(controller.drag(at(50, 50))).isTrue();
        assertThat(controller.release(at(50, 50))).isTrue();
        assertThat(log).isEmpty();
        assertThat(controller.release(at(50, 50))).isFalse();
    }

    @Test
    void cancelEndsTheGestureOnceAndTheReleaseThenGoesElsewhere() {
        controller.add(new RecordingHandle("ruler", new Rectangle(0, 0, 200, 10), 1, log));
        controller.press(at(50, 5));

        assertThat(controller.cancel()).isTrue();
        assertThat(controller.cancel()).isFalse();
        assertThat(controller.isActive()).isFalse();
        assertThat(controller.release(at(50, 50))).isTrue(); // the press still owns its release: the tool never saw it
        assertThat(controller.release(at(50, 50))).isFalse();
        assertThat(log).containsExactly("press ruler", "cancel ruler");
    }

    @Test
    void removingTheHandleOfTheRunningGestureCancelsIt() {
        Runnable remover = controller.add(new RecordingHandle("ruler", new Rectangle(0, 0, 200, 10), 1, log));
        controller.press(at(50, 5));

        remover.run();

        assertThat(controller.isActive()).isFalse();
        assertThat(controller.handles()).isEmpty();
        assertThat(log).containsExactly("press ruler", "cancel ruler");
    }

    @Test
    void hoverGivesTheCursorAndTheTooltipOfTheHandleUnderThePointer() {
        controller.add(new RecordingHandle("ruler", new Rectangle(0, 0, 200, 10), 1, log));

        assertThat(controller.hover(at(50, 5))).isTrue();
        assertThat(controller.cursor()).contains(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        assertThat(controller.tooltip(at(50, 5))).contains("ruler tip");
        assertThat(controller.hover(at(50, 50))).isTrue();
        assertThat(controller.cursor()).isEmpty();
        assertThat(controller.tooltip(at(50, 50))).isEmpty();
        assertThat(controller.hover(at(50, 60))).isFalse(); // nothing changed
    }

    @Test
    void handlesPaintByPriorityWithTheHoveredOneMarkedAndTheGestureOnTop() {
        controller.add(new RecordingHandle("high", new Rectangle(0, 0, 10, 10), 5, log));
        controller.add(new RecordingHandle("low", new Rectangle(0, 0, 200, 10), 1, log));
        controller.hover(at(50, 5));
        controller.press(at(50, 5));
        log.clear();

        BufferedImage image = new BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        controller.paint(graphics);
        graphics.dispose();

        assertThat(log).containsExactly("gesture low", "paint low hovered", "paint high");
    }
}
