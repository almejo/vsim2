package cl.almejo.vsim.ui.shortcuts.sheet;

import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.shortcuts.KeyBindings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The right column of the sheet (Ver, Simular, Ayuda: the short groups) keeps the width of its longest entry; the left one gives way. */
class ShortcutSheetColumnsTest {

    @BeforeEach
    void spanish() throws Exception {
        javax.swing.UIManager.setLookAndFeel(new com.formdev.flatlaf.FlatLightLaf());
        Messages.initialize(Messages.SPANISH);
    }

    @Test
    void theLongestNameOfTheRightColumnShowsWhole() {
        ShortcutSheetPanel panel = new ShortcutSheetPanel(shown -> { }, () -> { }, () -> { });
        panel.setColumns(SheetSelection.groups(SheetSelection.load(),
                new KeyBindings(AppPreferences.inMemory())));
        panel.setSize(560, 800);
        panel.doLayout();
        List<String> cut = new ArrayList<>();
        collectCut(panel, cut);
        assertThat(cut).filteredOn(text -> text.startsWith("Avanzar")).isEmpty();
    }

    private static void collectCut(Container container, List<String> cut) {
        for (Component child : container.getComponents()) {
            if (child instanceof JLabel label && label.getText() != null && !label.getText().isEmpty()
                    && label.getWidth() < label.getPreferredSize().width) {
                cut.add(label.getText());
            }
            if (child instanceof Container inner) {
                collectCut(inner, cut);
            }
        }
    }
}
