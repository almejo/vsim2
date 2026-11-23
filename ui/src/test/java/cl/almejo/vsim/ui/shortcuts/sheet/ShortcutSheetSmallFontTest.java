package cl.almejo.vsim.ui.shortcuts.sheet;

import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.shortcuts.KeyBindings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.UIManager;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** At a small interface size (75 %) the sheet's headings, rows and keys never go under 9 px; at 100 % nothing changes. */
class ShortcutSheetSmallFontTest {

    private Font labelFont;

    @BeforeEach
    void install() throws Exception {
        UIManager.setLookAndFeel(new com.formdev.flatlaf.FlatLightLaf());
        Messages.initialize(Messages.SPANISH);
        labelFont = UIManager.getFont("Label.font");
    }

    @AfterEach
    void restore() {
        UIManager.put("Label.font", labelFont);
    }

    private List<Float> fontSizes(float labelSize) {
        UIManager.put("Label.font", labelFont.deriveFont(labelSize));
        ShortcutSheetPanel panel = new ShortcutSheetPanel(shown -> { }, () -> { }, () -> { });
        panel.setColumns(SheetSelection.groups(SheetSelection.load(), new KeyBindings(AppPreferences.inMemory())));
        panel.setSize(900, 800);
        panel.doLayout();
        List<Float> sizes = new ArrayList<>();
        collect(panel, sizes);
        return sizes;
    }

    private static void collect(Container container, List<Float> sizes) {
        for (Component child : container.getComponents()) {
            if (child instanceof JComponent component && component.getFont() != null) {
                sizes.add(component.getFont().getSize2D());
            }
            if (child instanceof Container nested) {
                collect(nested, sizes);
            }
        }
    }

    @Test
    void headingsRowsAndKeysStayAtNinePixelsOrMoreWhenTheInterfaceIs75Percent() {
        assertThat(fontSizes(10f)).isNotEmpty().allSatisfy(size -> assertThat(size).isGreaterThanOrEqualTo(9f));
    }

    @Test
    void at100PercentTheDerivedSizesAreUnchanged() {
        List<Float> sizes = fontSizes(13f);
        assertThat(sizes).contains(13f * 0.8f, 13f * 0.9f);
    }
}
