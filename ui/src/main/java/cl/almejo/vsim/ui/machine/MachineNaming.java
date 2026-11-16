package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.command.RenameDefinitionCommand;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionNames;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.library.ComponentForm;

import java.util.List;
import java.util.Optional;

/**
 * A machine's own name (its component's, «Máquina 1» when made; hand test of hito 2, round 2): what the tab's
 * «Nombre» and the piece's «Nombre de la máquina» in Propiedades edit. The name follows a component's rules
 * ({@link DefinitionNames}: required, not another component's of the circuit, case and outer spaces aside) and says
 * what is wrong as «Guardar como componente» says it; the change is one undo step of the circuit, made on the circuit
 * itself (the definition lives there, whatever tab the edit comes from), and everything that shows the name (the
 * tab, the breadcrumb, «Valores de», the palette, the piece's face) reads it from the definition.
 */
final class MachineNaming {

    private MachineNaming() {
    }

    /** The circuit {@code place} belongs to: itself, or the document of a component's tab. */
    static OpenDocument circuitOf(OpenDocument place) {
        return place.parent().orElse(place);
    }

    /** The name of the machine {@code definitionId} in {@code place}'s circuit; empty when there is none. */
    static Optional<String> nameOf(OpenDocument place, String definitionId) {
        return circuitOf(place).document().definition(definitionId).map(ComponentDefinition::name);
    }

    /** Why {@code text} cannot be the name of {@code definitionId}, as the user reads it; empty when it can. */
    static Optional<String> problem(OpenDocument place, String definitionId, String text) {
        List<String> others = circuitOf(place).document().definitions().stream()
                .filter(definition -> !definition.id().equals(definitionId))
                .map(ComponentDefinition::name)
                .toList();
        return DefinitionNames.problem(text, others).map(found -> ComponentForm.describe(found, text));
    }

    /**
     * Renames {@code definitionId} to {@code text} (without its outer spaces) as one undo step of the circuit, when
     * it is a good name ({@link #problem}) and not the one it has; returns whether it did.
     */
    static boolean rename(OpenDocument place, String definitionId, String text) {
        if (problem(place, definitionId, text).isPresent()
                || nameOf(place, definitionId).filter(text.strip()::equals).isPresent()) {
            return false;
        }
        circuitOf(place).execute(new RenameDefinitionCommand(definitionId, text.strip()));
        return true;
    }
}
