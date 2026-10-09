package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;

import java.util.Map;

public final class ChangeParametersCommand implements Command {

    private final ElementId id;
    private final String typeId;
    private final Map<String, Object> parameters;
    private Map<String, Object> previousParameters;

    /** Normalizes {@code rawParameters} with {@code type}; invalid values throw IllegalArgumentException here. */
    public ChangeParametersCommand(ElementId id, ComponentType type, Map<String, Object> rawParameters) {
        this.id = id;
        this.typeId = type.id();
        this.parameters = type.resolveParameters(rawParameters);
    }

    @Override
    public void apply(CircuitDocument document) {
        Component component = document.component(id).orElseThrow();
        if (!component.typeId().equals(typeId)) {
            throw new IllegalArgumentException(id + " is a " + component.typeId() + ", not a " + typeId);
        }
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
