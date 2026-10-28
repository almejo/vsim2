package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.ui.snap.SnappedPlacement;
import cl.almejo.vsim.ui.tunnel.NewTunnels;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Adds one component with its starting parameters (the defaults; a tunnel proposes a name and a colour, see
 * {@link NewTunnels}; a palette preset such as NAND adds its own, {@code negate}) as one undo step and selects it.
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
        return snapped(context, type, Map.of(), raw, rotation);
    }

    /** Like {@link #snapped(ToolContext, ComponentType, GridPoint, Rotation)} for a part with a preset's parameters. */
    public static SnappedPlacement snapped(ToolContext context, ComponentType type, Map<String, Object> presetParameters,
                                           GridPoint raw, Rotation rotation) {
        Component atOrigin = type.newComponent(PREVIEW_ID, GridPoint.ORIGIN, rotation,
                startingParameters(context, type, presetParameters), "");
        return SnappedPlacement.ofBox(context, Optional.of(type.bounds(atOrigin)), context.openDocument().registry(),
                raw);
    }

    public static Component place(ToolContext context, ComponentType type, GridPoint position, Rotation rotation) {
        return place(context, type, Map.of(), position, rotation);
    }

    /**
     * Places {@code type} with {@code presetParameters} on top of its starting parameters (a NAND: an {@code and}
     * with {@code negate}); «Recientes» counts it by the entry it came from ({@code nand}).
     */
    public static Component place(ToolContext context, ComponentType type, Map<String, Object> presetParameters,
                                  GridPoint position, Rotation rotation) {
        Component component = type.newComponent(context.openDocument().document().newComponentId(), position,
                rotation, startingParameters(context, type, presetParameters), "");
        context.openDocument().execute(new AddElementsCommand(List.of(component)));
        NewTunnels.placed(context.openDocument(), component);
        context.selection().select(component.id());
        context.componentPlaced(StandardComponentTypes.nameId(type.id(), component.parameters()));
        context.repaint();
        return component;
    }

    /** What a new {@code type} starts with: the tunnel's proposal (if a tunnel), then the preset's parameters. */
    static Map<String, Object> startingParameters(ToolContext context, ComponentType type,
                                                  Map<String, Object> presetParameters) {
        Map<String, Object> parameters = new HashMap<>(NewTunnels.parameters(context.openDocument(), type));
        parameters.putAll(presetParameters);
        return parameters;
    }
}
