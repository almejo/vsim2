package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState.TerminalState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.component.standard.TerminalBehavior;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.simulation.PauseNotice;
import cl.almejo.vsim.core.simulation.TerminalRequest;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.awaitSimulation;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static cl.almejo.vsim.ui.UiTestSupport.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

/** A Terminal through the document: its window frames, «Limpiar» and the name its pause notice says. */
class OpenDocumentTerminalTest {

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
        ComponentType terminal = document.registry().require(StandardComponentTypes.TERMINAL);
        Component component = terminal.newComponent(document.document().newComponentId(), new GridPoint(0, 0),
                Rotation.R0, Map.of(), label);
        document.execute(new AddElementsCommand(List.of(component)));
        return component;
    }

    private static PauseNotice stopped(String componentId) {
        return new PauseNotice(componentId, TerminalBehavior.STOPPED_MESSAGE, List.of("=120"), 120, 1);
    }

    @Test
    void aPartsPauseNoticeStartsWithThePartsName() {
        Component consola = labelled("consola");
        Component unnamed = place(document, StandardComponentTypes.TERMINAL, 0, 100, Map.of());

        PauseNotice named = document.withPartName(stopped(consola.id().value()));
        assertThat(named.arguments()).containsExactly("consola", "=120");
        assertThat(Messages.format(named.messageKey(), named.arguments().toArray()))
                .isEqualTo("«consola» recibió «=120»");
        assertThat(document.withPartName(stopped(unnamed.id().value())).arguments())
                .containsExactly("Terminal", "=120");
        assertThat(document.withPartName(stopped(consola.id().value() + "/c9")).arguments())
                .as("a part inside a user component goes by the component placed on the circuit")
                .containsExactly("consola", "=120");
        assertThat(document.withPartName(stopped("gone")).arguments()).containsExactly("gone", "=120");
    }

    @Test
    void theWindowFramesComeAndLimpiarIsNotAnEdit() {
        Component terminal = place(document, StandardComponentTypes.TERMINAL, 0, 0, Map.of());
        awaitSimulation(document);
        boolean modified = document.isModified();
        Object undo = document.history().undoDescription();

        document.setTerminalRequest(Optional.of(new TerminalRequest(terminal.id().value(), 10, 0)));
        waitUntil(() -> document.latestTerminalFrame().isPresent());
        document.acknowledgeTerminalBytes(document.latestTerminalFrame().orElseThrow().owner(), 0);
        document.clearTerminal(terminal.id());
        awaitSimulation(document);

        assertThat(document.latestSnapshot().componentState(terminal.id().value())).isInstanceOf(TerminalState.class);
        assertThat(document.isModified()).isEqualTo(modified);
        assertThat(document.history().undoDescription()).isEqualTo(undo);
        document.setTerminalRequest(Optional.empty());
        awaitSimulation(document);
        assertThat(document.latestTerminalFrame()).isEmpty();
    }
}
