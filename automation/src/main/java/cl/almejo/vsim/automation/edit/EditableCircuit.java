package cl.almejo.vsim.automation.edit;

import cl.almejo.vsim.automation.DocumentVersion;
import cl.almejo.vsim.automation.check.TextMeasure;
import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.command.History;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.netlist.ProbeNaming;

import java.util.List;
import java.util.Optional;

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

    /**
     * Executes {@code command} as one undo step of the circuit's history. A place inside a component (spec §12) writes
     * the edit back to its definition as the user's edits there do, so the AI's edits go through this, never straight
     * to {@link #history()}.
     */
    default void execute(Command command) {
        history().execute(command);
    }

    /**
     * Refuses, changing nothing, a {@code command} that {@link #execute} would refuse before it changed anything (an
     * edit of a state machine's body, which only its own tab draws), with the same {@link
     * cl.almejo.vsim.automation.AutomationException}: a validate-only call answers what the real one would. Accepts
     * everything by default.
     */
    default void requireAccepts(Command command) {
    }

    /**
     * The circuit itself when this is a place inside a component (the document that keeps each piece's own memory
     * contents, spec §6.6b); empty otherwise, and for editors that have no pieces.
     */
    default Optional<CircuitDocument> circuit() {
        return Optional.empty();
    }

    /**
     * Executes {@code command} on the circuit itself, as one undo step of its history (what the memory editor does for
     * a piece's own contents); the same as {@link #execute} outside a place inside a component.
     */
    default void executeOnCircuit(Command command) {
        execute(command);
    }

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

    /**
     * The name a waveform channel with no name of its own shows (the editor's, in the user's language): when the AI
     * deletes what it watches and it moves, it keeps this name. {@link ProbeNaming#NONE} by default.
     */
    default ProbeNaming probeNaming() {
        return ProbeNaming.NONE;
    }
}
