package cl.almejo.vsim.ui.lens;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.EditorFixture;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.AppPreferences.GridStyle;
import cl.almejo.vsim.ui.app.RecordingKeyDispatchers;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.shortcuts.KeyBindings;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import cl.almejo.vsim.ui.window.panes.CanvasPanes.Orientation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.KeyStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.KeyEventDispatcher;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static cl.almejo.vsim.ui.UiTestSupport.texts;
import static org.assertj.core.api.Assertions.assertThat;

/** The accessibility lens of one view: held with its key, «Lupa fija», the pointer, the panes and its pixels. */
class LensFeaturesTest {

    private static final int CONTROL_SHIFT = InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK;
    private static final int WIDTH = 400;
    private static final int HEIGHT = 300;

    private final List<KeyEventDispatcher> dispatchers = new ArrayList<>();
    private final List<Runnable> deactivationWatchers = new ArrayList<>();
    private AppPreferences preferences;
    private EditorFixture fixture;
    private EditorView view;
    private LensFeatures features;

    @BeforeEach
    void create() {
        preferences = AppPreferences.inMemory();
        fixture = EditorFixture.create(preferences);
        view = fixture.view();
        features = install(view);
    }

    private LensFeatures install(EditorView target) {
        return new LensFeatures(target, new RecordingKeyDispatchers(dispatchers), (watched, whenDeactivated) -> {
            deactivationWatchers.add(whenDeactivated);
            return () -> deactivationWatchers.remove(whenDeactivated);
        });
    }

    @AfterEach
    void close() {
        features.dispose();
        fixture.close();
    }

    private void dispatch(KeyEvent event) {
        for (KeyEventDispatcher dispatcher : List.copyOf(dispatchers)) {
            dispatcher.dispatchKeyEvent(event);
        }
    }

    private void press(int keyCode, int modifiers) {
        dispatch(new KeyEvent(view.canvas(), KeyEvent.KEY_PRESSED, 0, modifiers, keyCode, KeyEvent.CHAR_UNDEFINED));
    }

    private void release(int keyCode, int modifiers) {
        dispatch(new KeyEvent(view.canvas(), KeyEvent.KEY_RELEASED, 0, modifiers, keyCode,
                KeyEvent.CHAR_UNDEFINED));
    }

    private static void move(CircuitCanvas pane, int x, int y) {
        pane.dispatchEvent(new MouseEvent(pane, MouseEvent.MOUSE_MOVED, 0, 0, x, y, 0, false));
    }

    private static void exit(CircuitCanvas pane) {
        pane.dispatchEvent(new MouseEvent(pane, MouseEvent.MOUSE_EXITED, 0, 0, -1, -1, 0, false));
    }

    private JCheckBoxMenuItem fixedItem() {
        for (java.awt.Component child : view.menu(MenuId.VIEW).getMenuComponents()) {
            if (child instanceof JCheckBoxMenuItem item && item.getAction() == features.fixedAction()) {
                return item;
            }
        }
        throw new AssertionError("no «Lupa fija» item in Ver");
    }

    private static final int UNDEFINED = KeyEvent.VK_UNDEFINED;

    /** A key Java reports as undefined with an extended code (º on a Spanish layout on Linux). */
    private void pressExtended(int extendedCode, int modifiers) {
        dispatch(new ExtendedKeyEvent(view.canvas(), KeyEvent.KEY_PRESSED, modifiers, extendedCode));
    }

    private void releaseExtended(int extendedCode, int modifiers) {
        dispatch(new ExtendedKeyEvent(view.canvas(), KeyEvent.KEY_RELEASED, modifiers, extendedCode));
    }

    private static final class ExtendedKeyEvent extends KeyEvent {

        private final int extendedCode;

        ExtendedKeyEvent(java.awt.Component source, int id, int modifiers, int extendedCode) {
            super(source, id, 0, modifiers, UNDEFINED, KeyEvent.CHAR_UNDEFINED);
            this.extendedCode = extendedCode;
        }

        @Override
        public int getExtendedKeyCode() {
            return extendedCode;
        }
    }

