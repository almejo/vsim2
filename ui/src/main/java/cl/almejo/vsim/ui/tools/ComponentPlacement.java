package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;

import java.util.List;
import java.util.Map;

/** Adds one component with default parameters as one undo step and selects it. */
public final class ComponentPlacement {

    private ComponentPlacement() {
    }

    public static Component place(ToolContext context, ComponentType type, GridPoint position, Rotation rotation) {
        Component component = type.newComponent(context.openDocument().document().newComponentId(), position,
                rotation, Map.of(), "");
        context.openDocument().execute(new AddElementsCommand(List.of(component)));
        context.selection().select(component.id());
        context.repaint();
        return component;
    }
}
