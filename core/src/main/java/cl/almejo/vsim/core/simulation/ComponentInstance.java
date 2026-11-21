package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.value.Value;

import java.util.List;
import java.util.Map;

/** A component inside the engine (port of VSim's Gate). It is also the behavior's context. */
final class ComponentInstance implements BehaviorContext {

    final String id;
    final ComponentType type;
    /** The parameters it was built with, or the last ones whose live value it was handed ({@link #takeLiveValue}). */
    private Map<String, Object> parameters;
    final Behavior behavior;
    final PinState[] pins;
    boolean removed;
    /** Set when the update that created this instance failed; such an instance is never reused. */
    boolean initializationFailed;
    private final SimulationEngine engine;

    ComponentInstance(SimulationEngine engine, FlatComponent component) {
        this.engine = engine;
        this.id = component.id();
        this.type = component.type();
        this.parameters = component.parameters();
        this.behavior = type.createBehavior(parameters);
        this.pins = new PinState[type.pinCount()];
        for (int index = 0; index < pins.length; index++) {
            pins[index] = new PinState(this, index, type.pin(index));
        }
    }

    /**
     * Whether this instance can go on simulating {@code component}: same type and shape, and the same parameters but
     * for display-only ones ({@link ComponentType#behaviorParameters}), which the behaviour never reads.
     */
    boolean matches(FlatComponent component) {
        return !initializationFailed
                && type.equals(component.type())
                && type.behaviorParameters(parameters).equals(type.behaviorParameters(component.parameters()));
    }

    /**
     * Whether {@code successor}, built for this component after a parameter change, may take over this instance's
     * state ({@link Behavior#adoptStateFrom}): the same type of part (its shape may differ) that started fine.
     */
    boolean canHandOverTo(ComponentInstance successor) {
        return !initializationFailed && type.id().equals(successor.type.id());
    }

    /**
     * Whether {@code component}, which this instance {@link #matches}, has another
     * {@linkplain cl.almejo.vsim.core.component.ParameterSpec#liveValue() live value} (a Switch's «Valor»).
     */
    boolean hasOtherLiveValue(FlatComponent component) {
        return hasOtherLiveValue(component.parameters());
    }

    /** {@link #hasOtherLiveValue(FlatComponent)} for the raw parameters {@code raw} of the same component. */
    boolean hasOtherLiveValue(Map<String, Object> raw) {
        return !type.liveValueParameters(parameters).equals(type.liveValueParameters(raw));
    }

    /**
     * Whether {@code raw} differs from the parameters this instance runs with in live values at most, so it can take
     * them with {@link #takeLiveValue(Map)}.
     */
    boolean differsOnlyInLiveValues(Map<String, Object> raw) {
        return !initializationFailed && type.behaviorParameters(parameters).equals(type.behaviorParameters(raw));
    }

    /**
     * Takes {@code component}'s parameters and hands their live value to the running behaviour, which drives it now
     * unless it drives it already ({@link Behavior#takeLiveValue}): nothing fires twice.
     */
    void takeLiveValue(FlatComponent component) {
        takeLiveValue(component.parameters());
    }

    /** {@link #takeLiveValue(FlatComponent)} for the raw parameters {@code raw}. */
    void takeLiveValue(Map<String, Object> raw) {
        parameters = Map.copyOf(raw);
        behavior.takeLiveValue(this, type.resolveParameters(parameters));
    }

    @Override
    public Value input(int pinIndex) {
        PinState pin = pins[pinIndex];
        return pin.net == null ? Value.highImpedance(pin.spec.width()) : pin.net.valueFor(pin);
    }

    @Override
    public void drive(int pinIndex, Value value, long delay) {
        PinState pin = pins[pinIndex];
        if (!pin.spec.isDriver()) {
            throw new IllegalArgumentException(type.id() + " pin " + pinIndex + " is an input");
        }
        if (value.width() != pin.spec.width()) {
            throw new IllegalArgumentException(type.id() + " pin " + pinIndex + " has width " + pin.spec.width()
                    + " but got " + value.width());
        }
        if (pin.pendingDrives == 0 && value.equals(pin.driven)) {
            // The pin would end exactly as it is now, so the net cannot change: no event. With a drive still pending
            // the event is kept, so the values the pin goes through stay in order.
            return;
        }
        if (pin.pendingDrives > 0 && delay == pin.headingToTime - engine.now()) {
            takeOverLatestDrive(pin, value);
            return;
        }
        DriveEvent event = new DriveEvent(this, pin, value);
        long due = engine.scheduler().schedule(delay, event);
        if (pin.pendingDrives > 0) {
            pin.changedCourse |= !value.equals(pin.headingTo);
        }
        if (pin.pendingDrives == 0 || due > pin.headingToTime) {
            pin.headingTo = value;
            pin.headingToTime = due;
            pin.latestDrive = event;
        }
        pin.pendingDrives++;
    }

    /**
     * A drive due together with the pin's last one would arrive right after it, at the same time: it takes that
     * drive's place instead, so the pin never goes through a value for no time at all, and a net whose value feeds
     * back into the pin cannot pile up drives due together (that grew the queue until the heap ran out, 2026-11-20).
     * Replacing the only drive in flight is no change of course; with others in flight before it, a new value is.
     */
    private static void takeOverLatestDrive(PinState pin, Value value) {
        if (pin.pendingDrives > 1) {
            pin.changedCourse |= !value.equals(pin.headingTo);
        }
        pin.latestDrive.value = value;
        pin.headingTo = value;
    }

    @Override
    public void schedule(long delay, Runnable action) {
        engine.scheduler().schedule(delay, new BehaviorEvent(this, action));
    }

    @Override
    public long now() {
        return engine.now();
    }

    @Override
    public void requestPause(String messageKey, List<String> arguments) {
        engine.requestPause(id, messageKey, arguments);
    }

    /** The event of a {@link #drive}: the pin takes the value and its net is resolved. It carries its data itself. */
    static final class DriveEvent extends Scheduler.Event {

        private final PinState pin;
        /** The value it brings; a later drive due at the same time takes it over ({@link #drive}). */
        private Value value;

        DriveEvent(ComponentInstance instance, PinState pin, Value value) {
            super(instance);
            this.pin = pin;
            this.value = value;
        }

        @Override
        void run() {
            if (owner.removed) {
                return;
            }
            pin.pendingDrives--;
            if (pin.pendingDrives == 0) {
                pin.changedCourse = false;
            }
            pin.driven = value;
            owner.engine.netChanged(pin.net);
        }
    }

    /** The event of a behavior's {@link #schedule}: it does nothing once the component is removed. */
    private static final class BehaviorEvent extends Scheduler.Event {

        private final Runnable action;

        BehaviorEvent(ComponentInstance instance, Runnable action) {
            super(instance);
            this.action = action;
        }

        @Override
        void run() {
            if (!owner.removed) {
                action.run();
            }
        }
    }
}
