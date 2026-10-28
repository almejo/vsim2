package cl.almejo.vsim.ui.memory;

import cl.almejo.vsim.core.component.standard.MemoryTypes;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.ui.app.OpenDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.DefaultCellEditor;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

class MemoryEditorPolishTest {

    private final OpenDocument document = OpenDocument.createEmpty(StandardComponentTypes.registry());

    @AfterEach
    void close() {
        document.close();
    }

    private MemoryEditorDialog dialog(String type, Map<String, Object> parameters) {
        Component component = place(document, type, 0, 0, parameters);
        return new MemoryEditorDialog(document, component.id(), null, NoLinkActions.INSTANCE, null);
    }

    private static void collect(Container root, List<AbstractButton> found) {
        for (java.awt.Component child : root.getComponents()) {
            if (child instanceof AbstractButton button) {
                found.add(button);
            }
            if (child instanceof Container container) {
                collect(container, found);
            }
        }
    }

    private static void layOut(Container root) {
        root.doLayout();
        for (java.awt.Component child : root.getComponents()) {
            if (child instanceof Container container) {
                layOut(container);
            }
        }
    }

    private static Rectangle boundsIn(java.awt.Component component, java.awt.Component ancestor) {
        return SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), ancestor);
    }

    private static boolean visibleWithin(java.awt.Component component, Container root) {
        for (java.awt.Component current = component; current != root; current = current.getParent()) {
            if (!current.isVisible()) {
                return false;
            }
        }
        return true;
    }

    private static java.awt.Component find(Container root, String name) {
        for (java.awt.Component child : root.getComponents()) {
            if (name.equals(child.getName())) {
                return child;
            }
            if (child instanceof Container container) {
                java.awt.Component nested = find(container, name);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private void assertLaysOutAtTheDefaultSize(MemoryEditorDialog dialog) {
        Dimension size = dialog.defaultSize();
        dialog.content().setSize(size);
        layOut(dialog.content());
        List<AbstractButton> buttons = new ArrayList<>();
        collect(dialog.content(), buttons);
        for (AbstractButton button : buttons) {
            if (button.getName() == null || !visibleWithin(button, dialog.content())) {
                continue;
            }
            Rectangle bounds = boundsIn(button, dialog.content());
            assertThat(bounds.x).as(button.getName()).isGreaterThanOrEqualTo(0);
            assertThat(bounds.x + bounds.width).as(button.getName() + " right edge").isLessThanOrEqualTo(size.width);
            assertThat(bounds.width).as(button.getName() + " width")
                    .isGreaterThanOrEqualTo(button.getPreferredSize().width);
            assertThat(bounds.height).as(button.getName() + " height")
                    .isGreaterThanOrEqualTo(button.getPreferredSize().height);
        }
        java.awt.Component radix = find(dialog.content(), "memory.radix");
        Rectangle radixBounds = boundsIn(radix, dialog.content());
        assertThat(radixBounds.x + radixBounds.width).isLessThanOrEqualTo(size.width);
        assertThat(radixBounds.width).isGreaterThanOrEqualTo(radix.getPreferredSize().width);
        if (dialog.linkBar().isVisible()) {
            assertThat(dialog.linkBar().getHeight()).isGreaterThanOrEqualTo(dialog.linkBar().getPreferredSize().height);
            Rectangle bar = boundsIn(dialog.linkBar(), dialog.content());
            Rectangle grid = boundsIn(dialog.table().getParent().getParent(), dialog.content());
            assertThat(bar.y + bar.height).isLessThanOrEqualTo(grid.y);
        }
    }

    @Test
    void aLinkedRomFitsItsToolbarAndLinkBarAtTheDefaultSize() {
        assertLaysOutAtTheDefaultSize(dialog(StandardComponentTypes.ROM, Map.of(MemoryTypes.WORDS, "1024",
                MemoryTypes.WIDTH, 16, MemoryTypes.LINK,
                "/home/someone/projects/circuits/very/deep/folder/structure/para-enlazar-sprites.hex")));
    }

    @Test
    void aLinkedRamWithDumpAndRestoreFitsAtTheDefaultSize() {
        assertLaysOutAtTheDefaultSize(dialog(StandardComponentTypes.RAM, Map.of(MemoryTypes.WORDS, "32",
                MemoryTypes.LINK, "/home/someone/projects/circuits/very/deep/folder/ram.hex")));
    }

    @Test
    void anUnlinkedRamFitsAtTheDefaultSize() {
        assertLaysOutAtTheDefaultSize(dialog(StandardComponentTypes.RAM, Map.of(MemoryTypes.WORDS, "32")));
    }

    @Test
    void theLinkBarAndTheStatusShowTheFileNameWithTheFullPathAsTooltip() {
        MemoryEditorDialog dialog = dialog(StandardComponentTypes.ROM, Map.of(MemoryTypes.WORDS, "16",
                MemoryTypes.LINK, "/home/someone/deep/folder/para-enlazar.hex"));
        ElidedLabel text = (ElidedLabel) find(dialog.linkBar(), "memory.linkBar.text");
        assertThat(text.getText()).contains("para-enlazar.hex").doesNotContain("/home/someone");
        assertThat(text.getToolTipText()).isEqualTo("/home/someone/deep/folder/para-enlazar.hex");
        assertThat(dialog.status().getText()).contains("para-enlazar.hex").doesNotContain("/home/someone");
    }

    @Test
    void middleElisionKeepsBothEndsAndFitsTheWidth() {
        JLabel label = new JLabel();
        FontMetrics metrics = label.getFontMetrics(label.getFont());
        String name = "a-very-long-file-name-for-a-memory-image.hex";
        String elided = ElidedLabel.elideMiddle(name, metrics, metrics.stringWidth(name) / 2);
        assertThat(elided).contains("…").startsWith("a-ver").endsWith(".hex");
        assertThat(metrics.stringWidth(elided)).isLessThanOrEqualTo(metrics.stringWidth(name) / 2);
        assertThat(ElidedLabel.elideMiddle(name, metrics, metrics.stringWidth(name))).isEqualTo(name);
    }

    @Test
    void theCellEditorIgnoresInvalidKeysUpperCasesAndLimitsTheDigits() throws BadLocationException {
        MemoryEditorDialog dialog = dialog(StandardComponentTypes.RAM, Map.of(MemoryTypes.WORDS, "16"));
        JTable table = dialog.table();
        assertThat(table.editCellAt(0, 1)).isTrue();
        JTextField field = (JTextField) ((DefaultCellEditor) table.getCellEditor()).getComponent();
        field.setText("");
        AbstractDocument typing = (AbstractDocument) field.getDocument();
        for (String key : List.of("z", "q", "a", "b", "c")) {
            typing.replace(field.getSelectionStart(), field.getSelectionEnd() - field.getSelectionStart(), key, null);
            field.setCaretPosition(field.getText().length());
        }
        assertThat(field.getText()).isEqualTo("AB");
    }

    @Test
    void theAddressColumnIsMutedAndTheWordsAreStrongInLightDarkAndReadOnlyGrids() {
        for (boolean linked : new boolean[]{false, true}) {
            MemoryEditorDialog dialog = dialog(StandardComponentTypes.ROM, linked
                    ? Map.of(MemoryTypes.WORDS, "16", MemoryTypes.LINK, "x.hex") : Map.of(MemoryTypes.WORDS, "16"));
            JTable table = dialog.table();
            for (Color[] colors : new Color[][]{{Color.BLACK, Color.WHITE}, {new Color(0xE0E0E0), new Color(0x2B2B2B)}}) {
                table.setForeground(colors[0]);
                table.setBackground(colors[1]);
                Color address = table.prepareRenderer(table.getCellRenderer(0, 0), 0, 0).getForeground();
                Color word = table.prepareRenderer(table.getCellRenderer(0, 1), 0, 1).getForeground();
                assertThat(word).isEqualTo(colors[0]);
                assertThat(address).isNotEqualTo(word);
                assertThat(distance(address, colors[1])).isLessThan(distance(word, colors[1]));
                assertThat(distance(address, colors[1])).as("still readable").isGreaterThan(60);
            }
        }
    }

    private static double distance(Color first, Color second) {
        return Math.abs(first.getRed() - second.getRed()) * 0.3 + Math.abs(first.getGreen() - second.getGreen()) * 0.59
                + Math.abs(first.getBlue() - second.getBlue()) * 0.11;
    }

    @Test
    void anEditedCellIsPaintedBold() {
        MemoryEditorDialog dialog = dialog(StandardComponentTypes.RAM, Map.of(MemoryTypes.WORDS, "16"));
        dialog.model().setCell(2, "7");
        JTable table = dialog.table();
        assertThat(table.prepareRenderer(table.getCellRenderer(0, 3), 0, 3).getFont().isBold()).isTrue();
        assertThat(table.prepareRenderer(table.getCellRenderer(0, 1), 0, 1).getFont().isBold()).isFalse();
    }

    @Test
    void theFindBarFindsWrapsAndCountsAndCloses() {
        MemoryEditorDialog dialog = dialog(StandardComponentTypes.ROM, Map.of(MemoryTypes.WORDS, "32",
                MemoryTypes.CONTENTS, "@2 AB @9 AB @1F AB"));
        assertThat(dialog.findBar().isVisible()).isFalse();
        dialog.openFind();
        assertThat(dialog.findBar().isVisible()).isTrue();
        dialog.findField().setText("ab");
        assertThat(dialog.selectedAddress()).isEqualTo(2);
        assertThat(dialog.findCounter().getText()).isEqualTo("1 de 3");
        dialog.findNext();
        assertThat(dialog.selectedAddress()).isEqualTo(9);
        dialog.findNext();
        dialog.findNext();
        assertThat(dialog.selectedAddress()).isEqualTo(2);
        assertThat(dialog.findCounter().getText()).isEqualTo("1 de 3");
        dialog.findPrevious();
        assertThat(dialog.selectedAddress()).isEqualTo(0x1F);
        assertThat(dialog.findCounter().getText()).isEqualTo("3 de 3");
        dialog.findField().setText("77");
        assertThat(dialog.findCounter().getText()).isEqualTo("Sin resultados");
        dialog.closeFind();
        assertThat(dialog.findBar().isVisible()).isFalse();
    }

    @Test
    void theFindToolbarButtonAndTheKeysAreThere() {
        MemoryEditorDialog dialog = dialog(StandardComponentTypes.ROM, Map.of(MemoryTypes.WORDS, "32"));
        AbstractButton button = (AbstractButton) find(dialog.content(), "memory.find");
        assertThat(button.getIcon()).isNotNull();
        button.doClick();
        assertThat(dialog.findBar().isVisible()).isTrue();
        assertThat(dialog.content().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .get(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK))).isNotNull();
        assertThat(dialog.findField().getInputMap(JComponent.WHEN_FOCUSED)
                .get(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0))).isNotNull();
        assertThat(dialog.findField().getInputMap(JComponent.WHEN_FOCUSED)
                .get(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK))).isNotNull();
        assertThat(dialog.content().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .get(KeyStroke.getKeyStroke(KeyEvent.VK_F3, 0))).isNotNull();
    }
}
