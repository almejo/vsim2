package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.value.Value;

import java.util.ArrayList;
import java.util.List;

/** Runtime net: resolves the value imposed by its driver pins (port of VSim's Pin.updateValues). */
final class RuntimeNet {

    final String id;
    final int width;
    final boolean widthMismatch;
    /** Other ids under which the snapshot publishes this net's value (see FlatNet#aliases). */
    List<String> aliases = List.of();
    final List<PinState> pins = new ArrayList<>();
    Value value;
    long conflictSince = -1;

    RuntimeNet(String id, int width, boolean widthMismatch) {
        this.id = id;
        this.width = width;
        this.widthMismatch = widthMismatch;
        this.value = Value.highImpedance(width);
    }

    /** Recomputes the value; returns true when it changed. */
    boolean resolve(long now) {
        if (widthMismatch) {
            value = Value.highImpedance(width);
            return false;
        }
        List<Value> stable = new ArrayList<>();
        List<Value> transitioning = new ArrayList<>();
        for (PinState pin : pins) {
            if (pin.spec.isDriver()) {
                (pin.pendingDrives > 0 ? transitioning : stable).add(pin.driven);
            }
        }
        Value resolved = Value.resolve(width, stable);
        if (!transitioning.isEmpty() && resolved.highImpedanceMask() != 0) {
            Value pending = Value.resolve(width, transitioning);
            long fill = resolved.highImpedanceMask() & ~pending.highImpedanceMask();
            resolved = new Value(width, resolved.bits() | (pending.bits() & fill),
                    resolved.highImpedanceMask() & ~fill, resolved.conflict());
        }
        if (resolved.conflict()) {
            if (conflictSince < 0) {
                conflictSince = now;
            }
        } else {
            conflictSince = -1;
        }
        boolean changed = !resolved.equals(value);
        value = resolved;
        return changed;
    }

    Value valueFor(PinState pin) {
        return widthMismatch ? Value.highImpedance(pin.spec.width()) : value;
    }
}
