package cl.almejo.vsim.core.library;

import java.util.List;

/** An explicit update whose interface geometry would detach wires inside a containing component. */
public final class UnsafeDefinitionUpdateException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;
    private final String definitionId;
    private final List<String> containingNames;
    private final String messageKey;

    public UnsafeDefinitionUpdateException(String definitionId, List<String> containingNames) {
        this(definitionId, containingNames, "appearance.update.nestedGeometry");
    }

    /** A separate reason is used when a top-level explicit update lacks a safe pin-follow plan. */
    public UnsafeDefinitionUpdateException(String definitionId, List<String> containingNames, String messageKey) {
        super("nested component geometry cannot be updated: " + definitionId);
        this.definitionId = definitionId;
        this.containingNames = List.copyOf(containingNames);
        this.messageKey = messageKey;
    }

    public String messageKey() { return messageKey; }
    public String definitionId() { return definitionId; }
    public List<String> containingNames() { return containingNames; }
}