    @Test
    void theDefaultIsTheKeyUnderEscHeldWithNoModifier() {
        assertThat(new KeyBindings(preferences).strokeFor(LensFeatures.SHORTCUT))
                .contains(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_QUOTE, 0));
    }

    @Test
    void holdingTheBackQuoteInTheCanvasShowsTheLensAndReleasingItHidesIt() {
        move(view.canvas(), 100, 100);
        press(KeyEvent.VK_BACK_QUOTE, 0);
        assertThat(features.state().isShowingOn(view.canvas())).isTrue();
        release(KeyEvent.VK_BACK_QUOTE, 0);
        assertThat(features.state().isShowing()).isFalse();
    }

    @Test
    void theSameKeyInATextFieldOfTheViewTypesNormally() {
        javax.swing.JTextField field = new javax.swing.JTextField();
        view.add(field);
        move(view.canvas(), 100, 100);
        dispatch(new KeyEvent(field, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_BACK_QUOTE, '`'));
        assertThat(features.state().isOn()).isFalse();
        view.remove(field);
    }

    @ParameterizedTest
    @ValueSource(ints = {0x010000BA, 0x010000B2, 0x010000A7, 0x010000B0})
    void theKeyUnderEscOfOtherLayoutsShowsItToo(int extendedCode) {
        move(view.canvas(), 100, 100);
        pressExtended(extendedCode, 0);
        assertThat(features.state().isShowingOn(view.canvas())).isTrue();
        releaseExtended(extendedCode, 0);
        assertThat(features.state().isShowing()).isFalse();
    }

    @Test
    void aKeyOfAnotherCharacterWithAnExtendedCodeDoesNothing() {
        move(view.canvas(), 100, 100);
        pressExtended(0x010000E9, 0);
        assertThat(features.state().isOn()).isFalse();
    }

    @Test
    void aModifierAddedMidHoldDoesNotBreakItAndReleasingTheKeyHides() {
        move(view.canvas(), 100, 100);
        press(KeyEvent.VK_BACK_QUOTE, 0);
        press(KeyEvent.VK_BACK_QUOTE, InputEvent.SHIFT_DOWN_MASK);
        release(KeyEvent.VK_SHIFT, 0);
        assertThat(features.state().isShowing()).isTrue();
        release(KeyEvent.VK_BACK_QUOTE, InputEvent.SHIFT_DOWN_MASK);
        assertThat(features.state().isShowing()).isFalse();
    }

    @Test
    void theKeyRepeatingKeepsItShowingAndAnotherKeyReleasedDoesNotHideIt() {
        move(view.canvas(), 100, 100);
        for (int repeat = 0; repeat < 5; repeat++) {
            press(KeyEvent.VK_BACK_QUOTE, 0);
        }
        release(KeyEvent.VK_A, 0);
        assertThat(features.state().isShowing()).isTrue();
    }

    @Test
    void theHoldRebindsToAnotherKeyAndTheOldOneStopsWorking() {
        new KeyBindings(preferences).assign(LensFeatures.SHORTCUT, KeyStroke.getKeyStroke(KeyEvent.VK_F9, 0));
        move(view.canvas(), 100, 100);
        press(KeyEvent.VK_BACK_QUOTE, 0);
        assertThat(features.state().isOn()).as("the old default").isFalse();
        pressExtended(0x010000BA, 0);
        assertThat(features.state().isOn()).as("an alias of the old default").isFalse();
        press(KeyEvent.VK_F9, 0);
        assertThat(features.state().isShowing()).isTrue();
        release(KeyEvent.VK_F9, 0);
        assertThat(features.state().isShowing()).isFalse();
    }

    @Test
    void aBareLetterCanBeTheHoldKeyToo() {
        new KeyBindings(preferences).assign(LensFeatures.SHORTCUT, KeyStroke.getKeyStroke(KeyEvent.VK_K, 0));
        move(view.canvas(), 100, 100);
        press(KeyEvent.VK_K, 0);
        assertThat(features.state().isShowing()).isTrue();
        release(KeyEvent.VK_K, 0);
        assertThat(features.state().isShowing()).isFalse();
    }

    @Test
    void aBindingWithModifiersIsMatchedOnThemAndReleasedWithThem() {
        new KeyBindings(preferences).assign(LensFeatures.SHORTCUT,
                KeyStroke.getKeyStroke(KeyEvent.VK_L, CONTROL_SHIFT));
        move(view.canvas(), 100, 100);
        press(KeyEvent.VK_L, 0);
        assertThat(features.state().isOn()).isFalse();
        press(KeyEvent.VK_L, CONTROL_SHIFT);
        assertThat(features.state().isShowing()).isTrue();
        release(KeyEvent.VK_CONTROL, InputEvent.SHIFT_DOWN_MASK);
        assertThat(features.state().isShowing()).isFalse();
    }

    @Test
    void theWindowLosingTheFocusHidesAHeldLens() {
        move(view.canvas(), 100, 100);
        press(KeyEvent.VK_BACK_QUOTE, 0);
        assertThat(deactivationWatchers).hasSize(1);
        deactivationWatchers.get(0).run();
        assertThat(features.state().isShowing()).isFalse();
    }

    @Test
    void aKeyFromOutsideTheViewDoesNotHoldIt() {
        EditorView other = fixture.otherView();
        move(view.canvas(), 100, 100);
        dispatch(new KeyEvent(other.canvas(), KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_BACK_QUOTE,
                KeyEvent.CHAR_UNDEFINED));
        assertThat(features.state().isOn()).isFalse();
    }

    @Test
    void lupaFijaIsAnUncheckedItemOfVerAndItsActionTogglesTheLens() {
        assertThat(texts(view.menu(MenuId.VIEW))).contains("Lupa fija");
        assertThat(fixedItem().isSelected()).isFalse();
        move(view.canvas(), 100, 100);

        fixedItem().doClick();

        assertThat(features.state().isFixed()).isTrue();
        assertThat(features.state().isShowingOn(view.canvas())).isTrue();
        assertThat(fixedItem().isSelected()).isTrue();
        features.fixedAction().actionPerformed(null);
        assertThat(features.state().isFixed()).isFalse();
        assertThat(fixedItem().isSelected()).isFalse();
    }

    @Test
    void lupaFijaIsNotSavedANewViewStartsWithItOff() {
        features.fixedAction().actionPerformed(null);
        EditorView other = fixture.otherView();
        LensFeatures otherFeatures = install(other);
        try {
            assertThat(otherFeatures.state().isFixed()).isFalse();
            assertThat(otherFeatures.fixedAction().getValue(Action.SELECTED_KEY)).isEqualTo(false);
        } finally {
            otherFeatures.dispose();
        }
    }

    @Test
    void theHoldIsAShortcutOfTheViewWithItsDefaultAndNoMenuItem() {
        assertThat(view.shortcutActions()).containsKey(LensFeatures.SHORTCUT);
        assertThat(new KeyBindings(preferences).strokeFor(LensFeatures.SHORTCUT))
                .contains(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_QUOTE, 0));
        Action hold = view.shortcutActions().get(LensFeatures.SHORTCUT);
        for (java.awt.Component child : view.menu(MenuId.VIEW).getMenuComponents()) {
            if (child instanceof javax.swing.JMenuItem item) {
                assertThat(item.getAction()).as("a menu item would run the hold on its own").isNotSameAs(hold);
            }
        }
    }

    @Test
    void thePointerLeavingThePaneHidesIt() {
        features.fixedAction().actionPerformed(null);
        move(view.canvas(), 100, 100);
        exit(view.canvas());
        assertThat(features.state().isShowing()).isFalse();
        assertThat(features.state().isFixed()).isTrue();
    }

    @Test
    void inASplitViewOnlyTheHoveredPaneShowsIt() {
        view.canvasPanes().split(Orientation.HORIZONTAL, 0.5);
        List<CircuitCanvas> panes = view.panes();
        assertThat(panes).hasSize(2);
        features.fixedAction().actionPerformed(null);
        move(panes.get(1), 50, 50);
        assertThat(features.state().isShowingOn(panes.get(1))).isTrue();
        assertThat(features.state().isShowingOn(panes.get(0))).isFalse();
        move(panes.get(0), 60, 60);
        assertThat(features.state().isShowingOn(panes.get(0))).isTrue();
        assertThat(features.state().isShowingOn(panes.get(1))).isFalse();
    }

    @Test
    void disposeTakesBackTheItemTheShortcutTheKeyHookAndTheOverlays() {
        features.dispose();
        assertThat(texts(view.menu(MenuId.VIEW))).doesNotContain("Lupa fija");
        assertThat(view.shortcutActions()).doesNotContainKey(LensFeatures.SHORTCUT);
        assertThat(dispatchers).isEmpty();
        assertThat(view.canvas().screenOverlayCount()).isZero();
    }

    // Pixels

    private void addLongWireAtRow(int gridY) {
        fixture.openDocument().execute(new AddElementsCommand(List.of(new Wire(
                fixture.openDocument().document().newWireId(), new GridPoint(-200, gridY),
                new GridPoint(200, gridY)))));
    }

    private BufferedImage paint(CircuitCanvas pane) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            pane.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static int difference(BufferedImage image, int x, int y) {
        Color background = ColorScheme.LIGHT.background();
        Color pixel = new Color(image.getRGB(x, y), true);
        return Math.abs(pixel.getRed() - background.getRed())
                + Math.abs(pixel.getGreen() - background.getGreen())
                + Math.abs(pixel.getBlue() - background.getBlue());
    }

    private static boolean inked(BufferedImage image, int x, int y) {
        return difference(image, x, y) > 60;
    }

    /**
     * How many pixels of ink column {@code x} holds between {@code fromY} and {@code toY}, the antialiased edges
     * counted by their share of the strongest pixel: a wire's painted thickness.
     */
    private static double inkWidth(BufferedImage image, int x, int fromY, int toY) {
        int strongest = 0;
        int total = 0;
        for (int y = fromY; y < toY; y++) {
            strongest = Math.max(strongest, difference(image, x, y));
            total += difference(image, x, y);
        }
        return (double) total / strongest;
    }

    /** The first and last inked rows of column {@code x} between {@code fromY} and {@code toY}. */
    private static int[] inkedRun(BufferedImage image, int x, int fromY, int toY) {
        int first = -1;
        int last = -1;
        for (int y = fromY; y < toY; y++) {
            if (inked(image, x, y)) {
                first = first < 0 ? y : first;
                last = y;
            }
        }
        return new int[]{first, last};
    }

    /** A pane with one long horizontal wire at screen row 150, at {@code zoom}, no grid, the size of the images. */
    private CircuitCanvas paneWithAWire(double zoom) {
        CircuitCanvas pane = view.canvas();
        pane.setSize(WIDTH, HEIGHT);
        pane.setGridStyle(GridStyle.NONE);
        addLongWireAtRow(15);
        pane.viewport().zoomAt(0, 0, zoom);
        double pixelsPerUnit = pane.viewport().pixelsPerUnit();
        pane.viewport().setOrigin(WIDTH / 2.0 - 13, 150 - 15 * pixelsPerUnit);
        return pane;
    }

    @Test
    void atThreeTimesTheWireIsThreeTimesThickerAroundThePointerAndNothingOutsideTheLensChanges() {
        CircuitCanvas pane = paneWithAWire(1);
        BufferedImage plain = paint(pane);
        int[] plainRun = inkedRun(plain, 240, 40, 260);
        assertThat(plainRun[0]).as("the wire shows").isPositive();

        features.fixedAction().actionPerformed(null);
        move(pane, 200, 160); // 10 px under the wire's centre
        BufferedImage magnified = paint(pane);

        int[] lensRun = inkedRun(magnified, 240, 75, 245); // inside the ring, which crosses this column
        double plainCentre = (plainRun[0] + plainRun[1]) / 2.0;
        double lensCentre = (lensRun[0] + lensRun[1]) / 2.0;
        assertThat(lensCentre).as("30 px above the pointer").isBetween(160 - 3 * (160 - plainCentre) - 1.5,
                160 - 3 * (160 - plainCentre) + 1.5);
        double plainWidth = inkWidth(plain, 240, 40, 260);
        double lensWidth = inkWidth(magnified, 240, 75, 245);
        assertThat(lensWidth / plainWidth).as("%s px against %s px", lensWidth, plainWidth).isBetween(2.6, 3.4);
        assertThat(differencesOutsideTheLens(plain, magnified, new Point2D.Double(200, 160))).isZero();
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.25, 4.0})
    void theGridPointUnderThePointerStaysPutAtTheExtremeZooms(double zoom) {
        CircuitCanvas pane = paneWithAWire(zoom);
        BufferedImage plain = paint(pane);
        int[] plainRun = inkedRun(plain, 240, 40, 260);
        double plainCentre = (plainRun[0] + plainRun[1]) / 2.0;

        features.fixedAction().actionPerformed(null);
        move(pane, 200, 160);
        BufferedImage magnified = paint(pane);

        int[] lensRun = inkedRun(magnified, 240, 75, 245);
        double lensCentre = (lensRun[0] + lensRun[1]) / 2.0;
        double expected = 160 - 3 * (160 - plainCentre);
        assertThat(lensCentre).isBetween(expected - 1.5, expected + 1.5);
    }

    @Test
    void anotherPaneOfASplitViewPaintsNoLens() {
        view.canvasPanes().split(Orientation.HORIZONTAL, 0.5);
        CircuitCanvas hovered = view.panes().get(0);
        CircuitCanvas other = view.panes().get(1);
        other.setSize(WIDTH, HEIGHT);
        other.setGridStyle(GridStyle.NONE);
        addLongWireAtRow(15);
        BufferedImage before = paint(other);
        features.fixedAction().actionPerformed(null);
        move(hovered, 200, 160);
        assertThat(differencesOutsideTheLens(before, paint(other), new Point2D.Double(-1000, -1000))).isZero();
    }

    /** Pixels that differ outside the lens's circle (with its ring) and its zoom label around {@code centre}. */
    private static int differencesOutsideTheLens(BufferedImage first, BufferedImage second, Point2D centre) {
        java.awt.Rectangle label = LensPainter.labelArea(centre);
        double ringOutside = LensState.RADIUS + 3;
        int count = 0;
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                boolean inLens = centre.distance(x, y) <= ringOutside || label.contains(x, y);
                if (!inLens && first.getRGB(x, y) != second.getRGB(x, y)) {
                    count++;
                }
            }
        }
        return count;
    }
}
