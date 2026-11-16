package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.command.RenameDefinitionCommand;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.library.DefinitionNames;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.library.ComponentForm;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A machine's own name (its component's, «Máquina 1» when made; hand test of hito 2, round 2): what the tab's
 * «Nombre» and the piece's «Nombre de la máquina» in Propiedades edit. The name follows a component's rules
 * ({@link DefinitionNames}: required, not another component's of the circuit or of «Mis componentes», case and outer
 * spaces aside, as the palette's «Renombrar…» checks it) and says what is wrong as «Guardar como componente» says it;
 * the change is one undo step of the circuit, made on the circuit itself (the definition lives there, whatever tab
 * the edit comes from), and everything that shows the name (the tab, the breadcrumb, «Valores de», the palette, the
 * piece's face) reads it from the definition.
 *
 * <p>A machine that is in «Mis componentes» is shown in the palette with the library's name, so it is not renamed
 * here (the palette and the library would keep the old one): Propiedades says to rename it from the palette.
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

    /**
     * Why {@code text} cannot be the name of {@code definitionId}, as the user reads it; empty when it can (keeping
     * the name it has always can).
     */
    static Optional<String> problem(OpenDocument place, ComponentLibrary library, String definitionId, String text) {
        Optional<String> current = nameOf(place, definitionId);
        if (current.filter(text.strip()::equals).isPresent()) {
            return Optional.empty();
        }
        if (library.ids().contains(definitionId)) {
            return Optional.of(Messages.format("machine.name.inLibrary", current.orElse(text.strip())));
        }
        List<String> others = new ArrayList<>();
        circuitOf(place).document().definitions().stream()
                .filter(definition -> !definition.id().equals(definitionId))
                .forEach(definition -> others.add(definition.name()));
        library.entries().stream()
                .filter(entry -> !entry.root().id().equals(definitionId))
                .forEach(entry -> others.add(entry.root().name()));
        return DefinitionNames.problem(text, others).map(found -> ComponentForm.describe(found, text.strip()));
    }

    /**
     * Renames {@code definitionId} to {@code text} (without its outer spaces) as one undo step of the circuit, when
     * it is a good name ({@link #problem}) and not the one it has; returns whether it did.
     */
    static boolean rename(OpenDocument place, ComponentLibrary library, String definitionId, String text) {
        if (problem(place, library, definitionId, text).isPresent()
                || nameOf(place, definitionId).filter(text.strip()::equals).isPresent()) {
            return false;
        }
        circuitOf(place).execute(new RenameDefinitionCommand(definitionId, text.strip()));
        return true;
    }
}
