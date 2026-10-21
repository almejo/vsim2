package cl.almejo.vsim.automation.edit;

import cl.almejo.vsim.automation.DocumentVersion;
import cl.almejo.vsim.automation.PinReference;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * What an edit call did (spec §10): whether it was applied (false for «validate only»), the circuit's version after
 * it, the real ids of the aliases, what each connection joined, the components it created, which elements it added,
 * changed and removed (wires merge, so a wire id may give way to another, A§5.1), warnings, and the i18n key of its
 * undo step.
 */
public record EditReceipt(boolean applied, DocumentVersion version, Map<String, String> aliases,
                          List<ConnectionReport> connections, List<CreatedComponent> components,
                          ElementChanges changes, List<String> warnings, String undoDescription) {

    public EditReceipt {
        Objects.requireNonNull(version, "version");
        aliases = Map.copyOf(aliases);
        connections = List.copyOf(connections);
        components = List.copyOf(components);
        Objects.requireNonNull(changes, "changes");
        warnings = List.copyOf(warnings);
        Objects.requireNonNull(undoDescription, "undoDescription");
    }

    /**
     * What operation {@code operation} (its index in the call) joined: {@code from} and {@code to}, the wires its
     * route added, and the nets it joined as their pins (two nets, or one when the pins were connected already).
     */
    public record ConnectionReport(int operation, PinReference from, PinReference to, List<String> wireIds,
                                   List<List<PinReference>> joinedNets, boolean alreadyConnected) {
        public ConnectionReport {
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
            wireIds = List.copyOf(wireIds);
            joinedNets = joinedNets.stream().map(List::copyOf).toList();
        }
    }

    /**
     * A user component an operation created: its alias, its id and name in the circuit, its pins in order, and the
     * instance that replaced the selection when it was asked to.
     */
    public record CreatedComponent(int operation, Optional<String> alias, String definitionId, String name,
                                   List<String> inputs, List<String> outputs, Optional<String> instanceId) {
        public CreatedComponent {
            Objects.requireNonNull(alias, "alias");
            Objects.requireNonNull(definitionId, "definitionId");
            Objects.requireNonNull(name, "name");
            inputs = List.copyOf(inputs);
            outputs = List.copyOf(outputs);
            Objects.requireNonNull(instanceId, "instanceId");
        }
    }

    /** The element ids the call added, changed and removed. */
    public record ElementChanges(List<String> added, List<String> changed, List<String> removed) {
        public static final ElementChanges NONE = new ElementChanges(List.of(), List.of(), List.of());

        public ElementChanges {
            added = List.copyOf(added);
            changed = List.copyOf(changed);
            removed = List.copyOf(removed);
        }
    }
}
