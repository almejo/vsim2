package cl.almejo.vsim.ui.tunnel;

import cl.almejo.vsim.core.command.ChangeParametersCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.tunnel.Tunnels;
import cl.almejo.vsim.ui.app.OpenDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/** The parameter change shared by «Configurar…» and the inspector follows the tunnel rename rules (spec §7). */
class TunnelNamesTest {

    private final OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());

    @AfterEach
    void close() {
        openDocument.close();
    }

    private Component entrance(String name, int y) {
        return place(openDocument, StandardComponentTypes.TUNNEL_ENTRANCE, 0, y, Map.of(Tunnels.NAME, name));
    }

    private Component exit(String name, int y) {
        return place(openDocument, StandardComponentTypes.TUNNEL_EXIT, 20, y, Map.of(Tunnels.NAME, name));
    }

    private Component current(Component component) {
        return openDocument.document().component(component.id()).orElseThrow();
    }

    private void configure(Component component, Map<String, Object> changes) {
        ComponentType type = openDocument.registry().typeOf(component);
        Map<String, Object> values = new HashMap<>(component.parameters());
        values.putAll(changes);
        TunnelNames.applyParameters(openDocument, component.id(), type, values);
    }

    @Test
    void configuringAnEntrancesNameRenamesItsExitsInOneUndoStep() {
        Component entrance = entrance("CLK", 0);
        Component first = exit("CLK", 10);
        Component second = exit("CLK", 20);

        configure(entrance, Map.of(Tunnels.NAME, "TICK", Tunnels.COLOR, 3));

        assertThat(Tunnels.nameOf(current(entrance))).isEqualTo("TICK");
        assertThat(Tunnels.colorParameterOf(current(entrance))).isEqualTo(3);
        assertThat(Tunnels.nameOf(current(first))).isEqualTo("TICK");
        assertThat(Tunnels.nameOf(current(second))).isEqualTo("TICK");
        assertThat(openDocument.lastTunnelEntranceName()).contains("TICK");

        openDocument.history().undo();
        assertThat(Tunnels.nameOf(current(entrance))).isEqualTo("CLK");
        assertThat(Tunnels.colorParameterOf(current(entrance))).isZero();
        assertThat(Tunnels.nameOf(current(first))).isEqualTo("CLK");
    }

    @Test
    void configuringOnlyTheColourDoesNotRememberTheName() {
        Component entrance = entrance("CLK", 0);

        configure(entrance, Map.of(Tunnels.COLOR, 5));

        assertThat(Tunnels.colorParameterOf(current(entrance))).isEqualTo(5);
        assertThat(openDocument.lastTunnelEntranceName()).isEmpty();
    }

    @Test
    void configuringAnExitsNameRelinksOnlyThatExit() {
        entrance("CLK", 0);
        Component renamed = exit("CLK", 10);
        Component kept = exit("CLK", 20);

        configure(renamed, Map.of(Tunnels.NAME, "RESET"));

        assertThat(Tunnels.nameOf(current(renamed))).isEqualTo("RESET");
        assertThat(Tunnels.nameOf(current(kept))).isEqualTo("CLK");
        assertThat(openDocument.lastTunnelEntranceName()).isEmpty();
    }

    @Test
    void anyOtherPartGetsAPlainParameterChange() {
        Component constant = place(openDocument, StandardComponentTypes.CONSTANT, 0, 0, Map.of());
        ComponentType type = openDocument.registry().typeOf(constant);
        Map<String, Object> values = new HashMap<>(constant.parameters());
        values.put("value", "1");

        assertThat(TunnelNames.parametersCommand(openDocument, constant.id(), type, values))
                .isInstanceOf(ChangeParametersCommand.class);
    }
}
