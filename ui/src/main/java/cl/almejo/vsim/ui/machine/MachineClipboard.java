package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.io.FragmentFormat;
import cl.almejo.vsim.core.io.MachineFragmentFormat;
import cl.almejo.vsim.core.machine.MachineLimits;
import cl.almejo.vsim.core.machine.MachineNote;
import cl.almejo.vsim.core.machine.MachineState;
import cl.almejo.vsim.core.machine.MachineText;
import cl.almejo.vsim.core.machine.MachineTransition;
import cl.almejo.vsim.core.machine.NoteTransfer;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.actions.ContentClipboard;
import cl.almejo.vsim.ui.clipboard.FragmentClipboard;
import cl.almejo.vsim.ui.i18n.Messages;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Cortar, Copiar, Pegar, Duplicar and Seleccionar todo on a machine's diagram (hand test of hito 2: the window's own
 * actions and keys, routed here in a machine's tab): the selected states travel with the arrows between them, and the
 * selected notes and free texts with them (or alone, hito 3), as {@link MachineFragmentFormat} text on the app's clipboard, so they
 * paste into this machine or another one; a paste or a duplicate is one {@code EditMachineCommand}
 * ({@link MachineCanvas#edit}) and leaves what it added selected. Of a circuit's copied selection only the notes and the free texts are
 * pasted here ({@link NoteTransfer}); the rest is left out with a reason on the status line, as a machine's states
 * are in a circuit.
 */
final class MachineClipboard implements ContentClipboard {

    private final MachineCanvas canvas;
    private final MachineTool tool;
    private final FragmentClipboard clipboard;
    private final Consumer<String> notices;

    MachineClipboard(MachineCanvas canvas, MachineTool tool, FragmentClipboard clipboard, Consumer<String> notices) {
        this.canvas = Objects.requireNonNull(canvas, "canvas");
        this.tool = Objects.requireNonNull(tool, "tool");
        this.clipboard = Objects.requireNonNull(clipboard, "clipboard");
        this.notices = Objects.requireNonNull(notices, "notices");
    }

    @Override
    public boolean canCopy() {
        return !canvas.selection().stateIds().isEmpty() || !canvas.selection().noteIds().isEmpty()
                || !canvas.selection().textIds().isEmpty();
    }

    @Override
    public void copy() {
        selectedPiece().ifPresent(piece -> clipboard.put(MachineFragmentFormat.write(piece), GridPoint.ORIGIN, null));
    }

    @Override
    public void cut() {
        if (selectedPiece().isPresent()) {
            copy();
            tool.delete();
        }
    }

    @Override
    public void paste() {
        Optional<String> text = clipboard.read();
        if (text.isEmpty()) {
            return;
        }
        Optional<StateMachine> piece = MachineFragmentFormat.read(text.get());
        if (piece.isPresent()) {
            add(piece.get());
        } else if (MachineFragmentFormat.isCircuitFragment(text.get())) {
            pasteCircuitNotes(text.get());
        }
    }

    /**
     * A circuit's copied selection: only its notes and free texts come in; the rest is left out, and the status line
     * says so.
     */
    private void pasteCircuitNotes(String text) {
        Optional<FragmentFormat.Notes> copied = FragmentFormat.notesOf(text)
                .filter(found -> !found.notes().isEmpty() || !found.texts().isEmpty());
        Optional<StateMachine> machine = canvas.machine();
        if (copied.isEmpty() || machine.isEmpty()) {
            notices.accept(Messages.text("machine.paste.notInAMachine"));
            return;
        }
        StateMachine piece = MachineEdits.notesPiece(machine.get(), NoteTransfer.toMachineNotes(copied.get().notes()),
                NoteTransfer.toMachineTexts(copied.get().texts()));
        if (add(piece) && copied.get().hasOtherElements()) {
            notices.accept(Messages.text("machine.paste.onlyNotes"));
        }
    }

    @Override
    public void duplicate() {
        selectedPiece().ifPresent(this::add);
    }

    @Override
    public void selectAll() {
        canvas.machine().ifPresent(machine -> canvas.selection().set(
                new LinkedHashSet<>(machine.states().stream().map(MachineState::id).toList()),
                new LinkedHashSet<>(machine.transitions().stream().map(MachineTransition::id).toList()),
                new LinkedHashSet<>(machine.notes().stream().map(MachineNote::id).toList()),
                new LinkedHashSet<>(machine.texts().stream().map(MachineText::id).toList())));
    }

    private Optional<StateMachine> selectedPiece() {
        return canvas.machine().flatMap(machine -> MachineEdits.pieceOf(machine, canvas.selection().stateIds(),
                canvas.selection().noteIds(), canvas.selection().textIds()));
    }

    /** Adds {@code piece} as one undo step and selects what it added; says why when it does not fit. */
    private boolean add(StateMachine piece) {
        Optional<StateMachine> machine = canvas.machine();
        if (machine.isEmpty()) {
            return false;
        }
        if (!MachineEdits.fits(machine.get(), piece)) {
            notices.accept(Messages.format("machine.paste.tooManyStates", MachineLimits.MAXIMUM_STATES));
            return false;
        }
        MachineEdits.Pasted pasted = MachineEdits.withPasted(machine.get(), piece);
        Optional<String> overLimit = MachineEdits.pinLimitProblem(machine.get(), pasted.machine());
        if (overLimit.isPresent()) {
            notices.accept(overLimit.get());
            return false;
        }
        if (canvas.edit(current -> MachineEdits.withPasted(current, piece).machine())) {
            canvas.selection().set(new LinkedHashSet<>(pasted.stateIds()),
                    new LinkedHashSet<>(pasted.transitionIds()), new LinkedHashSet<>(pasted.noteIds()),
                    new LinkedHashSet<>(pasted.textIds()));
            return true;
        }
        return false;
    }
}
