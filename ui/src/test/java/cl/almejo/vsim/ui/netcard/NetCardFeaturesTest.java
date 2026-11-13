package cl.almejo.vsim.ui.netcard;

import cl.almejo.vsim.core.command.RemoveElementsCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.CanvasClicks;
import cl.almejo.vsim.ui.EditorFixture;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.ManualFrameTicker;
import cl.almejo.vsim.ui.inline.InlineEditRequest;
import cl.almejo.vsim.ui.inline.InlineValidators;
import cl.almejo.vsim.ui.tools.PanTool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import java.awt.Font;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.WIDTH;
import static cl.almejo.vsim.ui.UiTestSupport.addWire;
import static cl.almejo.vsim.ui.netcard.NetCardCircuits.netOf;
import static cl.almejo.vsim.ui.netcard.NetCardCircuits.snapshot;
import static org.assertj.core.api.Assertions.assertThat;

class NetCardFeaturesTest {

    private static final long DELAY = 750;

    private final AppPreferences preferences = AppPreferences.inMemory();

    {
        // The feature starts off; these tests are about how it behaves once turned on.
        preferences.setHighlightNetOnHover(true);
    }
    private final ManualFrameTicker ticker = new ManualFrameTicker();
    private final AtomicReference<SimulationSnapshot> latest = new AtomicReference<>(SimulationSnapshot.EMPTY);
    private EditorFixture fixture;
    private OpenDocument openDocument;
    private CircuitCanvas canvas;
    private NetCardFeatures features;
    private NetCardCircuits.AndExample example;

    @BeforeEach
    void create() {
        fixture = EditorFixture.create(preferences);
        openDocument = fixture.openDocument();
        canvas = fixture.view().canvas();
        canvas.setSize(1200, 800);
        example = NetCardCircuits.andExample(openDocument);
        features = new NetCardFeatures(fixture.view(), preferences,
                new NetCardFeatures.Setup(() -> ticker, ticker::now, () -> DELAY, document -> latest.get()));
    }

    @AfterEach
    void close() {
        features.dispose();
        fixture.close();
    }

    private Point2D screenOf(double gridX, double gridY) {
        return canvas.viewport().gridToScreen(gridX, gridY);
    }

    private void moveTo(double gridX, double gridY) {
        Point2D point = screenOf(gridX, gridY);
        CanvasClicks.move(canvas, (int) Math.round(point.getX()), (int) Math.round(point.getY()));
    }

    private void moveOverTheAndOutput() {
        moveTo(32.5, 10);
    }

    private void moveOverNothing() {
        moveTo(60, 60);
    }

    private PaneNetCard pane() {
        return features.paneOf(canvas).orElseThrow();
    }

    @Test
    void hoveringAWireLightsItsWholeNetOnly() {
        moveOverTheAndOutput();

        assertThat(pane().shown()).hasValueSatisfying(summary -> {
            assertThat(summary.netId().value()).isEqualTo(netOf(openDocument, example.andOutput()));
            assertThat(summary.wires()).contains(example.notInput().id()).doesNotContain(example.inputA().id());
        });
        assertThat(canvas.overlays()).contains(pane().halo());
    }

    @Test
    void aRubberBandOverAWireLightsNothing() {
        Point2D start = screenOf(60, 60);
        Point2D over = screenOf(32.5, 10);
        MouseEvent pressed = new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 0, InputEvent.BUTTON1_DOWN_MASK,
                (int) start.getX(), (int) start.getY(), 1, false, MouseEvent.BUTTON1);
        for (MouseListener listener : canvas.getMouseListeners()) {
            listener.mousePressed(pressed);
        }
        MouseEvent dragged = new MouseEvent(canvas, MouseEvent.MOUSE_DRAGGED, 0, InputEvent.BUTTON1_DOWN_MASK,
                (int) over.getX(), (int) over.getY(), 0, false, MouseEvent.NOBUTTON);
        for (MouseMotionListener listener : canvas.getMouseMotionListeners()) {
            listener.mouseDragged(dragged);
        }

