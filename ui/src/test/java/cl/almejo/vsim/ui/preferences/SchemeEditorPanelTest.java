package cl.almejo.vsim.ui.preferences;

import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorRole;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import cl.almejo.vsim.ui.paint.NamedColorScheme;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SchemeEditorPanelTest {

    private final List<String> chooserTitles = new ArrayList<>();
    private final List<Color> chooserStarts = new ArrayList<>();
    private Optional<Color> chooserAnswer = Optional.empty();

    @BeforeEach
    void spanish() {
        Messages.initialize(Messages.SPANISH);
    }

    private SchemeEditorPanel panel(NamedColorScheme start) {
        return new SchemeEditorPanel(start, (parent, title, initial) -> {
            chooserTitles.add(title);
            chooserStarts.add(initial);
            return chooserAnswer;
        });
    }

    private static BufferedImage render(SchemePreview preview) {
        preview.setSize(preview.getPreferredSize());
        BufferedImage image = new BufferedImage(preview.getWidth(), preview.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        preview.paint(graphics);
        graphics.dispose();
        return image;
    }

    @Test
    void itStartsWithTheNameAndTheColorsOfTheStartingScheme() {
        SchemeEditorPanel panel = panel(new NamedColorScheme("", "Oscuro (copia)", ColorSchemes.DARK));
        assertThat(panel.name()).isEqualTo("Oscuro (copia)");
        assertThat(panel.scheme()).isEqualTo(ColorSchemes.DARK);
        assertThat(panel.preview().scheme()).isEqualTo(ColorSchemes.DARK);
        assertThat(panel.roleButtons()).hasSize(21);
        assertThat(panel.roleButton(ColorRole.BACKGROUND).getText()).isEqualTo("#1E1F22");
        assertThat(panel.roleButton(ColorRole.SHADOW).getText()).isEqualTo("#0000005A");
        assertThat(panel.roleLabel(ColorRole.WIRE_ONE).getText()).isEqualTo("Cable en 1");
    }

    @Test
    void everyRoleHasANameInBothLanguages() {
        for (Locale locale : List.of(Messages.SPANISH, Locale.ENGLISH)) {
            for (ColorRole role : ColorRole.values()) {
                assertThat(Messages.bundle(locale).containsKey(SchemeEditorPanel.ROLE_KEY_PREFIX + role.key()))
                        .as(locale + " " + role).isTrue();
            }
        }
    }

    @Test
    void aRoleButtonOpensTheChooserAndTakesItsColor() {
        SchemeEditorPanel panel = panel(new NamedColorScheme("", "Mío", ColorSchemes.LIGHT));
        chooserAnswer = Optional.of(new Color(0x123456));
        panel.roleButton(ColorRole.WIRE_ONE).doClick(0);
        assertThat(chooserTitles).containsExactly("Cable en 1");
        assertThat(chooserStarts).containsExactly(ColorSchemes.LIGHT.wireOne());
        assertThat(panel.scheme()).isEqualTo(ColorSchemes.LIGHT.with(ColorRole.WIRE_ONE, new Color(0x123456)));
        assertThat(panel.preview().scheme()).isEqualTo(panel.scheme());
        assertThat(panel.roleButton(ColorRole.WIRE_ONE).getText()).isEqualTo("#123456");
    }

    @Test
    void cancellingTheChooserChangesNothing() {
        SchemeEditorPanel panel = panel(new NamedColorScheme("", "Mío", ColorSchemes.LIGHT));
        panel.roleButton(ColorRole.TEXT).doClick(0);
        assertThat(panel.scheme()).isSameAs(ColorSchemes.LIGHT);
    }

    @Test
    void aBlankNameKeepsTheStartingName() {
        SchemeEditorPanel panel = panel(new NamedColorScheme("", "Mío", ColorSchemes.LIGHT));
        panel.nameField().setText("  Nuevo  ");
        assertThat(panel.name()).isEqualTo("Nuevo");
        panel.nameField().setText("   ");
        assertThat(panel.name()).isEqualTo("Mío");
    }

    @Test
    void changingTheBackgroundRepaintsThePreviewAtAnEmptySpot() {
        SchemeEditorPanel panel = panel(new NamedColorScheme("", "Mío", ColorSchemes.LIGHT));
        BufferedImage before = render(panel.preview());
        int emptyX = -1;
        int emptyY = -1;
        search:
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                if (before.getRGB(x, y) == ColorSchemes.LIGHT.background().getRGB()) {
                    emptyX = x;
                    emptyY = y;
                    break search;
                }
            }
        }
        assertThat(emptyX).as("an empty spot in the corner").isNotNegative();

        Color orange = new Color(0xFF8800);
        panel.setColor(ColorRole.BACKGROUND, orange);
        BufferedImage after = render(panel.preview());
        assertThat(after.getRGB(emptyX, emptyY)).isEqualTo(orange.getRGB());
    }

    @Test
    void thePreviewPaintsTheWireColorsOfTheScheme() {
        ColorScheme marked = ColorSchemes.LIGHT.with(ColorRole.WIRE_ZERO, new Color(0x010203))
                .with(ColorRole.WIRE_ONE, new Color(0x040506));
        SchemeEditorPanel panel = panel(new NamedColorScheme("", "Mío", marked));
        BufferedImage image = render(panel.preview());
        assertThat(contains(image, new Color(0x010203))).isTrue();
        assertThat(contains(image, new Color(0x040506))).isTrue();
    }

    private static boolean contains(BufferedImage image, Color color) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) == color.getRGB()) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    void theDialogContentScrollsWhenTheScreenIsSmallAndKeepsItsSizeWhenItFits() {
        SchemeEditorPanel editor = panel(new NamedColorScheme("light", "Claro", ColorSchemes.LIGHT));
        java.awt.Dimension wanted = editor.getPreferredSize();

        javax.swing.JScrollPane small = SchemeEditorDialog.scrollable(editor, new java.awt.Dimension(1366, 600));
        assertThat(small.getViewport().getView()).isSameAs(editor);
        assertThat(small.getPreferredSize().height).isLessThanOrEqualTo(510).isLessThan(wanted.height);

        javax.swing.JScrollPane large = SchemeEditorDialog.scrollable(
                panel(new NamedColorScheme("light", "Claro", ColorSchemes.LIGHT)), new java.awt.Dimension(4000, 4000));
        assertThat(large.getPreferredSize().height).isGreaterThanOrEqualTo(wanted.height);
        assertThat(large.getPreferredSize().width).isGreaterThanOrEqualTo(wanted.width);
    }
}
