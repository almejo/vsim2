package cl.almejo.vsim.ui.appearance;

import cl.almejo.vsim.core.component.appearance.SymbolTemplates;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPopupMenu;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class ShapeTemplatesPopupTest {
    private static final GridSize BOX = new GridSize(6, 8);

    @Test
    void theNineTemplatesShowAsAThreeByThreeGridInTheBundleOrder() {
        JPopupMenu popup = ShapeTemplatesPopup.create(BOX, () -> ColorSchemes.LIGHT, template -> { });
        Container grid = (Container) popup.getComponent(0);
        assertThat(grid.getLayout()).isInstanceOf(GridLayout.class);
        assertThat(((GridLayout) grid.getLayout()).getColumns()).isEqualTo(3);
        assertThat(cells(popup)).extracting(cell -> cell.getAccessibleContext().getAccessibleName()).containsExactly(
                Messages.text("shape.template.default_box"), Messages.text("shape.template.rectangle"),
                Messages.text("shape.template.square"), Messages.text("shape.template.triangle"),
                Messages.text("shape.template.alu"), Messages.text("shape.template.mux"),
                Messages.text("shape.template.and"), Messages.text("shape.template.or"),
                Messages.text("shape.template.xor"));
    }

    @Test
    void choosingAThumbnailReportsItsTemplateOnce() {
        List<SymbolTemplates.Template> chosen = new ArrayList<>();
        JPopupMenu popup = ShapeTemplatesPopup.create(BOX, () -> ColorSchemes.LIGHT, chosen::add);
        cells(popup).get(4).doClick();
        assertThat(chosen).containsExactly(SymbolTemplates.Template.ALU);
    }

    @Test
    void everyThumbnailIsDrawnInBothSchemesFromTheSchemeOfTheMoment() {
        ColorScheme[] current = {ColorSchemes.LIGHT};
        Supplier<ColorScheme> scheme = () -> current[0];
        JPopupMenu popup = ShapeTemplatesPopup.create(BOX, scheme, template -> { });
        for (AbstractButton cell : cells(popup)) {
            current[0] = ColorSchemes.LIGHT;
            int light = strokeInk(cell.getIcon(), ColorSchemes.LIGHT);
            current[0] = ColorSchemes.DARK;
            int dark = strokeInk(cell.getIcon(), ColorSchemes.DARK);
            assertThat(light).as(cell.getAccessibleContext().getAccessibleName() + " light").isGreaterThan(20);
            assertThat(dark).as(cell.getAccessibleContext().getAccessibleName() + " dark").isGreaterThan(20);
        }
    }

    @Test
    void thumbnailsCarryNoNameText() {
        JPopupMenu popup = ShapeTemplatesPopup.create(BOX, () -> ColorSchemes.LIGHT, template -> { });
        Icon rectangle = cells(popup).get(1).getIcon();
        BufferedImage image = paint(rectangle);
        int textInk = 0;
        for (int y = rectangle.getIconHeight() / 2 - 8; y < rectangle.getIconHeight() / 2 + 8; y++) {
            for (int x = rectangle.getIconWidth() / 2 - 8; x < rectangle.getIconWidth() / 2 + 8; x++) {
                if (image.getRGB(x, y) == ColorSchemes.LIGHT.text().getRGB()) { textInk++; }
            }
        }
        assertThat(textInk).isZero();
    }

    private static int strokeInk(Icon icon, ColorScheme scheme) {
        BufferedImage image = paint(icon);
        int ink = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) == scheme.componentStroke().getRGB()) { ink++; }
            }
        }
        return ink;
    }

    private static BufferedImage paint(Icon icon) {
        BufferedImage image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        icon.paintIcon(new JLabel(), graphics, 0, 0);
        graphics.dispose();
        return image;
    }

    private static List<AbstractButton> cells(JPopupMenu popup) {
        List<AbstractButton> cells = new ArrayList<>();
        for (Component cell : ((Container) popup.getComponent(0)).getComponents()) {
            cells.add((AbstractButton) cell);
        }
        return cells;
    }
}
