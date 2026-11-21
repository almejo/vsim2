package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.simulation.PauseNotice;
import cl.almejo.vsim.core.simulation.SimulationEngine;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** What the status bar says when an edge step is refused because a clock's net cannot show its edges (SIM-4). */
class OpenDocumentClockEdgeNoticeTest {

    private OpenDocument document;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        document = OpenDocument.createEmpty(StandardComponentTypes.registry());
    }

    @AfterEach
    void close() {
        document.close();
        Messages.initialize(Messages.SPANISH);
    }

    private String said(String messageKey) {
        ComponentType clock = document.registry().require(StandardComponentTypes.CLOCK);
        Component component = clock.newComponent(document.document().newComponentId(), new GridPoint(0, 0),
                Rotation.R0, Map.of(), "reloj");
        document.execute(new AddElementsCommand(List.of(component)));
        PauseNotice notice = document.withPartName(new PauseNotice(component.id().value(), messageKey, List.of(), 0,
                1));
        return Messages.format(notice.messageKey(), notice.arguments().toArray());
    }

    @Test
    void theNoticeNamesTheClockAndWhyItsNetCannotShowAnEdge() {
        assertThat(said(SimulationEngine.CLOCK_EDGE_SHORTED_MESSAGE))
                .isEqualTo("El reloj «reloj» no puede dar un flanco: su red está en cortocircuito");
        assertThat(said(SimulationEngine.CLOCK_EDGE_OTHER_WIDTH_MESSAGE))
                .isEqualTo("El reloj «reloj» no puede dar un flanco: su red tiene otro ancho");
    }

    @Test
    void theEnglishNoticeSaysTheSame() {
        Messages.initialize(Locale.ENGLISH);

        assertThat(said(SimulationEngine.CLOCK_EDGE_SHORTED_MESSAGE))
                .isEqualTo("Clock “reloj” cannot make an edge: its net has a short circuit");
        assertThat(said(SimulationEngine.CLOCK_EDGE_OTHER_WIDTH_MESSAGE))
                .isEqualTo("Clock “reloj” cannot make an edge: its net has another width");
    }
}
