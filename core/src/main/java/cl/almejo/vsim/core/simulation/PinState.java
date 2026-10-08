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
    RuntimeNet net;

    PinState(ComponentInstance instance, int index, PinSpec spec) {
        this.instance = instance;
        this.index = index;
        this.spec = spec;
        this.driven = Value.highImpedance(spec.width());
        this.lastSeen = driven;
    }
}
