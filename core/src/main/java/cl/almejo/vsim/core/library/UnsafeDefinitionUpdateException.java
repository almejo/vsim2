package cl.almejo.vsim.core.library;

import java.util.List;

/**
 * An update refused before anything changed: its interface geometry would detach wires inside a containing component
 * ({@code appearance.update.nestedGeometry}, the names are the containing components), or a new pin of a placed piece
 * would land on a wire or a pin ({@code appearance.update.newPinTouches}, the names are «piece · pin»).
 */
public final class UnsafeDefinitionUpdateException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;
    private final String definitionId;
    private final List<String> containingNames;
    private final String messageKey;

    public UnsafeDefinitionUpdateException(String definitionId, List<String> containingNames) {
        this(definitionId, containingNames, "appearance.update.nestedGeometry");
    }

    /** Another reason, with {@code containingNames} as the names its message lists. */
    public UnsafeDefinitionUpdateException(String definitionId, List<String> containingNames, String messageKey) {
        super("definition update refused (" + messageKey + "): " + definitionId);
        this.definitionId = definitionId;
        this.containingNames = List.copyOf(containingNames);
        this.messageKey = messageKey;
    }

    public String messageKey() {
        return messageKey;
    }

    public List<String> containingNames() {
        return containingNames;
    }
}
