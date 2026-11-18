package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.machine.StateMachine;

import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Which copy of its machine a machine tab reads and edits, and which state it lights (plan 2026-11-14, hito 4 task
 * 3). Every reader of the tab (the diagram, its gestures, the inline editor, Propiedades, the pin rows, the chips and
 * the glows) goes through {@link MachineCanvas#machine()}, {@link MachineCanvas#edit} and
 * {@link MachineCanvas#activeState()}, which ask the source installed with {@link MachineCanvas#useSource}. The
 * {@linkplain MachineCanvas#standardSource() standard source} is the circuit's: its machine definition, one
 * {@code EditMachineCommand} per edit through the tab's place, and the copy's state in the latest snapshot. The test
 * mode installs one that reads and edits its draft, and lights the state of the test's chosen column.
 * <p>
 * Used on the EDT only, as the canvas is.
 */
public interface MachineSource {

    /** The machine the tab shows now; empty once there is none (its definition left: the tab closes then). */
    Optional<StateMachine> machine();

    /**
     * Makes {@code change} of {@link #machine()} as one undo step of this source; nothing when it changes nothing or
     * there is no machine. Returns whether it made the edit. A source fires its change listeners after an edit.
     */
    boolean edit(UnaryOperator<StateMachine> change);

    /**
     * The state to light in the active colour, with the arrow to light as the next one; empty to light none. The
     * canvas asks on every frame of a shown diagram (16 ms) and after each change this source fires, so it must be
     * cheap.
     */
    Optional<ActiveMachineState> activeState();

    /**
     * Runs {@code listener} after each change of what this source answers ({@link #machine()},
     * {@link #activeState()}); returns its removal. The canvas follows its place's changes anyway, so a source need
     * not forward them; the standard source's changes are its place's, and the canvas does not follow them twice.
     */
    Runnable addChangeListener(Runnable listener);
}
