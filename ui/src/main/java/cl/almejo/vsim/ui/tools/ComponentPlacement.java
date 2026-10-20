package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.ui.tunnel.NewTunnels;

import java.util.List;

/**
 * Adds one component with its starting parameters (the defaults; a tunnel proposes a name and a colour, see
 * {@link NewTunnels}) as one undo step and selects it.
 */
public final class ComponentPlacement {

    private ComponentPlacement() {
    }

    public static Component place(ToolContext context, ComponentType type, GridPoint position, Rotation rotation) {
        Component component = type.newComponent(context.openDocument().document().newComponentId(), position,
                rotation, NewTunnels.parameters(context.openDocument(), type), "");
        context.openDocument().execute(new AddElementsCommand(List.of(component)));
        NewTunnels.placed(context.openDocument(), component);
        context.selection().select(component.id());
        context.repaint();
        return component;
    }
}
