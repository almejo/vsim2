package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.core.component.standard.KeyboardTypes;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.component.standard.TerminalTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.Selection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.JSpinner;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static cl.almejo.vsim.ui.UiTestSupport.addWire;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A size typed in Propiedades moves the pins as dragging the edge does, and their wires follow in the same undo step:
 * a Teclado at (10, 10) has AV at (26, 12) and D at (26, 14); a Normal Terminal at (10, 10) has CLK at (12, 62).
 */
class PropertiesPanelWiresTest {

    private final OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private final Selection selection = new Selection();
    private final PropertiesPanel panel = new PropertiesPanel(openDocument, selection);

    @AfterEach
    void close() {
        panel.dispose();
        openDocument.close();
    }

    private List<GridPoint> wireEnds() {
        return openDocument.document().wires().stream().flatMap(wire -> Stream.of(wire.start(), wire.end())).toList();
    }

    private void type(String parameter, int value) {
        ((JSpinner) panel.parameterForm().editor(parameter)).setValue(value);
    }

    @Test
    void aWiderTecladoTypedInTheInspectorCarriesTheWiresOfAvAndD() {
        Component keyboard = place(openDocument, StandardComponentTypes.KEYBOARD, 10, 10, Map.of());
        addWire(openDocument, 26, 12, 40, 12);
        addWire(openDocument, 26, 14, 44, 14);
        selection.select(keyboard.id());

        type(KeyboardTypes.PART_WIDTH, 24);

        assertThat(wireEnds()).contains(new GridPoint(34, 12), new GridPoint(34, 14))
                .doesNotContain(new GridPoint(26, 12), new GridPoint(26, 14));
        assertThat(openDocument.history().undoDescription()).contains("command.configure");
    }

    @Test
    void oneUndoRestoresTheWidthAndTheWires() {
        Component keyboard = place(openDocument, StandardComponentTypes.KEYBOARD, 10, 10, Map.of());
        addWire(openDocument, 26, 12, 40, 12);
        List<Wire> wiresBefore = openDocument.document().wires();
        selection.select(keyboard.id());

        type(KeyboardTypes.PART_WIDTH, 30);
        openDocument.history().undo();

        assertThat(openDocument.document().wires()).containsExactlyElementsOf(wiresBefore);
        assertThat(openDocument.document().component(keyboard.id()).orElseThrow().parameters())
                .containsEntry(KeyboardTypes.PART_WIDTH, KeyboardTypes.MINIMUM_PART_WIDTH);
        assertThat(openDocument.history().undoDescription()).contains("command.wire");
    }

    @Test
    void fewerTerminalRowsTypedInTheInspectorCarryTheClockWire() {
        Component terminal = place(openDocument, StandardComponentTypes.TERMINAL, 10, 10, Map.of());
        addWire(openDocument, 12, 62, 12, 70);
        selection.select(terminal.id());

        type(TerminalTypes.ROWS, 12);

        assertThat(wireEnds()).contains(new GridPoint(12, 38)).doesNotContain(new GridPoint(12, 62));
    }
}
