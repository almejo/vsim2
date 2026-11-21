package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.ui.paint.ColorScheme;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** The machine palette looks like the circuit's: rows with a drawn icon, hover and the tree's selection colours. */
class MachinePaletteLooksTest {

    private LookAndFeel before;

    @BeforeEach
    void rememberLookAndFeel() {
        before = UIManager.getLookAndFeel();
    }

    @AfterEach
    void restoreLookAndFeel() throws UnsupportedLookAndFeelException {
        UIManager.setLookAndFeel(before);
    }

    private static MachinePalette palette() {
        return new MachinePalette(() -> ColorScheme.LIGHT);
    }

    private static List<AbstractButton> rows(MachinePalette palette) {
        return List.of(palette.stateButton(), palette.transitionButton(), palette.noteButton(),
                palette.textButton());
    }

    private static BufferedImage paint(AbstractButton row) {
        Dimension size = row.getPreferredSize();
        row.setSize(size.width + 40, size.height);
        BufferedImage image = new BufferedImage(row.getWidth(), row.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        row.paint(graphics);
        graphics.dispose();
        return image;
    }

    private static long distinctColors(BufferedImage image) {
        return IntStream.range(0, image.getWidth() * image.getHeight())
                .map(index -> image.getRGB(index % image.getWidth(), index / image.getWidth())).distinct().count();
    }

    @Test
    void everyRowHasAnIconAtLeastAsTallAsTheCircuitPalettesRows() {
        for (AbstractButton row : rows(palette())) {
            Icon icon = row.getIcon();
            assertThat(icon).as(row.getText()).isNotNull();
            assertThat(icon.getIconHeight()).as(row.getText()).isGreaterThanOrEqualTo(24);
            assertThat(row.getPreferredSize().height).as(row.getText()).isGreaterThanOrEqualTo(24 + 4);
        }
    }

    @Test
    void theIconsDrawSomethingOnBothThemes() {
        for (Runnable theme : List.<Runnable>of(FlatLightLaf::setup, FlatDarkLaf::setup)) {
            theme.run();
            for (AbstractButton row : rows(palette())) {
                BufferedImage image = new BufferedImage(row.getIcon().getIconWidth(), row.getIcon().getIconHeight(),
                        BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = image.createGraphics();
                row.getIcon().paintIcon(row, graphics, 0, 0);
                graphics.dispose();
                assertThat(distinctColors(image)).as(row.getText()).isGreaterThan(1);
            }
        }
    }

    @Test
    void theChosenRowIsPaintedWithTheTreesSelectionBackground() {
        MachinePalette palette = palette();
        palette.stateButton().setSelected(true);

        BufferedImage image = paint(palette.stateButton());

        Color selection = UIManager.getColor("Tree.selectionBackground");
        assertThat(new Color(image.getRGB(image.getWidth() - 3, image.getHeight() / 2), true)).isEqualTo(selection);
        BufferedImage other = paint(palette.transitionButton());
        assertThat(new Color(other.getRGB(other.getWidth() - 3, other.getHeight() / 2), true)).isNotEqualTo(selection);
    }

    @Test
    void aRowKeepsItsIconAndDraws() {
        MachinePalette palette = palette();

        assertThat(palette.noteButton().getIcon()).isNotNull();
        assertThat(distinctColors(paint(palette.noteButton()))).isGreaterThan(1);
    }

    @Test
    void theToolsStayOneAtATime() {
        MachinePalette palette = palette();

        palette.stateButton().setSelected(true);
        palette.noteButton().setSelected(true);

        assertThat(palette.stateButton().isSelected()).isFalse();
        assertThat(palette.noteButton().isSelected()).isTrue();
        palette.clearTool();
        assertThat(palette.noteButton().isSelected()).isFalse();
    }
}
