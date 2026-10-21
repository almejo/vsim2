package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.ui.theme.AppThemes;
import cl.almejo.vsim.ui.theme.ReadableColors;
import org.junit.jupiter.api.Test;

import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ColorSwatchesTest {

    private static final List<String> COLORS = List.of("none", "red", "green", "blue");
    private static final Map<String, Color> FILLS = Map.of("red", Color.RED, "green", Color.GREEN,
            "blue", Color.BLUE);

    private static ColorSwatches<String> swatches(String selected) {
        return new ColorSwatches<>(COLORS, selected, name -> "Color " + name,
                name -> Optional.ofNullable(FILLS.get(name)));
    }

    @Test
    void theChosenSwatchHasAMarkedRingAndTheOthersDoNot() {
        ColorSwatches<String> swatches = swatches("green");

        int ring = ColorSwatches.selectionRingColor().getRGB();
        assertThat(ringPixel(swatches.swatch(2))).isEqualTo(ring);
        assertThat(ringPixel(swatches.swatch(1))).isNotEqualTo(ring);
    }

    @Test
    void aClickChoosesTheValueMovesTheRingAndTellsTheListenersOnce() {
        ColorSwatches<String> swatches = swatches("red");
        AtomicInteger choices = new AtomicInteger();
        swatches.addChoiceListener(choices::incrementAndGet);

        swatches.swatch(3).doClick();

        assertThat(swatches.selectedValue()).isEqualTo("blue");
        assertThat(choices).hasValue(1);
        int ring = ColorSwatches.selectionRingColor().getRGB();
        assertThat(ringPixel(swatches.swatch(3))).isEqualTo(ring);
        assertThat(ringPixel(swatches.swatch(1))).isNotEqualTo(ring);
    }

    @Test
    void clickingTheChosenSwatchAgainChangesNothing() {
        ColorSwatches<String> swatches = swatches("red");
        AtomicInteger choices = new AtomicInteger();
        swatches.addChoiceListener(choices::incrementAndGet);

        swatches.swatch(1).doClick();

        assertThat(swatches.selectedValue()).isEqualTo("red");
        assertThat(choices).hasValue(0);
    }

    @Test
    void selectingFromTheDocumentMovesTheRingWithoutTellingTheListeners() {
        ColorSwatches<String> swatches = swatches("red");
        AtomicInteger choices = new AtomicInteger();
        swatches.addChoiceListener(choices::incrementAndGet);

        swatches.select("none");

        assertThat(swatches.selectedValue()).isEqualTo("none");
        assertThat(swatches.swatch(0).isSelected()).isTrue();
        assertThat(swatches.swatch(1).isSelected()).isFalse();
        assertThat(choices).hasValue(0);
    }

    @Test
    void aValueOutsideTheListIsRefused() {
        assertThatThrownBy(() -> swatches("pink")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> swatches("red").select("pink")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void eachSwatchSaysItsNameAsTooltipAndAccessibleName() {
        ColorSwatches<String> swatches = swatches("red");

        assertThat(swatches.swatch(2).getToolTipText()).isEqualTo("Color green");
        assertThat(swatches.swatch(2).getAccessibleContext().getAccessibleName()).isEqualTo("Color green");
    }

    @Test
    void enterPicksTheFocusedSwatch() {
        ColorSwatches<String> swatches = swatches("red");

        press(swatches.swatch(2), KeyEvent.VK_ENTER);

        assertThat(swatches.selectedValue()).isEqualTo("green");
    }

    @Test
    void theArrowsAreBoundOnEverySwatch() {
        ColorSwatches<String> swatches = swatches("red");

        for (int key : List.of(KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT, KeyEvent.VK_UP, KeyEvent.VK_DOWN)) {
            assertThat(boundAction(swatches.swatch(0), key)).as(KeyEvent.getKeyText(key)).isNotNull();
        }
    }

    @Test
    void theFillIsAskedAgainAtEveryPaint() {
        AtomicReference<Color> current = new AtomicReference<>(Color.RED);
        ColorSwatches<String> swatches = new ColorSwatches<>(List.of("only"), "only", name -> name,
                name -> Optional.of(current.get()));

        assertThat(centrePixel(swatches.swatch(0))).isEqualTo(Color.RED.getRGB());
        current.set(Color.BLUE);
        assertThat(centrePixel(swatches.swatch(0))).isEqualTo(Color.BLUE.getRGB());
    }

    @Test
    void aSwatchWithoutColourLeavesItsInsideEmpty() {
        ColorSwatches<String> swatches = swatches("red");

        // the centre sits on the diagonal of «no colour»; a little off it the inside is see-through
        Icon icon = swatches.swatch(0).getIcon();
        BufferedImage image = paint(swatches.swatch(0));
        assertThat(image.getRGB(icon.getIconWidth() / 2 - 3, icon.getIconHeight() / 2 - 3) >>> 24).isZero();
    }

    @Test
    void theRingIsABlueThatStandsOutOnEveryThemeTheAppInstalls() throws Exception {
        LookAndFeel previous = UIManager.getLookAndFeel();
        try {
            for (LookAndFeel theme : AppThemes.all()) {
                UIManager.setLookAndFeel(theme);
                Color ring = ColorSwatches.selectionRingColor();
                assertThat(ring.getBlue()).as(theme.getName() + " ring " + ring)
                        .isGreaterThan(ring.getRed() + 40)
                        .isGreaterThan(ring.getGreen());
                Color panel = ReadableColors.panelBackground();
                // macOS Dark's pressed background is a translucent white: measure it as it shows over the panel.
                Color pressed = ReadableColors.over(UIManager.getColor("ToggleButton.toolbar.selectedBackground"),
                        panel);
                assertThat(ReadableColors.contrast(ring, panel)).as(theme.getName() + " panel")
                        .isGreaterThanOrEqualTo(ColorSwatches.MINIMUM_RING_CONTRAST);
                assertThat(ReadableColors.contrast(ring, pressed)).as(theme.getName() + " pressed")
                        .isGreaterThanOrEqualTo(ColorSwatches.MINIMUM_RING_CONTRAST);
            }
        } finally {
            UIManager.setLookAndFeel(previous);
        }
    }

    private static Action boundAction(JToggleButton swatch, int key) {
        Object name = swatch.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(key, 0));
        return name == null ? null : swatch.getActionMap().get(name);
    }

    private static void press(JToggleButton swatch, int key) {
        boundAction(swatch, key).actionPerformed(new ActionEvent(swatch, ActionEvent.ACTION_PERFORMED, ""));
    }

    private static BufferedImage paint(JToggleButton swatch) {
        Icon icon = swatch.getIcon();
        BufferedImage image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            icon.paintIcon(swatch, graphics, 0, 0);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    /** The colour on the middle of the ring's left side, where the swatch itself never paints. */
    private static int ringPixel(JToggleButton swatch) {
        return paint(swatch).getRGB(1, swatch.getIcon().getIconHeight() / 2);
    }

    private static int centrePixel(JToggleButton swatch) {
        Icon icon = swatch.getIcon();
        return paint(swatch).getRGB(icon.getIconWidth() / 2, icon.getIconHeight() / 2);
    }
}
