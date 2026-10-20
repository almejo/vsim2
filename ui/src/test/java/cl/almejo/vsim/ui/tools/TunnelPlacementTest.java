package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.tunnel.Tunnels;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** New tunnels from the palette propose a name and a colour. */
class TunnelPlacementTest {

    private final TestToolContext context = TestToolContext.withNewDocument();
    private final ComponentType entrance = context.openDocument().registry()
            .require(StandardComponentTypes.TUNNEL_ENTRANCE);
    private final ComponentType exit = context.openDocument().registry().require(StandardComponentTypes.TUNNEL_EXIT);

    @AfterEach
    void close() {
        context.close();
    }

    private Component placeAt(ComponentType type, double x) {
        new PlaceComponentTool(type).mousePressed(context, TestToolContext.at(x, 0));
        return context.openDocument().document().components().getLast();
    }

    @Test
    void eachNewEntradaTakesTheNextFreeNameAndColour() {
        Component first = placeAt(entrance, 0);
        Component second = placeAt(entrance, 20);

        assertThat(first.parameters()).containsEntry(Tunnels.NAME, "T1").containsEntry(Tunnels.COLOR, 0);
        assertThat(second.parameters()).containsEntry(Tunnels.NAME, "T2").containsEntry(Tunnels.COLOR, 1);
    }

    @Test
    void aNewSalidaTakesTheNameOfTheLastEntradaPlaced() {
        placeAt(entrance, 0);
        placeAt(entrance, 20);

        assertThat(placeAt(exit, 40).parameters()).containsEntry(Tunnels.NAME, "T2");
    }

    @Test
    void aNewSalidaWithoutEntradasIsNamedT1() {
        assertThat(placeAt(exit, 0).parameters()).containsEntry(Tunnels.NAME, "T1");
    }

    @Test
    void theGhostShowsTheNameTheTunnelWillTake() {
        placeAt(entrance, 0);
        PlaceComponentTool tool = new PlaceComponentTool(entrance);
        tool.mouseMoved(context, TestToolContext.at(5, 9));
        tool.paintOverlay(TestToolContext.scratchGraphics(), context);

        assertThat(context.ghosts).singleElement().isInstanceOfSatisfying(Component.class,
                ghost -> assertThat(ghost.parameters()).containsEntry(Tunnels.NAME, "T2"));
    }
}
