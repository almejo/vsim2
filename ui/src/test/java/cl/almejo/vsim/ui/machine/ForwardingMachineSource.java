package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.machine.StateMachine;

import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * A machine source that passes everything to another one (a canvas's standard source) and counts what went through
 * it: installed in front of the circuit's, every gesture must behave as before and reach the circuit through it.
 */
final class ForwardingMachineSource implements MachineSource {

    private final MachineSource target;
    private int reads;
    private int edits;

    ForwardingMachineSource(MachineSource target) {
        this.target = target;
    }

    /** Installs one in front of {@code canvas}'s standard source; returns it. */
    static ForwardingMachineSource installOn(MachineCanvas canvas) {
        ForwardingMachineSource source = new ForwardingMachineSource(canvas.standardSource());
        canvas.useSource(source);
        return source;
    }

    int reads() {
        return reads;
    }

    int edits() {
        return edits;
    }

    @Override
    public Optional<StateMachine> machine() {
        reads++;
        return target.machine();
    }

    @Override
    public boolean edit(UnaryOperator<StateMachine> change) {
        edits++;
        return target.edit(change);
    }

    @Override
    public Optional<ActiveMachineState> activeState() {
        return target.activeState();
    }

    @Override
    public Runnable addChangeListener(Runnable listener) {
        return target.addChangeListener(listener);
    }
}
