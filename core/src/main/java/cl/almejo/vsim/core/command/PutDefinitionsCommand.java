package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.library.ComponentDefinition;

import java.util.ArrayList;
import java.util.List;

/**
 * Adds the definitions whose id the document lacks (dependencies first). A definition the document already has is
 * kept as it is, even if it differs: replacing is {@link ReplaceDefinitionCommand}'s job.
 */
public final class PutDefinitionsCommand implements Command {

    private final List<ComponentDefinition> definitions;
    private List<String> addedIds = List.of();

    public PutDefinitionsCommand(List<ComponentDefinition> definitions) {
        this.definitions = List.copyOf(definitions);
    }

    @Override
    public void apply(CircuitDocument document) {
        List<String> added = new ArrayList<>();
        document.batch(() -> {
            try {
                for (ComponentDefinition definition : definitions) {
                    if (document.definition(definition.id()).isEmpty()) {
                        document.putDefinition(definition);
                        added.add(definition.id());
                    }
                }
            } catch (RuntimeException exception) {
                for (int index = added.size() - 1; index >= 0; index--) {
                    document.removeDefinition(added.get(index));
                }
                throw exception;
            }
        });
        addedIds = List.copyOf(added);
    }

    @Override
    public void revert(CircuitDocument document) {
        document.batch(() -> {
            for (int index = addedIds.size() - 1; index >= 0; index--) {
                document.removeDefinition(addedIds.get(index));
            }
        });
    }

    /** Ids added by the last {@link #apply}. */
    public List<String> addedIds() {
        return addedIds;
    }

    @Override
    public String description() {
        return "command.add";
    }
}
