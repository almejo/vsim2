package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.machine.MachineNote;
import cl.almejo.vsim.core.machine.MachineState;
import cl.almejo.vsim.core.machine.MachineTransition;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.inline.InlineEditRequest;
import cl.almejo.vsim.ui.inline.InlineTextEditor;
import cl.almejo.vsim.ui.paint.TextFonts;

import java.awt.Font;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Edits a state's name or an arrow's label (condition and Mealy outputs) right where the diagram draws it (spec
 * 2026-11-14 §1 «Drawing»), with the editor's usual one-line field over the canvas: Enter (or ✓, or a click elsewhere)
 * puts it as one edit of the machine, Esc (or ✗) leaves everything as it was. A name must be given and free; a
 * condition is kept as written, also when it cannot be read (the diagram draws it in the problem colour and the right
 * panel says what is wrong). A note's text is edited over its paper, on several wrapped lines ({@link #editNote}).
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

    /**
     * Opens the whole label of the arrow {@code transitionId} for editing, over it: the condition and, after a
     * {@code /}, its Mealy outputs ({@code x / Z=1}). Both are one edit; a bad outputs part blocks with its problem.
     */
    void editCondition(String transitionId) {
        Optional<StateMachine> machine = canvas.machine();
        Optional<MachineTransition> transition = machine.flatMap(found -> found.transition(transitionId));
        if (transition.isEmpty()) {
            return;
        }
        Font font = MachineDiagramPainter.labelFont();
        Rectangle2D area = MachineDiagramPainter.labelBounds(transition.get(),
                MachineDiagramGeometry.of(machine.get()).arrow(transitionId), canvas.getFontMetrics(font));
        editor.open(new InlineEditRequest(area, font, MachineLabelText.of(transition.get()),
                        text -> labelProblem(transitionId, text),
                        text -> applyLabel(transitionId, text),
                        InlineEditRequest.Anchor.CENTER, List.of()),
                () -> canvas.machine().flatMap(current -> current.transition(transitionId)).isPresent());
        // «/» is the only way to write outputs (the user's decision of 2026-11-15), so the editor shows it
        editor.showHint(Messages.text("machine.label.notation"));
    }

    /**
     * Opens the text of the note {@code noteId} for editing over its paper, wrapping at the paper's width, as a circuit
     * note's: Mayús+Enter adds a line, Enter (or a click elsewhere) puts it as one edit, Esc leaves it as it was.
     */
    void editNote(String noteId) {
        Optional<MachineNote> note = canvas.machine().flatMap(found -> found.notes().stream()
                .filter(candidate -> candidate.id().equals(noteId)).findFirst());
        if (note.isEmpty()) {
            return;
        }
        editor.open(InlineEditRequest.wrapped(MachineNotePainting.textArea(note.get()), TextFonts.labelFont(),
                        note.get().text(), text -> Optional.empty(),
                        text -> canvas.edit(current -> current.withNote(noteId,
                                found -> found.withText(text.strip())))),
                () -> canvas.machine().filter(current -> current.notes().stream()
                        .anyMatch(candidate -> candidate.id().equals(noteId))).isPresent());
    }

    /** Closes an open edit without applying it (the tab leaves machine mode). */
    void dispose() {
        editor.dispose();
    }

    /** What blocks the label {@code text}: a bad outputs part, or more names than the machine has pins for. */
    private Optional<String> labelProblem(String transitionId, String text) {
        MachineLabelText.Reading reading = MachineLabelText.read(text);
        if (reading.problem().isPresent()) {
            return reading.problem().map(problem -> Messages.format(problem.key(), problem.arguments().toArray()));
        }
        return canvas.machine().flatMap(machine -> MachineEdits.pinLimitProblem(machine,
                MachineEdits.withLabel(machine, transitionId, reading.condition(), reading.outputs())));
    }

    private void applyLabel(String transitionId, String text) {
        MachineLabelText.Reading reading = MachineLabelText.read(text);
        canvas.edit(current -> MachineEdits.withLabel(current, transitionId, reading.condition(), reading.outputs()));
    }

    private Optional<String> nameProblem(String stateId, String text) {
        return canvas.machine()
                .flatMap(machine -> MachineEdits.stateNameProblem(machine, stateId, text))
                .map(key -> Messages.format(key, text.strip()));
    }
}
