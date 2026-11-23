package cl.almejo.vsim.core.machine;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.InstancePaths;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.simulation.InstancePath;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Where the copies of a machine are in a circuit, by their simulation path ({@code w/m}): a problem of the machine's
 * model is shown on its first copy, a problem of one running copy names the machine the copy is of.
 */
public final class MachineCopies {

    private MachineCopies() {
    }

    /** The path of the first copy of {@code definitionId} in {@code root}, parents first; empty when none is placed. */
    public static Optional<String> firstPathOf(CircuitDocument root, String definitionId) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(definitionId, "definitionId");
        return InstancePaths.usesOf(root, definitionId).stream().findFirst().map(InstancePaths.InstanceUse::path);
    }

    /** The definition of the piece at {@code path} of {@code root} ({@code w/m}: piece m inside piece w). */
    public static Optional<ComponentDefinition> definitionAt(CircuitDocument root, String path) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(path, "path");
        List<Component> level = root.components();
        Optional<ComponentDefinition> definition = Optional.empty();
        for (String segment : InstancePath.segments(path)) {
            ElementId id = new ElementId(segment);
            Optional<Component> piece = level.stream().filter(component -> component.id().equals(id)).findFirst();
            definition = piece.flatMap(component -> SubcircuitTypes.definitionIdOf(component.typeId()))
                    .flatMap(root::definition);
            if (definition.isEmpty()) {
                return Optional.empty();
            }
            level = definition.get().body().components();
        }
        return definition;
    }
}
