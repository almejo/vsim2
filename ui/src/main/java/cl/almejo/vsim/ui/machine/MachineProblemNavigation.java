package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.machine.MachineCopies;
import cl.almejo.vsim.core.simulation.MachineProblem;
import cl.almejo.vsim.core.simulation.Problem;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.shortcuts.KeyBindings;
import cl.almejo.vsim.ui.window.EditorView;

import javax.swing.Action;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Where a state machine's problem is shown (plan 2026-11-14, hito 2 task 8): a click on its row in Problemas, from any
 * tab, opens or brings forward the machine's tab at the copy it is about (the first copy for a problem of the model,
 * «Sin valores» when no piece places the machine) and reveals there what is wrong. In a machine's own tab, F8 and
 * Mayús+F8 (Editar ▸ «Siguiente problema» and «Problema anterior», and their keys on the diagram) walk only that
 * machine's problems, each revealed on the diagram.
 */
public final class MachineProblemNavigation {

    private static final String NEXT_ID = "view.nextProblem";
    private static final String PREVIOUS_ID = "view.previousProblem";

    private MachineProblemNavigation() {
    }

    /**
     * Shows the machine problems of {@code view}'s Problemas on their machine and, when {@code view} is a machine's
     * tab, makes F8 walk that machine's problems on its diagram; returns what undoes it.
     */
    static Runnable install(EditorView view) {
        Objects.requireNonNull(view, "view");
        List<Runnable> undo = new ArrayList<>();
        undo.add(view.problems().addProblemShower(problem ->
                problem instanceof MachineProblem machine && show(view, machine)));
        MachineMode.of(view).ifPresent(mode -> {
            undo.add(view.problems().walkOnly(problem -> isShownBy(view, mode, problem)));
            undo.add(bindKeys(mode.canvas(), view));
        });
        return () -> {
            for (int index = undo.size() - 1; index >= 0; index--) {
                undo.get(index).run();
            }
        };
    }

    /**
     * Shows {@code problem}: in {@code from} when it is that machine's tab and the copy entered through is the one the
     * problem is about (any copy for a problem of the model), else in the machine's tab the window opens or brings
     * forward. False outside a real window, when there is no tab to show it in: the caller shows what it can.
     */
    public static boolean show(EditorView from, MachineProblem problem) {
        Optional<MachineMode> here = MachineMode.of(from).filter(mode -> isShownBy(from, mode, problem));
        if (here.isPresent()) {
            here.get().reveal(problem);
            return true;
        }
        OpenDocument circuit = from.openDocument().parent().orElse(from.openDocument());
        Optional<String> copy = problem.instancePath()
                .or(() -> MachineCopies.firstPathOf(circuit.document(), problem.definitionId()));
        Optional<EditorView> shown = from.windowCommands().showPlace(circuit, Optional.of(problem.definitionId()),
                copy);
        shown.flatMap(MachineMode::of).ifPresent(mode -> mode.reveal(problem));
        return shown.isPresent();
    }

    /** Whether {@code problem} belongs to the machine {@code mode} shows, at the copy {@code view} entered through. */
    private static boolean isShownBy(EditorView view, MachineMode mode, Problem problem) {
        return problem instanceof MachineProblem machine
                && machine.definitionId().equals(mode.canvas().definitionId())
                && (machine.instancePath().isEmpty()
                || machine.instancePath().equals(view.openDocument().contextPath()));
    }

    /**
     * F8 and Mayús+F8 on the diagram (the circuit's panes, which have them, are hidden in a machine's tab), following
     * the keys the user gives «Siguiente problema» and «Problema anterior» in Atajos de teclado.
     */
    private static Runnable bindKeys(MachineCanvas canvas, EditorView view) {
        List<Runnable> undo = new ArrayList<>();
        undo.add(bindKey(canvas, NEXT_ID, view.problems().nextProblem(), view.services().preferences()));
        undo.add(bindKey(canvas, PREVIOUS_ID, view.problems().previousProblem(), view.services().preferences()));
        return () -> undo.forEach(Runnable::run);
    }

    private static Runnable bindKey(MachineCanvas canvas, String shortcutId, Action action,
                                    AppPreferences preferences) {
        KeyBindings bindings = new KeyBindings(preferences);
        InputMap keys = canvas.getInputMap(JComponent.WHEN_FOCUSED);
        String name = "machine-" + shortcutId;
        canvas.getActionMap().put(name, action);
        KeyStroke[] bound = new KeyStroke[1];
        Runnable followTheBinding = () -> {
            if (bound[0] != null) {
                keys.remove(bound[0]);
                bound[0] = null;
            }
            bindings.strokeFor(shortcutId).ifPresent(stroke -> {
                keys.put(stroke, name);
                bound[0] = stroke;
            });
        };
        followTheBinding.run();
        preferences.addListener(followTheBinding);
        return () -> {
            preferences.removeListener(followTheBinding);
            if (bound[0] != null) {
                keys.remove(bound[0]);
            }
            canvas.getActionMap().remove(name);
        };
    }
}
