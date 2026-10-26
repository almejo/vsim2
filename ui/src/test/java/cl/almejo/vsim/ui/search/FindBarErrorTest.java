package cl.almejo.vsim.ui.search;

import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.inline.ProblemBadge;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static cl.almejo.vsim.ui.UiTestSupport.luminance;
import static org.assertj.core.api.Assertions.assertThat;

/** The find bar's error state reads well in light and dark themes (the badge of the inline editor's problems). */
class FindBarErrorTest {

    /** WCAG AA for normal text. */
    private static final double MINIMUM_CONTRAST = 4.5;

    private final LookAndFeel previous = UIManager.getLookAndFeel();

    @BeforeEach
    void spanish() {
        Messages.initialize(Messages.SPANISH);
    }

    @AfterEach
    void restoreTheLookAndFeel() throws UnsupportedLookAndFeelException {
        UIManager.setLookAndFeel(previous);
    }

    @Test
    void noResultsIsAReadableBadgeInBothThemes() throws UnsupportedLookAndFeelException {
        for (LookAndFeel lookAndFeel : new LookAndFeel[]{new FlatLightLaf(), new FlatDarkLaf()}) {
            UIManager.setLookAndFeel(lookAndFeel);
            FindBar bar = new FindBar();

            bar.showState(Messages.text("find.noResults"), "", true);

            JLabel counter = bar.counterLabel();
            assertThat(counter.getForeground()).as(lookAndFeel.getName()).isEqualTo(ProblemBadge.foreground());
            assertThat(contrast(counter.getForeground(), ProblemBadge.background())).as(lookAndFeel.getName())
                    .isGreaterThanOrEqualTo(MINIMUM_CONTRAST);
            assertThat(paintedBehindTheText(counter)).as(lookAndFeel.getName()).isEqualTo(ProblemBadge.background());
        }
    }

    @Test
    void aReasonIsABadgeTooAndClearingTheErrorTakesItAway() throws UnsupportedLookAndFeelException {
        UIManager.setLookAndFeel(new FlatDarkLaf());
        FindBar bar = new FindBar();

        bar.showState("", Messages.text("search.regexTooExpensive"), true);
        assertThat(bar.messageLabel().getForeground()).isEqualTo(ProblemBadge.foreground());
        assertThat(paintedBehindTheText(bar.messageLabel())).isEqualTo(ProblemBadge.background());

        bar.showState("1 de 2", "", false);
        assertThat(bar.counterLabel().getForeground()).isNotEqualTo(ProblemBadge.foreground());
        assertThat(paintedBehindTheText(bar.counterLabel())).isNotEqualTo(ProblemBadge.background());
    }

    @Test
    void theFieldGetsTheErrorOutlineAndARedTintFromTheTheme() throws UnsupportedLookAndFeelException {
        for (LookAndFeel lookAndFeel : new LookAndFeel[]{new FlatLightLaf(), new FlatDarkLaf()}) {
            UIManager.setLookAndFeel(lookAndFeel);
            SearchField field = new SearchField("");
            SwingUtilities.updateComponentTreeUI(field);
            Color normal = field.getBackground();

            field.showError(true);
            Color tinted = field.getBackground();
            assertThat(field.getClientProperty("JComponent.outline")).isEqualTo("error");
            assertThat(tinted).as(lookAndFeel.getName()).isNotEqualTo(normal);
            assertThat(tinted.getRed() - tinted.getGreen()).as(lookAndFeel.getName())
                    .isGreaterThan(normal.getRed() - normal.getGreen());
            assertThat(contrast(field.getForeground(), tinted)).as("the typed text stays about as readable")
                    .isGreaterThanOrEqualTo(0.85 * contrast(field.getForeground(), normal));

            field.showError(false);
            assertThat(field.getBackground()).isEqualTo(normal);
            assertThat(field.getClientProperty("JComponent.outline")).isNull();
        }
    }

    /** The colour painted just inside the label's left edge, half way down (where the badge starts). */
    private static Color paintedBehindTheText(JLabel label) {
        label.setSize(label.getPreferredSize());
        BufferedImage image = new BufferedImage(label.getWidth(), label.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        label.paint(graphics);
        graphics.dispose();
        return new Color(image.getRGB(1, label.getHeight() / 2), true);
    }

    private static double contrast(Color first, Color second) {
        double lighter = Math.max(luminance(first), luminance(second));
        double darker = Math.min(luminance(first), luminance(second));
        return (lighter + 0.05) / (darker + 0.05);
    }
}
