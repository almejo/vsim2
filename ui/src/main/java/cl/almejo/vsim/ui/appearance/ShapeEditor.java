package cl.almejo.vsim.ui.appearance;

import java.awt.Component;
import java.util.Optional;

/** Injectable modal editor; a cancelled session never reaches the circuit's History. */
@FunctionalInterface
public interface ShapeEditor {
    Optional<ShapeEditResult> edit(Component owner, ShapeEditRequest request);
}
