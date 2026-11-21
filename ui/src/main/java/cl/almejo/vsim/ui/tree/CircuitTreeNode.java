package cl.almejo.vsim.ui.tree;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.machine.StateColor;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One row of the circuit tree (spec §5.3). {@code path} identifies the row: {@link #DOCUMENT_PATH} for the document,
 * the hierarchical simulation id ({@code c5}, {@code c5/c2}) for a piece. {@code name} is the document's name or the
 * piece's ({@link cl.almejo.vsim.ui.window.InstanceNames}); {@code kind} is the type name a labeled piece shows after
 * its label («IR · Registro»), empty otherwise; {@code typeName} is what the search matches besides the name.
 * {@code paletteId} and {@code type} draw the palette's icon (the type or a preset's: a NAND shows its bubble);
 * {@code definitionId} is the user component an instance can be entered into. A state machine's piece holds a row per
 * state and a «Notas (n)» group: {@code machineRow} says which (such a row is no piece and can not be entered).
 */
public record CircuitTreeNode(String path, String name, Optional<String> kind, String typeName, String paletteId,
                              Optional<ComponentType> type, Optional<String> definitionId,
                              Optional<MachineRow> machineRow, List<CircuitTreeNode> children) {

    public static final String DOCUMENT_PATH = "/";

    /**
     * What a row inside a state machine's piece is: a state, the notes group or one note of it. {@code machineId} is
     * the machine's definition, {@code elementId} the state's or note's id (the group's is empty); {@code problem} is
     * the worst finding the state is in.
     */
    public record MachineRow(Kind kind, String machineId, String elementId, boolean initial,
                             Optional<StateColor> color, Severity problem) {
        public enum Kind { STATE, NOTES, NOTE }

        public enum Severity { NONE, WARNING, ERROR }

        public MachineRow {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(machineId, "machineId");
            Objects.requireNonNull(elementId, "elementId");
            Objects.requireNonNull(color, "color");
            Objects.requireNonNull(problem, "problem");
        }
    }

    public CircuitTreeNode {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(typeName, "typeName");
        Objects.requireNonNull(paletteId, "paletteId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(definitionId, "definitionId");
        Objects.requireNonNull(machineRow, "machineRow");
        children = List.copyOf(children);
    }

    /** The document's row, holding the circuit's pieces. */
    static CircuitTreeNode document(String name, List<CircuitTreeNode> pieces) {
        return new CircuitTreeNode(DOCUMENT_PATH, name, Optional.empty(), "", "", Optional.empty(), Optional.empty(),
                Optional.empty(), pieces);
    }

    public boolean isDocument() {
        return DOCUMENT_PATH.equals(path);
    }

    /** Whether the row is an instance of a user component of the circuit (it can be entered). */
    public boolean isUserComponent() {
        return definitionId.isPresent();
    }

    /** The texts the search matches: the name (the label, when there is one) and the type name; a state's name only. */
    List<String> matchTexts() {
        if (isDocument()) {
            return List.of();
        }
        return machineRow.map(row -> row.kind() == MachineRow.Kind.STATE ? List.of(name) : List.<String>of())
                .orElseGet(() -> List.of(name, typeName));
    }
}
