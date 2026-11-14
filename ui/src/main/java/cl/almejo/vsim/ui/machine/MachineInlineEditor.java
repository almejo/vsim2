package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.machine.MachineState;
import cl.almejo.vsim.core.machine.MachineTransition;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.inline.InlineEditRequest;
import cl.almejo.vsim.ui.inline.InlineTextEditor;

import java.awt.Font;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Edits a state's name or an arrow's condition right where the diagram draws it (spec 2026-11-14 §1 «Drawing»), with
 * the editor's usual one-line field over the canvas: Enter (or ✓, or a click elsewhere) puts it as one edit of the
 * machine, Esc (or ✗) leaves everything as it was. A name must be given and free; a condition is kept as written, also
 * when it cannot be read (the diagram draws it in the problem colour and the right panel says what is wrong).
 */
final class MachineInlineEditor {

    private final MachineCanvas canvas;
    private final InlineTextEditor editor;

    MachineInlineEditor(MachineCanvas canvas) {
        this.canvas = Objects.requireNonNull(canvas, "canvas");
        this.editor = InlineTextEditor.forHost(canvas.inlineHost());
    }

    /** The editor that floats over the diagram (tests type into it). */
    InlineTextEditor editor() {
        return editor;
    }

    /** Opens the name of the state {@code stateId} for editing, over the name in its circle. */
    void editStateName(String stateId) {
        Optional<StateMachine> machine = canvas.machine();
        Optional<MachineState> state = machine.flatMap(found -> found.state(stateId));
        if (state.isEmpty()) {
            return;
        }
        Font font = MachineDiagramPainter.nameFont();
        Point2D centre = MachineDiagramGeometry.of(machine.get()).centre(stateId);
        Rectangle2D area = MachineDiagramPainter.textBounds(state.get().name(), centre, canvas.getFontMetrics(font));
        editor.open(new InlineEditRequest(area, font, state.get().name(),
                        text -> nameProblem(stateId, text),
                        text -> canvas.edit(current -> MachineEdits.withStateName(current, stateId, text)),
                        InlineEditRequest.Anchor.CENTER, List.of()),
                () -> canvas.machine().flatMap(current -> current.state(stateId)).isPresent());
    }

    /** Opens the condition of the arrow {@code transitionId} for editing, over its label. */
    void editCondition(String transitionId) {
        Optional<StateMachine> machine = canvas.machine();
        Optional<MachineTransition> transition = machine.flatMap(found -> found.transition(transitionId));
        if (transition.isEmpty()) {
            return;
        }
        Font font = MachineDiagramPainter.labelFont();
        Rectangle2D area = MachineDiagramPainter.labelBounds(transition.get(),
                MachineDiagramGeometry.of(machine.get()).arrow(transitionId), canvas.getFontMetrics(font));
        editor.open(new InlineEditRequest(area, font, transition.get().condition(), text -> Optional.empty(),
                        text -> canvas.edit(current -> MachineEdits.withCondition(current, transitionId, text)),
                        InlineEditRequest.Anchor.CENTER, List.of()),
                () -> canvas.machine().flatMap(current -> current.transition(transitionId)).isPresent());
    }

    /** Closes an open edit without applying it (the tab leaves machine mode). */
    void dispose() {
        editor.dispose();
    }

    private Optional<String> nameProblem(String stateId, String text) {
        return canvas.machine()
                .flatMap(machine -> MachineEdits.stateNameProblem(machine, stateId, text))
                .map(key -> Messages.format(key, text.strip()));
    }
}
