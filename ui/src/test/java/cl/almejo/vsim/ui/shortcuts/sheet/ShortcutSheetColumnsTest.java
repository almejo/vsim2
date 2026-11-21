package cl.almejo.vsim.ui.shortcuts.sheet;

import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.shortcuts.KeyBindings;
import org.junit.jupiter.api.AfterEach;
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

    private javax.swing.LookAndFeel previous;

    @BeforeEach
    void spanish() throws Exception {
        previous = javax.swing.UIManager.getLookAndFeel();
        javax.swing.UIManager.setLookAndFeel(new com.formdev.flatlaf.FlatLightLaf());
        Messages.initialize(Messages.SPANISH);
    }

    /** The look and feel goes back: the next test class of this JVM must not depend on this one running first. */
    @AfterEach
    void restoreTheLookAndFeel() throws Exception {
        javax.swing.UIManager.setLookAndFeel(previous);
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

    /** In the narrowest area that leaves the veil's least margin: no name is clipped by its column or the viewport. */
    @Test
    void noNameIsClippedAtTheNarrowestAreaThatFitsThem() {
        ShortcutSheetPanel panel = new ShortcutSheetPanel(shown -> { }, () -> { }, () -> { });
        panel.setColumns(SheetSelection.groups(SheetSelection.load(),
                new KeyBindings(AppPreferences.inMemory())));
        panel.setSize(panel.leastCardWidth() + 2 * 8, 820); // the veil keeps its least margin; the area is as narrow as can show every name
        panel.doLayout();
        List<String> clipped = new ArrayList<>();
        collectClipped(panel, panel.groupsScroll().getViewport(), clipped);
        assertThat(clipped).isEmpty();
    }

    @Test
    void theCardGrowsToTheLongestEntriesWhenTheWindowAllows() {
        ShortcutSheetPanel panel = new ShortcutSheetPanel(shown -> { }, () -> { }, () -> { });
        panel.setColumns(SheetSelection.groups(SheetSelection.load(),
                new KeyBindings(AppPreferences.inMemory())));
        panel.setSize(1600, 820);
        panel.doLayout();
        List<String> clipped = new ArrayList<>();
        collectClipped(panel, panel.groupsScroll().getViewport(), clipped);
        assertThat(clipped).isEmpty();
    }

    private static void collectClipped(Container container, java.awt.Component viewport, List<String> clipped) {
        for (Component child : container.getComponents()) {
            if (child instanceof JLabel label && label.getText() != null && !label.getText().isEmpty()
                    && javax.swing.SwingUtilities.isDescendingFrom(label, ((Container) viewport))) {
                java.awt.Rectangle visible = new java.awt.Rectangle(0, 0, label.getWidth(), label.getHeight());
                java.awt.Rectangle inViewport = javax.swing.SwingUtilities.convertRectangle(label, visible, viewport);
                int right = inViewport.x + label.getPreferredSize().width;
                if (right > viewport.getWidth() || label.getWidth() < label.getPreferredSize().width) {
                    clipped.add(label.getText());
                }
                Container parent = label.getParent();
                while (parent != viewport) {
                    java.awt.Rectangle inParent = javax.swing.SwingUtilities.convertRectangle(label, visible, parent);
                    if (inParent.x + label.getPreferredSize().width > parent.getWidth()) {
                        clipped.add(label.getText());
                        break;
                    }
                    parent = parent.getParent();
                }
            }
            if (child instanceof Container inner) {
                collectClipped(inner, viewport, clipped);
            }
        }
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
