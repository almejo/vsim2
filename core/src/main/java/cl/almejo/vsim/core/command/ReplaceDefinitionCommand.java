package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;

/** Replaces a definition (adding missing dependencies); every instance follows. Revert restores the old one exactly. */
public final class ReplaceDefinitionCommand implements Command {

    private final ComponentDefinition replacement;
    private final PutDefinitionsCommand dependencies;
    private ComponentDefinition previous;

    public ReplaceDefinitionCommand(DefinitionBundle bundle) {
        this.replacement = bundle.root();
        this.dependencies = new PutDefinitionsCommand(bundle.dependencies());
    }

    @Override
    public void apply(CircuitDocument document) {
        document.batch(() -> {
            dependencies.apply(document);
            try {
                previous = document.putDefinition(replacement).orElse(null);
            } catch (RuntimeException exception) {
                dependencies.revert(document);
                throw exception;
            }
        });
    }

    @Override
    public void revert(CircuitDocument document) {
        document.batch(() -> {
            if (previous == null) {
                document.removeDefinition(replacement.id());
            } else {
                document.putDefinition(previous);
            }
            dependencies.revert(document);
        });
    }

    @Override
    public String description() {
        return "command.updateDefinition";
    }
}