        assertThat(pane().shown()).isEmpty();
    }

    @Test
    void anotherToolLightsNothing() {
        fixture.view().setToolOnEveryPane(PanTool::new);

        moveOverTheAndOutput();

        assertThat(pane().shown()).isEmpty();
    }

    @Test
    void leavingTheWireKeepsTheHaloForOneSecond() {
        moveOverTheAndOutput();
        ticker.advance(DELAY);

        moveOverNothing();
        ticker.advance(999);
        assertThat(pane().shown()).isPresent();
        ticker.advance(1);

        assertThat(pane().shown()).isEmpty();
        assertThat(ticker.isRunning()).isFalse();
    }

    @Test
    void crossingAnotherWireBrieflyKeepsTheFirstNetAndRestingThereSwitches() {
        moveOverTheAndOutput();
        ticker.advance(DELAY);
        String first = netOf(openDocument, example.andOutput());

        moveTo(example.inputA().start().x() + 3, example.inputA().start().y());
        ticker.advance(DELAY - 16);
        assertThat(pane().shown().orElseThrow().netId().value()).isEqualTo(first);
        ticker.advance(16);

        assertThat(pane().shown().orElseThrow().netId().value()).isEqualTo(netOf(openDocument, example.inputA()));
    }

    @Test
    void withThePreferenceOffNothingLightsAndTheBusBalloonComesBack() {
        Component constant = NetCardCircuits.placeWithPinAt(openDocument, StandardComponentTypes.CONSTANT,
                Map.of(WIDTH, 4), "", 0, 10, 40);
        Wire bus = addWire(openDocument, 10, 40, 20, 40);
        fixture.view().problems().showSnapshot(snapshot(openDocument, Map.of(netOf(openDocument, bus),
                Value.of(4, 0xB))));
        Point2D over = screenOf(15, 40);
        MouseEvent hover = new MouseEvent(canvas, MouseEvent.MOUSE_MOVED, 0, 0, (int) over.getX(), (int) over.getY(),
                0, false);
        assertThat(canvas.getToolTipText(hover)).isNull();

        preferences.setHighlightNetOnHover(false);
        moveTo(15, 40);

        assertThat(pane().shown()).isEmpty();
        assertThat(canvas.getToolTipText(hover)).startsWith("Bus de 4 bits = 0xB");
        assertThat(constant).isNotNull();
    }

    @Test
    void whereNoCardCanShowTheBusBalloonStaysEvenWithThePreferenceOn() {
        NetCardCircuits.placeWithPinAt(openDocument, StandardComponentTypes.CONSTANT, Map.of(WIDTH, 4), "", 0, 10,
                40);
        Wire bus = addWire(openDocument, 10, 40, 20, 40);
        fixture.view().problems().showSnapshot(snapshot(openDocument, Map.of(netOf(openDocument, bus),
                Value.of(4, 0xB))));
        Point2D over = screenOf(15, 40);
        MouseEvent hover = new MouseEvent(canvas, MouseEvent.MOUSE_MOVED, 0, 0, (int) over.getX(), (int) over.getY(),
                0, false);

        fixture.view().setToolOnEveryPane(PanTool::new);

        assertThat(preferences.highlightNetOnHover()).isTrue();
        assertThat(canvas.getToolTipText(hover)).startsWith("Bus de 4 bits = 0xB");
    }

    @Test
    void turningThePreferenceOffPutsOutTheHalo() {
        moveOverTheAndOutput();

        preferences.setHighlightNetOnHover(false);

        assertThat(pane().shown()).isEmpty();
    }

    @Test
    void theStaticInstallReturnsItsUndoing() {
        features.dispose();
        int overlays = canvas.overlays().size();
        int listeners = canvas.snapshotListenerCount();

        Runnable uninstall = NetCardFeatures.install(fixture.view(), preferences);
        assertThat(canvas.overlays()).hasSize(overlays + 1);
        uninstall.run();

        assertThat(canvas.overlays()).hasSize(overlays);
        assertThat(canvas.snapshotListenerCount()).isEqualTo(listeners);
    }

    @Test
    void draggingTheCardMovesItByTheDeltaAndPinsIt() {
        showTheCard();
        SignalCard card = shownCard();
        int startX = card.getX();
        int startY = card.getY();

        drag(card, 5, 5, 45, 25);

        assertThat(card.getX()).isEqualTo(startX + 40);
        assertThat(card.getY()).isEqualTo(startY + 20);
        assertThat(card.isPinned()).isTrue();
    }

    @Test
    void theDraggedCardStaysInsideThePane() {
        showTheCard();
        SignalCard card = shownCard();

        drag(card, 5, 5, 5 + 5_000, 5 + 5_000);
        assertThat(card.getX() + card.getWidth()).isLessThanOrEqualTo(canvas.getWidth());
        assertThat(card.getY() + card.getHeight()).isLessThanOrEqualTo(canvas.getHeight());
        assertThat(card.getX()).isGreaterThan(canvas.getWidth() - card.getWidth() - 10);

        drag(card, 5, 5, 5 - 5_000, 5 - 5_000);
        assertThat(card.getX()).isBetween(0, 10);
        assertThat(card.getY()).isBetween(0, 10);
    }

    @Test
    void pressingOnAPartLinkAndMovingDoesNotDragTheCard() {
        showTheCard();
        SignalCard card = shownCard();
        javax.swing.AbstractButton link = card.endButton("LED L1 · in").orElseThrow();
        int startX = card.getX();
        int startY = card.getY();

        drag(link, 1, 1, 61, 41);

        assertThat(card.getX()).isEqualTo(startX);
        assertThat(card.getY()).isEqualTo(startY);
        assertThat(card.isPinned()).isFalse();
    }

    @Test
    void aClickWithoutMovementNeitherMovesNorPinsTheCard() {
        showTheCard();
        SignalCard card = shownCard();
        int startX = card.getX();
        int startY = card.getY();

        drag(card, 5, 5, 6, 6); // under the threshold

        assertThat(card.getX()).isEqualTo(startX);
        assertThat(card.getY()).isEqualTo(startY);
        assertThat(card.isPinned()).isFalse();
    }

    @Test
    void aValueRefreshKeepsTheMovedPosition() {
        showTheCard();
        SignalCard card = shownCard();
        drag(card, 5, 5, 105, 65);
        int movedX = card.getX();
        int movedY = card.getY();

        latest.set(snapshot(openDocument, Map.of(netOf(openDocument, example.andOutput()), Value.ONE)));
        pane().snapshotArrived();

        assertThat(shownCard().getX()).isEqualTo(movedX);
        assertThat(shownCard().getY()).isEqualTo(movedY);
    }

    @Test
    void thePinnedCardHasNoHintLine() {
        pinTheCard();

        assertThat(shownCard().texts()).noneMatch(text -> text.contains("Esc"));
    }

    /**
     * A press on {@code pressed}, a drag and a release, with the points relative to {@code pressed} where it was
     * pressed: like the real events, each one is relative to where the component is at that moment.
     */
    private void drag(java.awt.Component pressed, int fromX, int fromY, int toX, int toY) {
        java.awt.Point origin = javax.swing.SwingUtilities.convertPoint(pressed, 0, 0, canvas);
        int fromPaneX = origin.x + fromX;
        int fromPaneY = origin.y + fromY;
        MouseEvent press = relativeEvent(pressed, MouseEvent.MOUSE_PRESSED, fromPaneX, fromPaneY);
        for (MouseListener listener : pressed.getMouseListeners()) {
            listener.mousePressed(press);
        }
        for (int step = 1; step <= 4; step++) {
            MouseEvent move = relativeEvent(pressed, MouseEvent.MOUSE_DRAGGED,
                    fromPaneX + (toX - fromX) * step / 4, fromPaneY + (toY - fromY) * step / 4);
            for (MouseMotionListener listener : pressed.getMouseMotionListeners()) {
                listener.mouseDragged(move);
            }
        }
        MouseEvent release = relativeEvent(pressed, MouseEvent.MOUSE_RELEASED, fromPaneX + toX - fromX,
                fromPaneY + toY - fromY);
        for (MouseListener listener : pressed.getMouseListeners()) {
            listener.mouseReleased(release);
        }
    }

    /** An event at a point of the pane, expressed relative to {@code target} where it is now. */
    private MouseEvent relativeEvent(java.awt.Component target, int id, int paneX, int paneY) {
        java.awt.Point point = javax.swing.SwingUtilities.convertPoint(canvas, paneX, paneY, target);
        return new MouseEvent(target, id, 0, InputEvent.BUTTON1_DOWN_MASK, point.x, point.y, 1, false,
                MouseEvent.BUTTON1);
    }

    private SignalCard shownCard() {
        return pane().card().orElseThrow(() -> new AssertionError("no card on the pane"));
    }

    private void showTheCard() {
        moveOverTheAndOutput();
        ticker.advance(DELAY);
    }

    private void pinTheCard() {
        showTheCard();
        SignalCard card = shownCard();
        dispatchToCard(card, MouseEvent.MOUSE_ENTERED, 2, 2);
        card.pinButton().doClick();
    }

    private static void dispatchToCard(SignalCard card, int id, int x, int y) {
        MouseEvent event = new MouseEvent(card, id, 0, 0, x, y, 0, false);
        for (MouseListener listener : card.getMouseListeners()) {
            if (id == MouseEvent.MOUSE_ENTERED) {
                listener.mouseEntered(event);
            } else {
                listener.mouseExited(event);
            }
        }
    }

    /** Esc as the editor delivers it: to every key listener of the canvas, its own first. */
    private KeyEvent pressEscape() {
        KeyEvent escape = new KeyEvent(canvas, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED);
        for (KeyListener listener : canvas.getKeyListeners()) {
            listener.keyPressed(escape);
        }
        return escape;
    }

    /** The pointer leaves the canvas (onto the card, which is a child of it). */
    private void leaveTheCanvas() {
        MouseEvent exited = new MouseEvent(canvas, MouseEvent.MOUSE_EXITED, 0, 0, -1, -1, 0, false);
        for (MouseListener listener : canvas.getMouseListeners()) {
            listener.mouseExited(exited);
        }
    }

    @Test
    void theCardShowsAfterTheBalloonDelayNextToThePointerInsideThePane() {
        moveOverTheAndOutput();
        ticker.advance(DELAY - 16);
        assertThat(pane().card()).isEmpty();
        ticker.advance(16);

        SignalCard card = shownCard();
        assertThat(card.getParent()).isSameAs(canvas);
        assertThat(card.texts()).containsSubsequence("Red de 1 bit = sin simular", "La maneja", "AND U1 · out",
                "Llega a");
        assertThat(card.texts()).contains("LED L1 · in", "NOT U2 · in");
        Point2D pointer = screenOf(32.5, 10);
        assertThat(card.getX()).isGreaterThan((int) pointer.getX());
        assertThat(card.getY()).isGreaterThan((int) pointer.getY());
        assertThat(card.getX() + card.getWidth()).isLessThanOrEqualTo(canvas.getWidth());
    }

    @Test
    void theCardShowsTheLiveValue() {
        showTheCard();

        latest.set(snapshot(openDocument, Map.of(netOf(openDocument, example.andOutput()), Value.ONE)));
        pane().snapshotArrived();

        assertThat(shownCard().texts()).contains("Red de 1 bit = 1");
    }

    @Test
    void aNewSnapshotThatLeavesTheNetAsItWasDoesNotBuildItsSummaryAgain() {
        showTheCard();
        latest.set(snapshot(openDocument, Map.of(netOf(openDocument, example.andOutput()), Value.ONE)));
        pane().snapshotArrived();
        NetSummary first = pane().shown().orElseThrow();

        latest.set(snapshot(openDocument, Map.of(netOf(openDocument, example.andOutput()), Value.ONE,
                netOf(openDocument, example.inputA()), Value.ZERO)));
        pane().snapshotArrived();

        assertThat(pane().shown()).containsSame(first);
    }

    @Test
    void thePointerOverTheCardKeepsItAndLeavingItFadesItAway() {
        showTheCard();
        moveOverNothing();
        ticker.advance(500);
        assertThat(shownCard().opacity()).isLessThan(1f);

        dispatchToCard(shownCard(), MouseEvent.MOUSE_ENTERED, 2, 2);
        ticker.advance(3_000);
        assertThat(shownCard().opacity()).isEqualTo(1f);
        dispatchToCard(shownCard(), MouseEvent.MOUSE_EXITED, -50, -50);
        ticker.advance(1_000);

        assertThat(pane().card()).isEmpty();
        assertThat(pane().shown()).isEmpty();
    }

    @Test
    void aPinnedCardStaysAfterThePointerLeavesWithItsNetLit() {
        pinTheCard();
        dispatchToCard(shownCard(), MouseEvent.MOUSE_EXITED, -50, -50);
        moveOverNothing();
        ticker.advance(2_000);

        assertThat(shownCard().isPinned()).isTrue();
        assertThat(pane().shown()).isPresent();
    }

    @Test
    void escClosesAPinnedCardAndIsLeftAloneOtherwise() {
        showTheCard();
        assertThat(canvas.isEscapeClaimed()).isFalse();
        pressEscape();
        assertThat(pane().card()).isPresent();

        pinTheCard();
        assertThat(canvas.isEscapeClaimed()).isTrue();
        KeyEvent escape = pressEscape();

        assertThat(escape.isConsumed()).isTrue();
        assertThat(pane().card()).isEmpty();
        assertThat(canvas.isEscapeClaimed()).isFalse();
    }

    @Test
    void escOnAPinnedCardClosesOnlyTheCardAndKeepsTheSelection() {
        pinTheCard();
        shownCard().endButton("NOT U2 · in").orElseThrow().doClick();
        assertThat(fixture.view().selection().ids()).containsExactly(example.not().id());

        pressEscape();

        assertThat(pane().card()).isEmpty();
        assertThat(fixture.view().selection().ids()).containsExactly(example.not().id());
    }

    @Test
    void aCardClosedWithThePointerOnItDoesNotLeaveTheNextOneFadingUnderThePointer() {
        pinTheCard();
        shownCard().pinButton().doClick();
        assertThat(pane().card()).isEmpty();

        showTheCard();
        leaveTheCanvas();
        dispatchToCard(shownCard(), MouseEvent.MOUSE_ENTERED, 2, 2);
        ticker.advance(3_000);

        assertThat(pane().card()).isPresent();
        assertThat(shownCard().opacity()).isEqualTo(1f);
    }

    @Test
    void aCardShownAgainTakesTheLookAndFeelThatChangedWhileItWasAway() {
        showTheCard();
        moveOverNothing();
        ticker.advance(2_000);
        assertThat(pane().card()).isEmpty();
        Font original = UIManager.getFont("Button.font");
        try {
            FontUIResource bigger = new FontUIResource(Font.SANS_SERIF, Font.PLAIN, 31);
            UIManager.put("Button.font", bigger);

            showTheCard();

            assertThat(shownCard().endButton("NOT U2 · in").orElseThrow().getFont()).isEqualTo(bigger);
        } finally {
            UIManager.put("Button.font", original);
        }
    }

    @Test
    void hidingTheTabClosesAPinnedCard() {
        pinTheCard();

        fixture.view().interruptGestures();

        assertThat(pane().card()).isEmpty();
        assertThat(pane().shown()).isEmpty();
    }

    @Test
    void aClickOnTheCanvasOutsideClosesAPinnedCard() {
        pinTheCard();

        Point2D away = screenOf(60, 60);
        MouseEvent pressed = new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 0, InputEvent.BUTTON1_DOWN_MASK,
                (int) away.getX(), (int) away.getY(), 1, false, MouseEvent.BUTTON1);
        for (MouseListener listener : canvas.getMouseListeners()) {
            listener.mousePressed(pressed);
        }

        assertThat(pane().card()).isEmpty();
    }

    @Test
    void clickingAPartOnTheCardSelectsItAndTakesTheViewThere() {
        showTheCard();

        shownCard().endButton("NOT U2 · in").orElseThrow().doClick();

        assertThat(fixture.view().selection().ids()).containsExactly(example.not().id());
    }

    @Test
    void deletingEveryWireOfTheNetClosesAPinnedCard() {
        pinTheCard();
        List<ElementId> wires = List.copyOf(pane().shown().orElseThrow().wires());

        openDocument.execute(new RemoveElementsCommand(wires));

        assertThat(pane().card()).isEmpty();
        assertThat(pane().shown()).isEmpty();
    }

    @Test
    void deletingTheWireTheCardWasOpenedOnKeepsItOnTheRestOfTheNet() {
        pinTheCard();

        openDocument.execute(new RemoveElementsCommand(List.of(example.andOutput().id())));

        assertThat(pane().card()).isPresent();
        assertThat(pane().shown()).hasValueSatisfying(summary -> {
            assertThat(summary.wires()).contains(example.notInput().id()).doesNotContain(example.andOutput().id());
            assertThat(summary.drivers()).isEmpty();
        });
        assertThat(shownCard().texts()).contains("⚠ Nadie la maneja: vale Z");
    }

    @Test
    void anEditOfThePinnedNetShowsOnTheCardWithoutMovingThePointer() {
        pinTheCard();

        NetCardCircuits.placeWithPinAt(openDocument, StandardComponentTypes.LED, Map.of(), "L7", 0, 35, 30);
        addWire(openDocument, 35, 20, 35, 30);

        assertThat(shownCard().texts()).contains("LED L7 · in");
    }

    @Test
    void theCardFollowsItsNetWhenTheNetlistIsCompiledAgainUnderOtherIds() {
        showTheCard();
        String before = netOf(openDocument, example.andOutput());

        openDocument.execute(new RemoveElementsCommand(List.of(example.and().id()))); // the net was named by its pin
        String after = netOf(openDocument, example.andOutput());

        assertThat(after).isNotEqualTo(before);
        assertThat(pane().shown().orElseThrow().netId().value()).isEqualTo(after);
        assertThat(pane().card()).isPresent();
    }

    @Test
    void aWireBeingDrawnLightsNothing() {
        NetCardCircuits.placeWithPinAt(openDocument, StandardComponentTypes.LED, Map.of(), "L8", 0, 50, 30);
        Point2D pin = screenOf(50, 30);
        CanvasClicks.click(canvas, (int) Math.round(pin.getX()), (int) Math.round(pin.getY()));
        assertThat(canvas.drawsBetweenClicks()).isTrue();

        moveOverTheAndOutput();

        assertThat(pane().shown()).isEmpty();
    }

    @Test
    void theInlineEditorOpenLightsNothing() {
        fixture.view().inlineEditor().open(new InlineEditRequest(new Rectangle2D.Double(80, 40, 160, 24),
                new Font(Font.SANS_SERIF, Font.PLAIN, 12), "hola", InlineValidators.anyText(), text -> { }));

        moveOverTheAndOutput();

        assertThat(pane().shown()).isEmpty();
    }

    @Test
    void panningWithSpaceHeldLightsNothing() {
        KeyEvent space = new KeyEvent(canvas, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_SPACE, ' ');
        for (KeyListener listener : canvas.getKeyListeners()) {
            listener.keyPressed(space);
        }

        moveOverTheAndOutput();

        assertThat(pane().shown()).isEmpty();
    }

    @Test
    void aPinnedCardStaysWhenTheToolChanges() {
        pinTheCard();

        fixture.view().setToolOnEveryPane(PanTool::new);

        assertThat(shownCard().isPinned()).isTrue();
    }

    @Test
    void turningThePreferenceOffClosesTheCard() {
        pinTheCard();

        preferences.setHighlightNetOnHover(false);

        assertThat(pane().card()).isEmpty();
    }

    @Test
    void disposingTakesTheCardAway() {
        pinTheCard();

        features.dispose();

        assertThat(canvas.getComponentCount()).isZero();
        assertThat(canvas.isEscapeClaimed()).isFalse();
    }
}
