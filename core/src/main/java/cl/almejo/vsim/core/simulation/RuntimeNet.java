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
    final List<PinState> drivers = new ArrayList<>();
    /** The pins of {@link #pins} that read the net (inputs and bidirectional pins), in the same order. */
    final List<PinState> inputs = new ArrayList<>();
    private final Value.Resolution stable;
    private final Value.Resolution pending;
    /** Where the net is heading once every drive in flight arrives; see {@link #fillHeadings()}. */
    private final Value.Resolution headings;
    Value value;
    long conflictSince = -1;

    RuntimeNet(String id, int width, boolean widthMismatch) {
        this.id = id;
        this.width = width;
        this.widthMismatch = widthMismatch;
        this.value = Value.highImpedance(width);
        this.stable = new Value.Resolution(width);
        this.pending = new Value.Resolution(width);
        this.headings = new Value.Resolution(width);
    }

    void add(PinState pin) {
        pins.add(pin);
        if (pin.spec.isDriver()) {
            drivers.add(pin);
        }
        if (pin.spec.isInput()) {
            inputs.add(pin);
        }
    }

    /**
     * Recomputes the value; returns true when it changed. A pin with drives still on its way that is
     * {@linkplain #lettingGo letting go} counts only to fill the bits the other pins leave in high impedance, and
     * never adds a conflict (VSim 1's rule, so a bus handoff shows no false short); any other pin counts with the value
     * it drives now.
     */
    boolean resolve(long now) {
        if (widthMismatch) {
            value = Value.highImpedance(width);
            return false;
        }
        boolean anyInFlight = fillHeadings();
        stable.reset();
        pending.reset();
        for (int index = 0; index < drivers.size(); index++) {
            PinState pin = drivers.get(index);
            (anyInFlight && pin.pendingDrives > 0 && lettingGo(pin) ? pending : stable).add(pin.driven);
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

    /**
     * Fills {@link #headings} with where the net is heading: each pin with drives in flight at the value its last one
     * brings ({@link PinState#headingTo}), every other pin at the value it drives. Returns whether any pin has drives
     * in flight; when none has, {@link #headings} is left as it was.
     */
    private boolean fillHeadings() {
        boolean anyInFlight = false;
        for (int index = 0; index < drivers.size() && !anyInFlight; index++) {
            anyInFlight = drivers.get(index).pendingDrives > 0;
        }
        if (!anyInFlight) {
            return false;
        }
        headings.reset();
        for (int index = 0; index < drivers.size(); index++) {
            PinState pin = drivers.get(index);
            headings.add(pin.pendingDrives > 0 ? pin.headingTo : pin.driven);
        }
        return true;
    }

    /**
     * Whether {@code pin}, which has drives in flight, is really letting go of the net: the value its last drive brings
     * is high impedance, or the net is heading to no conflict on any bit that value drives (it agrees with what the
     * other pins drive or are heading to). A pin heading to a fight is no handoff: it counts as a settled driver now,
     * so a part slower than the changes of its input (a buffer of delay 2 on a clock of 1/1 against a Constante) shows
     * its short instead of hiding it forever.
     */
    private boolean lettingGo(PinState pin) {
        return pin.headingTo.isFullyHighImpedance() || !headings.conflictsOn(pin.headingTo);
    }

    Value valueFor(PinState pin) {
        return widthMismatch ? Value.highImpedance(pin.spec.width()) : value;
    }
}
