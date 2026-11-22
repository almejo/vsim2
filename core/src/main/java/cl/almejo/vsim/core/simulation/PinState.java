package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.value.Value;

/** Runtime state of one pin (port of VSim's Pin). */
final class PinState {

    final ComponentInstance instance;
    final int index;
    final PinSpec spec;
    Value driven;
    Value lastSeen;
    int pendingDrives;
    /**
     * The value the pin ends at once its drives in flight arrive: the one of the drive due last (VSim 1's
     * {@code programedValue}). Meaningful only while {@link #pendingDrives} &gt; 0.
     */
    Value headingTo;
    /** The time the drive that brings {@link #headingTo} is due. */
    long headingToTime;
    /**
     * Set when the pin was given a drive to another value than {@link #headingTo} while drives were in flight, until
     * it settles (no drive in flight): such a pin is not letting go of its net, whatever it is heading to now.
     */
    boolean changedCourse;
    /** The drive in flight that brings {@link #headingTo}; meaningful only while {@link #pendingDrives} &gt; 0. */
    ComponentInstance.DriveEvent latestDrive;
    RuntimeNet net;

    PinState(ComponentInstance instance, int index, PinSpec spec) {
        this.instance = instance;
        this.index = index;
        this.spec = spec;
        this.driven = Value.highImpedance(spec.width());
        this.lastSeen = driven;
        this.headingTo = driven;
    }
}
