package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** A click on a switch is an edit of its «Valor»; a run of clicks on one switch is one undo step (2026-10-28). */
class SetSwitchValueCommandTest {

    private CircuitDocument document;
    private History history;
    private Component toggle;
    private Component wide;

    @BeforeEach
    void create() {
        document = new CircuitDocument();
        history = new History(document);
        ComponentType switchType = StandardComponentTypes.registry().require(StandardComponentTypes.SWITCH);
        toggle = switchType.newComponent(document.newComponentId(), GridPoint.ORIGIN, Rotation.R0, Map.of(), "");
        wide = switchType.newComponent(document.newComponentId(), new GridPoint(10, 0), Rotation.R0,
                Map.of(StandardComponentTypes.WIDTH, 4, StandardComponentTypes.VALUE, "0b0101"), "");
        history.execute(new AddElementsCommand(List.of(toggle, wide)));
        history.markSaved();
    }

    private Object valueOf(ElementId id) {
        return document.component(id).orElseThrow().parameters().get(StandardComponentTypes.VALUE);
    }

    @Test
    void aClickSetsTheValueAndAnUndoPutsItBack() {
        history.execute(SetSwitchValueCommand.click(toggle.id(), "0"));

        assertThat(valueOf(toggle.id())).isEqualTo("0");
        assertThat(history.isModified()).isTrue();
        assertThat(history.undoDescription()).contains(SetSwitchValueCommand.CLICK_DESCRIPTION);
        history.undo();
        assertThat(valueOf(toggle.id())).isEqualTo("1");
        assertThat(history.isModified()).isFalse();
    }

    @Test
    void aRunOfClicksOnOneSwitchIsOneUndoStep() {
        history.execute(SetSwitchValueCommand.click(wide.id(), "0b111"));
        history.execute(SetSwitchValueCommand.click(wide.id(), "0b1111"));
        history.execute(SetSwitchValueCommand.click(wide.id(), "0b1110"));
        assertThat(valueOf(wide.id())).isEqualTo("0b1110");

        history.undo();

        assertThat(valueOf(wide.id())).isEqualTo("0b101");
        assertThat(history.undoDescription()).contains("command.add");
    }

    @Test
    void aRunThatComesBackToItsStartLeavesNoStep() {
        history.markSaved();
        history.execute(new SetLabelCommand(toggle.id(), "A"));
        history.execute(SetSwitchValueCommand.click(toggle.id(), "0"));
        history.execute(SetSwitchValueCommand.click(toggle.id(), "1"));

        assertThat(valueOf(toggle.id())).isEqualTo("1");
        assertThat(history.undoDescription()).contains("command.label");
    }

    @Test
    void aClickOnAnotherSwitchStartsANewStep() {
        history.execute(SetSwitchValueCommand.click(toggle.id(), "0"));
        history.execute(SetSwitchValueCommand.click(wide.id(), "0b100"));

        history.undo();

        assertThat(valueOf(wide.id())).isEqualTo("0b101");
        assertThat(valueOf(toggle.id())).isEqualTo("0");
    }

    @Test
    void anyOtherCommandEndsTheRun() {
        history.execute(SetSwitchValueCommand.click(toggle.id(), "0"));
        history.execute(new SetLabelCommand(wide.id(), "B"));
        history.execute(SetSwitchValueCommand.click(toggle.id(), "1"));

        history.undo();

        assertThat(valueOf(toggle.id())).isEqualTo("0");
    }

    @Test
    void valuesSetTogetherAreOneStepThatNeverMerges() {
        Map<ElementId, String> values = new LinkedHashMap<>();
        values.put(toggle.id(), "0");
        values.put(wide.id(), "0b11");
        history.execute(SetSwitchValueCommand.of(values, "command.aiSwitchValues"));
        history.execute(SetSwitchValueCommand.click(toggle.id(), "1"));

        history.undo();
        assertThat(valueOf(toggle.id())).isEqualTo("0");
        assertThat(valueOf(wide.id())).isEqualTo("0b11");
        history.undo();
        assertThat(valueOf(toggle.id())).isEqualTo("1");
        assertThat(valueOf(wide.id())).isEqualTo("0b101");
    }

    @Test
    void theHistorySaysWhichCommandItsLastChangeMoved() {
        history.execute(SetSwitchValueCommand.click(toggle.id(), "0"));
        assertThat(history.lastChange()).containsInstanceOf(SetSwitchValueCommand.class);
        history.undo();
        assertThat(history.lastChange()).containsInstanceOf(SetSwitchValueCommand.class);
        history.markSaved();
        assertThat(history.lastChange()).isEmpty();
    }
}
