package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;

import java.util.Map;

public final class ChangeParametersCommand implements Command {

    private final ElementId id;
    private final Map<String, Object> parameters;
    private Map<String, Object> previousParameters;

    public ChangeParametersCommand(ElementId id, Map<String, Object> parameters) {
        this.id = id;
        this.parameters = Map.copyOf(parameters);
    }

    @Override
    public void apply(CircuitDocument document) {
        Component component = document.component(id).orElseThrow();
        previousParameters = component.parameters();
        document.replace(component.withParameters(parameters));
    }

    @Override
    public void revert(CircuitDocument document) {
        document.replace(document.component(id).orElseThrow().withParameters(previousParameters));
    }

    @Override
    public String description() {
        return "command.configure";
    }
}
