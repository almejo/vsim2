package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.Selection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import java.util.List;
import java.util.Map;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/** A click on a switch changes its «Valor»: Propiedades in every window on the document shows it at once. */
class SwitchClickPropertiesTest {

    private final OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private final Selection selection = new Selection();
    private final Selection otherWindowSelection = new Selection();
    private final PropertiesPanel panel = new PropertiesPanel(openDocument, selection);
    private final PropertiesPanel otherWindowPanel = new PropertiesPanel(openDocument, otherWindowSelection);

    @AfterEach
    void close() {
        panel.dispose();
        otherWindowPanel.dispose();
        openDocument.close();
    }

    private static Object shownValue(PropertiesPanel shown) {
        return shown.parameterForm().rawValues().get(StandardComponentTypes.VALUE);
    }

    private static List<String> readingTexts(PropertiesPanel shown) {
        return List.of(shown.parameterForm().valueReading().getComponents()).stream()
                .map(component -> ((JLabel) component).getText()).toList();
    }

    @Test
    void bothWindowsShowTheValueABitClickLeft() {
        Component bits = place(openDocument, StandardComponentTypes.SWITCH, 0, 0,
                Map.of(StandardComponentTypes.WIDTH, 4, StandardComponentTypes.VALUE, "0b0101"));
        selection.select(bits.id());
        otherWindowSelection.select(bits.id());

        openDocument.interactBit(bits.id(), 1);

        assertThat(shownValue(panel)).isEqualTo("0b111");
        assertThat(shownValue(otherWindowPanel)).isEqualTo("0b111");
        assertThat(readingTexts(panel)).containsExactly("binario", "0111", "hexa", "0x7", "decimal", "7");
    }

    @Test
    void aOneBitSwitchShowsTheValueItsClickLeft() {
        Component toggle = place(openDocument, StandardComponentTypes.SWITCH, 0, 0, Map.of());
        selection.select(toggle.id());

        openDocument.interact(toggle.id());

        assertThat(shownValue(panel)).isEqualTo("0");
        openDocument.history().undo();
        assertThat(shownValue(panel)).isEqualTo("1");
    }
}
