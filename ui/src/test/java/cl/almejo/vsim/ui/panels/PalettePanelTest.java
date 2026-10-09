package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PalettePanelTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final List<String> chosen = new ArrayList<>();
    private PalettePanel palette;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        palette = new PalettePanel(registry, PainterRegistry.standard(), ColorScheme.LIGHT,
                type -> chosen.add(type.id()));
    }

    @Test
    void listsEveryStandardTypeGroupedByCategory() {
        assertThat(palette.visibleTypeIds()).containsExactlyElementsOf(
                registry.all().stream()
                        .sorted(java.util.Comparator.comparing(ComponentType::category))
                        .map(ComponentType::id).toList());
    }

    @Test
    void filterMatchesIdsAndLocalizedNames() {
        palette.setFilter("and");
        assertThat(palette.visibleTypeIds()).containsExactly("and2", "and3", "and4", "nand2", "nand3", "nand4");
        palette.setFilter("RELOJ");
        assertThat(palette.visibleTypeIds()).containsExactly(StandardComponentTypes.CLOCK);
        palette.setFilter("zzz");
        assertThat(palette.visibleTypeIds()).isEmpty();
        palette.setFilter("  ");
        assertThat(palette.visibleTypeIds()).hasSize(registry.all().size());
    }

    @Test
    void choosingATypeCallsBackAndCanBeRepeatedAfterClearing() {
        palette.choose(StandardComponentTypes.LED);
        palette.clearSelection();
        palette.choose(StandardComponentTypes.LED);
        assertThat(chosen).containsExactly(StandardComponentTypes.LED, StandardComponentTypes.LED);
    }

    @Test
    void filterHelperIgnoresCase() {
        ComponentType clock = registry.require(StandardComponentTypes.CLOCK);
        assertThat(PaletteFilter.matches(clock, "")).isTrue();
        assertThat(PaletteFilter.matches(clock, "CLO")).isTrue();
        assertThat(PaletteFilter.matches(clock, "reL")).isTrue();
        assertThat(PaletteFilter.matches(clock, "led")).isFalse();
    }

    @Test
    void searchFieldConsumesUndoAndRedoKeystrokes() {
        int mask = java.awt.GraphicsEnvironment.isHeadless() ? java.awt.event.InputEvent.CTRL_DOWN_MASK
                : java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        javax.swing.JTextField field = palette.searchField();
        for (int modifiers : new int[]{mask, mask | java.awt.event.InputEvent.SHIFT_DOWN_MASK}) {
            Object actionKey = field.getInputMap(javax.swing.JComponent.WHEN_FOCUSED)
                    .get(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z, modifiers));
            assertThat(actionKey).isNotNull();
            assertThat(field.getActionMap().get(actionKey)).isNotNull();
        }
    }

    @Test
    void searchFieldKeepsItsOwnClipboardKeys() {
        int mask = java.awt.GraphicsEnvironment.isHeadless() ? java.awt.event.InputEvent.CTRL_DOWN_MASK
                : java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        javax.swing.JTextField field = palette.searchField();
        for (int keyCode : new int[]{java.awt.event.KeyEvent.VK_X, java.awt.event.KeyEvent.VK_C,
                java.awt.event.KeyEvent.VK_V, java.awt.event.KeyEvent.VK_A}) {
            Object actionKey = field.getInputMap(javax.swing.JComponent.WHEN_FOCUSED)
                    .get(javax.swing.KeyStroke.getKeyStroke(keyCode, mask));
            assertThat(actionKey).as("binding for key code %d", keyCode).isNotNull();
            assertThat(field.getActionMap().get(actionKey)).isNotNull();
            assertThat(field.getActionMap().get(actionKey).isEnabled()).isTrue();
        }
    }
}
