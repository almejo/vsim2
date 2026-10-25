package cl.almejo.vsim.ui.appearance;

import cl.almejo.vsim.core.component.appearance.ComponentAppearance;
import cl.almejo.vsim.core.library.ComponentDefinition;

/**
 * Immutable starting state: library watcher changes cannot replace a draft while the modal editor is open.
 * {@code savesToLibrary} says that changing the circuit's copy also saves it in the library (the library holds the same
 * version), and {@code circuitUsesOtherVersion} that a library edit leaves this circuit alone; both only feed the
 * scope sentence at the top of the editor («Se guarda también en la librería.»).
 */
public record ShapeEditRequest(ComponentDefinition definition, ComponentAppearance appearance,
                               ShapeEditScope initialScope, boolean switchable, boolean definitionPinsLocked,
                               String lockReason, int affectedInstances, String instanceName,
                               boolean savesToLibrary, boolean circuitUsesOtherVersion) {

    /** A request whose definition edits stay in the circuit. */
    public ShapeEditRequest(ComponentDefinition definition, ComponentAppearance appearance,
                            ShapeEditScope initialScope, boolean switchable, boolean definitionPinsLocked,
                            String lockReason, int affectedInstances, String instanceName) {
        this(definition, appearance, initialScope, switchable, definitionPinsLocked, lockReason, affectedInstances,
                instanceName, false, false);
    }
}
