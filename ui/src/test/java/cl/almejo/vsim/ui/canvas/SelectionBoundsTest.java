package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.command.AddWireCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.app.OpenDocument;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

class SelectionBoundsTest {

    @Test
    void theTopLeftCoversEveryKnownElement() {
        try (OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry())) {
            Component gate = place(openDocument, StandardComponentTypes.BUFFER, 5, 7, Map.of(StandardComponentTypes.NEGATE, true));
            Wire wire = new Wire(openDocument.document().newWireId(), new GridPoint(3, 12), new GridPoint(9, 12));
            openDocument.execute(new AddWireCommand(wire));

            assertThat(SelectionBounds.topLeft(openDocument, List.of(gate.id(), wire.id())))
                    .contains(new GridPoint(3, 7));
            assertThat(SelectionBounds.topLeft(openDocument, List.of(gate.id(), new ElementId("c999"))))
                    .contains(new GridPoint(5, 7));
            assertThat(SelectionBounds.topLeft(openDocument, List.of(new ElementId("c999")))).isEmpty();
        }
    }
}
