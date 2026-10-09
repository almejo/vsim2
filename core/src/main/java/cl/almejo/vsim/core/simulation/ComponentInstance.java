package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.value.Value;

import java.util.Map;

/** A component inside the engine (port of VSim's Gate). It is also the behavior's context. */
final class ComponentInstance implements BehaviorContext {

    final String id;
    final ComponentType type;
    final Map<String, Object> parameters;
    final Behavior behavior;
    final PinState[] pins;
    boolean removed;
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

    boolean matches(FlatComponent component) {
        return type.id().equals(component.type().id())
                && type.resolveParameters(parameters).equals(type.resolveParameters(component.parameters()));
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
        pin.pendingDrives++;
        engine.scheduler().schedule(delay, () -> {
            if (removed) {
                return;
            }
            pin.pendingDrives--;
            pin.driven = value;
            engine.netChanged(pin.net);
        });
    }

    @Override
    public void schedule(long delay, Runnable action) {
        engine.scheduler().schedule(delay, () -> {
            if (!removed) {
                action.run();
            }
        });
    }

    @Override
    public long now() {
        return engine.now();
    }
}
