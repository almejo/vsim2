package cl.almejo.vsim.automation.edit;

import cl.almejo.vsim.automation.DocumentVersion;
import cl.almejo.vsim.automation.check.TextMeasure;
import cl.almejo.vsim.core.command.History;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.library.DefinitionBundle;

import java.util.List;

/**
 * One open circuit while the AI's edit runs on the editor thread ({@link cl.almejo.vsim.automation.EditHost#onCircuit}).
 * Only valid during that call: never keep it, never use it from another thread.
 */
public interface EditableCircuit {

    /** The live document; change it only through {@link #history()}. */
    CircuitDocument document();

    /** The circuit's own registry (it resolves the document's user components). */
    ComponentTypeRegistry registry();

    /** The circuit's undo history: the AI's edits are executed here, one step each. */
    History history();

    /** The version an edit must have been prepared against (spec §8): it moves with every change of the history. */
    DocumentVersion version();

    /**
     * The user's component library («Mis componentes»): {@code PlaceInstance} may place its components, bringing their
     * definitions into the circuit as the palette does. None by default.
     */
    default List<DefinitionBundle> library() {
        return List.of();
    }

    /**
     * Where the editor draws texts (notes, part labels), so the AI's frames wrap them whole;
     * {@link TextMeasure#APPROXIMATE} by default.
     */
    default TextMeasure textMeasure() {
        return TextMeasure.APPROXIMATE;
    }
}
