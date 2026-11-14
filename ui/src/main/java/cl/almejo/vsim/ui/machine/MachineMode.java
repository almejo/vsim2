package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.command.EditMachineCommand;
import cl.almejo.vsim.core.command.InsideEditRefusedException;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.window.EditorView;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The machine mode of an editor view (spec 2026-11-14 §2.4): the tab of a «Máquina de estados» is an ordinary component
 * tab of the machine's definition (tabs, window, toolbar, menus, history, «Valores de» and presentation mode work as
 * everywhere) that shows the machine's diagram ({@link MachineCanvas}) where the circuit panes go and its own palette
 * ({@link MachinePalette}) instead of the parts. The generated pins-only body never reaches the user: its panes leave
 * the view, every circuit action is disabled with its reason ({@link MachineActionPolicy}) and the place refuses every
 * circuit edit (a paste, a dropped file, the AI). The machine's own edits ({@link MachineEditing}) are each one
 * {@link EditMachineCommand} of its definition, the only edit the place lets through, so it is one undo step of the
 * circuit that belongs to this tab.
 */
public final class MachineMode {

    private final MachineCanvas canvas;
    private final MachinePalette palette;
    private final MachineEditing editing;

    private MachineMode(MachineCanvas canvas, MachinePalette palette, MachineEditing editing) {
        this.canvas = canvas;
        this.palette = palette;
        this.editing = editing;
    }

    /**
     * Puts {@code view} in machine mode when it shows the body of a machine; returns what puts it back (nothing to do
     * for any other view).
     */
    public static Runnable install(EditorView view) {
        return install(view, MachineStateSource.standard());
    }

    /** As above, the active state read through {@code stateSource} (tests). */
    static Runnable install(EditorView view, MachineStateSource stateSource) {
        Objects.requireNonNull(view, "view");
        Optional<String> machineId = machineDefinitionOf(view.openDocument());
        if (machineId.isEmpty()) {
            return () -> { };
        }
        OpenDocument place = view.openDocument();
        MachineSelection selection = new MachineSelection();
        MachineCanvas canvas = new MachineCanvas(place, machineId.get(), selection, stateSource,
                () -> view.services().appearance().scheme());
        MachinePalette palette = new MachinePalette();
        Runnable showDiagram = view.showInsteadOfCanvas(canvas, canvas);
        Runnable showPalette = view.leftColumn().showInsteadOfPalette(palette);
        Runnable enableCircuitActions = MachineActionPolicy.disableCircuitActions(view);
        Runnable acceptCircuitEdits = place.addInsideEditGuard(
                command -> refuseCircuitEdit(command, machineId.get()));
        // the hidden panes' snapshot listeners (status bar, run buttons, Problemas) follow the diagram's frames
        Runnable stopFeedingThePanes = canvas.addFrameListener(
                () -> view.panes().forEach(CircuitCanvas::refreshSnapshot));
        MachineEditing editing = MachineEditing.install(view, canvas, palette);
        view.putClientProperty(MachineMode.class, new MachineMode(canvas, palette, editing));
        return () -> {
            view.putClientProperty(MachineMode.class, null);
            for (Runnable undo : List.of(editing::uninstall, stopFeedingThePanes, acceptCircuitEdits,
                    enableCircuitActions, showPalette, showDiagram, canvas::dispose)) {
                undo.run();
            }
        };
    }

    /** The machine mode of {@code view}; empty for a circuit's tab or a user component's. */
    public static Optional<MachineMode> of(EditorView view) {
        return view.getClientProperty(MachineMode.class) instanceof MachineMode mode
                ? Optional.of(mode) : Optional.empty();
    }

    /** The diagram shown in the centre. */
    public MachineCanvas canvas() {
        return canvas;
    }

    /** The palette shown on the left. */
    public MachinePalette palette() {
        return palette;
    }

    /** The diagram's gestures. */
    MachineTool tool() {
        return editing.tool();
    }

    /** The inline editor of names and conditions. */
    MachineInlineEditor inlineEditor() {
        return editing.inlineEditor();
    }

    /** The rows the right panel's «Propiedades» shows. */
    MachineInspectorRows inspectorRows() {
        return editing.rows();
    }

    /** What is selected in the diagram (the canvas's). */
    public MachineSelection selection() {
        return canvas.selection();
    }

    /** The machine definition {@code place} shows the body of, when it is a machine's body place. */
    private static Optional<String> machineDefinitionOf(OpenDocument place) {
        Optional<OpenDocument> circuit = place.parent();
        Optional<String> definitionId = place.definitionId();
        if (circuit.isEmpty() || definitionId.isEmpty()) {
            return Optional.empty();
        }
        return circuit.get().document().definition(definitionId.get())
                .filter(ComponentDefinition::isMachine)
                .map(ComponentDefinition::id);
    }

    /**
     * A machine's body holds only its generated pins: no circuit edit is made there. The one edit that passes is the
     * machine's own ({@link EditMachineCommand} of this definition): it goes through the place so the step is the
     * machine tab's (Deshacer there undoes it there, and its name says where).
     */
    private static void refuseCircuitEdit(Command command, String machineId) {
        if (command instanceof EditMachineCommand edit && edit.definitionId().equals(machineId)) {
            return;
        }
        throw new InsideEditRefusedException("machine.refused.circuitEdit", List.of());
    }
}
