package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.BreakpointTypes;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.simulation.PauseNotice;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** What the status bar says when a «Punto de parada» pauses the simulation. */
class OpenDocumentBreakpointTest {

    private OpenDocument document;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        document = OpenDocument.createEmpty(StandardComponentTypes.registry());
    }

    @AfterEach
    void close() {
        document.close();
    }

    private Component labelled(String label) {
        ComponentType breakpoint = document.registry().require(BreakpointTypes.BREAKPOINT);
        Component component = breakpoint.newComponent(document.document().newComponentId(), new GridPoint(0, 0),
                Rotation.R0, Map.of(), label);
        document.execute(new AddElementsCommand(List.of(component)));
        return component;
    }

    @Test
    void theNoticeSaysTheBreakpointsLabelAndTheValueItRead() {
        Component stop = labelled("nueve");

        PauseNotice equals = document.withPartName(new PauseNotice(stop.id().value(),
                BreakpointTypes.STOPPED_EQUALS_MESSAGE, List.of("1001"), 90, 1));
        PauseNotice differs = document.withPartName(new PauseNotice(stop.id().value(),
                BreakpointTypes.STOPPED_DIFFERS_MESSAGE, List.of("0011", "0000"), 90, 2));

        assertThat(Messages.format(equals.messageKey(), equals.arguments().toArray()))
                .isEqualTo("Punto de parada «nueve»: entrada = 1001");
        assertThat(Messages.format(differs.messageKey(), differs.arguments().toArray()))
                .isEqualTo("Punto de parada «nueve»: entrada = 0011, distinto de 0000");
    }

    @Test
    void anUnlabelledBreakpointGoesByItsTypeName() {
        Component stop = labelled("");

        PauseNotice equals = document.withPartName(new PauseNotice(stop.id().value(),
                BreakpointTypes.STOPPED_EQUALS_MESSAGE, List.of("0x9"), 90, 1));

        assertThat(equals.arguments()).containsExactly("Punto de parada", "0x9");
    }
}
