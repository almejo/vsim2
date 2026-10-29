package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.core.waveform.ChannelTrace;

import java.util.ArrayList;
import java.util.List;

/** Runtime net: resolves the value imposed by its driver pins (port of VSim's Pin.updateValues). */
final class RuntimeNet {

    final String id;
    final int width;
    final boolean widthMismatch;
    /** Other ids under which the snapshot publishes this net's value (see FlatNet#aliases). */
    List<String> aliases = List.of();
    /** Waveform channels watching this net; {@code List.of()} for an unprobed net (see WaveformRecorder#rebind). */
    List<ChannelTrace> recorders = List.of();
    final List<PinState> pins = new ArrayList<>();
    /** The pins of {@link #pins} that can drive the net (outputs and bidirectional pins), in the same order. */
    private final List<PinState> drivers = new ArrayList<>();
    private final Value.Resolution stable;
    private final Value.Resolution pending;
    Value value;
    long conflictSince = -1;

    RuntimeNet(String id, int width, boolean widthMismatch) {
        this.id = id;
        this.width = width;
        this.widthMismatch = widthMismatch;
        this.value = Value.highImpedance(width);
        this.stable = new Value.Resolution(width);
        this.pending = new Value.Resolution(width);
    }

    void add(PinState pin) {
        pins.add(pin);
        if (pin.spec.isDriver()) {
            drivers.add(pin);
        }
    }

    /**
     * Recomputes the value; returns true when it changed. The pins with a drive still on its way count only to fill
     * the bits the settled pins leave in high impedance, and never add a conflict.
     */
    boolean resolve(long now) {
        if (widthMismatch) {
            value = Value.highImpedance(width);
            return false;
        }
        stable.reset();
        pending.reset();
        for (int index = 0; index < drivers.size(); index++) {
            PinState pin = drivers.get(index);
            (pin.pendingDrives > 0 ? pending : stable).add(pin.driven);
        }
        Value resolved = stable.value();
        if (!pending.isEmpty() && resolved.highImpedanceMask() != 0) {
            resolved = resolved.filledWith(pending.value());
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
