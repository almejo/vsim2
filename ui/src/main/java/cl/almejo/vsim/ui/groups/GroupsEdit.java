package cl.almejo.vsim.ui.groups;

import cl.almejo.vsim.core.command.ChangeParametersWithWiresCommand;
import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.BitGroups;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.panels.PinJoinRefusal;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Reads and changes the groups of a «Separador de bus» or «Unión de bus». A change is one
 * {@link ChangeParametersWithWiresCommand} (one undo step, «Deshacer cambiar grupos de bits»): a wire follows the pin
 * that keeps its bits (the same name) and a wire whose pin disappears stays where it was. It stores the canonical
 * text, so one bit per pin is {@code ""}, as in every circuit made before groups. A change that would join a pin to a
 * wire of its own width it was not on is refused first ({@link #refusal}).
 */
final class GroupsEdit {

    /** The undo name of a change of groups. */
    static final String DESCRIPTION = "command.busGroups";

    private GroupsEdit() {
    }

    /** Whether {@code component} is a bus splitter or joiner, the parts that have groups. */
    static boolean hasGroups(Component component) {
        return StandardComponentTypes.hasBitGroups(component.typeId());
    }

    /** Whether {@code component} is a joiner (its unused bits are Z on the bus) rather than a splitter. */
    static boolean isJoiner(Component component) {
        return StandardComponentTypes.BUS_JOINER.equals(component.typeId());
    }

    /** The groups of the bus part {@code component} now. */
    static BitGroups groupsOf(OpenDocument openDocument, Component component) {
        ComponentType type = openDocument.registry().typeOf(component);
        Map<String, Object> resolved = type.resolveParameters(component.parameters());
        return BitGroups.parse((String) resolved.get(StandardComponentTypes.GROUPS),
                (Integer) resolved.get(StandardComponentTypes.WIDTH));
    }

    /** The edit that gives part {@code id} {@code groups}; empty when the part is gone or already has them. */
    static Optional<Command> command(OpenDocument openDocument, ElementId id, BitGroups groups) {
        Optional<Component> component = openDocument.document().component(id).filter(GroupsEdit::hasGroups);
        if (component.isEmpty() || groupsOf(openDocument, component.get()).equals(groups)) {
            return Optional.empty();
        }
        return Optional.of(ChangeParametersWithWiresCommand.of(openDocument.document(), openDocument.registry(), id,
                valuesWith(openDocument, component.get(), groups), DESCRIPTION));
    }

    /**
     * Why giving part {@code id} {@code groups} is refused ({@link PinJoinRefusal}: a pin would join a wire of its own
     * width it was not on), empty when the change is fine or the part is gone.
     */
    static Optional<String> refusal(OpenDocument openDocument, ElementId id, BitGroups groups) {
        return openDocument.document().component(id).filter(GroupsEdit::hasGroups)
                .flatMap(component -> PinJoinRefusal.of(openDocument, id, valuesWith(openDocument, component, groups)));
    }

    /** The part's parameters with {@code groups} (and their width), the groups in canonical text. */
    private static Map<String, Object> valuesWith(OpenDocument openDocument, Component component, BitGroups groups) {
        ComponentType type = openDocument.registry().typeOf(component);
        Map<String, Object> values = new HashMap<>(type.resolveParameters(component.parameters()));
        values.put(StandardComponentTypes.WIDTH, groups.width());
        values.put(StandardComponentTypes.GROUPS, groups.canonical());
        return values;
    }
}
