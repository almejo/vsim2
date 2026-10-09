package cl.almejo.vsim.core.library;

import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.document.ElementId;

import java.util.Objects;

/** A pin of a template box and the {@code input-pin}/{@code output-pin} of the body it connects to. */
public record DefinitionPin(PinSpec spec, ElementId source) {
    public DefinitionPin {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(source, "source");
    }
}
