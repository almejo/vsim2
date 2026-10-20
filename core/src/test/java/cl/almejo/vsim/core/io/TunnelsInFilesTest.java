package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.tunnel.TunnelCircuit;
import cl.almejo.vsim.core.tunnel.Tunnels;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TunnelsInFilesTest {

    private final TunnelCircuit circuit = new TunnelCircuit();
    private final CircuitFormat format = new CircuitFormat(circuit.registry());

    @Test
    void tunnelsKeepTheirKindNameColourAndRotation() throws CircuitFormatException {
        Component entrance = circuit.entrance("CLK", 3, new GridPoint(0, 0));
        Component exit = circuit.place(StandardComponentTypes.TUNNEL_EXIT, 20, 20, Rotation.R270,
                Map.of(Tunnels.NAME, "CLK"));

        CircuitDocument loaded = format.read(format.write(circuit.document()));

        assertThat(loaded.component(entrance.id())).contains(entrance);
        assertThat(loaded.component(exit.id())).contains(exit);
        assertThat(loaded.component(entrance.id()).orElseThrow().parameters())
                .isEqualTo(Map.of(Tunnels.NAME, "CLK", Tunnels.COLOR, 3));
    }

    @Test
    void aTunnelWithoutParametersInTheFileGetsTheDefaults() throws CircuitFormatException {
        circuit.entrance("CLK", new GridPoint(0, 0));
        String json = format.write(circuit.document()).replaceAll("\"parameters\"\\s*:\\s*\\{[^}]*}", "\"parameters\" : { }");

        CircuitDocument loaded = format.read(json);

        assertThat(Tunnels.nameOf(loaded.components().get(0))).isEqualTo("T1");
    }

    @Test
    void writingWhatWasReadGivesTheSameText() throws CircuitFormatException {
        circuit.entrance("CLK", 3, new GridPoint(0, 0));
        circuit.exit("CLK", new GridPoint(10, 10));
        String written = format.write(circuit.document());

        assertThat(format.write(format.read(written))).isEqualTo(written);
    }
}
