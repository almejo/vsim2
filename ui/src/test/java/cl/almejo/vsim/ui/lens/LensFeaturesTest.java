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
import cl.almejo.vsim.ui.shortcuts.ShortcutsExtension;
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
import javax.swing.SwingUtilities;
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

/** The accessibility lens of one view: toggled with its key or Ver ▸ «Lupa», the pointer, the panes and its pixels. */
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

    /** The time of the next key event: a person's presses are well apart unless a test says otherwise. */
    private long now = 10_000;

    /**
     * Hands {@code event} to the key hooks as the focus manager does (a hook that takes it stops it), then, if nobody
     * took it, to the key bindings of the component it is for. Returns whether a hook took it.
     */
    private boolean dispatch(KeyEvent event) {
        for (KeyEventDispatcher dispatcher : List.copyOf(dispatchers)) {
            if (dispatcher.dispatchKeyEvent(event)) {
                return true;
            }
        }
        SwingUtilities.processKeyBindings(event);
        return false;
    }

    private boolean press(int keyCode, int modifiers) {
        now += 200;
        return pressAt(now, keyCode, modifiers);
    }

    private boolean pressAt(long when, int keyCode, int modifiers) {
        return dispatch(new KeyEvent(view.canvas(), KeyEvent.KEY_PRESSED, when, modifiers, keyCode,
                KeyEvent.CHAR_UNDEFINED));
    }

    private void release(int keyCode, int modifiers) {
        now += 200;
        releaseAt(now, keyCode, modifiers);
    }

    private void releaseAt(long when, int keyCode, int modifiers) {
        dispatch(new KeyEvent(view.canvas(), KeyEvent.KEY_RELEASED, when, modifiers, keyCode,
                KeyEvent.CHAR_UNDEFINED));
    }

    /** A press and its release, a moment apart. */
    private void tap(int keyCode, int modifiers) {
        press(keyCode, modifiers);
        release(keyCode, modifiers);
    }

    private static void move(CircuitCanvas pane, int x, int y) {
        pane.dispatchEvent(new MouseEvent(pane, MouseEvent.MOUSE_MOVED, 0, 0, x, y, 0, false));
    }

    private static void exit(CircuitCanvas pane) {
        pane.dispatchEvent(new MouseEvent(pane, MouseEvent.MOUSE_EXITED, 0, 0, -1, -1, 0, false));
    }

    private JCheckBoxMenuItem lensItem() {
        for (java.awt.Component child : view.menu(MenuId.VIEW).getMenuComponents()) {
            if (child instanceof JCheckBoxMenuItem item && item.getAction() == features.toggleAction()) {
                return item;
            }
        }
        throw new AssertionError("no «Lupa» item in Ver");
    }

    private static final int UNDEFINED = KeyEvent.VK_UNDEFINED;

    /** A key Java reports as undefined with an extended code (º on a Spanish layout on Linux). */
    private void pressExtended(int extendedCode, int modifiers) {
        now += 200;
        dispatch(new ExtendedKeyEvent(view.canvas(), KeyEvent.KEY_PRESSED, now, modifiers, extendedCode));
    }

    private void releaseExtended(int extendedCode, int modifiers) {
        now += 200;
        dispatch(new ExtendedKeyEvent(view.canvas(), KeyEvent.KEY_RELEASED, now, modifiers, extendedCode));
    }

    /** A key Java gives a real code and, besides, an extended one that says which character it is (Windows, macOS). */
    private void pressRealWithExtended(int keyCode, int extendedCode) {
        now += 200;
        dispatch(new ExtendedKeyEvent(view.canvas(), KeyEvent.KEY_PRESSED, now, 0, keyCode, extendedCode));
    }

    private static final class ExtendedKeyEvent extends KeyEvent {

        private final int extendedCode;

        ExtendedKeyEvent(java.awt.Component source, int id, long when, int modifiers, int extendedCode) {
            this(source, id, when, modifiers, UNDEFINED, extendedCode);
        }

        ExtendedKeyEvent(java.awt.Component source, int id, long when, int modifiers, int keyCode,
                         int extendedCode) {
            super(source, id, when, modifiers, keyCode, KeyEvent.CHAR_UNDEFINED);
            this.extendedCode = extendedCode;
        }

        @Override
        public int getExtendedKeyCode() {
            return extendedCode;
        }
    }

    @Test
    void theDefaultIsTheKeyUnderEscWithNoModifier() {
        assertThat(new KeyBindings(preferences).strokeFor(LensFeatures.SHORTCUT))
                .contains(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_QUOTE, 0));
    }

    @Test
    void theKeyTurnsTheLensOnAndPressingItAgainTurnsItOff() {
        move(view.canvas(), 100, 100);
        press(KeyEvent.VK_BACK_QUOTE, 0);
        assertThat(features.state().isShowingOn(view.canvas())).isTrue();
        release(KeyEvent.VK_BACK_QUOTE, 0);
        assertThat(features.state().isShowingOn(view.canvas())).as("releasing does not turn it off").isTrue();
        tap(KeyEvent.VK_BACK_QUOTE, 0);
        assertThat(features.state().isOn()).isFalse();
    }

    @Test
    void theKeyRepeatingWithoutReleasesTogglesOnceAndTheRepeatsAreTaken() {
        move(view.canvas(), 100, 100);
        assertThat(pressAt(1_000, KeyEvent.VK_BACK_QUOTE, 0)).as("the first press goes on").isFalse();
        for (int repeat = 1; repeat <= 10; repeat++) {
            assertThat(pressAt(1_000 + 500 + 30L * repeat, KeyEvent.VK_BACK_QUOTE, 0))
                    .as("a repeat reaches nobody else").isTrue();
        }
        releaseAt(2_000, KeyEvent.VK_BACK_QUOTE, 0);
        assertThat(features.state().isOn()).isTrue();
    }

    @Test
    void theKeyRepeatingAsReleaseAndPressPairsTogglesOnce() {
        move(view.canvas(), 100, 100);
        pressAt(1_000, KeyEvent.VK_BACK_QUOTE, 0);
        for (long when = 1_500; when < 2_000; when += 30) {
            releaseAt(when, KeyEvent.VK_BACK_QUOTE, 0);
            assertThat(pressAt(when, KeyEvent.VK_BACK_QUOTE, 0)).as("a repeat reaches nobody else").isTrue();
        }
        releaseAt(2_000, KeyEvent.VK_BACK_QUOTE, 0);
        assertThat(features.state().isOn()).isTrue();
        pressAt(2_200, KeyEvent.VK_BACK_QUOTE, 0);
        assertThat(features.state().isOn()).as("a new press, well after the release").isFalse();
    }

    @Test
    void anotherKeyBetweenTwoPressesDoesNotMakeTheSecondARepeat() {
        move(view.canvas(), 100, 100);
        press(KeyEvent.VK_BACK_QUOTE, 0);
        release(KeyEvent.VK_A, 0);
        release(KeyEvent.VK_BACK_QUOTE, 0);
        press(KeyEvent.VK_BACK_QUOTE, 0);
        assertThat(features.state().isOn()).isFalse();
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
    @ValueSource(ints = {0x010000BA, 0x010000B2, 0x010000A7})
    void theKeyUnderEscOfOtherLayoutsTogglesItToo(int extendedCode) {
        move(view.canvas(), 100, 100);
        pressExtended(extendedCode, 0);
        pressExtended(extendedCode, 0);
        assertThat(features.state().isShowingOn(view.canvas())).as("on once, the second press repeats").isTrue();
        releaseExtended(extendedCode, 0);
        pressExtended(extendedCode, 0);
        assertThat(features.state().isOn()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {0x010000AA, 0x010000B0, KeyEvent.VK_DEAD_CIRCUMFLEX})
    void keysThatAreNotTheOneUnderEscOnAnyLayoutDoNothing(int code) {
        move(view.canvas(), 100, 100);
        if (code == KeyEvent.VK_DEAD_CIRCUMFLEX) {
            press(code, 0);
        } else {
            pressExtended(code, 0);
        }
        assertThat(features.state().isOn()).isFalse();
    }

    @Test
    void aRealKeyCodeWithTheExtendedCodeOfTheKeyUnderEscStillToggles() {
        move(view.canvas(), 100, 100);
        pressRealWithExtended(KeyEvent.VK_BACK_SLASH, 0x010000BA); // º as a system with a real code reports it
        assertThat(features.state().isOn()).isTrue();
        pressRealWithExtended(KeyEvent.VK_BACK_SLASH, 0x010000BA);
        assertThat(features.state().isOn()).as("the same key repeating").isTrue();
    }

    @Test
    void aKeyBoundByItsExtendedCodeMatchesWhenTheEventAlsoHasARealCode() {
        new KeyBindings(preferences).assign(LensFeatures.SHORTCUT, KeyStroke.getKeyStroke(0x010000E7, 0)); // ç
        move(view.canvas(), 100, 100);
        pressRealWithExtended(KeyEvent.VK_C, 0x010000E7);
        assertThat(features.state().isOn()).isTrue();
    }

    @Test
    void whileAPartHasTheKeyboardTheKeyDoesNothingAndEveryPressReachesThePart() {
        view.keyboardCapture().capture("some-part");
        move(view.canvas(), 100, 100);
        assertThat(press(KeyEvent.VK_BACK_QUOTE, 0)).isFalse();
        assertThat(pressAt(now + 30, KeyEvent.VK_BACK_QUOTE, 0)).as("a repeat is not taken").isFalse();
        pressExtended(0x010000BA, 0);
        assertThat(features.state().isOn()).isFalse();
        view.keyboardCapture().release();
        pressExtended(0x010000BA, 0);
        assertThat(features.state().isOn()).as("with the keyboard back it works").isTrue();
    }

    @Test
    void turningItOnWithThePointerAlreadyOverAPaneShowsItAtOnce() {
        features.dispose();
        features = new LensFeatures(view, new RecordingKeyDispatchers(dispatchers),
                (watched, whenDeactivated) -> () -> {
                }, pane -> java.util.Optional.of(new java.awt.Point(120, 80)));
        features.toggleAction().actionPerformed(null);
        assertThat(features.state().isShowingOn(view.canvas())).isTrue();
        assertThat(features.state().pointer()).contains(new java.awt.Point(120, 80));
    }

    @Test
    void turningItOnWithNoPointerOverAPaneWaitsForTheFirstMove() {
        features.toggleAction().actionPerformed(null);
        assertThat(features.state().isShowing()).isFalse();
        move(view.canvas(), 10, 10);
        assertThat(features.state().isShowing()).isTrue();
    }

    @Test
    void aKeyOfAnotherCharacterWithAnExtendedCodeDoesNothing() {
        move(view.canvas(), 100, 100);
        pressExtended(0x010000E9, 0);
        assertThat(features.state().isOn()).isFalse();
    }

    @Test
    void theKeyRebindsToAnotherKeyAndTheOldOneStopsWorking() {
        new KeyBindings(preferences).assign(LensFeatures.SHORTCUT, KeyStroke.getKeyStroke(KeyEvent.VK_F9, 0));
        move(view.canvas(), 100, 100);
        tap(KeyEvent.VK_BACK_QUOTE, 0);
        assertThat(features.state().isOn()).as("the old default").isFalse();
        pressExtended(0x010000BA, 0);
        assertThat(features.state().isOn()).as("an alias of the old default").isFalse();
        tap(KeyEvent.VK_F9, 0);
        assertThat(features.state().isShowing()).isTrue();
        tap(KeyEvent.VK_F9, 0);
        assertThat(features.state().isShowing()).isFalse();
    }

    @Test
    void aBareLetterCanBeTheKeyToo() {
        new KeyBindings(preferences).assign(LensFeatures.SHORTCUT, KeyStroke.getKeyStroke(KeyEvent.VK_K, 0));
        move(view.canvas(), 100, 100);
        tap(KeyEvent.VK_K, 0);
        assertThat(features.state().isShowing()).isTrue();
    }

    @Test
    void aBindingWithModifiersIsMatchedOnThem() {
        new KeyBindings(preferences).assign(LensFeatures.SHORTCUT,
                KeyStroke.getKeyStroke(KeyEvent.VK_L, CONTROL_SHIFT));
        move(view.canvas(), 100, 100);
        tap(KeyEvent.VK_L, 0);
        assertThat(features.state().isOn()).isFalse();
        tap(KeyEvent.VK_L, CONTROL_SHIFT);
        assertThat(features.state().isShowing()).isTrue();
    }

    @Test
    void aReleaseLostWithTheWindowDoesNotSwallowTheNextPress() {
        move(view.canvas(), 100, 100);
        press(KeyEvent.VK_BACK_QUOTE, 0);
        assertThat(deactivationWatchers).hasSize(1);
        deactivationWatchers.get(0).run(); // the key went up in another window
        assertThat(features.state().isOn()).as("going to another window does not turn it off").isTrue();
        press(KeyEvent.VK_BACK_QUOTE, 0);
        assertThat(features.state().isOn()).isFalse();
    }

    @Test
    void aKeyFromOutsideTheViewDoesNothing() {
        EditorView other = fixture.otherView();
        move(view.canvas(), 100, 100);
        dispatch(new KeyEvent(other.canvas(), KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_BACK_QUOTE,
                KeyEvent.CHAR_UNDEFINED));
        assertThat(features.state().isOn()).isFalse();
    }

    @Test
    void withTheCanvasKeyBindingsEachPressTogglesExactlyOnce() {
        Runnable shortcuts = new ShortcutsExtension().install(view);
        try {
            move(view.canvas(), 100, 100);
            press(KeyEvent.VK_BACK_QUOTE, 0);
            assertThat(features.state().isOn()).as("the binding and the hook do not both toggle").isTrue();
            release(KeyEvent.VK_BACK_QUOTE, 0);
            pressExtended(0x010000BA, 0);
            assertThat(features.state().isOn()).as("º, which the binding does not know").isFalse();
            releaseExtended(0x010000BA, 0);
            press(KeyEvent.VK_BACK_QUOTE, 0);
            assertThat(features.state().isOn()).isTrue();
        } finally {
            shortcuts.run();
        }
    }

    @Test
    void lupaIsAnUncheckedItemOfVerSharingItsStateWithTheKey() {
        assertThat(texts(view.menu(MenuId.VIEW))).contains("Lupa").doesNotContain("Lupa fija");
        assertThat(lensItem().isSelected()).isFalse();
        move(view.canvas(), 100, 100);

        lensItem().doClick();
        assertThat(features.state().isShowingOn(view.canvas())).isTrue();
        assertThat(lensItem().isSelected()).isTrue();

        tap(KeyEvent.VK_BACK_QUOTE, 0);
        assertThat(features.state().isOn()).isFalse();
        assertThat(lensItem().isSelected()).as("the key unchecks it").isFalse();
        tap(KeyEvent.VK_BACK_QUOTE, 0);
        assertThat(lensItem().isSelected()).isTrue();
    }

    @Test
    void theLensIsNotSavedANewViewStartsWithItOff() {
        features.toggleAction().actionPerformed(null);
        EditorView other = fixture.otherView();
        LensFeatures otherFeatures = install(other);
        try {
            assertThat(otherFeatures.state().isOn()).isFalse();
            assertThat(otherFeatures.toggleAction().getValue(Action.SELECTED_KEY)).isEqualTo(false);
        } finally {
            otherFeatures.dispose();
        }
    }

    @Test
    void theKeysActionIsTheMenuItemsAction() {
        assertThat(view.shortcutActions().get(LensFeatures.SHORTCUT)).isSameAs(features.toggleAction());
        assertThat(lensItem().getAction()).isSameAs(features.toggleAction());
    }

    @Test
    void thePointerLeavingThePaneHidesIt() {
        features.toggleAction().actionPerformed(null);
        move(view.canvas(), 100, 100);
        exit(view.canvas());
        assertThat(features.state().isShowing()).isFalse();
        assertThat(features.state().isOn()).isTrue();
    }

    @Test
    void inASplitViewOnlyTheHoveredPaneShowsIt() {
        view.canvasPanes().split(Orientation.HORIZONTAL, 0.5);
        List<CircuitCanvas> panes = view.panes();
        assertThat(panes).hasSize(2);
        features.toggleAction().actionPerformed(null);
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
        assertThat(texts(view.menu(MenuId.VIEW))).doesNotContain("Lupa");
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
    void atTheDefaultFactorTheWireIsThatMuchThickerAroundThePointerAndNothingOutsideTheLensChanges() {
        CircuitCanvas pane = paneWithAWire(1);
        BufferedImage plain = paint(pane);
        int[] plainRun = inkedRun(plain, 240, 40, 260);
        assertThat(plainRun[0]).as("the wire shows").isPositive();

        features.toggleAction().actionPerformed(null);
        move(pane, 200, 160); // 10 px under the wire's centre
        BufferedImage magnified = paint(pane);

        int[] lensRun = inkedRun(magnified, 240, 75, 245); // inside the ring, which crosses this column
        double plainCentre = (plainRun[0] + plainRun[1]) / 2.0;
        double lensCentre = (lensRun[0] + lensRun[1]) / 2.0;
        double factor = features.state().factor();
        assertThat(lensCentre).as("factor × 10 px above the pointer")
                .isBetween(160 - factor * (160 - plainCentre) - 1.5, 160 - factor * (160 - plainCentre) + 1.5);
        double plainWidth = inkWidth(plain, 240, 40, 260);
        double lensWidth = inkWidth(magnified, 240, 75, 245);
        assertThat(lensWidth / plainWidth).as("%s px against %s px", lensWidth, plainWidth).isBetween(factor * 0.87, factor * 1.13);
        assertThat(differencesOutsideTheLens(plain, magnified, new Point2D.Double(200, 160))).isZero();
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.25, 4.0})
    void theGridPointUnderThePointerStaysPutAtTheExtremeZooms(double zoom) {
        CircuitCanvas pane = paneWithAWire(zoom);
        BufferedImage plain = paint(pane);
        int[] plainRun = inkedRun(plain, 240, 40, 260);
        double plainCentre = (plainRun[0] + plainRun[1]) / 2.0;

        features.toggleAction().actionPerformed(null);
        move(pane, 200, 160);
        BufferedImage magnified = paint(pane);

        int[] lensRun = inkedRun(magnified, 240, 75, 245);
        double lensCentre = (lensRun[0] + lensRun[1]) / 2.0;
        double expected = 160 - features.state().factor() * (160 - plainCentre);
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
        features.toggleAction().actionPerformed(null);
        move(hovered, 200, 160);
        assertThat(differencesOutsideTheLens(before, paint(other), new Point2D.Double(-1000, -1000))).isZero();
    }

    /** How many times thicker the wire (row 150) is at column {@code x} with the lens at (200, 150) than without it. */
    private double thickening(int x) {
        CircuitCanvas pane = paneWithAWire(1);
        BufferedImage plain = paint(pane);
        features.toggleAction().actionPerformed(null);
        move(pane, 200, 150);
        double ratio = inkWidth(paint(pane), x, 60, 240) / inkWidth(plain, x, 60, 240);
        exit(pane);
        features.toggleAction().actionPerformed(null);
        return ratio;
    }

    @Test
    void theRoundLensDoesNotReachThePointsOfARectangularOnesWings() {
        assertThat(thickening(330)).isLessThan(1.3);
        assertThat(thickening(260)).isGreaterThan(3.3);
    }

    @Test
    void theRectangularLensIsOneAndAHalfRadiiWideAndOneHighOnEachSide() {
        preferences.setLensShape(AppPreferences.LensShape.RECTANGLE);
        assertThat(features.state().shape()).isEqualTo(AppPreferences.LensShape.RECTANGLE);
        assertThat(thickening(330)).as("inside, beyond the circle").isGreaterThan(3.3);
        assertThat(thickening(355)).as("outside, 5 px from the edge").isLessThan(1.3);
        assertThat(thickening(200 - 155)).isLessThan(1.3);
    }

    @Test
    void theRectangularOutlineHasCornersRoundedWithAnEightPixelRadius() {
        LensLook rectangle = new LensLook(AppPreferences.LensShape.RECTANGLE, 100);
        java.awt.Shape outline = rectangle.outline(new Point2D.Double(200, 150));
        assertThat(outline.contains(345, 55)).isTrue();
        assertThat(outline.contains(348, 52)).as("2 px in from both edges is cut by the corner").isFalse();
        assertThat(outline.contains(349, 150)).isTrue();
        assertThat(outline.contains(200, 51)).isTrue();
        assertThat(outline.contains(200, 48)).isFalse();
        assertThat(new LensLook(AppPreferences.LensShape.ROUND, 100).outline(new Point2D.Double(200, 150))
                .contains(348, 150)).isFalse();
    }

    @Test
    void theSizesAreSeventyOneHundredAndOneHundredFortyPixelsOfRadius() {
        preferences.setLensSize(AppPreferences.LensSize.SMALL);
        assertThat(thickening(200 + 60)).isGreaterThan(3.3);
        assertThat(thickening(200 + 85)).isLessThan(1.3);
        preferences.setLensSize(AppPreferences.LensSize.LARGE);
        assertThat(thickening(200 + 130)).isGreaterThan(3.3);
        assertThat(thickening(200 + 150)).isLessThan(1.3);
    }

    @Test
    void changingTheShapeOrSizeInPreferencesReshapesAShowingLensAndRepaintsBothAreas() {
        CircuitCanvas pane = paneWithAWire(1);
        features.toggleAction().actionPerformed(null);
        move(pane, 200, 150);
        java.awt.Rectangle round = LensPainter.area(new Point2D.Double(200, 150), features.state().look());

        preferences.setLensShape(AppPreferences.LensShape.RECTANGLE);
        preferences.setLensSize(AppPreferences.LensSize.SMALL);

        assertThat(features.state().shape()).isEqualTo(AppPreferences.LensShape.RECTANGLE);
        assertThat(features.state().radius()).isEqualTo(70);
        java.awt.Rectangle small = LensPainter.area(new Point2D.Double(200, 150), features.state().look());
        assertThat(small.contains(200 + 105, 150)).isTrue();
        assertThat(small.contains(200 + 112, 150)).isFalse();
        assertThat(round.contains(200 + 100, 150)).isTrue();
        assertThat(round.contains(200 + 110, 150)).isFalse();
    }

    @Test
    void theRectangularAreaReachesTheWingsTheLabelStaysUnderTheBottomRight() {
        LensLook rectangle = new LensLook(AppPreferences.LensShape.RECTANGLE, 100);
        Point2D centre = new Point2D.Double(200, 150);
        java.awt.Rectangle area = LensPainter.area(centre, rectangle);
        assertThat(area.contains(200 + 150, 150)).isTrue();
        assertThat(area.contains(200 - 150, 150 - 100)).isTrue();
        java.awt.Rectangle label = LensPainter.labelArea(centre, rectangle);
        assertThat(label.y).isGreaterThan(150 + 100);
        assertThat(label.x).isGreaterThan(200);
        assertThat(area.contains(label)).isTrue();
    }

    /** Pixels that differ outside the lens's circle (with its ring) and its zoom label around {@code centre}. */
    private static int differencesOutsideTheLens(BufferedImage first, BufferedImage second, Point2D centre) {
        java.awt.Rectangle label = LensPainter.labelArea(centre, new LensLook(AppPreferences.LensShape.ROUND, 100));
        double ringOutside = 100 + 3;
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
