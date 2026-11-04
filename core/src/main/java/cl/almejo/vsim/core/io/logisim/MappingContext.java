package cl.almejo.vsim.core.io.logisim;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;

import java.util.Objects;

/**
 * What the mapper needs beyond the component: the circuit it is in (for the notes), VSim's registry (every part is
 * resolved by it), and what only the nets step knows: which way a splitter goes.
 */
public record MappingContext(String circuit, ComponentTypeRegistry registry, SplitterDirection splitterDirection) {

    public MappingContext {
        Objects.requireNonNull(circuit, "circuit");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(splitterDirection, "splitterDirection");
    }

    /** A context for {@code circuit}: splitters split until the nets step says otherwise. */
    public static MappingContext of(String circuit, ComponentTypeRegistry registry) {
        return new MappingContext(circuit, registry, SplitterDirection.SPLIT);
    }

    public MappingContext withSplitterDirection(SplitterDirection direction) {
        return new MappingContext(circuit, registry, direction);
    }
}
