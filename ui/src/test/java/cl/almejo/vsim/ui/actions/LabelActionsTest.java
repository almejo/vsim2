package cl.almejo.vsim.ui.actions;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.AddWireCommand;
import cl.almejo.vsim.core.command.SetLabelCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.command.CreateFrameCommand;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.Selection;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

class LabelActionsTest {

    private final OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private final Selection selection = new Selection();
    private final FakePrompt prompt = new FakePrompt();
    private LabelActions actions;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        actions = new LabelActions(openDocument, selection, null, prompt);
    }

    @AfterEach
    void close() {
        openDocument.close();
    }

    private Component gate() {
        return place(openDocument, StandardComponentTypes.NOT, 0, 0, Map.of());
    }

    private Component labelOf(Component component) {
        return openDocument.document().component(component.id()).orElseThrow();
    }

    private TextLabel note(String text) {
        TextLabel label = new TextLabel(openDocument.document().newLabelId(), new GridPoint(10, 10), text);
        openDocument.execute(new AddElementsCommand(List.of(label)));
        return label;
    }

    private Wire wire(int startX, int startY, int endX, int endY) {
        Wire wire = new Wire(openDocument.document().newWireId(), new GridPoint(startX, startY),
                new GridPoint(endX, endY));
        openDocument.execute(new AddWireCommand(wire));
        return wire;
    }

    @Test
    void editLabelSetsTheComponentLabelAsOneUndoStep() {
        Component gate = gate();
        prompt.answer(" U3 ");
        actions.editLabel(gate);
        assertThat(labelOf(gate).label()).isEqualTo("U3");
        assertThat(prompt.initialTexts).containsExactly("");
        assertThat(openDocument.history().undoDescription()).contains("command.label");
        openDocument.history().undo();
        assertThat(labelOf(gate).label()).isEmpty();
    }

    @Test
    void blankAnswerRemovesAComponentLabel() {
        Component gate = gate();
        openDocument.execute(new SetLabelCommand(gate.id(), "U1"));
        prompt.answer("   ");
        actions.editLabel(gate);
        assertThat(prompt.initialTexts).containsExactly("U1");
        assertThat(labelOf(gate).label()).isEmpty();
    }

    @Test
    void cancelOrSameTextAddsNoStep() {
        Component gate = gate();
        prompt.cancel().answer("");
        actions.editLabel(gate);
        actions.editLabel(gate);
        assertThat(openDocument.history().undoDescription()).contains("command.add");
    }

    @Test
    void editLabelOfAWireAsksNothing() {
        actions.editLabel(wire(0, 0, 5, 0));
        assertThat(prompt.initialTexts).isEmpty();
    }

    @Test
    void blankAnswerKeepsTheFreeText() {
        TextLabel label = note("nota");
        prompt.answer("  ").answer("otra");
        actions.editLabel(label);
        assertThat(openDocument.document().label(label.id()).orElseThrow().text()).isEqualTo("nota");
        actions.editLabel(label);
        assertThat(openDocument.document().label(label.id()).orElseThrow().text()).isEqualTo("otra");
    }

    @Test
    void addTextAtCreatesASelectedFreeText() {
        prompt.answer("hola");
        actions.addTextAt(new GridPoint(3, 4));
        assertThat(openDocument.document().labels()).singleElement().satisfies(label -> {
            assertThat(label.text()).isEqualTo("hola");
            assertThat(label.position()).isEqualTo(new GridPoint(3, 4));
            assertThat(selection.ids()).containsExactly(label.id());
        });
        openDocument.history().undo();
        assertThat(openDocument.document().labels()).isEmpty();
    }

    @Test
    void blankTextAddsNoLabel() {
        prompt.answer("  ").cancel();
        actions.addTextAt(GridPoint.ORIGIN);
        actions.addTextAt(GridPoint.ORIGIN);
        assertThat(openDocument.document().labels()).isEmpty();
        assertThat(openDocument.history().canUndo()).isFalse();
    }

    @Test
    void removeWireSegmentCutsTheWireAsOneStep() {
        Wire main = wire(0, 0, 10, 0);
        wire(5, 0, 5, 4);
        actions.removeWireSegment(main, new GridPoint(8, 0));
        assertThat(openDocument.document().wire(main.id()).orElseThrow().end()).isEqualTo(new GridPoint(5, 0));
        assertThat(openDocument.history().undoDescription()).contains("command.removeSegment");
    }

    @Test
    void editLabelActionIsEnabledForOneComponentOrFreeText() {
        Component gate = gate();
        TextLabel label = note("nota");
        Wire wire = wire(20, 20, 25, 20);
        assertThat(actions.editLabel().isEnabled()).isFalse();
        selection.select(gate.id());
        assertThat(actions.editLabel().isEnabled()).isTrue();
        selection.select(label.id());
        assertThat(actions.editLabel().isEnabled()).isTrue();
        selection.select(wire.id());
        assertThat(actions.editLabel().isEnabled()).isFalse();
        selection.set(List.of(gate.id(), label.id()));
        assertThat(actions.editLabel().isEnabled()).isFalse();
    }

    @Test
    void editLabelOnAFrameChangesItsTitleAsOneUndoStepAndAnEmptyTitleIsAllowed() {
        Frame frame = new Frame(openDocument.document().newFrameId(), new GridPoint(0, 0), 20, 10, "Bloque",
                FrameTint.NONE, false);
        openDocument.execute(new CreateFrameCommand(frame));
        prompt.answer(" Sumador ");
        actions.editLabel(frame);
        assertThat(openDocument.document().frame(frame.id()).orElseThrow().title()).isEqualTo("Sumador");
        assertThat(prompt.initialTexts).containsExactly("Bloque");
        assertThat(openDocument.history().undoDescription()).contains("command.renameFrame");

        prompt.answer("");
        actions.editLabel(openDocument.document().frame(frame.id()).orElseThrow());
        assertThat(openDocument.document().frame(frame.id()).orElseThrow().title()).isEmpty();

        openDocument.history().undo();
        assertThat(openDocument.document().frame(frame.id()).orElseThrow().title()).isEqualTo("Sumador");
        openDocument.history().undo();
        assertThat(openDocument.document().frame(frame.id()).orElseThrow().title()).isEqualTo("Bloque");
    }

    @Test
    void f2IsEnabledWithASingleSelectedFrame() {
        Frame frame = new Frame(openDocument.document().newFrameId(), new GridPoint(0, 0), 20, 10, "Bloque",
                FrameTint.NONE, false);
        openDocument.execute(new CreateFrameCommand(frame));
        selection.select(frame.id());
        assertThat(actions.editLabel().isEnabled()).isTrue();
        prompt.answer("Lógica");
        actions.editLabel().actionPerformed(null);
        assertThat(openDocument.document().frame(frame.id()).orElseThrow().title()).isEqualTo("Lógica");
    }

    @Test
    void f2EditsTheTitleOfASingleSelectedLockedFrame() {
        Frame frame = new Frame(openDocument.document().newFrameId(), new GridPoint(0, 0), 20, 10, "Bloque",
                FrameTint.NONE, true);
        openDocument.execute(new CreateFrameCommand(frame));
        selection.select(frame.id());
        assertThat(actions.editLabel().isEnabled()).isTrue();
        prompt.answer("Banderas");
        actions.editLabel().actionPerformed(null);
        assertThat(openDocument.document().frame(frame.id()).orElseThrow().title()).isEqualTo("Banderas");
    }
}
