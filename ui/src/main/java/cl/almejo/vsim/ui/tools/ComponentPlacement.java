package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.ui.snap.SnappedPlacement;
import cl.almejo.vsim.ui.tunnel.NewTunnels;

import java.util.List;
import java.util.Optional;

/**
 * Adds one component with its starting parameters (the defaults; a tunnel proposes a name and a colour, see
 * {@link NewTunnels}) as one undo step and selects it.
 */
public final class ComponentPlacement {

    private static final ElementId PREVIEW_ID = new ElementId("placement");

    private ComponentPlacement() {
    }

    /**
     * Where a new {@code type} turned by {@code rotation} lands when the pointer is on {@code raw}: the magnet's
     * answer for its box (the ghost of {@link PlaceComponentTool}, a palette drop).
     */
    public static SnappedPlacement snapped(ToolContext context, ComponentType type, GridPoint raw, Rotation rotation) {
        Component atOrigin = type.newComponent(PREVIEW_ID, GridPoint.ORIGIN, rotation,
                NewTunnels.parameters(context.openDocument(), type), "");
        return SnappedPlacement.ofBox(context, Optional.of(type.bounds(atOrigin)), context.openDocument().registry(),
                raw);
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
