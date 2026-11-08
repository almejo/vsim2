package cl.almejo.vsim.ui.waveform;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.actions.TextPrompt;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.JToggleButton;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The pin of presentation mode in the waveform toolbar (the user, 2026-11-08): icon only, like its neighbours; an
 * outline pushpin tilted while unpinned, an upright filled red one while pinned; the tooltip says what a press does.
 */
class WaveformPinButtonTest {

    private static final TextPrompt NO_ANSWER = (parent, title, message, initialText) -> Optional.empty();

    private OpenDocument openDocument;
    private WaveformPanel panel;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        panel = new WaveformPanel(openDocument, new ProbeActions(openDocument), () -> ColorScheme.LIGHT, NO_ANSWER,
                probeId -> { }, () -> { });
    }

    @AfterEach
    void close() {
        panel.dispose();
        openDocument.close();
    }

    private JToggleButton pin() {
        return panel.pinButton();
    }

    /** The icon the button paints in its current state. */
    private Icon shownIcon() {
        Icon selected = pin().getSelectedIcon();
        return pin().isSelected() && selected != null ? selected : pin().getIcon();
    }

    private static boolean paintsRed(Icon icon) {
        BufferedImage image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        icon.paintIcon(null, graphics, 0, 0);
        graphics.dispose();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                Color pixel = new Color(image.getRGB(x, y), true);
                if (pixel.getAlpha() > 200 && pixel.getRed() > 170 && pixel.getGreen() < 120 && pixel.getBlue() < 120) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    void thePinShowsNoTextInEitherState() {
        assertThat(pin().getText()).isNullOrEmpty();
        panel.showPinned(true);
        assertThat(pin().getText()).isNullOrEmpty();
    }

    @Test
    void theTooltipSaysWhatAPressDoes() {
        panel.showPinned(false);
        assertThat(pin().getToolTipText()).isEqualTo("Fijar las formas de onda");
        panel.showPinned(true);
        assertThat(pin().getToolTipText()).isEqualTo("Soltar las formas de onda");
    }

    @Test
    void eachStateHasItsOwnIconAndThePinnedOnePaintsRed() {
        panel.showPinned(false);
        Icon unpinned = shownIcon();
        panel.showPinned(true);
        Icon pinned = shownIcon();
        assertThat(pinned).isNotSameAs(unpinned);
        assertThat(paintsRed(pinned)).isTrue();
        assertThat(paintsRed(unpinned)).isFalse();
    }

    @Test
    void thePinIsAToggleAsBigAsTheOtherToolbarButtons() {
        AbstractButton hide = panel.button("Ocultar");
        assertThat(pin()).isInstanceOf(JToggleButton.class);
        assertThat(shownIcon().getIconWidth()).isEqualTo(hide.getIcon().getIconWidth());
        assertThat(shownIcon().getIconHeight()).isEqualTo(hide.getIcon().getIconHeight());
        assertThat(pin().getPreferredSize()).isEqualTo(hide.getPreferredSize());
    }
}
