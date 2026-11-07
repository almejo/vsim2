package cl.almejo.vsim.ui.tree;

import cl.almejo.vsim.core.component.ComponentType;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One row of the circuit tree (spec §5.3). {@code path} identifies the row: {@link #DOCUMENT_PATH} for the document,
 * the hierarchical simulation id ({@code c5}, {@code c5/c2}) for a piece. {@code name} is the document's name or the
 * piece's ({@link cl.almejo.vsim.ui.window.InstanceNames}); {@code kind} is the type name a labeled piece shows after
 * its label («IR · Registro»), empty otherwise; {@code typeName} is what the search matches besides the name.
 * {@code paletteId} and {@code type} draw the palette's icon (the type or a preset's: a NAND shows its bubble);
 * {@code definitionId} is the user component an instance can be entered into.
 */
public record CircuitTreeNode(String path, String name, Optional<String> kind, String typeName, String paletteId,
                              Optional<ComponentType> type, Optional<String> definitionId,
                              List<CircuitTreeNode> children) {

    public static final String DOCUMENT_PATH = "/";

    public CircuitTreeNode {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(typeName, "typeName");
        Objects.requireNonNull(paletteId, "paletteId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(definitionId, "definitionId");
        children = List.copyOf(children);
    }

    /** The document's row, holding the circuit's pieces. */
    static CircuitTreeNode document(String name, List<CircuitTreeNode> pieces) {
        return new CircuitTreeNode(DOCUMENT_PATH, name, Optional.empty(), "", "", Optional.empty(), Optional.empty(),
                pieces);
    }

    public boolean isDocument() {
        return DOCUMENT_PATH.equals(path);
    }

    /** Whether the row is an instance of a user component of the circuit (it can be entered). */
    public boolean isUserComponent() {
        return definitionId.isPresent();
    }

    /** The texts the search matches: the name (the label, when there is one) and the type name. */
    List<String> matchTexts() {
        return isDocument() ? List.of() : List.of(name, typeName);
    }
}
